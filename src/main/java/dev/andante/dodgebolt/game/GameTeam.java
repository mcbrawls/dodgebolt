package dev.andante.dodgebolt.game;

import com.mojang.datafixers.util.Pair;
import dev.andante.dodgebolt.Dodgebolt;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public enum GameTeam implements StringIdentifiable {
    RED(new BlockData(Blocks.RED_CONCRETE, Blocks.RED_CARPET), 0xFC5453, Formatting.RED),
    ORANGE(new BlockData(Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CARPET), 0xFCA800, Formatting.GOLD),
    YELLOW(new BlockData(Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CARPET), 0xFCFC54, Formatting.YELLOW),
    LIME(new BlockData(Blocks.LIME_CONCRETE, Blocks.LIME_CARPET), 0x54FC54, Formatting.GREEN),
    GREEN(new BlockData(Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET), 0x00A800, Formatting.DARK_GREEN),
    AQUA(new BlockData(Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CARPET), 0x54DAFC, Formatting.AQUA),
    CYAN(new BlockData(Blocks.CYAN_CONCRETE, Blocks.CYAN_CARPET), 0x00B997, Formatting.DARK_AQUA),
    BLUE(new BlockData(Blocks.BLUE_CONCRETE, Blocks.BLUE_CARPET), 0x5486FC, Formatting.BLUE),
    PURPLE(new BlockData(Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CARPET), 0x8632FC, Formatting.DARK_PURPLE),
    PINK(new BlockData(Blocks.PINK_CONCRETE, Blocks.PINK_CARPET), 0xFC54FC, Formatting.LIGHT_PURPLE);

    private final BlockData blockData;
    private final int color;
    private final Formatting formatting;

    GameTeam(BlockData blockData, int color, Formatting formatting) {
        this.blockData = blockData;
        this.color = color;
        this.formatting = formatting;
    }

    public BlockData getBlockData() {
        return this.blockData;
    }

    public int getColor() {
        return this.color;
    }

    public Formatting getFormattingColor() {
        return this.formatting;
    }

    public Team getTeam(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(this.name());
        if (team != null) {
            return team;
        }

        throw new IllegalStateException("Team not present");
    }

    public List<ServerPlayerEntity> getPlayers(MinecraftServer server) {
        return PlayerLookup.all(server)
                           .stream()
                           .filter(playerx -> of(playerx.getScoreboardTeam()) == this)
                           .toList();
    }

    public List<ServerPlayerEntity> getPlayers() {
        return this.getPlayers(Dodgebolt.DODGEBOLT_MANAGER.getServer());
    }

    public static GameTeam of(AbstractTeam team) {
        if (team == null) {
            return null;
        }

        String id = team.getName();
        try {
            return GameTeam.valueOf(id);
        } catch (IllegalArgumentException ignored) {
        }

        return null;
    }

    public static Pair<GameTeam, GameTeam> getRandomPair() {
        List<GameTeam> teams = new ArrayList<>(Arrays.asList(values()));
        Random random = Random.create();
        GameTeam first = teams.get(random.nextInt(teams.size()));
        teams.remove(first);
        return Pair.of(first, teams.get(random.nextInt(teams.size())));
    }

    public static void forEachPair(BiConsumer<GameTeam, GameTeam> action) {
        GameTeam[] values = values();
        for (GameTeam first : values) {
            for (GameTeam second : values) {
                action.accept(first, second);
            }
        }
    }

    @Override
    public String asString() {
        return this.name().toLowerCase();
    }

    public record BlockData(Block concrete, Block carpet) {
    }
}
