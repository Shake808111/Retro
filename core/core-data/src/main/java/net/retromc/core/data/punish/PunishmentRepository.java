package net.retromc.core.data.punish;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.retromc.core.api.event.Kick;
import net.retromc.core.api.event.MuteUpdate;
import net.retromc.core.api.event.PunishIssued;
import net.retromc.core.api.event.PunishRevoked;
import net.retromc.core.api.punish.Punishment;
import net.retromc.core.api.punish.PunishmentDuration;
import net.retromc.core.api.punish.PunishmentId;
import net.retromc.core.api.punish.PunishmentOrigin;
import net.retromc.core.api.punish.PunishmentReason;
import net.retromc.core.api.punish.PunishmentRequest;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.punish.PunishmentType;
import net.retromc.core.api.util.IpHmac;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import org.jspecify.annotations.Nullable;

/**
 * {@link PunishmentService} auf {@code moderation.punishment}, {@code moderation.reason} und {@code moderation.ban_ip}
 * (Spec 01 §2.2, §6.3 Schritt 2, §6.10). Caches: {@code mute:{uuid}} (HASH, TTL = Restlaufzeit; Negativ-Eintrag
 * {@code none=1} für 60 s) und {@code ban:none:{uuid}} (60 s). Ereignisse: {@code PUNISH_ISSUED},
 * {@code PUNISH_REVOKED}, {@code MUTE_UPDATE}, {@code KICK}.
 */
public final class PunishmentRepository implements PunishmentService {

    /** Versuche, eine freie Anzeige-ID zu finden (Unique-Index auf {@code public_id}). */
    static final int ID_ATTEMPTS = 8;

    private static final String COLUMNS = "id, public_id, uuid, type, reason_code, reason_text, issued_by, issued_at,"
            + " expires_at, revoked_at, revoked_by, revoke_note, evidence_ref, server, origin, ac_code, stage";
    private static final String PREFIXED_COLUMNS = Arrays.stream(COLUMNS.split(",\\s*"))
            .map(column -> "p." + column).collect(Collectors.joining(", "));
    private static final String ACTIVE = "revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)";
    private static final String NONE = "none";

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final EventSink events;
    private final Clock clock;

