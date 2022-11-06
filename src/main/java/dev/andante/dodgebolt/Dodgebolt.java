package dev.andante.dodgebolt;

import com.google.common.reflect.Reflection;
import com.mojang.logging.LogUtils;
import dev.andante.dodgebolt.command.SpawnArenaCommand;
import dev.andante.dodgebolt.processor.DodgeboltStructureProcessors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;

public class Dodgebolt implements ModInitializer {
    public static final String MOD_ID = "dodgebolt";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);
        Reflection.initialize(DodgeboltStructureProcessors.class);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SpawnArenaCommand.register(dispatcher);
        });
    }
}
