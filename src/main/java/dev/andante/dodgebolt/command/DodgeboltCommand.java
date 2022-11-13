package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.andante.dodgebolt.Dodgebolt;
import dev.andante.dodgebolt.game.GameTeam;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public interface DodgeboltCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = literal("dodgebolt").requires(source -> source.hasPermissionLevel(2)).executes(context -> execute(context, GameTeam.RED, GameTeam.BLUE)).then(literal("end").executes(DodgeboltCommand::executeEnd));
        GameTeam.forEachPair((alpha, beta) -> builder.then(literal(alpha.name()).then(literal(beta.name()).executes(context -> execute(context, alpha, beta)))));
        dispatcher.register(builder);
    }

    private static int execute(CommandContext<ServerCommandSource> context, GameTeam alpha, GameTeam beta) throws CommandSyntaxException {
        if (!Dodgebolt.DODGEBOLT_MANAGER.tryStart(context.getSource().getServer(), alpha, beta)) {
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
