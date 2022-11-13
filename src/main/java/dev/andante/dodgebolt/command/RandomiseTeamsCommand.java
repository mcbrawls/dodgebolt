package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import dev.andante.dodgebolt.game.GameTeam;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public interface RandomiseTeamsCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = literal("randomiseteams").requires(source -> source.hasPermissionLevel(2)).executes(RandomiseTeamsCommand::execute);
        GameTeam.forEachPair((alpha, beta) -> builder.then(literal(alpha.name()).then(literal(beta.name()).executes(context -> execute(context, alpha, beta)))));
        dispatcher.register(builder);
    }

    static int execute(CommandContext<ServerCommandSource> context) {
        Pair<GameTeam, GameTeam> pair = GameTeam.getRandomPair();
        return execute(context, pair.getFirst(), pair.getSecond());
    }

    private static int execute(CommandContext<ServerCommandSource> context, GameTeam alpha, GameTeam beta) {
        MinecraftServer server = context.getSource().getServer();
        ServerScoreboard scoreboard = server.getScoreboard();

        List<ServerPlayerEntity> players = new ArrayList<>(PlayerLookup.all(server));
        Collections.shuffle(players);

        int size = players.size();
        int half = size / 2;

        AbstractTeam alphaTeam = alpha.getTeam(server);
        AbstractTeam betaTeam = beta.getTeam(server);
        for (int i = 0; i < half; i++) {
            scoreboard.addPlayerToTeam(players.get(i).getEntityName(), scoreboard.getTeam(alphaTeam.getName()));
        }
        for (int i = half; i < size; i++) {
            scoreboard.addPlayerToTeam(players.get(i).getEntityName(), scoreboard.getTeam(betaTeam.getName()));
        }

        return 1;
    }
}
