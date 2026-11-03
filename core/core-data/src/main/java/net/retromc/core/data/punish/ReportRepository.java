package net.retromc.core.data.punish;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.retromc.core.api.event.ReportClosed;
import net.retromc.core.api.event.ReportNew;
import net.retromc.core.api.punish.ChatLine;
import net.retromc.core.api.punish.Chatlog;
import net.retromc.core.api.punish.PunishmentId;
import net.retromc.core.api.punish.Report;
import net.retromc.core.api.punish.ReportReason;
import net.retromc.core.api.punish.ReportRequest;
import net.retromc.core.api.punish.ReportResult;
import net.retromc.core.api.punish.ReportService;
import net.retromc.core.api.punish.ReportStatus;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import org.jspecify.annotations.Nullable;

/**
 * {@link ReportService} auf {@code moderation.report} und {@code moderation.chatlog} (Spec 01 §6.9). Sperren in
 * Valkey: {@code cd:report:{reporter}:{target}} (300 s) und {@code cd:report:{reporter}} (Zähler je Stunde);
 * Chatpuffer {@code chatbuf:{server}} (200 Zeilen JSON, 15 min). Ereignisse {@code REPORT_NEW}, {@code REPORT_CLOSED}.
 */
public final class ReportRepository implements ReportService {

    private static final String COLUMNS = "id, public_id, reporter, target, reason, server, match_id, chatlog_id,"
            + " status, handled_by, created_at, handled_at";
    private static final int ID_ATTEMPTS = 8;
    private static final Gson GSON = new Gson();

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<RankService> ranks;
    private final EventSink events;
    private final Clock clock;

    public ReportRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Supplier<RankService> ranks, EventSink events, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<ReportResult> create(ReportRequest request) {
        UUID reporter = request.reporter();
        UUID target = request.target();
        Optional<ReportResult.Outcome> local = ReportRules.precheck(reporter, target, request.reason(),
                request.serverMode());
        if (local.isPresent()) {
            return CompletableFuture.completedFuture(new ReportResult(local.get(), null));
        }
        String cooldown = ValkeyKeys.reportCooldown(reporter, target);
        String hourly = ValkeyKeys.reportHourly(reporter);
        return ranks.get().rank(target).thenCompose(rank -> {
            if (rank.isTeam()) {
                return done(ReportResult.Outcome.STAFF);
            }
            return Jdbc.future(valkey.exists(cooldown)).thenCombine(Jdbc.future(valkey.get(hourly)),
                    (blocked, count) -> ReportRules.limits(blocked > 0, count == null ? 0 : Integer.parseInt(count)))
                    .thenCompose(limit -> limit.isPresent()
                            ? done(limit.get())
                            : store(request).thenCompose(report -> Jdbc.future(valkey.set(cooldown, "1",
                                    SetArgs.Builder.ex(ValkeyKeys.REPORT_TARGET_COOLDOWN_TTL.toSeconds())))
                                    .thenCompose(ignored -> Jdbc.future(valkey.incr(hourly)))
                                    .thenCompose(value -> value == 1
                                            ? Jdbc.future(valkey.expire(hourly,
                                                    ValkeyKeys.REPORT_HOURLY_TTL.toSeconds())).thenApply(ok -> report)
                                            : CompletableFuture.completedFuture(report))
                                    .thenApply(stored -> {
                                        events.publish(new ReportNew(stored.publicId(), reporter, target,
                                                stored.reason().key(), stored.server()));
                                        return new ReportResult(ReportResult.Outcome.CREATED, stored);
                                    })));
        });
    }

    private static CompletableFuture<ReportResult> done(ReportResult.Outcome outcome) {
        return CompletableFuture.completedFuture(new ReportResult(outcome, null));
    }

