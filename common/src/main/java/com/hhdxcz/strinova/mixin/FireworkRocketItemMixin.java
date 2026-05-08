package com.hhdxcz.strinova.mixin;

import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

