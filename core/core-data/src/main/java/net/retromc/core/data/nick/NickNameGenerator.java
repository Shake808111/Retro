package net.retromc.core.data.nick;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;
import net.retromc.core.api.util.PlayerName;

/**
 * Zufallsnamen, die wie echte Minecraft-Namen aussehen (Spec 01 §6.11): 3–16 Zeichen aus {@code [A-Za-z0-9_]},
 * gebaut aus Wortkombinationen, Vornamen, Zahlen und typischen Mustern ({@code SilentFox}, {@code Keks_2009},
 * {@code xPandax}, {@code ItsLena}, {@code frostwolf73}). Die Wortlisten sind eigene, neutrale Wörter. Ob ein Name
 * frei und erlaubt ist, prüft der Füllvorgang ({@link NickNameFilter}, bekannte Spieler, Mojang).
 */
public final class NickNameGenerator {

    static final List<String> ADJECTIVES = List.of("Silent", "Lucky", "Swift", "Wild", "Frozen", "Golden", "Crazy",
            "Happy", "Sleepy", "Brave", "Clever", "Fluffy", "Rapid", "Cosmic", "Mystic", "Little", "Mighty", "Shiny",
            "Lazy", "Fancy", "Grumpy", "Sneaky", "Sunny", "Rusty", "Dusty", "Icy", "Witty", "Jolly", "Quiet", "Royal",
            "Epic", "Hyper", "Turbo", "Neon", "Pixel", "Retro", "Super", "Chill", "Dark", "Red", "Blue", "Green",
            "Snowy", "Stormy", "Fast", "Tiny", "Big", "Wise", "Bold", "Calm", "Kleiner", "Wilder", "Schneller",
            "Flinker", "Frecher", "Lustiger", "Stiller", "Mutiger");
    static final List<String> NOUNS = List.of("Fox", "Wolf", "Tiger", "Panda", "Falcon", "Hawk", "Raven", "Otter",
            "Badger", "Lynx", "Bear", "Bunny", "Koala", "Gecko", "Shark", "Whale", "Dragon", "Phoenix", "Knight",
            "Ninja", "Pirate", "Wizard", "Ranger", "Hunter", "Rider", "Archer", "Miner", "Crafter", "Farmer", "Baker",
            "Storm", "Blaze", "Frost", "Stone", "River", "Forest", "Comet", "Rocket", "Pixel", "Block", "Cookie",
            "Muffin", "Taco", "Potato", "Banana", "Mango", "Pepper", "Waffle", "Noodle", "Pickle", "Cactus", "Maple",
            "Fuchs", "Keks", "Hase", "Igel", "Dachs", "Biber", "Eule", "Adler", "Falke", "Baer", "Luchs", "Otto",
            "Blitz", "Sturm", "Nebel", "Wolke", "Kiesel", "Nudel", "Brezel", "Krapfen", "Gurke", "Zwiebel", "Ritter");
    static final List<String> FIRST_NAMES = List.of("Max", "Paul", "Leon", "Lukas", "Tim", "Jonas", "Finn", "Ben",
            "Elias", "Noah", "Felix", "Jan", "Nico", "Tom", "Luca", "Emil", "Moritz", "Jakob", "Anton", "Theo", "Mia",
            "Lena", "Anna", "Lea", "Emma", "Lina", "Nele", "Sophie", "Marie", "Clara", "Ida", "Ella", "Frieda", "Zoe",
            "Jule", "Paula", "Greta", "Mila", "Leni", "Lotta", "Alex", "Sam", "Kim", "Robin", "Charlie", "Nils",
            "Ole", "Malte", "Hannes", "Jannik");
    static final List<String> PREFIXES = List.of("Its", "The", "Mr", "Real", "Not", "Der", "Just", "Only", "Ur");
    static final List<String> SUFFIXES = List.of("PvP", "MC", "LP", "Gaming", "Plays", "TV", "Pro", "Craft");

    private final RandomGenerator random;

    public NickNameGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Nächster gültiger Name (Länge und Zeichensatz immer passend). */
    public String next() {
        while (true) {
            String name = candidate();
            if (PlayerName.valid(name)) {
                return name;
            }
        }
    }

    private String candidate() {
        String noun = pick(NOUNS);
        String name = switch (random.nextInt(12)) {
            case 0 -> pick(ADJECTIVES) + noun;
            case 1 -> pick(ADJECTIVES) + noun + number(2);
            case 2 -> noun + year();
            case 3 -> pick(FIRST_NAMES) + noun;
            case 4 -> pick(FIRST_NAMES) + "_" + (random.nextBoolean() ? year() : number(2));
            case 5 -> "x" + noun + "x";
            case 6 -> noun + "_" + pick(NOUNS);
            case 7 -> (pick(ADJECTIVES) + noun).toLowerCase(Locale.ROOT) + number(random.nextInt(1, 4));
            case 8 -> pick(PREFIXES) + (random.nextBoolean() ? pick(FIRST_NAMES) : noun);
            case 9 -> noun + pick(SUFFIXES);
            case 10 -> pick(FIRST_NAMES) + pick(ADJECTIVES) + (random.nextBoolean() ? "" : number(1));
            default -> pick(FIRST_NAMES).toLowerCase(Locale.ROOT) + "_" + noun.toLowerCase(Locale.ROOT);
        };
        return random.nextInt(10) == 0 ? name + "_" : name;
    }

    private String pick(List<String> words) {
        return words.get(random.nextInt(words.size()));
    }

    /** Zahl mit {@code digits} Stellen ohne führende Null (keine 88, siehe Wortfilter). */
    private String number(int digits) {
        int low = digits == 1 ? 1 : (int) Math.pow(10, digits - 1);
        int high = (int) Math.pow(10, digits);
        String value;
        do {
            value = Integer.toString(random.nextInt(low, high));
        } while (value.contains("88"));
        return value;
    }

    /** Geburtsjahr-artige Zahl 1995–2015 oder kurz 95–15. */
    private String year() {
        int year = random.nextInt(1995, 2016);
        return random.nextBoolean() ? Integer.toString(year) : String.format(Locale.ROOT, "%02d", year % 100);
    }
}
