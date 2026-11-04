package net.retromc.core.data.nick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import net.retromc.core.api.social.NickPoolService.Fill;
import net.retromc.core.api.social.NickPoolService.FillStatus;
import org.jspecify.annotations.Nullable;

/**
 * Ein Füllvorgang des Nick-Pools (Spec 01 §6.11, {@code /nickpool fill <anzahl>}), blockierend auf einem
 * Hintergrund-Thread:
 * <ol>
 *   <li>Skins nachholen: Mojang-Sessionserver für jede konfigurierte Quell-UUID, die noch fehlt, optional MineSkin.</li>
 *   <li>Namen erzeugen ({@link NickNameGenerator}) und verwerfen, was der Wortfilter sperrt, was ein echter Spieler
 *       trägt (online, {@code core.player}, Namenshistorie) oder schon im Pool steht.</li>
 *   <li>Optional bei Mojang prüfen, ob der Name vergeben ist (nur freie Namen kommen in den Pool); HTTP 429 beendet
 *       den Vorgang schonend, bis dahin gefundene Namen bleiben.</li>
 *   <li>Jeden neuen Namen zufällig mit einem aktiven Skin kombinieren und speichern.</li>
 * </ol>
 */
public final class NickPoolFiller {

    /** Quelle {@code core.nick_skin.source}. */
    public static final String SOURCE_MOJANG = "MOJANG";
    public static final String SOURCE_MINESKIN = "MINESKIN";
    /** Namen je Datenbankabfrage gegen bekannte Spieler. */
    static final int BATCH = 40;
    /** Erzeugte Kandidaten je gewünschtem Namen, bevor der Vorgang aufgibt. */
    static final int ATTEMPTS_PER_NAME = 30;

    /** Gespeicherter, aktiver Skin. */
    public record StoredSkin(long id, String value, String signature) {

        public StoredSkin {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(signature, "signature");
        }
    }

    /** Datenzugriff des Füllvorgangs (blockierend). */
    public interface Store {

        /** Bekannte {@code source_ref} einer Quelle. */
        Set<String> skinRefs(String source);

        /** Neue Skins speichern (vorhandene bleiben). */
        void addSkins(String source, List<SkinTexture> skins);

        List<StoredSkin> enabledSkins();

        /** Welche der kleingeschriebenen Namen schon vergeben sind (Spieler, Namenshistorie, Nick-Pool). */
        Set<String> knownNames(Collection<String> lowerNames);

        /** Name mit Skin speichern; {@code false}, wenn es ihn schon gibt. */
        boolean addName(String name, StoredSkin skin);
    }

    private final Store store;
    private final NickNameGenerator generator;
    private final NickNameFilter filter;
    private final Predicate<String> onlineName;
    private final MojangApi mojang;
    private final @Nullable MineSkinApi mineSkin;
    private final NickPoolSettings settings;
    private final RandomGenerator random;

    public NickPoolFiller(Store store, NickNameGenerator generator, NickNameFilter filter,
            Predicate<String> onlineName, MojangApi mojang, @Nullable MineSkinApi mineSkin, NickPoolSettings settings,
            RandomGenerator random) {
        this.store = Objects.requireNonNull(store, "store");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.filter = Objects.requireNonNull(filter, "filter");
        this.onlineName = Objects.requireNonNull(onlineName, "onlineName");
        this.mojang = Objects.requireNonNull(mojang, "mojang");
        this.mineSkin = mineSkin;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Füllt um bis zu {@code count} Namen. */
    public Fill fill(int count) throws InterruptedException {
        if (count < 1) {
            throw new IllegalArgumentException("Anzahl muss positiv sein: " + count);
        }
        boolean rateLimited = false;
        int skinsAdded = 0;
        Set<String> knownMojang = store.skinRefs(SOURCE_MOJANG);
        List<SkinTexture> fetched = new ArrayList<>();
        for (UUID source : settings.skinSources()) {
            String ref = source.toString().replace("-", "");
            if (knownMojang.contains(ref)) {
                continue;
            }
            MojangApi.TextureResult result = mojang.texture(source);
            if (result.status() == MojangApi.FetchStatus.RATE_LIMITED) {
                rateLimited = true;
                break;
            }
            result.texture().ifPresent(fetched::add);
        }
        if (!fetched.isEmpty()) {
            store.addSkins(SOURCE_MOJANG, fetched);
            skinsAdded += fetched.size();
        }
        if (mineSkin != null) {
            List<SkinTexture> more = mineSkin.skins(settings.mineSkinIds(), store.skinRefs(SOURCE_MINESKIN),
                    settings.mineSkinMax());
            if (!more.isEmpty()) {
                store.addSkins(SOURCE_MINESKIN, more);
                skinsAdded += more.size();
            }
        }
        List<StoredSkin> skins = store.enabledSkins();
        if (skins.isEmpty()) {
            return new Fill(rateLimited ? FillStatus.RATE_LIMITED : FillStatus.NO_SKINS, count, 0, skinsAdded, 0, 0,
                    0, 0);
        }
        if (rateLimited && settings.checkMojang()) {
            return new Fill(FillStatus.RATE_LIMITED, count, 0, skinsAdded, skins.size(), 0, 0, 0);
        }
        return names(count, skins, skinsAdded);
    }

    private Fill names(int count, List<StoredSkin> skins, int skinsAdded) throws InterruptedException {
        int added = 0;
        int known = 0;
        int filtered = 0;
        int taken = 0;
        boolean rateLimited = false;
        Set<String> seen = new HashSet<>();
        long attempts = 0;
        long maxAttempts = (long) count * ATTEMPTS_PER_NAME;
        while (added < count && attempts < maxAttempts && !rateLimited) {
            List<String> batch = new ArrayList<>();
            while (batch.size() < BATCH && attempts < maxAttempts) {
                attempts++;
                String name = generator.next();
                if (!seen.add(lower(name))) {
                    continue;
                }
                if (!filter.allowed(name)) {
                    filtered++;
                } else if (onlineName.test(name)) {
                    known++;
                } else {
                    batch.add(name);
                }
            }
            Set<String> knownLower = batch.isEmpty() ? Set.of()
                    : store.knownNames(batch.stream().map(NickPoolFiller::lower).toList());
            for (String name : batch) {
                if (added >= count) {
                    break;
                }
                if (knownLower.contains(lower(name))) {
                    known++;
                    continue;
                }
                if (settings.checkMojang()) {
                    MojangApi.NameStatus status = mojang.nameStatus(name);
                    if (status == MojangApi.NameStatus.RATE_LIMITED) {
                        rateLimited = true;
                        break;
                    }
                    if (status != MojangApi.NameStatus.FREE) {
                        taken++;
                        continue;
                    }
                }
                if (store.addName(name, skins.get(random.nextInt(skins.size())))) {
                    added++;
                } else {
                    known++;
                }
            }
        }
        return new Fill(rateLimited ? FillStatus.RATE_LIMITED : FillStatus.DONE, count, added, skinsAdded,
                skins.size(), known, filtered, taken);
    }

    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
