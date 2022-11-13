package dev.andante.dodgebolt.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import static dev.andante.dodgebolt.util.Constants.SPAWN_POS;

public class DodgeboltGameManager {
    private MinecraftServer server;

    @Nullable
    private DodgeboltGame game;

    public DodgeboltGameManager() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = server);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayerEvents.ALLOW_DEATH.register(this::onDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(this::onRespawn);
        ServerPlayConnectionEvents.JOIN.register(this::onJoin);
    }

    protected void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            HungerManager hungerManager = player.getHungerManager();
            hungerManager.setFoodLevel(20);

            player.setFireTicks(0);
            player.setOnFire(false);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, Integer.MAX_VALUE, 0, false, false));

            if (this.game == null && player.isAlive()) {
                player.setHealth(player.getMaxHealth());
            }
        }

        if (this.game != null) {
            this.game.tick(server);
        }
    }

    protected void onJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;
        if (this.game != null) {
            this.game.onJoin(player, handler, sender, server);
        } else {
            player.teleport(server.getOverworld(), SPAWN_POS.getX(), SPAWN_POS.getY(), SPAWN_POS.getZ(), 0.0F, 0.0F);
            player.getInventory().clear();
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    protected boolean onDeath(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.game != null) {
            this.game.onDeath(player, source, amount);
        }

        return true;
    }

    protected void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity player, boolean alive) {
        if (this.game != null) {
            this.game.onRespawn(oldPlayer, player, alive);
        }
    }

    public boolean tryStart(MinecraftServer server) {
        if (this.game != null) {
            return false;
        }

        this.game = new DodgeboltGame();
        this.game.initialize(server);
        return true;
    }

    public boolean tryEnd(MinecraftServer server) {
        if (this.game == null) {
            return false;
        }

        this.game.terminate(server);
        this.game = null;
        return true;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public void onArrowTick(ArrowEntity entity) {
        if (entity.world instanceof ServerWorld world) {
            if (entity.getOwner() instanceof PlayerEntity player) {
                int color = player.getTeamColorValue();
                float r = ((color >> 16) & 0xFF) / 255F;
                float g = ((color >> 8) & 0xFF) / 255F;
                float b = ((color) & 0xFF) / 255F;
                Vec3f to = new Vec3f(r, g, b);
                Vec3f from = to.copy();
                from.scale(0.5F);
                ParticleEffect particleEffect = new DustColorTransitionParticleEffect(from, to, 1.0F);
                world.spawnParticles(particleEffect, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            } else {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    public void onHitBlock(ArrowEntity entity, BlockHitResult hit) {
        if (this.game != null) {
            this.game.onHitBlock(entity, hit);
        }
    }

    public void onHitEntity(ArrowEntity entity, EntityHitResult hit) {
        if (this.game != null) {
            this.game.onHitEntity(entity, hit);
        }
    }

    public void onArrowItemDestroyed(ItemEntity entity) {
        if (this.game != null) {
            this.game.onArrowItemDestroyed(entity);
        }
    }
}
