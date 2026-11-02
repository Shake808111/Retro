package net.retromc.velocity.core.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.Type;
import java.util.Locale;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.platform.common.command.CommandError;
import net.retromc.platform.common.command.CommandErrorHandlers;
import net.retromc.platform.common.command.CommandErrorMapper;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/** Befehls-Manager auf Velocity (cloud-velocity) mit Fehlermeldungen aus dem Textkatalog. */
public final class CommandFeature implements CoreFeature<VelocityCore> {

    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Befehle";
    }

    @Override
    public void enable(VelocityCore core) {
        VelocityCommandManager<CommandSource> manager = new VelocityCommandManager<>(core.container(), core.proxy(),
                ExecutionCoordinator.simpleCoordinator(), SenderMapper.identity());
        CommandErrorHandlers.install(manager, new CommandErrorMapper(CommandFeature::isPlayer),
                (source, error) -> reply(core, source, error), core.log());
        registration = core.services().registerDefault(CommandService.class, () -> manager);
    }

    @Override
    public void disable(VelocityCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
            registration = null;
        }
    }

    private static void reply(VelocityCore core, CommandSource source, CommandError error) {
        I18n i18n = core.services().get(I18n.class);
        Locale locale = source instanceof Player player ? i18n.locale(player.getUniqueId()) : Messages.DEFAULT_LOCALE;
        source.sendMessage(error.render(i18n, locale));
    }

    private static boolean isPlayer(Type type) {
        return type instanceof Class<?> sender && Player.class.isAssignableFrom(sender);
    }
}
