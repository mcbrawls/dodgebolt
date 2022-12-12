package dev.andante.dodgebolt.processor;

import com.mojang.serialization.Codec;
import dev.andante.dodgebolt.Dodgebolt;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;

public interface DodgeboltStructureProcessors {
    StructureProcessorType<ArenaStructureProcessor> ARENA = register("arena", ArenaStructureProcessor.CODEC);

    private static <P extends StructureProcessor> StructureProcessorType<P> register(String id, Codec<P> codec) {
        return Registry.register(Registries.STRUCTURE_PROCESSOR, new Identifier(Dodgebolt.MOD_ID, id), () -> codec);
    }
}
