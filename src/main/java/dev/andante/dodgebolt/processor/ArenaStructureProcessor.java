package dev.andante.dodgebolt.processor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.andante.dodgebolt.game.GameTeam;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public class ArenaStructureProcessor extends StructureProcessor {
    public static final Codec<ArenaStructureProcessor> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            StringIdentifiable.createCodec(GameTeam::values)
                              .fieldOf("alpha")
                              .forGetter(ArenaStructureProcessor::getAlpha),
            StringIdentifiable.createCodec(GameTeam::values)
                              .fieldOf("beta")
                              .forGetter(ArenaStructureProcessor::getBeta)
        ).apply(instance, ArenaStructureProcessor::new)
    );

    private final GameTeam alpha, beta;

    public ArenaStructureProcessor(GameTeam alpha, GameTeam beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public GameTeam getAlpha() {
        return this.alpha;
    }

    public GameTeam getBeta() {
        return this.beta;
    }

    @Override
    public StructureTemplate.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo currentBlockInfo, StructurePlacementData data) {
        Block block = currentBlockInfo.state.getBlock();
        Block nu = block;

        if (block == Blocks.RED_CONCRETE) {
            nu = this.alpha.getConcrete();
        } else if (block == Blocks.RED_CARPET) {
            nu = this.alpha.getCarpet();
        } else if (block == Blocks.BLUE_CONCRETE) {
            nu = this.beta.getConcrete();
        } else if (block == Blocks.BLUE_CARPET) {
            nu = this.beta.getCarpet();
        }

        return nu != block ? new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos, nu.getDefaultState(), currentBlockInfo.nbt) : currentBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return DodgeboltStructureProcessors.ARENA;
    }

    public static StructureTemplate getStructure(ServerWorld world, Identifier id) {
        return world == null ? new StructureTemplate() : world.getStructureTemplateManager().getTemplateOrBlank(id);
    }

    public static void placeStructure(ServerWorld world, BlockPos pos, Identifier id, GameTeam alpha, GameTeam beta) {
        StructureTemplate structure = getStructure(world, id);
        StructurePlacementData data = new StructurePlacementData().addProcessor(new ArenaStructureProcessor(alpha, beta));
        structure.place(world, pos, BlockPos.ORIGIN, data, world.random, Block.NOTIFY_LISTENERS);
    }
}
