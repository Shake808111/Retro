package net.retromc.core.api.event;

import java.util.List;
import java.util.UUID;

/**
 * Gemeinsame Form der weitergeleiteten Chats {@code PrivateMessage}, {@code PartyChat}, {@code ClanChat},
 * {@code StaffChat}: Payload {@code from, to[], text} (Spec 01 §8.2), Velocity → Velocity (Multi-Proxy).
 */
public sealed interface RelayedChat permits PrivateMessage, PartyChat, ClanChat, StaffChat {

    UUID from();

    List<UUID> to();

    String text();
}
