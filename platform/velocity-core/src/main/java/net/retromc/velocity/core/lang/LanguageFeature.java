package net.retromc.velocity.core.lang;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Language;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.PlayerLocales;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

/**
 * {@code /language [Code]} (Spec 01 #67, Alias {@code /sprache}): ohne Argument eine klickbare Liste aller Sprachen
 * (§6.14), mit Argument setzen. Ablauf nach Spec 02 §6.13 Nr. 3: Datenbank → {@code profile:{uuid}.locale} →
 * {@code PROFILE_CHANGED} → alle Server rendern neu. Die Profil-Sprache verbundener Spieler hält {@link PlayerLocales}.
 */
public final class LanguageFeature implements CoreFeature<VelocityCore> {

    public static final CommandName LANGUAGE = CommandName.of("language", "sprache");
    public static final List<CommandName> COMMANDS = List.of(LANGUAGE);

    private final List<Registration> subscriptions = new ArrayList<>();
    private @Nullable Social social;

    @Override
    public String name() {
        return "Sprache";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        var manager = core.services().get(CommandService.class).manager();
        manager.command(LANGUAGE.builder(manager).senderType(Player.class)
                .handler(ctx -> list(ctx.sender())));
        manager.command(LANGUAGE.builder(manager).senderType(Player.class)
                .required("code", stringParser(), SuggestionProvider.blocking((ctx, input) -> Arrays
                        .stream(Language.values()).map(language -> Suggestion.suggestion(language.code())).toList()))
                .handler(ctx -> set(ctx.sender(), ctx.get("code"))));
        subscriptions.add(core.services().get(MessageBus.class).subscribe(MessageType.PROFILE_CHANGED,
                envelope -> changed(envelope.d())).join());
        core.proxy().getEventManager().register(core.plugin(), this);
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        List<Registration> reversed = new ArrayList<>(subscriptions);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        subscriptions.clear();
        social = null;
    }

    /** Beim ersten Server nach dem Login ist {@code core.player} geschrieben: Profil-Sprache übernehmen. */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getPreviousServer().isEmpty()) {
            load(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Social helper = social;
        if (helper != null) {
            helper.get(PlayerLocales.class).remove(event.getPlayer().getUniqueId());
        }
    }

    private void changed(ProfileChanged change) {
        if (change.fields().contains(ProfileChanged.LOCALE)) {
            load(change.uuid());
        }
    }

    private void load(UUID player) {
        Social helper = social;
        if (helper == null || helper.online(player).isEmpty()) {
            return;
        }
        helper.log(helper.get(ProfileService.class).profile(player).thenAccept(profile -> profile.ifPresent(
                found -> helper.get(PlayerLocales.class).put(player, found.locale()))), "Sprache laden");
    }

    private void list(Player player) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.send(player, "lang.list.header");
        for (Language language : Language.values()) {
            player.sendMessage(helper.text(player, "lang.list.line", Placeholder.unparsed("name",
                    language.displayName())).clickEvent(ClickEvent.runCommand("/" + LANGUAGE.name() + " "
                    + language.code())));
        }
    }

    private void set(Player player, String code) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        Optional<Language> language = Language.byCode(code);
        if (language.isEmpty()) {
            list(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        helper.log(helper.get(SettingsService.class).setLocale(uuid, language.get().code()).thenRun(() -> {
            helper.get(PlayerLocales.class).put(uuid, language.get().code());
            helper.send(player, "lang.set", Placeholder.unparsed("name", language.get().displayName()));
        }), "Sprache setzen");
    }
}
