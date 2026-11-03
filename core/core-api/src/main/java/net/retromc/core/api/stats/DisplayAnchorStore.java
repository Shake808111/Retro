package net.retromc.core.api.stats;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Anzeige-Anker der Bestenlisten ({@code core.display_anchor}, Spec 02 §2.1, §3 {@code /statswall}). */
public interface DisplayAnchorStore {

    /** Speichert einen neuen Anker und liefert ihn mit vergebener ID. */
    CompletableFuture<DisplayAnchor> create(DisplayAnchor anchor);

    /** Alle Anker einer Servergruppe, nach ID. */
    CompletableFuture<List<DisplayAnchor>> list(String serverGroup);

    /** Entfernt einen Anker; {@code true}, wenn es ihn gab. */
    CompletableFuture<Boolean> delete(long id);
}
