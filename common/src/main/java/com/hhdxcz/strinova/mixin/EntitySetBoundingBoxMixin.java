package com.hhdxcz.strinova.mixin;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.paper.WaPaperState;
import dev.architectury.platform.Platform;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Entity.class)
public abstract class EntitySetBoundingBoxMixin {

    @Unique
    private static final ConcurrentHashMap<UUID, Boolean> WA_PAPER_IDLE_THIN_X = new ConcurrentHashMap<>();

    @Unique
    private static final double WA_VANILLA_STANDING_EYE_HEIGHT = 1.62D;

    @Unique
    private static final String WA_TARGET_YSM_MODEL_NAME = "香奈美-世纪歌姬";

    @Unique
    private static final String WA_TARGET_YSM_MODEL_FILE = "香奈美-世纪歌姬.ysm";

    @Unique
    private static Boolean WA_YSM_LOADED;

    @Unique
    private static final ConcurrentHashMap<UUID, Long> WA_YSM_TARGET_CACHE = new ConcurrentHashMap<>();

    @Unique
    private static final ThreadLocal<Boolean> WA_DISABLE_FLY_EYE_HOOK = ThreadLocal.withInitial(() -> false);

    @Unique
    private static boolean wa$isYsmLoaded() {
        Boolean loaded = WA_YSM_LOADED;
        if (loaded != null) {
            return loaded.booleanValue();
        }
        boolean v;
        try {
            v = Platform.isModLoaded("yes_steve_model");
        } catch (Throwable t) {
            v = false;
        }
        WA_YSM_LOADED = v;
        return v;
    }

    @Unique
    private static long wa$packYsmCache(int nextCheckTick, boolean matched) {
        return (((long) nextCheckTick) << 32) | (matched ? 1L : 0L);
    }

    @Unique
    private static int wa$unpackNextCheckTick(long packed) {
        return (int) (packed >>> 32);
    }

    @Unique
    private static boolean wa$unpackMatched(long packed) {
        return (packed & 1L) != 0L;
    }

    @Unique
    private static boolean wa$isTargetYsmModel(Player player) {
        if (player == null) {
            return false;
        }
        int tick = player.tickCount;
        if ((tick & 0xFF) == 0 && WA_YSM_TARGET_CACHE.size() > 1024) {
            WA_YSM_TARGET_CACHE.clear();
        }
        UUID id = player.getUUID();
        Long packed = WA_YSM_TARGET_CACHE.get(id);
        if (packed != null) {
            int nextCheckTick = wa$unpackNextCheckTick(packed.longValue());
            if (tick < nextCheckTick) {
                return wa$unpackMatched(packed.longValue());
            }
        }
        boolean matched = wa$scanSynchedDataForTarget(player);
        WA_YSM_TARGET_CACHE.put(id, wa$packYsmCache(tick + 20, matched));
        return matched;
    }

