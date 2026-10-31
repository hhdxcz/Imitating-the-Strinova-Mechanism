package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * TACZ（Timeless and Classics Zero）兼容层：识别枪械/读取弹匣与备弹/区分类别图标。
 * 不直接依赖 TACZ 类（编译期无依赖），全部通过注册表 id + 反射 + NBT 兼容，
 * TACZ 版本/API 变化时最多退回占位显示，不会让游戏崩溃。
 */
public final class StrinovaTaczCompat {

    /** 武器槽图标类别（程序化几何剪影用）。 */
    public enum IconKind {
        RIFLE, PISTOL, KNIFE, GRENADE, MEDKIT, BOW, GENERIC, EMPTY
    }

    private StrinovaTaczCompat() {
    }

    /** TACZ 是否已加载（Architectury 平台查询，结果缓存）。 */
    private static Boolean taczLoadedCache;

    public static boolean isTaczLoaded() {
        Boolean cached = taczLoadedCache;
        if (cached != null) {
            return cached;
        }
        boolean loaded;
        try {
            loaded = dev.architectury.platform.Platform.isModLoaded("tacz");
        } catch (Throwable t) {
            loaded = false;
        }
        taczLoadedCache = loaded;
        return loaded;
    }

    /** 主手物品注册名（诊断用，空手返回 ""）。 */
    public static String heldItemId(LocalPlayer player) {
        if (player == null) {
            return "";
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "?" : id.toString();
    }

    public static boolean isTaczItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "tacz".equals(id.getNamespace());
    }

