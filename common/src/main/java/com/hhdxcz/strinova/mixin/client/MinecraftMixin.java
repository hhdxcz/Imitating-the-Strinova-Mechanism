package com.hhdxcz.strinova.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Minecraft 主类 Mixin，控制自定义轮廓线仅在视线可见时显示
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    // 轮廓线队伍名前缀
    @Unique
    private static final String STRINOVA_OUTLINE_TEAM_PREFIX = "strinova_outline_";

    // 视线可见性缓存，避免每帧大量射线检测
    @Unique
    private static final ConcurrentHashMap<UUID, Long> STRINOVA_OUTLINE_VIS_CACHE = new ConcurrentHashMap<>();

    // 上次清理缓存的 tick
    @Unique
    private static long strinova$lastCleanupTick;

    @Shadow
    public LocalPlayer player;

    @Shadow
    public ClientLevel level;

    // 轮廓线玩家仅在视线可见时发光，利用缓存降低性能开销
    @Inject(
            method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void strinova$outlineOnlyWhenVisible(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!(entity instanceof AbstractClientPlayer target)) {
            return;
        }
        Team team = target.getTeam();
        if (team == null) {
            return;
        }
        String teamName = team.getName();
        if (teamName == null || !teamName.startsWith(STRINOVA_OUTLINE_TEAM_PREFIX)) {
            return;
        }

        LocalPlayer self = this.player;
        ClientLevel level = this.level;
        if (self == null || level == null) {
            return;
        }
        long tick = level.getGameTime();
        if (!strinova$hasLineOfSightCached(self, target, tick)) {
            cir.setReturnValue(false);
        }
    }

    // 带缓存的视线检测，缓存有效期为 2 tick
    @Unique
    private static boolean strinova$hasLineOfSightCached(LocalPlayer self, Entity target, long tick) {
        UUID id = target.getUUID();
        Long cached = STRINOVA_OUTLINE_VIS_CACHE.get(id);
        if (cached != null) {
            long packed = cached.longValue();
            long cachedTick = packed >>> 1;
            if ((tick - cachedTick) <= 2L) {
                return (packed & 1L) != 0L;
            }
        }
        boolean visible = self.hasLineOfSight(target);
        long packed = (tick << 1) | (visible ? 1L : 0L);
        STRINOVA_OUTLINE_VIS_CACHE.put(id, Long.valueOf(packed));
        strinova$cleanupCache(tick);
        return visible;
    }

    // 定期清理过期缓存条目，每 200 tick 执行一次
    @Unique
    private static void strinova$cleanupCache(long tick) {
        if ((tick - strinova$lastCleanupTick) < 200L) {
            return;
        }
        strinova$lastCleanupTick = tick;
        STRINOVA_OUTLINE_VIS_CACHE.entrySet().removeIf(e -> {
            Long packed = e.getValue();
            if (packed == null) {
                return true;
            }
            long cachedTick = packed.longValue() >>> 1;
            return (tick - cachedTick) > 600L;
        });
    }
}