    @Unique
    private static boolean wa$scanSynchedDataForTarget(Player player) {
        try {
            SynchedEntityData data = player.getEntityData();
            Iterable<?> items = wa$getSynchedDataItems(data);
            if (items == null) {
                return false;
            }
            for (Object item : items) {
                Object value = wa$getDataItemValue(item);
                if (wa$isTargetModelValue(value)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    @Unique
    private static boolean wa$isTargetModelValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return s.contains(WA_TARGET_YSM_MODEL_NAME) || s.contains(WA_TARGET_YSM_MODEL_FILE);
        }
        String s = String.valueOf(value);
        return s.contains(WA_TARGET_YSM_MODEL_NAME) || s.contains(WA_TARGET_YSM_MODEL_FILE);
    }

    @Unique
    private static Iterable<?> wa$getSynchedDataItems(SynchedEntityData data) {
        if (data == null) {
            return null;
        }
        try {
            Method m = SynchedEntityData.class.getDeclaredMethod("getAll");
            m.setAccessible(true);
            Object res = m.invoke(data);
            if (res instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Throwable ignored) {
        }
        try {
            Field byId = SynchedEntityData.class.getDeclaredField("itemsById");
            byId.setAccessible(true);
            Object map = byId.get(data);
            Iterable<?> values = wa$tryGetValuesIterable(map);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }
        try {
            Field items = SynchedEntityData.class.getDeclaredField("items");
            items.setAccessible(true);
            Object res = items.get(data);
            if (res instanceof Iterable<?> iterable) {
                return iterable;
            }
            Iterable<?> values = wa$tryGetValuesIterable(res);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }
        for (Field f : SynchedEntityData.class.getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object res = f.get(data);
                if (res instanceof Iterable<?> iterable) {
                    return iterable;
                }
                Iterable<?> values = wa$tryGetValuesIterable(res);
                if (values != null) {
                    return values;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Unique
    private static Iterable<?> wa$tryGetValuesIterable(Object maybeMap) {
        if (maybeMap == null) {
            return null;
        }
        try {
            Method values = maybeMap.getClass().getMethod("values");
            Object res = values.invoke(maybeMap);
            if (res instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Unique
    private static Object wa$getDataItemValue(Object item) {
        if (item == null) {
            return null;
        }
        try {
            Method m = item.getClass().getMethod("getValue");
            m.setAccessible(true);
            return m.invoke(item);
        } catch (Throwable ignored) {
        }
        try {
            Method m = item.getClass().getMethod("value");
            m.setAccessible(true);
            return m.invoke(item);
        } catch (Throwable ignored) {
        }
        try {
            Field f = item.getClass().getDeclaredField("value");
            f.setAccessible(true);
            return f.get(item);
        } catch (Throwable ignored) {
        }
        for (Field f : item.getClass().getDeclaredFields()) {
            try {
                if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0) {
                    continue;
                }
                f.setAccessible(true);
                return f.get(item);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Unique
    private static double wa$getModelHeightScale(Player player) {
        if (player == null) {
            return 1.0D;
        }
        WA_DISABLE_FLY_EYE_HOOK.set(true);
        float eye;
        try {
            eye = player.getEyeHeight(Pose.STANDING);
        } finally {
            WA_DISABLE_FLY_EYE_HOOK.set(false);
        }
        if (!(eye > 1.0E-4F)) {
            return 1.0D;
        }
        double scale = (double) eye / WA_VANILLA_STANDING_EYE_HEIGHT;
        if (scale < 0.25D) {
            scale = 0.25D;
        } else if (scale > 4.0D) {
            scale = 4.0D;
        }
        return Math.abs(scale - 1.0D) < 0.01D ? 1.0D : scale;
    }

    @Unique
    private static AABB wa$scaleBoxHeight(AABB box, double scale) {
        if (box == null || scale == 1.0D) {
            return box;
        }
        double h = box.getYsize();
        if (!(h > 0.0D)) {
            return box;
        }
        double minY = box.minY;
        double maxY = minY + Math.max(0.1D, h * scale);
        return new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ);
    }

    // 统一拦截所有 setBoundingBox 写入，避免贴墙移动时被原版逻辑“回弹”成正常碰撞箱
    @ModifyVariable(
            method = "setBoundingBox(Lnet/minecraft/world/phys/AABB;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private AABB wa$adjustBoundingBox(AABB box) {
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return box;
        }
        var playerId = self.getUUID();
        if (box == null) {
            return null;
        }

        boolean targetYsmModel = wa$isTargetYsmModel(self);

        double heightScale = wa$getModelHeightScale(self);
        if (heightScale != 1.0D) {
            box = wa$scaleBoxHeight(box, heightScale);
        }

        if (WaPaperState.isPaper(playerId)) {
            double sizeX = box.getXsize();
            double sizeZ = box.getZsize();
            if (sizeX <= 0.0D || sizeZ <= 0.0D) {
                return box;
            }

            boolean wall = WaPaperState.isWall(playerId);
            double finalSizeX;
            double finalSizeZ;
            
            double factor = 0.06D;
            double wide = Math.max(sizeX, sizeZ);
            double thin = wide * factor;

            if (wall) {
                WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
                boolean thinX = plane != null && plane.axisX;
                finalSizeX = thinX ? thin : wide;
                finalSizeZ = thinX ? wide : thin;
            } else {
                Vec3 motion = self.getDeltaMovement();
                double h = motion.x * motion.x + motion.z * motion.z;
                boolean idle = self.onGround()
                        && h < 1.0E-6D
                        && Math.abs(motion.y) < 1.0E-6D;
                boolean thinX;
                if (idle) {
                    Boolean cached = WA_PAPER_IDLE_THIN_X.get(playerId);
                    if (cached != null) {
                        thinX = cached.booleanValue();
                    } else {
                        float yawDeg = self.getYRot();
                        double yawRad = Math.toRadians(yawDeg);
                        thinX = Math.abs(Math.sin(yawRad)) > Math.abs(Math.cos(yawRad));
                        WA_PAPER_IDLE_THIN_X.put(playerId, thinX);
                    }
                } else {
                    float yawDeg = self.getYRot();
                    double yawRad = Math.toRadians(yawDeg);
                    thinX = Math.abs(Math.sin(yawRad)) > Math.abs(Math.cos(yawRad));
                    WA_PAPER_IDLE_THIN_X.put(playerId, thinX);
                }
                finalSizeX = thinX ? thin : wide;
                finalSizeZ = thinX ? wide : thin;
            }

            double centerX = (box.minX + box.maxX) * 0.5D;
            double centerZ = (box.minZ + box.maxZ) * 0.5D;
            double minY = box.minY;
            double maxY = box.maxY + (targetYsmModel ? 1.0D : 0.0D);

            double halfX = finalSizeX * 0.5D;
            double halfZ = finalSizeZ * 0.5D;

            StrinovaCollisionBoxTuning.Tuning syncTuning = StrinovaCollisionBoxTuning.getSync(playerId);
            if (syncTuning.offsetY() != 0.0D || syncTuning.sizeY() != 0.0D) {
                double tunedMinY = minY + Math.min(0.0D, syncTuning.offsetY());
                double tunedHeight = Math.max(0.05D, (maxY - minY) + syncTuning.sizeY());
                minY = tunedMinY;
                maxY = tunedMinY + tunedHeight;
            }

            return new AABB(
                    centerX - halfX, minY, centerZ - halfZ,
                    centerX + halfX, maxY, centerZ + halfZ
            );
        }
        WA_PAPER_IDLE_THIN_X.remove(playerId);

        if (targetYsmModel && self.onGround()) {
            box = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY + 1.0D, box.maxZ);
        }

        if (WaPaperState.isFly(playerId)) {
            // 飘飞：人物横躺，碰撞箱贴合身体（长度≈身高，厚度≈站立宽度），并在Y轴居中
            double sizeX = box.getXsize();
            double sizeY = box.getYsize();
            double sizeZ = box.getZsize();
            if (sizeX <= 0.0D || sizeY <= 0.0D || sizeZ <= 0.0D) {
                return box;
            }

            float yawDeg = self.getYRot();
            double yawRad = Math.toRadians(yawDeg);
            double centerX = (box.minX + box.maxX) * 0.5D;
            double centerZ = (box.minZ + box.maxZ) * 0.5D;
            EntityDimensions standing = self.getDimensions(Pose.STANDING);
            double standingHeight = Math.max(sizeY, standing.height * heightScale);
            double standingWidth = Math.max(Math.max(sizeX, sizeZ), standing.width);

            double length = standingHeight;
            double factor = 0.2D;
            double height = Math.max(0.02D, standingWidth * factor);
            double bodyWidth = standingWidth;

            double longHalf = Math.max(0.25D, length * 0.5D);
            double shortHalf = Math.max(0.05D, bodyWidth * 0.5D);

            boolean longAxisX = Math.abs(Math.sin(yawRad)) > Math.abs(Math.cos(yawRad));

            StrinovaCollisionBoxTuning.Tuning flyTuning = StrinovaCollisionBoxTuning.getFly(playerId);
            centerX += flyTuning.offsetX();
            centerZ += flyTuning.offsetZ();

            double baseHalfX = longAxisX ? longHalf : shortHalf;
            double baseHalfZ = longAxisX ? shortHalf : longHalf;

            double halfX = Math.max(0.05D, baseHalfX + flyTuning.sizeX() * 0.5D);
            double halfZ = Math.max(0.05D, baseHalfZ + flyTuning.sizeZ() * 0.5D);
            double finalHeight = Math.max(0.02D, height + flyTuning.sizeY());
            double minY = box.minY + flyTuning.offsetY();
            double maxY = minY + finalHeight;

            return new AABB(
                    centerX - halfX, minY, centerZ - halfZ,
                    centerX + halfX, maxY, centerZ + halfZ
            );
        }

        double sizeX = box.getXsize();
        double sizeZ = box.getZsize();
        if (sizeX <= 0.0D || sizeZ <= 0.0D) {
            return box;
        }

        StrinovaCollisionBoxTuning.Tuning syncTuning = StrinovaCollisionBoxTuning.getSync(playerId);
        if (syncTuning.offsetX() == 0.0D && syncTuning.offsetY() == 0.0D && syncTuning.offsetZ() == 0.0D
                && syncTuning.sizeX() == 0.0D && syncTuning.sizeY() == 0.0D && syncTuning.sizeZ() == 0.0D) {
            return box;
        }

        double centerX = (box.minX + box.maxX) * 0.5D + syncTuning.offsetX();
        double centerZ = (box.minZ + box.maxZ) * 0.5D + syncTuning.offsetZ();
        double minY = box.minY + Math.min(0.0D, syncTuning.offsetY());
        double halfX = Math.max(0.05D, (box.getXsize() * 0.5D) + syncTuning.sizeX() * 0.5D);
        double halfZ = Math.max(0.05D, (box.getZsize() * 0.5D) + syncTuning.sizeZ() * 0.5D);
        double maxY = minY + Math.max(0.05D, box.getYsize() + syncTuning.sizeY());

        return new AABB(
                centerX - halfX, minY, centerZ - halfZ,
                centerX + halfX, maxY, centerZ + halfZ
        );
    }

    @Unique
    private static boolean wa$shouldAdjustFlyEye(Object selfObj) {
        if (!(selfObj instanceof Player player)) {
            return false;
        }
        if (WA_DISABLE_FLY_EYE_HOOK.get().booleanValue()) {
            return false;
        }
        return WaPaperState.isFly(player.getUUID())
                || StrinovaCollisionPreviewFlyContext.isPreviewFly(player.getUUID());
    }

    @Unique
    private static double wa$getFlyEyeY(Player player) {
        AABB box = player.getBoundingBox();
        double minY = box.minY;
        double maxY = box.maxY;
        if (!Double.isFinite(minY) || !Double.isFinite(maxY) || maxY < minY) {
            return player.getY() + WA_VANILLA_STANDING_EYE_HEIGHT;
        }
        return (minY + maxY) * 0.5D + 0.02D;
    }

    @Inject(method = "getEyeY()D", at = @At("HEAD"), cancellable = true, require = 0)
    private void wa$flyEyeY(CallbackInfoReturnable<Double> cir) {
        Object selfObj = this;
        if (!wa$shouldAdjustFlyEye(selfObj)) {
            return;
        }
        Player player = (Player) selfObj;
        cir.setReturnValue(wa$getFlyEyeY(player));
    }

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true, require = 0)
    private void wa$flyEyePosition(CallbackInfoReturnable<Vec3> cir) {
        Object selfObj = this;
        if (!wa$shouldAdjustFlyEye(selfObj)) {
            return;
        }
        Player player = (Player) selfObj;
        double y = wa$getFlyEyeY(player);
        cir.setReturnValue(new Vec3(player.getX(), y, player.getZ()));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true, require = 0)
    private void wa$flyEyePosition(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Object selfObj = this;
        if (!wa$shouldAdjustFlyEye(selfObj)) {
            return;
        }
        Player player = (Player) selfObj;
        double y = wa$getFlyEyeY(player);
        cir.setReturnValue(new Vec3(player.getX(), y, player.getZ()));
    }

    @Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true, require = 0)
    private void wa$flyEyeHeight(CallbackInfoReturnable<Float> cir) {
        Object selfObj = this;
        if (!wa$shouldAdjustFlyEye(selfObj)) {
            return;
        }
        Player player = (Player) selfObj;
        float eyeHeight = (float) (wa$getFlyEyeY(player) - player.getY());
        cir.setReturnValue(Math.max(0.0F, eyeHeight));
    }

    @Inject(method = "getEyeHeight(Lnet/minecraft/world/entity/Pose;)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void wa$flyEyeHeight(Pose pose, CallbackInfoReturnable<Float> cir) {
        Object selfObj = this;
        if (!wa$shouldAdjustFlyEye(selfObj)) {
            return;
        }
        Player player = (Player) selfObj;
        float eyeHeight = (float) (wa$getFlyEyeY(player) - player.getY());
        cir.setReturnValue(Math.max(0.0F, eyeHeight));
    }

    @Inject(method = "getEyeHeight(Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void wa$flyEyeHeight(Pose pose, EntityDimensions dims, CallbackInfoReturnable<Float> cir) {
        Object selfObj = this;
        if (!wa$shouldAdjustFlyEye(selfObj)) {
            return;
        }
        Player player = (Player) selfObj;
        float eyeHeight = (float) (wa$getFlyEyeY(player) - player.getY());
        cir.setReturnValue(Math.max(0.0F, eyeHeight));
    }
}