    public static boolean isTaczGun(ItemStack stack) {
        if (!isTaczItem(stack)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id == null || id.getPath() == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("gun") || path.contains("pistol") || path.contains("rifle") || path.contains("shotgun");
    }

    /** 是否 TACZ 子弹实体：类名/包名含 tacz 或 Bullet（TACZ 的 EntityKineticBullet 同时含两者），实体类型 id 兜底。 */
    public static boolean isTaczBullet(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            for (Class<?> clazz = entity.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                String className = clazz.getName().toLowerCase(Locale.ROOT);
                if (className.contains("tacz") || className.contains("bullet")) {
                    return true;
                }
            }
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (typeId != null) {
                String id = typeId.toString().toLowerCase(Locale.ROOT);
                if (id.startsWith("tacz:") || id.contains("bullet")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 爆头探测用的常见方法名（TACZ/附属模组不同版本命名不一致，逐个尝试）。 */
    private static final String[] HEADSHOT_METHODS = {"isHeadshot", "getHeadshot", "isHeadShot", "getHeadShot",
            "isHeadshotHit", "isHeadShotHit"};

    /** 爆头探测用的常见字段名。 */
    private static final String[] HEADSHOT_FIELDS = {"headshot", "headShot", "isHeadshot", "isHeadShot",
            "head_shot", "headshotHit"};

    /** 爆头 NBT / scoreboard tag 的键名片段（统一小写匹配）。 */
    private static final String[] HEADSHOT_KEY_PARTS = {"headshot", "head_shot", "isheadshot"};

    /**
     * 反射探测实体上是否携带爆头标记（供 StrinovaCombatManager 判定伤害数字/击杀事件）：
     * <ol>
     *   <li>方法：isHeadshot()/getHeadshot()/isHeadShot() 等，返回 Boolean.TRUE 视为爆头；</li>
     *   <li>字段：headshot/headShot/isHeadshot 等（含父类层级），值为 Boolean.TRUE 视为爆头；</li>
     *   <li>NBT：Forge persistentData（反射调用，Fabric 无此方法时跳过）+ 实体存档 NBT，
     *       键名含 headshot/isheadshot 且布尔为 true 视为爆头（嵌套最多 3 层）；</li>
     *   <li>scoreboard tag：tag 名含爆头标记。</li>
     * </ol>
     * TACZ/附属模组 API 未知，任何反射异常都被吞掉，找不到或出错统一返回 false —— 绝不崩溃。
     */
    public static boolean probeHeadshot(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            // 1) 方法探测
            for (String name : HEADSHOT_METHODS) {
                try {
                    Method method = entity.getClass().getMethod(name);
                    method.setAccessible(true);
                    Object value = method.invoke(entity);
                    if (value instanceof Boolean flag && flag) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
            // 2) 字段探测（含父类，部分模组把标记放在子弹父类里）
            for (Class<?> clazz = entity.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (String name : HEADSHOT_FIELDS) {
                    try {
                        Field field = clazz.getDeclaredField(name);
                        field.setAccessible(true);
                        Object value = field.get(entity);
                        if (value instanceof Boolean flag && flag) {
                            return true;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (Throwable ignored) {
                    }
                }
            }
            // 3) NBT 探测：Forge persistentData 优先，再用实体存档 NBT 兜底
            //    （存活实体序列化开销大且不会带子弹爆头标记，跳过存档 NBT 序列化）
            if (probeHeadshotTag(persistentDataTag(entity), 0)) {
                return true;
            }
            if (!(entity instanceof LivingEntity) && probeHeadshotTag(savedTag(entity), 0)) {
                return true;
            }
            // 4) scoreboard tag 探测
            for (String tag : entity.getTags()) {
                if (tag != null && isHeadshotKey(tag.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // Forge 的 Entity#getPersistentData()（Fabric 没有该方法 → 反射失败返回 null）
    private static CompoundTag persistentDataTag(Entity entity) {
        try {
            Method method = entity.getClass().getMethod("getPersistentData");
            method.setAccessible(true);
            Object value = method.invoke(entity);
            return value instanceof CompoundTag tag ? tag : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 实体存档 NBT（saveWithoutId 是原版 API，两个平台都有）
    private static CompoundTag savedTag(Entity entity) {
        try {
            CompoundTag tag = new CompoundTag();
            entity.saveWithoutId(tag);
            return tag;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 递归扫描 NBT：布尔键为 true 且键名含爆头标记即命中；嵌套 compound/list 最多下探 3 层
    private static boolean probeHeadshotTag(CompoundTag tag, int depth) {
        if (tag == null || depth > 3) {
            return false;
        }
        try {
            for (String key : tag.getAllKeys()) {
                if (key == null) {
                    continue;
                }
                String lower = key.toLowerCase(Locale.ROOT);
                if (tag.contains(key, Tag.TAG_BYTE) && tag.getBoolean(key) && isHeadshotKey(lower)) {
                    return true;
                }
                if (tag.contains(key, Tag.TAG_COMPOUND) && probeHeadshotTag(tag.getCompound(key), depth + 1)) {
                    return true;
                }
                if (tag.contains(key, Tag.TAG_LIST)) {
                    var list = tag.getList(key, Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        if (probeHeadshotTag(list.getCompound(i), depth + 1)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isHeadshotKey(String lowerKey) {
        for (String part : HEADSHOT_KEY_PARTS) {
            if (lowerKey.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** 读取 TACZ 枪械当前弹匣子弹数（反射 IGun API → NBT 兜底 → 0）。 */
    public static int magazine(ItemStack stack) {
        if (!isTaczGun(stack)) {
            return 0;
        }
        Object gun = taczGun(stack);
        if (gun != null) {
            for (String name : new String[]{"getCurrentAmmoCount", "getAmmoCount", "getAmmoAmount", "getMagazineAmmoCount"}) {
                try {
                    Method method = gun.getClass().getMethod(name, ItemStack.class);
                    Object value = method.invoke(gun, stack);
                    if (value instanceof Integer ammo) {
                        return Math.max(0, ammo);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // NBT 兜底：TACZ 不同版本字段名不一致，逐个尝试
        try {
            var tag = stack.getTag();
            if (tag != null) {
                for (String key : new String[]{"AmmoCount", "CurrentAmmoCount", "GunCurrentAmmoCount",
                        "ammo_count", "current_ammo", "Ammo"}) {
                    if (tag.contains(key)) {
                        return Math.max(0, tag.getInt(key));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** 读取备弹：优先枪械 ammoId 对应的弹药物品数量，其次统计背包内 TACZ 弹药。 */
    public static int reserve(LocalPlayer player, ItemStack gunStack) {
        if (player == null || !isTaczGun(gunStack)) {
            return 0;
        }
        ResourceLocation ammoId = taczAmmoId(gunStack);
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matchesAmmo(stack, ammoId)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matchesAmmo(stack, ammoId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean matchesAmmo(ItemStack stack, ResourceLocation ammoId) {
        if (stack == null || stack.isEmpty() || !isTaczItem(stack)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        if (ammoId != null) {
            return id.equals(ammoId);
        }
        String path = id.getPath() == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("ammo") || path.matches("\\d+(\\.\\d+)?x\\d+.*");
    }

    private static Object taczGun(ItemStack stack) {
        try {
            Class<?> gunClass = Class.forName("com.tacz.guns.api.item.IGun");
            try {
                Method getter = gunClass.getMethod("getIGunOrNull", ItemStack.class);
                return getter.invoke(null, stack);
            } catch (NoSuchMethodException ignored) {
                return gunClass.isInstance(stack.getItem()) ? stack.getItem() : null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResourceLocation taczAmmoId(ItemStack stack) {
        Object gun = taczGun(stack);
        if (gun == null) {
            return null;
        }
        for (String name : new String[]{"getAmmoId", "getCurrentAmmoId"}) {
            try {
                Method method = gun.getClass().getMethod(name, ItemStack.class);
                Object value = method.invoke(gun, stack);
                if (value instanceof ResourceLocation id) {
                    return id;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 根据物品类型推断武器栏图标类别。 */
    public static IconKind iconKind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return IconKind.EMPTY;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id == null || id.getPath() == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
        if ("tacz".equals(id == null ? null : id.getNamespace())) {
            if (path.contains("pistol") || path.contains("smg")) {
                return IconKind.PISTOL;
            }
            if (path.contains("knife") || path.contains("sword") || path.contains("melee")) {
                return IconKind.KNIFE;
            }
            if (path.contains("grenade") || path.contains("ammo")) {
                return IconKind.GRENADE;
            }
            return IconKind.RIFLE;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
            return IconKind.KNIFE;
        }
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
            return IconKind.BOW;
        }
        String name = stack.getHoverName().getString();
        if (name.contains("雷") || name.toLowerCase(Locale.ROOT).contains("grenade")) {
            return IconKind.GRENADE;
        }
        if (name.contains("治疗") || name.contains("药") || name.contains("medkit")) {
            return IconKind.MEDKIT;
        }
        if (stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) {
            return IconKind.GRENADE;
        }
        return IconKind.GENERIC;
    }
}
