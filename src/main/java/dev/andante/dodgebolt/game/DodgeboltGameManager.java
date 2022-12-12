package dev.andante.dodgebolt.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;

import static dev.andante.dodgebolt.util.Constants.SPAWN_POS;

public class DodgeboltGameManager {
    private MinecraftServer server;

    @Nullable
    private DodgeboltGame game;

    public DodgeboltGameManager() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Scoreboard scoreboard = server.getScoreboard();
            for (GameTeam gameTeam : GameTeam.values()) {
                String id = gameTeam.name();
                Team team = Optional.ofNullable(scoreboard.getTeam(id)).orElseGet(() -> scoreboard.addTeam(id));
                team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
                team.setFriendlyFireAllowed(false);
                team.setColor(gameTeam.getFormattingColor());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLivingEntityEvents.ALLOW_DEATH.register(this::onDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(this::onRespawn);
        ServerPlayConnectionEvents.JOIN.register(this::onJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
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

            AbstractTeam team = player.getScoreboardTeam();
            if (team == null) {
                server.getScoreboard().addPlayerToTeam(player.getEntityName(), (player.hasPermissionLevel(2) ? GameTeam.ADMIN : GameTeam.SPECTATOR).getTeam(server));
            }
        }

        if (this.game != null) {
            try {
                this.game.tick(server);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
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

    protected void onDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        if (this.game != null) {
            this.game.onDisconnect(handler, server);
        }
    }

    protected boolean onDeath(LivingEntity entity, DamageSource source, float amount) {
        if (this.game != null && entity instanceof ServerPlayerEntity player) {
            this.game.onDeath(player, source, amount);
        }

        return true;
    }

    protected void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity player, boolean alive) {
        if (this.game != null) {
            this.game.onRespawn(oldPlayer, player, alive);
        }
    }

    public boolean tryStart(MinecraftServer server, GameTeam alpha, GameTeam beta) {
        if (this.game != null) {
            return false;
        }

        this.game = new DodgeboltGame(alpha, beta);
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
                ParticleEffect particleEffect = new DustParticleEffect(new Vector3f(r, g, b), 1.0F);
                world.spawnParticles(particleEffect, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            } else {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        entity.setGlowing(true);
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

    public void onItemTick(ItemEntity entity) {
        if (this.game != null) {
            this.game.onItemTick(entity);
        }
    }
}
