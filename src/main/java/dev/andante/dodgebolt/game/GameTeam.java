package dev.andante.dodgebolt.game;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;

public enum GameTeam implements StringIdentifiable {
    RED(Blocks.RED_CONCRETE, Blocks.RED_CARPET, 0xFC5453, Formatting.RED),
    ORANGE(Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CARPET, 0xFCA800, Formatting.GOLD),
    YELLOW(Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CARPET, 0xFCFC54, Formatting.YELLOW),
    LIME(Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET, 0x54FC54, Formatting.GREEN),
    GREEN(Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET, 0x00A800, Formatting.DARK_GREEN),
    AQUA(Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CARPET, 0x54DAFC, Formatting.AQUA),
    CYAN(Blocks.CYAN_CONCRETE, Blocks.CYAN_CARPET, 0x00B997, Formatting.DARK_AQUA),
    BLUE(Blocks.BLUE_CONCRETE, Blocks.BLUE_CARPET, 0x5486FC, Formatting.BLUE),
    PURPLE(Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CARPET, 0x8632FC, Formatting.DARK_PURPLE),
    PINK(Blocks.PINK_CONCRETE, Blocks.PINK_CARPET, 0xFC54FC, Formatting.LIGHT_PURPLE);

    private final Block concrete, carpet;
    private final int color;
    private final Formatting formattingColor;

    GameTeam(Block concrete, Block carpet, int color, Formatting formattingColor) {
        this.concrete = concrete;
        this.carpet = carpet;
        this.color = color;
        this.formattingColor = formattingColor;
    }

    public Block getConcrete() {
        return this.concrete;
    }

    public Block getCarpet() {
        return this.carpet;
    }

    public int getColor() {
        return this.color;
    }

    public Formatting getFormattingColor() {
        return this.formattingColor;
    }

    public AbstractTeam getTeam(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        AbstractTeam team = scoreboard.getTeam(this.name());
        if (team != null) {
            return team;
        }

        throw new IllegalStateException("Team not present");
    }

    public static GameTeam of(AbstractTeam team) {
        String id = team.getName();
        return GameTeam.valueOf(id);
    }

    @Override
    public String asString() {
        return this.name().toLowerCase();
    }
}
