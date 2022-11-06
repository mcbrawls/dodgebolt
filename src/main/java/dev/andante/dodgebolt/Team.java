package dev.andante.dodgebolt;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.StringIdentifiable;

public enum Team implements StringIdentifiable {
    RED(Blocks.RED_CONCRETE, Blocks.RED_CARPET),
    ORANGE(Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CARPET),
    YELLOW(Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CARPET),
    LIME(Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET),
    GREEN(Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET),
    AQUA(Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CARPET),
    CYAN(Blocks.CYAN_CONCRETE, Blocks.CYAN_CARPET),
    BLUE(Blocks.BLUE_CONCRETE, Blocks.BLUE_CARPET),
    PURPLE(Blocks.PURPLE_CONCRETE, Blocks.PURPLE_CARPET),
    PINK(Blocks.PINK_CONCRETE, Blocks.PINK_CARPET);

    private final Block concrete, carpet;

    Team(Block concrete, Block carpet) {
        this.concrete = concrete;
        this.carpet = carpet;
    }

    public Block getConcrete() {
        return this.concrete;
    }

    public Block getCarpet() {
        return this.carpet;
    }

    @Override
    public String asString() {
        return this.name().toLowerCase();
    }
}
