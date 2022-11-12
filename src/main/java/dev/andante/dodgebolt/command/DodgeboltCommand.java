package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.andante.dodgebolt.Dodgebolt;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

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

    private static int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!Dodgebolt.DODGEBOLT_MANAGER.tryStart(context.getSource().getServer())) {
            throw new SimpleCommandExceptionType(Text.literal("Could not start a game")).create();
        }

        return 1;
    }

    private static int executeEnd(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!Dodgebolt.DODGEBOLT_MANAGER.tryEnd(context.getSource().getServer())) {
            throw new SimpleCommandExceptionType(Text.literal("Could not end the game")).create();
        }

        return 1;
    }
}
