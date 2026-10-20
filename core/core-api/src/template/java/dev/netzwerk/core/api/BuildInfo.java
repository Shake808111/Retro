package dev.netzwerk.core.api;

/**
 * Build-Informationen. Diese Datei ist eine Vorlage: Gradle ersetzt die Platzhalter aus
 * {@code gradle.properties} (brandName, version) und legt das Ergebnis unter
 * {@code build/generated/sources/buildinfo} ab. Nicht im Ziel von Hand bearbeiten.
 */
public final class BuildInfo {

    /** Anzeigename der Marke, z. B. in Plugin-Namen und Texten. */
    public static final String BRAND_NAME = "${brandName}";

    /** Technische ID der Marke: nur Kleinbuchstaben und Ziffern, beginnt mit einem Buchstaben. */
    public static final String BRAND_ID = "${brandId}";

    /** Projektversion aus gradle.properties. */
    public static final String VERSION = "${version}";

    private BuildInfo() {
    }
}
