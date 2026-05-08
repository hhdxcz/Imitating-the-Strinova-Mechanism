package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPosePreviewMixin {
    @Inject(method = "getPose()Lnet/minecraft/world/entity/Pose;", at = @At("RETURN"), cancellable = true)
    private void strinova$forceFallFlyingPoseInPreview(CallbackInfoReturnable<Pose> cir) {
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        if (!StrinovaCollisionPreviewFlyContext.isPreviewFly(self.getUUID())) {
            return;
        }
        if (cir.getReturnValue() == Pose.FALL_FLYING) {
            return;
        }
        cir.setReturnValue(Pose.FALL_FLYING);
    }
}
