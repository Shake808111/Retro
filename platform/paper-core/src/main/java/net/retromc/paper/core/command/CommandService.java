package net.retromc.paper.core.command;

import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Zugang zum Befehls-Manager des Kerns (Incendo Cloud, Spec 02 §1). Spielmodule registrieren ihre Befehle hier in
 * {@code onEnable}; Befehle nur für Spieler verlangen {@code senderType(PlayerSource.class)}. Fehlermeldungen kommen
 * aus dem Textkatalog.
 */
public interface CommandService {

    PaperCommandManager<Source> manager();
}
