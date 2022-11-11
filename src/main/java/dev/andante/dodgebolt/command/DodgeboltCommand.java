package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.andante.dodgebolt.game.DodgeboltGame;
import dev.andante.dodgebolt.game.GameTeam;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

public interface DodgeboltCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = literal("dodgebolt").requires(source -> source.hasPermissionLevel(2)).then(literal("end").executes(DodgeboltCommand::executeEnd));

        for (GameTeam alpha : GameTeam.values()) {
            for (GameTeam beta : GameTeam.values()) {
                builder.then(literal(alpha.name()).then(literal(beta.name()).executes(context -> execute(context, alpha, beta))));
            }
        }

        dispatcher.register(builder.executes(context -> execute(context, null, null)));
    }

    static int execute(CommandContext<ServerCommandSource> context, GameTeam alpha, GameTeam beta) {
        if (DodgeboltGame.INSTANCE.isRunning()) {
            throw new IllegalStateException("Dodgebolt game is running");
        }

        if (alpha == null || beta == null) {
            alpha = GameTeam.RED;
            beta  = GameTeam.BLUE;
        }

        DodgeboltGame.INSTANCE.tryStart(context.getSource().getWorld(), alpha, beta);

        return 1;
    }

    static int executeEnd(CommandContext<ServerCommandSource> context) {
        if (!DodgeboltGame.INSTANCE.isRunning()) {
            throw new IllegalStateException("Dodgebolt game is not running");
        }

        DodgeboltGame.INSTANCE.end(context.getSource().getServer());

        return 1;
    }
}
