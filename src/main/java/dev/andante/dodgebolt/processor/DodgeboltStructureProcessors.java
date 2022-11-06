package dev.andante.dodgebolt.processor;

import com.mojang.serialization.Codec;
import dev.andante.dodgebolt.Dodgebolt;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public interface DodgeboltStructureProcessors {
    StructureProcessorType<ArenaStructureProcessor> ARENA = register("arena", ArenaStructureProcessor.CODEC);

    private static <P extends StructureProcessor> StructureProcessorType<P> register(String id, Codec<P> codec) {
        return Registry.register(Registry.STRUCTURE_PROCESSOR, new Identifier(Dodgebolt.MOD_ID, id), () -> codec);
    }
}
