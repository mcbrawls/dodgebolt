package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.andante.dodgebolt.Dodgebolt;
import dev.andante.dodgebolt.Team;
import dev.andante.dodgebolt.processor.ArenaStructureProcessor;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class SpawnArenaCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("spawnarena").executes(SpawnArenaCommand::execute)
        );
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        Random random = Random.create();
        List<Team> values = new ArrayList<>(Arrays.asList(Team.values()));
        Team alpha = values.get(random.nextInt(values.size()));
        values.remove(alpha);
        Team beta = values.get(random.nextInt(values.size()));

        ServerCommandSource source = context.getSource();
        Vec3d pos = source.getPosition();
        ArenaStructureProcessor.placeStructure(source.getWorld(), new BlockPos(pos), new Identifier(Dodgebolt.MOD_ID, "arena"), alpha, beta);

        return 1;
    }
}
