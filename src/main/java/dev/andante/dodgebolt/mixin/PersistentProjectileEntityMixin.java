package dev.andante.dodgebolt.mixin;

import dev.andante.dodgebolt.game.DodgeboltGame;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin extends ProjectileEntity {
    private PersistentProjectileEntityMixin(EntityType<? extends ProjectileEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        PersistentProjectileEntity that = (PersistentProjectileEntity) (Object) this;
        if (that instanceof ArrowEntity arrowEntity) {
            DodgeboltGame.INSTANCE.onArrowTick(arrowEntity);
        }
    }

    @Inject(method = "onBlockHit", at = @At("TAIL"))
    private void onHitBlock(BlockHitResult hit, CallbackInfo ci) {
        PersistentProjectileEntity that = (PersistentProjectileEntity) (Object) this;
        if (that instanceof ArrowEntity arrowEntity) {
            DodgeboltGame.INSTANCE.onHitBlock(arrowEntity, hit);
        }
    }

    @Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true)
    private void onHitEntity(EntityHitResult hit, CallbackInfo ci) {
        PersistentProjectileEntity that = (PersistentProjectileEntity) (Object) this;
        if (that instanceof ArrowEntity arrowEntity) {
            DodgeboltGame.INSTANCE.onHitEntity(arrowEntity, hit);
            ci.cancel();
        }
    }
}
