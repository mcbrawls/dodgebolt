package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import dev.andante.dodgebolt.game.GameTeam;
import dev.andante.dodgebolt.util.StructureHelper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.literal;

public interface SpawnArenaCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = literal("spawnarena").requires(source -> source.hasPermissionLevel(2)).executes(context -> execute(context, GameTeam.getRandomPair()));
        GameTeam.forEachTeamPair((alpha, beta) -> builder.then(literal(alpha.name()).then(literal(beta.name()).executes(context -> execute(context, Pair.of(alpha, beta))))));
        dispatcher.register(builder);
    }

    private static int execute(CommandContext<ServerCommandSource> context, Pair<GameTeam, GameTeam> pair) {
        ServerCommandSource source = context.getSource();
        StructureHelper.placeArena(source.getWorld(), new BlockPos(source.getPosition()), pair.getFirst(), pair.getSecond());
        return 1;
    }
}
