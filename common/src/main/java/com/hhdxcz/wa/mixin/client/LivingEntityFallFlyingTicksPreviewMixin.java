package com.hhdxcz.wa.mixin.client;

import com.hhdxcz.wa.client.WaCollisionPreviewFlyContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityFallFlyingTicksPreviewMixin {
    @Inject(method = "getFallFlyingTicks()I", at = @At("RETURN"), cancellable = true)
    private void wa$forceFallFlyingTicksInPreview(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() > 0) {
            return;
        }
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        if (!WaCollisionPreviewFlyContext.isPreviewFly(self.getUUID())) {
            return;
        }
        cir.setReturnValue(1);
    }
}
