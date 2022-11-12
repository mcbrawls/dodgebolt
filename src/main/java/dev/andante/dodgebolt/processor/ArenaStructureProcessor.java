package dev.andante.dodgebolt.processor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.andante.dodgebolt.game.GameTeam;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate.StructureBlockInfo;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

import java.util.Map;
import java.util.function.Function;

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

    public static final Map<Block, Function<GameTeam.BlockData, Block>> ALPHA_REMAPPERS = Map.of(
            Blocks.RED_CONCRETE, GameTeam.BlockData::concrete,
            Blocks.RED_CARPET, GameTeam.BlockData::carpet
    );

    public static final Map<Block, Function<GameTeam.BlockData, Block>> BETA_REMAPPERS = Map.of(
            Blocks.BLUE_CONCRETE, GameTeam.BlockData::concrete,
            Blocks.BLUE_CARPET, GameTeam.BlockData::carpet
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
    public StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot, StructureBlockInfo originalBlockInfo, StructureBlockInfo currentBlockInfo, StructurePlacementData data) {
        BlockState state = currentBlockInfo.state;
        Block block = state.getBlock();
        BlockState nu = state;
        Function<GameTeam.BlockData, Block> alphaRemapper = ALPHA_REMAPPERS.get(block);
        if (alphaRemapper == null) {
            Function<GameTeam.BlockData, Block> betaRemapper = BETA_REMAPPERS.get(block);
            if (betaRemapper != null) {
                nu = betaRemapper.apply(this.beta.getBlockData()).getDefaultState();
            }
        } else {
            nu = alphaRemapper.apply(this.alpha.getBlockData()).getDefaultState();
        }

        return new StructureBlockInfo(currentBlockInfo.pos, nu, currentBlockInfo.nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return DodgeboltStructureProcessors.ARENA;
    }
}
