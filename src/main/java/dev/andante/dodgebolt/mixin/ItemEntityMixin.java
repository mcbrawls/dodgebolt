package dev.andante.dodgebolt.mixin;

import dev.andante.dodgebolt.Dodgebolt;
import dev.andante.dodgebolt.ItemEntityAccess;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin implements ItemEntityAccess {
    @Unique
    private int timer;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Dodgebolt.DODGEBOLT_MANAGER.onItemTick((ItemEntity) (Object) this);
    }

    @Unique
    @Override
    public void setTimer(int timer) {
        this.timer = timer;
    }

    @Unique
    @Override
    public int getTimer() {
        return this.timer;
    }
}
