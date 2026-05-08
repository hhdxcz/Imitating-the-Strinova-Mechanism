package com.hhdxcz.strinova.gameplay;

import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class StrinovaDoubleJump {

    public static final String TAG_USED = "wa_djump_used";

    private static final double BASE_Y_VEL = 0.42D;

    private StrinovaDoubleJump() {
    }

    public static Result tryApply(ServerPlayer player) {
        if (player == null) {
            return Result.fail("player_null");
        }
        if (player.isSpectator()) {
            return Result.fail("spectator");
        }
        if (player.getAbilities().flying) {
            return Result.fail("flying");
        }
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            return Result.fail("not_in_air");
        }

        UUID playerId = player.getUUID();
        if (WaPaperState.isFly(playerId) || WaPaperState.isWall(playerId) || player.isFallFlying()) {
            return Result.fail("conflict_state");
        }
        int maxExtra = StrinovaAirJumpSettings.DEFAULT_EXTRA_JUMPS;
        var server = player.getServer();
        if (server != null) {
            maxExtra = StrinovaAirJumpSettings.get(server).getExtraJumps(playerId);
        }
        if (maxExtra != StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
            int used = StrinovaAirJumpRuntime.getUsed(playerId);
            if (used >= maxExtra) {
                return Result.fail("limit");
            }
        }

        double yVel = BASE_Y_VEL;
        MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP);
        if (jumpBoost != null) {
            yVel += (double) ((jumpBoost.getAmplifier() + 1) * 0.1F);
        }

        Vec3 motion = player.getDeltaMovement();
        double newY = motion.y >= (yVel - 1.0E-3D) ? Math.min(motion.y + 0.1D, 1.0D) : yVel;
        player.setDeltaMovement(motion.x, newY, motion.z);
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
        if (maxExtra != StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
            StrinovaAirJumpRuntime.incrementUsed(playerId);
        }
        return Result.success();
    }

    public static final class Result {
        public final boolean applied;
        public final String reason;

        private Result(boolean applied, String reason) {
            this.applied = applied;
            this.reason = reason;
        }

        public static Result success() {
            return new Result(true, "applied");
        }

        public static Result fail(String reason) {
            return new Result(false, reason);
        }
    }
}