    public PunishmentRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey, EventSink events,
            Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<Optional<Punishment>> activeBan(UUID player, @Nullable IpHmac ip) {
        String negative = ValkeyKeys.banNone(player);
        return Jdbc.future(valkey.exists(negative)).thenCompose(cached -> {
            if (cached > 0) {
                return CompletableFuture.completedFuture(Optional.<Punishment>empty());
            }
            Instant now = clock.instant();
            return database.query(connection -> {
                Optional<Punishment> own = Jdbc.one(connection, "SELECT " + COLUMNS
                        + " FROM moderation.punishment WHERE uuid = ? AND type = 'BAN' AND " + ACTIVE
                        + " ORDER BY expires_at DESC NULLS FIRST LIMIT 1", PunishmentRepository::map, player, now);
                if (own.isPresent() || ip == null) {
                    return own;
                }
                return Jdbc.one(connection, "SELECT " + PREFIXED_COLUMNS + " FROM moderation.ban_ip b"
                        + " JOIN moderation.punishment p ON p.id = b.punishment_id"
                        + " WHERE b.ip_hmac = ? AND p.type = 'BAN'"
                        + " AND p.revoked_at IS NULL AND (p.expires_at IS NULL OR p.expires_at > ?)"
                        + " ORDER BY p.expires_at DESC NULLS FIRST LIMIT 1", PunishmentRepository::map,
                        ip.bytes(), now);
            }).thenCompose(ban -> ban.isPresent()
                    ? CompletableFuture.completedFuture(ban)
                    : Jdbc.future(valkey.set(negative, "1", SetArgs.Builder.ex(ValkeyKeys.BAN_NONE_TTL.toSeconds())))
                            .thenApply(ok -> ban));
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> activeMute(UUID player) {
        String key = ValkeyKeys.mute(player);
        return Jdbc.future(valkey.hgetall(key)).thenCompose(hash -> {
            Instant now = clock.instant();
            if (hash != null && hash.containsKey(NONE)) {
                return CompletableFuture.completedFuture(Optional.<Punishment>empty());
            }
            if (hash != null && hash.containsKey("publicId")) {
                Punishment cached = fromCache(player, hash);
                return CompletableFuture.completedFuture(cached.isActive(now) ? Optional.of(cached) : Optional.empty());
            }
            return database.query(connection -> Jdbc.one(connection, "SELECT " + COLUMNS
                    + " FROM moderation.punishment WHERE uuid = ? AND type = 'MUTE' AND " + ACTIVE
                    + " ORDER BY expires_at DESC NULLS FIRST LIMIT 1", PunishmentRepository::map, player, now))
                    .thenCompose(mute -> cacheMute(player, mute.orElse(null)).thenApply(ignored -> mute));
        });
    }

    @Override
    public CompletableFuture<Punishment> issue(PunishmentRequest request) {
        Instant now = clock.instant();
        return database.transaction(connection -> {
            PunishmentReason reason = loadReason(connection, request.reasonCode())
                    .orElseThrow(() -> new IllegalArgumentException("Unbekannter Grund: " + request.reasonCode()));
            long previous = Jdbc.count(connection, "SELECT count(*) FROM moderation.punishment"
                    + " WHERE uuid = ? AND reason_code = ? AND revoked_at IS NULL", request.target(), reason.code());
            int stage = reason.stageFor((int) Math.min(previous, Integer.MAX_VALUE));
            PunishmentDuration duration = request.durationOverride() != null
                    ? request.durationOverride() : reason.durationForStage(stage);
            Punishment punishment = insert(connection, reason.type(), request.target(), reason.code(),
                    request.reasonText(), request.issuedBy(), now, duration.expiresAt(now), request.evidenceRef(),
                    request.server(), request.origin(), request.acCode(), stage);
            if (request.ip() != null && reason.type() == PunishmentType.BAN) {
                Jdbc.update(connection, "INSERT INTO moderation.ban_ip (ip_hmac, punishment_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING", request.ip().bytes(), punishment.id());
            }
            return punishment;
        }).thenCompose(punishment -> afterIssue(punishment).thenApply(ignored -> punishment));
    }

    @Override
    public CompletableFuture<Punishment> kick(UUID player, String reasonText, @Nullable UUID actor,
            @Nullable String server) {
        return note(PunishmentType.KICK, KICK_REASON_CODE, player, reasonText, actor, server)
                .thenApply(punishment -> {
                    events.publish(new Kick(player, reasonText));
                    return punishment;
                });
    }

    @Override
    public CompletableFuture<Punishment> warn(UUID player, String reasonText, @Nullable UUID actor,
            @Nullable String server) {
        return note(PunishmentType.WARN, WARN_REASON_CODE, player, reasonText, actor, server)
                .thenApply(punishment -> {
                    events.publish(new PunishIssued(punishment.publicId(), player, PunishmentType.WARN, null));
                    return punishment;
                });
    }

    private CompletableFuture<Punishment> note(PunishmentType type, String code, UUID player, String reasonText,
            @Nullable UUID actor, @Nullable String server) {
        Objects.requireNonNull(reasonText, "reasonText");
        Instant now = clock.instant();
        return database.transaction(connection -> insert(connection, type, player, code, reasonText, actor, now, now,
                null, server, PunishmentOrigin.MANUAL, null, 1));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> revokeActive(UUID player, PunishmentType type, UUID actor,
            String note) {
        Instant now = clock.instant();
        return revokeWhere("uuid = ? AND type = ? AND " + ACTIVE, actor, note, now, player, type.name(), now);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> revoke(String publicId, UUID actor, String note) {
        Instant now = clock.instant();
        return revokeWhere("public_id = ? AND revoked_at IS NULL", actor, note, now, publicId);
    }

    private CompletableFuture<Optional<Punishment>> revokeWhere(String where, UUID actor, String note, Instant now,
            Object... parameters) {
        Object[] all = new Object[parameters.length + 3];
        all[0] = now;
        all[1] = actor;
        all[2] = note;
        System.arraycopy(parameters, 0, all, 3, parameters.length);
        return database.transaction(connection -> Jdbc.list(connection, "UPDATE moderation.punishment"
                + " SET revoked_at = ?, revoked_by = ?, revoke_note = ? WHERE " + where + " RETURNING " + COLUMNS,
                PunishmentRepository::map, all))
                .thenCompose(revoked -> {
                    List<CompletableFuture<?>> work = new ArrayList<>();
                    for (Punishment punishment : revoked) {
                        events.publish(new PunishRevoked(punishment.publicId(), punishment.uuid(), punishment.type(),
                                punishment.expiresAt()));
                        if (punishment.type() == PunishmentType.MUTE) {
                            work.add(Jdbc.future(valkey.del(ValkeyKeys.mute(punishment.uuid()))));
                            events.publish(new MuteUpdate(punishment.publicId(), punishment.uuid(),
                                    PunishmentType.MUTE, now));
                        }
                    }
                    return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> revoked.stream()
                                    .max((a, b) -> a.issuedAt().compareTo(b.issuedAt())));
                });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byPublicId(String publicId) {
        return database.query(connection -> Jdbc.one(connection, "SELECT " + COLUMNS
                + " FROM moderation.punishment WHERE public_id = ?", PunishmentRepository::map, publicId));
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID player) {
        return database.query(connection -> Jdbc.list(connection, "SELECT " + COLUMNS
                + " FROM moderation.punishment WHERE uuid = ? ORDER BY issued_at DESC, id DESC",
                PunishmentRepository::map, player));
    }

    @Override
    public CompletableFuture<List<PunishmentReason>> reasons() {
        return database.query(connection -> Jdbc.list(connection, "SELECT code, type, label_key, stages_s,"
                + " reportable, report_scope FROM moderation.reason ORDER BY type, code",
                PunishmentRepository::reason));
    }

    @Override
    public CompletableFuture<Optional<PunishmentReason>> reason(String code) {
        return database.query(connection -> loadReason(connection, code));
    }

    private static Optional<PunishmentReason> loadReason(Connection connection, String code) throws SQLException {
        return Jdbc.one(connection, "SELECT code, type, label_key, stages_s, reportable, report_scope"
                + " FROM moderation.reason WHERE code = ?", PunishmentRepository::reason, code);
    }

    private static Punishment insert(Connection connection, PunishmentType type, UUID target, String code,
            @Nullable String text, @Nullable UUID actor, Instant now, @Nullable Instant expires,
            @Nullable String evidence, @Nullable String server, PunishmentOrigin origin, @Nullable Integer acCode,
            int stage) throws SQLException {
        for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
            String publicId = PunishmentId.forPunishment(type, origin);
            Optional<Punishment> row = Jdbc.one(connection, "INSERT INTO moderation.punishment (public_id, uuid,"
                    + " type, reason_code, reason_text, issued_by, issued_at, expires_at, evidence_ref, server, origin,"
                    + " ac_code, stage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (public_id) DO NOTHING RETURNING " + COLUMNS, PunishmentRepository::map,
                    publicId, target, type.name(), code, text, actor, now, expires, evidence, server, origin.name(),
                    acCode, (short) stage);
            if (row.isPresent()) {
                return row.get();
            }
        }
        throw new SQLException("Keine freie Straf-ID nach " + ID_ATTEMPTS + " Versuchen");
    }

    private CompletableFuture<Void> afterIssue(Punishment punishment) {
        events.publish(new PunishIssued(punishment.publicId(), punishment.uuid(), punishment.type(),
                punishment.expiresAt()));
        if (punishment.type() == PunishmentType.MUTE) {
            events.publish(new MuteUpdate(punishment.publicId(), punishment.uuid(), PunishmentType.MUTE,
                    punishment.expiresAt()));
            return cacheMute(punishment.uuid(), punishment);
        }
        if (punishment.type() == PunishmentType.BAN) {
            return Jdbc.future(valkey.del(ValkeyKeys.banNone(punishment.uuid()))).thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    /** Schreibt {@code mute:{uuid}}: aktive Strafe mit Restlaufzeit oder Negativ-Eintrag für 60 s. */
    private CompletableFuture<Void> cacheMute(UUID player, @Nullable Punishment mute) {
        String key = ValkeyKeys.mute(player);
        Map<String, String> hash = new LinkedHashMap<>();
        Duration ttl;
        if (mute == null) {
            hash.put(NONE, "1");
            ttl = ValkeyKeys.BAN_NONE_TTL;
        } else {
            hash.put("id", Long.toString(mute.id()));
            hash.put("publicId", mute.publicId());
            hash.put("reasonCode", mute.reasonCode());
            hash.put("issuedMs", Long.toString(mute.issuedAt().toEpochMilli()));
            hash.put("expiresMs", mute.expiresAt() == null ? Long.toString(PunishmentDuration.PERMANENT_SECONDS)
                    : Long.toString(mute.expiresAt().toEpochMilli()));
            hash.put("stage", Integer.toString(mute.stage()));
            hash.put("origin", mute.origin().name());
            ttl = mute.remaining(clock.instant()).orElse(null);
        }
        CompletableFuture<?> write = Jdbc.future(valkey.del(key))
                .thenCompose(ignored -> Jdbc.future(valkey.hset(key, hash)));
        if (ttl != null) {
            Duration effective = ttl.isZero() ? Duration.ofMillis(1) : ttl;
            write = write.thenCompose(ignored -> Jdbc.future(valkey.pexpire(key, effective.toMillis())));
        }
        return write.thenApply(ignored -> null);
    }

    private static Punishment fromCache(UUID player, Map<String, String> hash) {
        long expires = Long.parseLong(hash.getOrDefault("expiresMs", "-1"));
        return new Punishment(Long.parseLong(hash.getOrDefault("id", "0")), hash.get("publicId"), player,
                PunishmentType.MUTE, hash.getOrDefault("reasonCode", ""), null, null,
                Instant.ofEpochMilli(Long.parseLong(hash.getOrDefault("issuedMs", "0"))),
                expires == PunishmentDuration.PERMANENT_SECONDS ? null : Instant.ofEpochMilli(expires), null, null,
                null, null, null, PunishmentOrigin.valueOf(hash.getOrDefault("origin", PunishmentOrigin.MANUAL.name())),
                null, Integer.parseInt(hash.getOrDefault("stage", "1")));
    }

    static Punishment map(ResultSet row) throws SQLException {
        int acCode = row.getInt("ac_code");
        boolean noAcCode = row.wasNull();
        Instant issued = Jdbc.instant(row, "issued_at");
        return new Punishment(row.getLong("id"), row.getString("public_id"), Jdbc.requireUuid(row, "uuid"),
                PunishmentType.valueOf(row.getString("type")), row.getString("reason_code"),
                row.getString("reason_text"), Jdbc.uuid(row, "issued_by"), issued == null ? Instant.EPOCH : issued,
                Jdbc.instant(row, "expires_at"), Jdbc.instant(row, "revoked_at"), Jdbc.uuid(row, "revoked_by"),
                row.getString("revoke_note"), row.getString("evidence_ref"), row.getString("server"),
                PunishmentOrigin.valueOf(row.getString("origin")), noAcCode ? null : acCode,
                Math.max(1, row.getInt("stage")));
    }

    private static PunishmentReason reason(ResultSet row) throws SQLException {
        Array array = row.getArray("stages_s");
        List<Long> stages = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            stages.add(((Number) value).longValue());
        }
        return new PunishmentReason(row.getString("code"), PunishmentType.valueOf(row.getString("type")),
                row.getString("label_key"), stages, row.getBoolean("reportable"), row.getString("report_scope"));
    }
}
