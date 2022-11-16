package dev.andante.dodgebolt;

import com.google.common.reflect.Reflection;
import com.mojang.logging.LogUtils;
import dev.andante.dodgebolt.command.CreateTeamsCommand;
import dev.andante.dodgebolt.command.DodgeboltCommand;
import dev.andante.dodgebolt.command.RandomiseTeamsCommand;
import dev.andante.dodgebolt.command.SpawnArenaCommand;
import dev.andante.dodgebolt.game.DodgeboltGameManager;
import dev.andante.dodgebolt.game.GameTeam;
import dev.andante.dodgebolt.processor.DodgeboltStructureProcessors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

public class Dodgebolt implements ModInitializer {
    public static final String MOD_ID = "dodgebolt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DodgeboltGameManager DODGEBOLT_MANAGER = new DodgeboltGameManager();

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);

        Reflection.initialize(DodgeboltStructureProcessors.class);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DodgeboltCommand.register(dispatcher);
            SpawnArenaCommand.register(dispatcher);
            CreateTeamsCommand.register(dispatcher);
            RandomiseTeamsCommand.register(dispatcher);
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::allowChatMessage);
    }

    public boolean allowChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            Text name = getDisplayName(player);
            Text text = Text.translatable("%s: %s", name, message.getContent());
            LOGGER.info("[CHAT] {}", text.getString());

            for (ServerPlayerEntity xplayer : PlayerLookup.all(server)) {
                xplayer.sendMessage(text);
            }

            return false;
        }

        return true;
    }

    public static MutableText getDisplayName(ServerPlayerEntity player) {
        GameTeam gameTeam = GameTeam.of(player.getScoreboardTeam());
        return player.getDisplayName().copy().setStyle(getTeamStyle(gameTeam));
    }

    @SuppressWarnings("DataFlowIssue")
    public static Style getTeamStyle(GameTeam team) {
        return Style.EMPTY.withColor(team != null ? team.getColor() : Formatting.GRAY.getColorValue());
    }
}
