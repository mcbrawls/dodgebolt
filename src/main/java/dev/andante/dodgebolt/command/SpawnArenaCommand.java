package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.andante.dodgebolt.Constants;
import dev.andante.dodgebolt.game.GameTeam;
import dev.andante.dodgebolt.processor.ArenaStructureProcessor;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public interface SpawnArenaCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = literal("spawnarena").requires(source -> source.hasPermissionLevel(2));

        for (GameTeam alpha : GameTeam.values()) {
            for (GameTeam beta : GameTeam.values()) {
                builder.then(literal(alpha.name()).then(literal(beta.name()).executes(context -> execute(context, alpha, beta))));
            }
        }

        dispatcher.register(builder.executes(context -> execute(context, null, null)));
    }

    static int execute(CommandContext<ServerCommandSource> context, GameTeam alpha, GameTeam beta) {
        if (alpha == null || beta == null) {
            Random random = Random.create();
            List<GameTeam> values = new ArrayList<>(Arrays.asList(GameTeam.values()));
            alpha = values.get(random.nextInt(values.size()));
            values.remove(alpha);
            beta = values.get(random.nextInt(values.size()));
        }

        ServerCommandSource source = context.getSource();
        Vec3d pos = source.getPosition();
        ArenaStructureProcessor.placeStructure(source.getWorld(), new BlockPos(pos), Constants.ARENA_STRUCTURE_ID, alpha, beta);

        return 1;
    }
}