    private CompletableFuture<Report> store(ReportRequest request) {
        CompletableFuture<Optional<Chatlog>> chatlog = request.reason().needsChatlog()
                ? createChatlog(request.reporter(), request.target(), request.server())
                : CompletableFuture.completedFuture(Optional.empty());
        Instant now = clock.instant();
        return chatlog.thenCompose(log -> database.transaction(connection -> {
            for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
                String publicId = PunishmentId.newReportId();
                Optional<Report> row = Jdbc.one(connection, "INSERT INTO moderation.report (public_id, reporter,"
                        + " target, reason, server, match_id, chatlog_id, status, created_at)"
                        + " SELECT ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?"
                        + " WHERE NOT EXISTS (SELECT 1 FROM moderation.report WHERE public_id = ?)"
                        + " RETURNING " + COLUMNS, ReportRepository::map, publicId, request.reporter(),
                        request.target(), request.reason().key(), request.server(), request.matchId(),
                        log.map(Chatlog::id).orElse(null), now, publicId);
                if (row.isPresent()) {
                    return row.get();
                }
            }
            throw new SQLException("Keine freie Report-ID nach " + ID_ATTEMPTS + " Versuchen");
        }));
    }

    @Override
    public CompletableFuture<List<Report>> open() {
        return database.query(connection -> Jdbc.list(connection, "SELECT " + COLUMNS + " FROM moderation.report"
                + " WHERE status IN ('OPEN', 'CLAIMED') ORDER BY created_at, id", ReportRepository::map));
    }

    @Override
    public CompletableFuture<Optional<Report>> claim(String publicId, UUID staff) {
        return database.transaction(connection -> Jdbc.one(connection, "UPDATE moderation.report"
                + " SET status = 'CLAIMED', handled_by = ? WHERE public_id = ? AND status = 'OPEN' RETURNING "
                + COLUMNS, ReportRepository::map, staff, publicId));
    }

    @Override
    public CompletableFuture<Optional<Report>> close(String publicId, UUID staff, boolean justified) {
        ReportStatus status = justified ? ReportStatus.ACCEPTED : ReportStatus.REJECTED;
        return database.transaction(connection -> Jdbc.one(connection, "UPDATE moderation.report"
                + " SET status = ?, handled_by = ?, handled_at = ? WHERE public_id = ?"
                + " AND status IN ('OPEN', 'CLAIMED') RETURNING " + COLUMNS, ReportRepository::map,
                status.name(), staff, clock.instant(), publicId))
                .thenApply(closed -> {
                    closed.ifPresent(report -> events.publish(new ReportClosed(report.publicId(), report.reporter(),
                            report.target(), report.reason().key(), report.server(), justified)));
                    return closed;
                });
    }

    @Override
    public CompletableFuture<List<Report>> resultsSince(UUID reporter, Instant since) {
        return database.query(connection -> Jdbc.list(connection, "SELECT " + COLUMNS + " FROM moderation.report"
                + " WHERE reporter = ? AND status IN ('ACCEPTED', 'REJECTED') AND handled_at > ?"
                + " ORDER BY handled_at", ReportRepository::map, reporter, since));
    }

    @Override
    public CompletableFuture<Void> bufferChat(String server, ChatLine line) {
        String key = ValkeyKeys.chatBuffer(server);
        return Jdbc.future(valkey.lpush(key, toJson(line).toString()))
                .thenCompose(ignored -> Jdbc.future(valkey.ltrim(key, 0, CHAT_BUFFER_LINES - 1)))
                .thenCompose(ignored -> Jdbc.future(valkey.expire(key, ValkeyKeys.CHAT_BUFFER_TTL.toSeconds())))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Optional<Chatlog>> createChatlog(@Nullable UUID creator, UUID target, String server) {
        Instant now = clock.instant();
        Instant from = now.minus(CHAT_BUFFER_WINDOW);
        return Jdbc.future(valkey.lrange(ValkeyKeys.chatBuffer(server), 0, -1)).thenCompose(raw -> {
            List<ChatLine> lines = new ArrayList<>();
            for (int i = raw.size() - 1; i >= 0; i--) {
                ChatLine line = parse(raw.get(i));
                if (line != null && !line.at().isBefore(from)) {
                    lines.add(line);
                }
            }
            if (lines.stream().noneMatch(line -> line.uuid().equals(target))) {
                return CompletableFuture.completedFuture(Optional.<Chatlog>empty());
            }
            Chatlog chatlog = new Chatlog(UUID.randomUUID(), creator, target, server, lines, now);
            return database.transaction(connection -> Jdbc.update(connection, "INSERT INTO moderation.chatlog"
                    + " (id, created_by, target, server, messages, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
                    chatlog.id(), creator, target, server, toJson(lines), now))
                    .thenApply(ignored -> Optional.of(chatlog));
        });
    }

    @Override
    public CompletableFuture<Optional<Chatlog>> chatlog(UUID id) {
        return database.query(connection -> Jdbc.one(connection, "SELECT id, created_by, target, server, messages,"
                + " created_at FROM moderation.chatlog WHERE id = ?", row -> {
                    List<ChatLine> lines = new ArrayList<>();
                    for (var element : JsonParser.parseString(row.getString("messages")).getAsJsonArray()) {
                        ChatLine line = parse(element.toString());
                        if (line != null) {
                            lines.add(line);
                        }
                    }
                    Instant created = Jdbc.instant(row, "created_at");
                    return new Chatlog(Jdbc.requireUuid(row, "id"), Jdbc.uuid(row, "created_by"),
                            Jdbc.requireUuid(row, "target"), row.getString("server"), lines,
                            created == null ? Instant.EPOCH : created);
                }, id));
    }

    static JsonObject toJson(ChatLine line) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", line.uuid().toString());
        json.addProperty("name", line.name());
        json.addProperty("text", line.text());
        json.addProperty("ts", line.at().toEpochMilli());
        return json;
    }

    private static String toJson(List<ChatLine> lines) {
        JsonArray array = new JsonArray();
        lines.forEach(line -> array.add(toJson(line)));
        return GSON.toJson(array);
    }

    static @Nullable ChatLine parse(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return new ChatLine(UUID.fromString(object.get("uuid").getAsString()), object.get("name").getAsString(),
                    object.get("text").getAsString(), Instant.ofEpochMilli(object.get("ts").getAsLong()));
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static Report map(ResultSet row) throws SQLException {
        String reason = row.getString("reason");
        Instant created = Jdbc.instant(row, "created_at");
        return new Report(row.getLong("id"), Objects.requireNonNullElse(row.getString("public_id"), ""),
                Jdbc.requireUuid(row, "reporter"), Jdbc.requireUuid(row, "target"),
                ReportReason.byKey(reason == null ? "" : reason).orElse(ReportReason.HACKING),
                Objects.requireNonNullElse(row.getString("server"), ""), Jdbc.uuid(row, "match_id"),
                Jdbc.uuid(row, "chatlog_id"), ReportStatus.valueOf(row.getString("status")),
                Jdbc.uuid(row, "handled_by"), created == null ? Instant.EPOCH : created,
                Jdbc.instant(row, "handled_at"));
    }
}
