package dev.andante.dodgebolt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.andante.dodgebolt.game.GameTeam;
import net.minecraft.scoreboard.AbstractTeam.CollisionRule;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.literal;

public interface CreateTeamsCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("createteams").requires(source -> source.hasPermissionLevel(2)).executes(CreateTeamsCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (GameTeam gameTeam : GameTeam.values()) {
            String id = gameTeam.name();
            Team team = Optional.ofNullable(scoreboard.getTeam(id)).orElseGet(() -> scoreboard.addTeam(id));
            team.setCollisionRule(CollisionRule.NEVER);
            team.setFriendlyFireAllowed(false);
            team.setColor(gameTeam.getFormattingColor());
        }

        source.sendFeedback(Text.literal("Created and configured teams"), true);
        return 1;
    }
}
