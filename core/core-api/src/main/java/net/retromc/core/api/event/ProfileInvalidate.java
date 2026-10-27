package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Profil-Cache neu lesen ({@code PROFILE_INVALIDATE}, net:social; Spec 01 §8.2); {@code fields} = geänderte HASH-Felder, leer = alle. */
public record ProfileInvalidate(UUID uuid, List<String> fields) {

    public ProfileInvalidate {
        Objects.requireNonNull(uuid, "uuid");
        fields = List.copyOf(fields);
    }
}
