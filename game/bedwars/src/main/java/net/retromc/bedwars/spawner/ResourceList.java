package net.retromc.bedwars.spawner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Inhalt der Meldung {@code bw.resources.transfer} (Spec 04 §7.1, §9 #4, U3): nur Werte &gt; 0, Reihenfolge
 * Kupfer/Bronze, Eisen, Gold, Diamant; Diamant in Einzahl/Mehrzahl ({@code Diamant}/{@code Diamanten}).
 */
public final class ResourceList {

    /** Reihenfolge der Liste. */
    static final List<Resource> ORDER = List.of(Resource.COPPER, Resource.BRONZE, Resource.IRON, Resource.GOLD,
            Resource.DIAMOND);

    /** Ein Listeneintrag mit Textschlüssel ({@code .one} bei 1 Diamant). */
    public record Entry(Resource resource, int amount, String key) {
    }

    private ResourceList() {
    }

    /** Einträge mit Betrag &gt; 0 in Meldungs-Reihenfolge. */
    public static List<Entry> entries(Map<Resource, Integer> amounts) {
        List<Entry> entries = new ArrayList<>();
        for (Resource resource : ORDER) {
            int amount = amounts.getOrDefault(resource, 0);
            if (amount > 0) {
                String key = "bw.resources." + resource.id() + (amount == 1 ? ".one" : "");
                entries.add(new Entry(resource, amount, key));
            }
        }
        return entries;
    }

    /**
     * Endung des Verbs {@code wurde{n}}: „n“ nur bei genau einer Ressourcenart mit mehr als einem Stück
     * („Dir wurden 8 Diamanten übertragen“), sonst wie im Beleg „Dir wurde 160 Kupfer, 4 Eisen … übertragen“.
     */
    public static String verbSuffix(List<Entry> entries) {
        return entries.size() == 1 && entries.getFirst().amount() > 1 ? "n" : "";
    }

    /** Summen zweier Bestände. */
    public static Map<Resource, Integer> add(Map<Resource, Integer> a, Map<Resource, Integer> b) {
        Map<Resource, Integer> sum = new EnumMap<>(Resource.class);
        a.forEach((resource, amount) -> sum.merge(resource, amount, Integer::sum));
        b.forEach((resource, amount) -> sum.merge(resource, amount, Integer::sum));
        return sum;
    }

    /** Teilt {@code amount} gleichmäßig auf {@code receivers} auf (Rest an die ersten, Spec 04 §6.1.4 Nr. 6). */
    public static List<Integer> split(int amount, int receivers) {
        List<Integer> parts = new ArrayList<>();
        for (int i = 0; i < receivers; i++) {
            parts.add(amount / receivers + (i < amount % receivers ? 1 : 0));
        }
        return parts;
    }
}
