package net.retromc.platform.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Jede Cloud-Ausnahme landet als Text aus dem Katalog beim Absender (ohne Paper/Velocity). */
class CommandErrorHandlersTest {

    /** Absender der Tests. */
    private sealed interface Sender permits Console, Gamer {

        Set<String> permissions();
    }

    private record Console(Set<String> permissions) implements Sender {
    }

    private record Gamer(Set<String> permissions) implements Sender {
    }

    private enum Mode {
        BEDWARS,
        SKYWARS
    }

    /** Minimaler Manager ohne Plattform. */
    private static final class TestManager extends CommandManager<Sender> {

        TestManager() {
            super(ExecutionCoordinator.simpleCoordinator(),
                    CommandRegistrationHandler.nullCommandRegistrationHandler());
        }

        @Override
        public boolean hasPermission(Sender sender, String permission) {
            return permission.isEmpty() || sender.permissions().contains(permission);
        }
    }

    /** Parser-Fehler mit der Caption der Plattform-Spielerparser. */
    private static final class UnknownPlayer extends ParserException {

        private static final long serialVersionUID = 1L;

        UnknownPlayer(CommandContext<?> context, String input) {
            super(UnknownPlayer.class, context, Caption.of(CommandErrorMapper.CAPTION_PLAYER),
                    CaptionVariable.of("input", input));
        }
    }

    private static final I18n I18N = new CatalogI18n(player -> Optional.empty());

    private final List<CommandError> replies = new ArrayList<>();
    private final TestManager manager = new TestManager();
    private final Gamer gamer = new Gamer(Set.of());

    @BeforeEach
    void setUp() {
        manager.command(manager.commandBuilder("range").required("n", IntegerParser.integerParser(1, 5)));
        manager.command(manager.commandBuilder("count").required("n", IntegerParser.integerParser()));
        manager.command(manager.commandBuilder("mode").required("m", EnumParser.enumParser(Mode.class)));
        manager.command(manager.commandBuilder("secret").permission("retromc.test.secret"));
        manager.command(manager.commandBuilder("fly").senderType(Gamer.class));
        manager.command(manager.commandBuilder("boom").handler(context -> {
            throw new IllegalStateException("absichtlich");
        }));
        manager.command(manager.commandBuilder("who").required("target", ParserDescriptor.of(
                (context, input) -> ArgumentParseResult.failure(new UnknownPlayer(context, input.readString())),
                String.class)));
        CommandErrorHandlers.install(manager, new CommandErrorMapper(type -> type == Gamer.class),
                (sender, error) -> replies.add(error), LoggerFactory.getLogger(CommandErrorHandlersTest.class));
    }

    @Test
    void falscheSyntaxZeigtDieVerwendung() {
        assertEquals(new CommandError(CommandErrorMapper.USAGE, Map.of("syntax", "range <n>")), run(gamer, "range"));
    }

    @Test
    void zahlAusserhalbDesBereichs() {
        assertEquals(new CommandError(CommandErrorMapper.RANGE, Map.of("input", "9", "min", "1", "max", "5")),
                run(gamer, "range 9"));
    }

    @Test
    void keineZahlOhneBereich() {
        assertEquals(new CommandError(CommandErrorMapper.NUMBER, Map.of("input", "abc")), run(gamer, "count abc"));
    }

    @Test
    void ungueltigeAuswahl() {
        assertEquals(new CommandError(CommandErrorMapper.ARGUMENT, Map.of("input", "ttt")), run(gamer, "mode ttt"));
    }

    @Test
    void unbekannterSpieler() {
        assertEquals(new CommandError(CommandErrorMapper.PLAYER_UNKNOWN, Map.of("player", "Niemand")),
                run(gamer, "who Niemand"));
    }

    @Test
    void fehlendesRecht() {
        assertEquals(new CommandError(CommandErrorMapper.NO_PERMISSION), run(gamer, "secret"));
    }

    @Test
    void nurFuerSpieler() {
        assertEquals(new CommandError(CommandErrorMapper.PLAYER_ONLY), run(new Console(Set.of()), "fly"));
    }

    @Test
    void ausfuehrungsfehler() {
        assertEquals(new CommandError(CommandErrorMapper.FAILED), run(gamer, "boom"));
    }

    @Test
    void unbekannterBefehl() {
        assertEquals(new CommandError(CommandErrorMapper.UNKNOWN), run(gamer, "gibtsnicht"));
    }

    @Test
    void jederFehlerTextStehtImKatalog() {
        for (String key : List.of(CommandErrorMapper.USAGE, CommandErrorMapper.PLAYER_ONLY,
                CommandErrorMapper.ARGUMENT, CommandErrorMapper.INVALID, CommandErrorMapper.NUMBER,
                CommandErrorMapper.RANGE, CommandErrorMapper.UNKNOWN, CommandErrorMapper.FAILED,
                CommandErrorMapper.NO_PERMISSION, CommandErrorMapper.PLAYER_UNKNOWN)) {
            assertEquals(true, I18N.has(key), key);
        }
    }

    @Test
    void eingabenWerdenNieAlsMiniMessageGelesen() {
        CommandError error = new CommandError(CommandErrorMapper.ARGUMENT, Map.of("input", "<red>x</red>"));

        String text = PlainTextComponentSerializer.plainText().serialize(error.render(I18N, Locale.GERMANY));

        assertEquals("Ungültige Eingabe: <red>x</red>", text);
    }

    @Test
    void textInDerSpracheDesAbsenders() {
        CommandError error = new CommandError(CommandErrorMapper.USAGE, Map.of("syntax", "range <n>"));

        assertEquals("Usage: /range <n>",
                PlainTextComponentSerializer.plainText().serialize(error.render(I18N, Locale.US)));
    }

    private CommandError run(Sender sender, String input) {
        replies.clear();
        manager.commandExecutor().executeCommand(sender, input).exceptionally(error -> null).join();
        assertEquals(1, replies.size(), "Antworten: " + replies);
        return replies.getFirst();
    }
}
