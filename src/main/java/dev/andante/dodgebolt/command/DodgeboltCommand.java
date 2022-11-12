package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

public interface DodgeboltCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("dodgebolt")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(DodgeboltCommand::execute)
                        .then(
                                literal("end")
                                        .executes(DodgeboltCommand::executeEnd)
                        )
        );
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        return 1;
    }

    private static int executeEnd(CommandContext<ServerCommandSource> context) {
        return 1;
    }
}
