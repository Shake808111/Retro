package net.retromc.skywars.event;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Globale Events aus {@code events.yml} (Spec 04 §6.2.1 Nr. 6, §8.2 {@code GlobalEventService}): je Eintrag ID, Art,
 * Stufe und Sekunde nach Kampfbeginn. Standard: Chestrefill Stufe 1 bei Kampf + 5:00, Stufe 2 bei Kampf + 10:00
 * (Spec 04 §10 #21). Ohne Server testbar.
 *
 * @param entries Einträge nach Zeitpunkt
 */
public record GlobalEventSchedule(List<Entry> entries) {

    /** Art „Chestrefill“. */
    public static final String CHESTREFILL = "chestrefill";

    /**
     * Ein geplantes Event.
     *
     * @param id    eindeutige ID ({@code /swdebug event <id>})
     * @param type  Art ({@link #CHESTREFILL})
     * @param stage Stufe (1, 2 …)
     * @param at    Sekunde nach Kampfbeginn
     */
    public record Entry(String id, String type, int stage, int at) {

        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            if (stage < 1 || at < 0) {
                throw new IllegalArgumentException("Event " + id + ": stage ≥ 1, at ≥ 0");
            }
        }
    }

    /** Standard der Spec. */
    public static final GlobalEventSchedule DEFAULT = new GlobalEventSchedule(List.of(
            new Entry("chestrefill1", CHESTREFILL, 1, 300), new Entry("chestrefill2", CHESTREFILL, 2, 600)));

    public GlobalEventSchedule {
        entries = entries.stream().sorted(java.util.Comparator.comparingInt(Entry::at)).toList();
    }

    public static GlobalEventSchedule load(Reader reader) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("events")) {
            String id = String.valueOf(raw.get("id"));
            Object type = raw.get("type");
            entries.add(new Entry(id, type == null ? CHESTREFILL : String.valueOf(type).toLowerCase(Locale.ROOT),
                    Integer.parseInt(String.valueOf(raw.get("stage"))), Integer.parseInt(String.valueOf(raw.get("at")))));
        }
        return new GlobalEventSchedule(entries);
    }

    /** Events, die genau in Kampfsekunde {@code fightSecond} fällig sind. */
    public List<Entry> due(int fightSecond) {
        return entries.stream().filter(entry -> entry.at() == fightSecond).toList();
    }

    public Optional<Entry> byId(String id) {
        return entries.stream().filter(entry -> entry.id().equalsIgnoreCase(id)).findFirst();
    }
}
