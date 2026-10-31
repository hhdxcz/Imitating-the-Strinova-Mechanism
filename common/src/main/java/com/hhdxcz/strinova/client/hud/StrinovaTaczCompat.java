package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

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
