package dev.netzwerk.discord.bot;

import dev.netzwerk.core.api.BuildInfo;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/** Platzhalter des Discord-Bots. Geplant: JDA (libs.jda im Versionskatalog), Notiz 07, Abschnitt 7. */
public final class DiscordBotApplication {

    private static final Logger LOG = System.getLogger(DiscordBotApplication.class.getName());

    private DiscordBotApplication() {
    }

    public static void main(String[] args) {
        LOG.log(Level.INFO, "{0}-Discord-Bot (Platzhalter) - noch keine Funktion.", BuildInfo.BRAND_NAME);
    }
}
