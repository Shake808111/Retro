package net.retromc.velocity.core.command;

import com.velocitypowered.api.command.CommandSource;
import org.incendo.cloud.velocity.VelocityCommandManager;

/**
 * Zugang zum Befehls-Manager des Proxy-Kerns (Incendo Cloud, Spec 01 §1). Befehle nur für Spieler verlangen
 * {@code senderType(Player.class)}; Fehlermeldungen kommen aus dem Textkatalog.
 */
public interface CommandService {

    VelocityCommandManager<CommandSource> manager();
}
