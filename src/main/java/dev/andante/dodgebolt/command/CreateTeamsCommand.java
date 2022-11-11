package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.andante.dodgebolt.game.GameTeam;
import net.minecraft.scoreboard.AbstractTeam.CollisionRule;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.literal;

public interface CreateTeamsCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("createteams").requires(source -> source.hasPermissionLevel(2)).executes(CreateTeamsCommand::execute));
    }

    static int execute(CommandContext<ServerCommandSource> context) {
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();

        for (GameTeam gameTeam : GameTeam.values()) {
            String id = gameTeam.name();
            Team team = Optional.ofNullable(scoreboard.getTeam(id)).orElseGet(() -> scoreboard.addTeam(id));
            team.setCollisionRule(CollisionRule.NEVER);
            team.setFriendlyFireAllowed(false);
            team.setColor(gameTeam.getFormattingColor());
        }

        return 1;
    }
}
