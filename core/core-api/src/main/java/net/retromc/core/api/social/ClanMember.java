package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Mitglied aus {@code social.clan_member} mit Namen aus {@code core.player} (Spec 01 §3.4 #52, GUI §5.4). */
public record ClanMember(UUID uuid, String name, ClanRole role, @Nullable Instant joinedAt) {

    /** GUI/Liste: nach Rolle (Leiter, Moderatoren, Mitglieder, Testmitglieder), dann alphabetisch. */
    public static final Comparator<ClanMember> ROLE_ORDER = Comparator
            .comparing((ClanMember member) -> -member.role().code())
            .thenComparing(ClanMember::name, String.CASE_INSENSITIVE_ORDER);

    public ClanMember {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
    }
}
