package dev.andante.dodgebolt.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3f;

public class DodgeboltGameManager {
    public DodgeboltGameManager() {
    }

    public void onArrowTick(ArrowEntity entity) {
        if (entity.world instanceof ServerWorld world && entity.getOwner() instanceof PlayerEntity player) {
            int color = player.getTeamColorValue();
            float r = ((color >> 16 ) & 0xFF) / 255F;
            float g = ((color >>  8 ) & 0xFF) / 255F;
            float b = ((color       ) & 0xFF) / 255F;
            Vec3f to = new Vec3f(r, g, b);
            Vec3f from = to.copy();
            from.scale(0.5F);
            ParticleEffect particleEffect = new DustColorTransitionParticleEffect(from, to, 1.0F);
            world.spawnParticles(particleEffect, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public void onHitBlock(ArrowEntity entity, BlockHitResult hit) {
    }

    public void onHitEntity(ArrowEntity entity, EntityHitResult hit) {
    }
}
