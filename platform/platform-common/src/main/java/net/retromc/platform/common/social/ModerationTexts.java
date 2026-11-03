package net.retromc.platform.common.social;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.punish.Punishment;
import net.retromc.core.api.punish.PunishmentDuration;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.punish.PunishmentStatus;
import net.retromc.core.api.punish.PunishmentType;
import net.retromc.core.api.util.DurationFormat;
import net.retromc.platform.common.text.I18n;

/**
 * Texte der Moderation für Proxy und Paper (Spec 01 §7 {@code screen.*}, {@code mute.notice}, {@code punish.*},
 * {@code history.*}): Bann-, Kick- und Mute-Bildschirme mit Grund, ID, Dauer ({@code 29d 13h 42m 34s}) und Restzeit.
 */
public final class ModerationTexts {

    /** Datum in {@code /history} (Europe/Berlin). */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));

    private final I18n i18n;

    public ModerationTexts(I18n i18n) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
    }

    /** Anzeige des Grundes: {@code punish.reason.<code>}, bei Kick/Verwarnung der freie Text. */
    public Component reason(Locale locale, Punishment punishment) {
        String code = punishment.reasonCode();
        if (punishment.reasonText() != null && (code.equals(PunishmentService.KICK_REASON_CODE)
                || code.equals(PunishmentService.WARN_REASON_CODE) || !i18n.has("punish.reason." + code))) {
            return Component.text(punishment.reasonText());
        }
        return reasonLabel(locale, code);
    }

    /** Anzeigename eines Grund-Codes ({@code punish.reason.<code>}, sonst der Code). */
    public Component reasonLabel(Locale locale, String code) {
        String key = "punish.reason." + code;
        return i18n.has(key) ? i18n.render(locale, key) : Component.text(code);
    }

    public Component type(Locale locale, PunishmentType type) {
        return i18n.render(locale, "punish.type." + type.name().toLowerCase(Locale.ROOT));
    }

    /** Dauer {@code 30d 0h 0m 0s} oder {@code permanent}. */
    public Component duration(Locale locale, PunishmentDuration duration) {
        return duration.isPermanent() ? i18n.render(locale, "punish.duration.permanent")
                : Component.text(DurationFormat.format(Objects.requireNonNull(duration.duration())));
    }

    /** Bildschirm beim Login mit aktivem Bann ({@code screen.ban.perm} / {@code screen.ban.temp}, A8). */
    public Component banScreen(Locale locale, Punishment ban, Instant now, String appealUrl) {
        TagResolver common = TagResolver.resolver(Placeholder.component("reason", reason(locale, ban)),
                Placeholder.unparsed("id", ban.publicId()), Placeholder.unparsed("appeal_url", appealUrl));
        if (ban.isPermanent()) {
            return i18n.render(locale, "screen.ban.perm", common);
        }
        Duration remaining = ban.remaining(now).orElse(Duration.ZERO);
        return i18n.render(locale, "screen.ban.temp", common,
                Placeholder.component("duration", duration(locale, ban.duration())),
                Placeholder.unparsed("remaining", DurationFormat.format(remaining)));
    }

    /** Sofort-Kick bei Bann während des Spiels ({@code screen.ban.kick}). */
    public Component banKick(Locale locale, Punishment ban) {
        return i18n.render(locale, "screen.ban.kick", Placeholder.component("reason", reason(locale, ban)),
                Placeholder.unparsed("id", ban.publicId()));
    }

    /** Kick vom Netzwerk ({@code screen.kick}). */
    public Component kickScreen(Locale locale, String reasonText) {
        return i18n.render(locale, "screen.kick", Placeholder.unparsed("reason", reasonText));
    }

    /** Hinweis beim Schreibversuch mit Mute ({@code mute.notice}, 3 Zeilen). */
    public Component muteNotice(Locale locale, Punishment mute, Instant now) {
        Component remaining = mute.isPermanent() ? i18n.render(locale, "punish.duration.permanent")
                : Component.text(DurationFormat.format(mute.remaining(now).orElse(Duration.ZERO)));
        return i18n.render(locale, "mute.notice", Formatter.booleanChoice("permanent", mute.isPermanent()),
                Placeholder.component("duration", duration(locale, mute.duration())),
                Placeholder.component("reason", reason(locale, mute)), Placeholder.unparsed("id", mute.publicId()),
                Placeholder.component("remaining", remaining));
    }

    /** Bestätigung für das Team ({@code punish.ok}). */
    public Component issued(Locale locale, Punishment punishment, String playerName) {
        return i18n.render(locale, "punish.ok", Placeholder.component("type", type(locale, punishment.type())),
                Placeholder.unparsed("player", playerName),
                Placeholder.component("reason", reason(locale, punishment)),
                Placeholder.unparsed("id", punishment.publicId()),
                Placeholder.component("duration", duration(locale, punishment.duration())));
    }

    /** Zeile in {@code /history}. */
    public Component historyLine(Locale locale, Punishment punishment, Instant now) {
        PunishmentStatus status = punishment.status(now);
        return i18n.render(locale, "history.line", Placeholder.unparsed("id", punishment.publicId()),
                Placeholder.component("type", type(locale, punishment.type())),
                Placeholder.component("reason", reason(locale, punishment)),
                Placeholder.unparsed("date", DATE.format(punishment.issuedAt())),
                Placeholder.component("status", i18n.render(locale,
                        "history.status." + status.name().toLowerCase(Locale.ROOT))));
    }
}
