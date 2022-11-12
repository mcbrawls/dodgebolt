package dev.andante.dodgebolt.util;

import dev.andante.dodgebolt.Constants;
import dev.andante.dodgebolt.game.GameTeam;
import dev.andante.dodgebolt.processor.ArenaStructureProcessor;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public interface StructureHelper {
    static StructureTemplate getStructure(ServerWorld world, Identifier id) {
        return world == null ? new StructureTemplate() : world.getStructureTemplateManager().getTemplateOrBlank(id);
    }

    static void placeArena(ServerWorld world, BlockPos pos, GameTeam alpha, GameTeam beta) {
        StructureTemplate structure = getStructure(world, Constants.ARENA_STRUCTURE_ID);
        StructurePlacementData data = new StructurePlacementData().addProcessor(new ArenaStructureProcessor(alpha, beta));
        structure.place(world, pos, BlockPos.ORIGIN, data, world.random, Block.NOTIFY_LISTENERS);
    }
}
