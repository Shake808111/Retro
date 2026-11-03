package net.retromc.platform.common.social;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.util.IpHmac;
import net.retromc.platform.common.config.EnvKeys;
import net.retromc.platform.common.config.EnvReader;
import org.jspecify.annotations.Nullable;

/**
 * Einstellungen für Social und Moderation aus der Umgebung: {@code NETWORK_WEB_ORIGIN} (Standard
 * {@code https://<brandId>.net}) für Chatlog- und Entbannungs-Links sowie die Werbe-Whitelist, und
 * {@code NETWORK_HMAC_SECRET} für IP-HMACs (ohne Schlüssel keine IP-Bans).
 */
public record SocialConfig(String webOrigin, @Nullable String hmacSecret) {

    public SocialConfig {
        Objects.requireNonNull(webOrigin, "webOrigin");
        webOrigin = webOrigin.endsWith("/") ? webOrigin.substring(0, webOrigin.length() - 1) : webOrigin;
    }

    public static SocialConfig read(EnvReader env) {
        return new SocialConfig(env.optional(EnvKeys.WEB_ORIGIN).orElse("https://" + BuildInfo.BRAND_ID + ".net"),
                env.optional(EnvKeys.HMAC_SECRET).orElse(null));
    }

    /** Domain der eigenen Seite (Werbe-Whitelist, Spec 01 §6.16). */
    public String domain() {
        String host = URI.create(webOrigin).getHost();
        String value = host == null ? webOrigin : host;
        return (value.startsWith("www.") ? value.substring(4) : value).toLowerCase(Locale.ROOT);
    }

    /** Entbannungsantrag ({@code {appealUrl}}, Spec 01 §6.10). */
    public String appealUrl() {
        return webOrigin + "/entbannung";
    }

    /** Chatlog-Seite ({@code GET /chatlog/{id}}, Spec 01 §8.3). */
    public String chatlogUrl(UUID id) {
        return webOrigin + "/chatlog/" + id;
    }

    /** HMAC einer IP-Adresse; leer ohne Schlüssel. */
    public Optional<IpHmac> ip(@Nullable InetAddress address) {
        if (hmacSecret == null || address == null) {
            return Optional.empty();
        }
        return Optional.of(IpHmac.of(hmacSecret.getBytes(StandardCharsets.UTF_8), address));
    }
}
