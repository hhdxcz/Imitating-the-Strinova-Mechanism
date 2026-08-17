package com.hhdxcz.strinova.collision;

import com.hhdxcz.strinova.StrinovaMod;
import dev.architectury.platform.Platform;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// YSM 模型骨骼解析器：通过反射获取 Yes Steve Model 的骨骼位置，用于精确碰撞检测
public final class StrinovaYsmBoneResolver {

    private static final int RESOLVE_INTERVAL_TICKS = 3;
    private static final String YSM_MOD_ID = "yes_steve_model";

    private static final ConcurrentHashMap<UUID, ResolvedBoneSet> BONE_CACHE = new ConcurrentHashMap<>();

    private static Boolean ysmLoaded;
    private static Class<?> ysmRendererClass;
    private static Class<?> animationDataClass;
    private static Class<?> boneDataClass;

    private StrinovaYsmBoneResolver() {}

    // 骨骼位置记录
    public record BonePos(double x, double y, double z, boolean resolved) {
        public static final BonePos UNRESOLVED = new BonePos(0, 0, 0, false);
    }

    // 缓存条目：骨骼集合 + 解析时 tick
    private record ResolvedBoneSet(Map<String, BonePos> bones, int tick) {}

    // 检测 YSM 是否已加载并初始化反射类
    public static boolean isYsmLoaded() {
        if (ysmLoaded != null) return ysmLoaded;
        try {
            ysmLoaded = Platform.isModLoaded(YSM_MOD_ID);
        } catch (Throwable t) {
            ysmLoaded = false;
        }
        if (ysmLoaded) initReflection();
        return ysmLoaded;
    }

    // 初始化 YSM 相关类的反射引用
    private static void initReflection() {
        try {
            ysmRendererClass = Class.forName("yes_steve_model.client.render.YsmRenderer");
        } catch (Throwable ignored) {}
        try {
            animationDataClass = Class.forName("yes_steve_model.api.client.AnimationData");
        } catch (Throwable ignored) {}
        try {
            boneDataClass = Class.forName("yes_steve_model.api.model.BoneData");
        } catch (Throwable ignored) {}
    }

    // 解析玩家当前帧的骨骼位置（带缓存，每 3 tick 刷新）
    public static Map<String, BonePos> resolveBonePositions(Player player) {
        if (player == null || !isYsmLoaded()) return Collections.emptyMap();

        UUID id = player.getUUID();
        int tick = player.tickCount;

        ResolvedBoneSet cached = BONE_CACHE.get(id);
        if (cached != null && tick - cached.tick < RESOLVE_INTERVAL_TICKS) {
            return cached.bones;
        }

        Map<String, BonePos> result = resolveViaReflection(player);
        if (result.isEmpty()) result = resolveViaEntityData(player);

        BONE_CACHE.put(id, new ResolvedBoneSet(Collections.unmodifiableMap(result), tick));
        return result;
    }

    // 通过反射获取 YSM 骨骼位置
    private static Map<String, BonePos> resolveViaReflection(Player player) {
        Map<String, BonePos> result = new LinkedHashMap<>();
        try {
            Object renderer = getYsmRenderer(player);
            if (renderer == null) return result;

            Object animData = getAnimationData(renderer);
            if (animData == null) return result;

            Map<String, float[]> boneTransforms = getBoneTransforms(animData);
            if (boneTransforms == null || boneTransforms.isEmpty()) return result;

            double baseX = player.getX();
            double baseY = player.getY();
            double baseZ = player.getZ();

            for (Map.Entry<String, float[]> entry : boneTransforms.entrySet()) {
                float[] transform = entry.getValue();
                if (transform == null || transform.length < 3) continue;
                result.put(entry.getKey(), new BonePos(
                        transform[0] - baseX,
                        transform[1] - baseY,
                        transform[2] - baseZ,
                        true
                ));
            }
        } catch (Throwable e) {
            StrinovaMod.LOGGER.debug("YSM bone resolve via reflection failed: {}", e.getMessage());
        }
        return result;
    }

