package net.retromc.paper.core.command;

import java.lang.reflect.Type;
import java.util.Locale;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.command.CommandError;
import net.retromc.platform.common.command.CommandErrorHandlers;
import net.retromc.platform.common.command.CommandErrorMapper;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jspecify.annotations.Nullable;

/**
 * Befehls-Manager auf Paper (cloud-paper, Brigadier) mit Fehlermeldungen aus dem Textkatalog in der Sprache des
 * Absenders. Befehle anderer Plugins, die in ihrem {@code onEnable} registriert werden, übernimmt Paper beim
 * Befehls-Lebenszyklus nach dem Start aller Plugins.
 */
public final class CommandFeature implements CoreFeature<PaperCore> {

    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Befehle";
    }

    @Override
    public void enable(PaperCore core) {
        PaperCommandManager<Source> manager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(core.plugin());
        CommandErrorHandlers.install(manager, new CommandErrorMapper(CommandFeature::isPlayerSource),
                (source, error) -> reply(core, source.source(), error), core.log());
        registration = core.services().registerDefault(CommandService.class, () -> manager);
    }

    @Override
    public void disable(PaperCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
            registration = null;
        }
    }

    private static void reply(PaperCore core, CommandSender sender, CommandError error) {
        I18n i18n = core.services().get(I18n.class);
        Locale locale = sender instanceof Player player ? i18n.locale(player.getUniqueId()) : Messages.DEFAULT_LOCALE;
        sender.sendMessage(error.render(i18n, locale));
    }

    private static boolean isPlayerSource(Type type) {
        return type instanceof Class<?> sender && PlayerSource.class.isAssignableFrom(sender);
    }
}
