package net.retromc.core.api.util;

import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * IP-Adresse nur als HMAC-SHA256 mit dem Geheimnis {@code NETWORK_HMAC_SECRET} (Spec 08 §6.19, N07 §9 E).
 * Gespeichert als {@code BYTEA} ({@code core.player_ip}, {@code moderation.ban_ip}) bzw. hexadezimal in
 * Valkey-Schlüsseln ({@code rl:api:{ipHmac}}). Die Klartext-IP verlässt diese Klasse nie.
 */
public record IpHmac(String hex) {

    public static final String ALGORITHM = "HmacSHA256";
    private static final Pattern HEX = Pattern.compile("[0-9a-f]{64}");

    public IpHmac {
        Objects.requireNonNull(hex, "hex");
        if (!HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException("IpHmac erwartet 64 Hex-Zeichen (klein)");
        }
    }

    /** Berechnet den HMAC der Adresse (IPv4 4 Byte, IPv6 16 Byte). */
    public static IpHmac of(byte[] secret, InetAddress address) {
        Objects.requireNonNull(address, "address");
        if (Objects.requireNonNull(secret, "secret").length == 0) {
            throw new IllegalArgumentException("Leeres HMAC-Geheimnis");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return new IpHmac(HexFormat.of().formatHex(mac.doFinal(address.getAddress())));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC nicht verfügbar", e);
        }
    }

    public static IpHmac ofBytes(byte[] bytes) {
        return new IpHmac(HexFormat.of().formatHex(Objects.requireNonNull(bytes, "bytes")));
    }

    /** Rohbytes für {@code BYTEA}-Spalten. */
    public byte[] bytes() {
        return HexFormat.of().parseHex(hex);
    }

    @Override
    public String toString() {
        // Nur ein Präfix ausgeben, damit Logs keine vollständigen Hashes sammeln.
        return "IpHmac[" + hex.substring(0, 8) + "…]";
    }
}
