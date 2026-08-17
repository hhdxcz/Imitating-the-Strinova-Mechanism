package com.hhdxcz.strinova.mixin;

import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// 烟花火箭 Mixin：拦截 isFallFlying 检测，防止飘飞状态下使用烟花火箭加速
@Mixin(FireworkRocketItem.class)
public abstract class FireworkRocketItemMixin {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;isFallFlying()Z"
            )
    )
    private boolean strinova$disableBoostWhenPaperFlying(Player player) {
        if (WaPaperState.isFly(player.getUUID())) {
            return false;
        }
        return player.isFallFlying();
    }
}