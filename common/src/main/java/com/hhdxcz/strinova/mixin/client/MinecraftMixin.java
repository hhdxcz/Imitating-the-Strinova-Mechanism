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

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Unique
    private static final String STRINOVA_OUTLINE_TEAM_PREFIX = "wa_outline_";

    @Unique
    private static final ConcurrentHashMap<UUID, Long> STRINOVA_OUTLINE_VIS_CACHE = new ConcurrentHashMap<>();

    @Unique
    private static long strinova$lastCleanupTick;

    @Shadow
    public LocalPlayer player;

    @Shadow
    public ClientLevel level;

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