    // 获取玩家的 YSM 渲染器实例
    private static Object getYsmRenderer(Player player) {
        if (ysmRendererClass == null) return null;
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.getEntityRenderDispatcher() == null) return null;
            var dispatcher = mc.getEntityRenderDispatcher();
            Object renderer = dispatcher.getRenderer(player);
            if (ysmRendererClass.isInstance(renderer)) return renderer;
        } catch (Throwable ignored) {}
        return null;
    }

    // 从渲染器获取动画数据，尝试多种字段名和方法名
    private static Object getAnimationData(Object renderer) {
        if (renderer == null || animationDataClass == null) return null;
        try {
            for (String fieldName : new String[]{"animationData", "animData", "modelData", "data"}) {
                try {
                    Field f = findFieldInHierarchy(renderer.getClass(), fieldName);
                    if (f == null) continue;
                    f.setAccessible(true);
                    Object val = f.get(renderer);
                    if (animationDataClass.isInstance(val)) return val;
                } catch (Throwable ignored) {}
            }

            for (String methodName : new String[]{"getAnimationData", "getAnimData", "getModelData"}) {
                try {
                    Method m = renderer.getClass().getMethod(methodName);
                    m.setAccessible(true);
                    Object val = m.invoke(renderer);
                    if (animationDataClass.isInstance(val)) return val;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // 从动画数据中提取骨骼变换矩阵
    private static Map<String, float[]> getBoneTransforms(Object animData) {
        Map<String, float[]> result = new LinkedHashMap<>();
        if (animData == null) return result;
        try {
            List<?> bones = null;
            try {
                Method m = animData.getClass().getMethod("getBones");
                m.setAccessible(true);
                Object val = m.invoke(animData);
                if (val instanceof List) bones = (List<?>) val;
            } catch (Throwable ignored) {}

            if (bones == null) {
                try {
                    Field f = findFieldInHierarchy(animData.getClass(), "bones");
                    if (f != null) {
                        f.setAccessible(true);
                        Object val = f.get(animData);
                        if (val instanceof List) bones = (List<?>) val;
                    }
                } catch (Throwable ignored) {}
            }

            if (bones == null) return result;

            for (Object bone : bones) {
                if (bone == null) continue;
                String name = null;
                float[] pos = null;

                try {
                    Method m = bone.getClass().getMethod("getName");
                    m.setAccessible(true);
                    Object v = m.invoke(bone);
                    if (v instanceof String) name = (String) v;
                } catch (Throwable ignored) {}

                if (name == null) {
                    try {
                        Field f = findFieldInHierarchy(bone.getClass(), "name");
                        if (f != null) {
                            f.setAccessible(true);
                            Object v = f.get(bone);
                            if (v instanceof String) name = (String) v;
                        }
                    } catch (Throwable ignored) {}
                }

                try {
                    Method m = bone.getClass().getMethod("getWorldPosition");
                    m.setAccessible(true);
                    Object v = m.invoke(bone);
                    if (v instanceof float[]) pos = (float[]) v;
                } catch (Throwable ignored) {}

                if (pos == null) {
                    try {
                        Method m = bone.getClass().getMethod("getPosition");
                        m.setAccessible(true);
                        Object v = m.invoke(bone);
                        if (v instanceof float[]) pos = (float[]) v;
                    } catch (Throwable ignored) {}
                }

                if (name != null && pos != null && pos.length >= 3) {
                    result.put(name, pos);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    // 通过实体数据解析骨骼位置（备用方案，当前返回空）
    private static Map<String, BonePos> resolveViaEntityData(Player player) {
        return Collections.emptyMap();
    }

    // 将骨骼位置应用到碰撞部件列表
    public static List<StrinovaCompoundCollision.Part> applyBonePositions(
            List<StrinovaCompoundCollision.Part> parts, Player player) {
        if (parts == null || parts.isEmpty() || player == null) return parts;
        if (!isYsmLoaded()) return parts;

        Map<String, BonePos> bones = resolveBonePositions(player);
        if (bones.isEmpty()) return parts;

        List<StrinovaCompoundCollision.Part> result = new ArrayList<>(parts.size());
        for (StrinovaCompoundCollision.Part part : parts) {
            String boneName = part.ysmBoneName();
            if (boneName != null && !boneName.isEmpty()) {
                BonePos bp = bones.get(boneName);
                if (bp != null && bp.resolved()) {
                    result.add(new StrinovaCompoundCollision.Part(
                            part.name(), bp.x(), bp.y(), bp.z(),
                            part.sizeX(), part.sizeY(), part.sizeZ(),
                            boneName
                    ));
                    continue;
                }
            }
            result.add(part);
        }
        return result;
    }

    // 清除指定玩家的骨骼缓存
    public static void clearCache(UUID playerId) {
        if (playerId != null) BONE_CACHE.remove(playerId);
    }

    // 清除所有骨骼缓存
    public static void clearAll() {
        BONE_CACHE.clear();
    }

    // 在类层次结构中查找字段
    private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}