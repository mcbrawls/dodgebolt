package dev.andante.dodgebolt.mixin;

import dev.andante.dodgebolt.Dodgebolt;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public class ItemMixin {
    @Inject(method = "onItemEntityDestroyed", at = @At("HEAD"))
    private void onOnItemEntityDestroyed(ItemEntity entity, CallbackInfo ci) {
        Item that = (Item) (Object) this;
        if (that == Items.ARROW) {
            Dodgebolt.DODGEBOLT_MANAGER.onArrowItemDestroyed(entity);
        }
    }
}
