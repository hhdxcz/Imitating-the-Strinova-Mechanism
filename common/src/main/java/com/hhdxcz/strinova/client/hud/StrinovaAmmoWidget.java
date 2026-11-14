package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 武器 / 弹药部件（右下，1:1 对齐真实截图）：
 * 屏幕右侧贴边竖排 5 个武器槽（1 在下、5 在上，数字键徽标挂在槽位右侧），
 * 当前槽向左展开一张武器卡：大字枪械剪影 + 武器名 + 大号弹匣数（白）+ 备弹（蓝）。
 *
 * <p>数据源：模拟数据开启时用 {@link StrinovaHudState}；否则读取玩家快捷栏 1~5 号位，
 * TACZ 枪械通过 {@link StrinovaTaczCompat} 反射读取弹匣/备弹，其他模组物品只显示名称与剪影。</p>
 */
public final class StrinovaAmmoWidget implements StrinovaHudWidget {

    /** 选中行后面那块灰黑色矩形（卡片）的宽度。 */
    private static final int CARD_WIDTH = 150;
    private static final int BADGE_WIDTH = 11;
    private static final int BADGE_HEIGHT = 10;
    private static final int BADGE_GAP = 4;
    /** 槽位图标区：贴图按游戏尺寸的 0.8 倍完整显示（不缩成小图标），右侧留一点空隙。 */
    private static final int ICON_W = 90;
    private static final int ICON_H = 30;
    private static final int ICON_GAP = 3;
    /** 槽位间距按游戏比例。 */
    private static final int SLOT_STEP = 44;
    private static final int WIDTH = CARD_WIDTH + BADGE_GAP + BADGE_WIDTH;
    private static final int HEIGHT = ICON_H + 4 * SLOT_STEP + 6;
    /** 卡片高度：包住武器贴图 + 下面的武器名。 */
    private static final int CARD_HEIGHT = 52;
    private static final int AMMO_BLUE = 0xFF6FB6FF;

    private static final String[] DEFAULT_SLOT_NAMES = {"主武器", "副武器", "近战", "战术道具", "战术道具"};

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.AMMO;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        return WIDTH;
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        return HEIGHT;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean mock = StrinovaHudMockData.isEnabled();

        int selected = mock
                ? StrinovaHudState.getSelectedSlot()
                : player == null ? 0 : Math.min(4, Math.max(0, player.getInventory().selected));

        String[] names = new String[5];
        StrinovaTaczCompat.IconKind[] kinds = new StrinovaTaczCompat.IconKind[5];
        for (int i = 0; i < 5; i++) {
            ItemStack stack = mock ? ItemStack.EMPTY : hotbarStack(player, i);
            if (mock) {
                names[i] = mockSlotName(i);
                kinds[i] = mockIconKind(names[i]);
            } else if (stack.isEmpty()) {
                names[i] = "";
                kinds[i] = StrinovaTaczCompat.IconKind.EMPTY;
            } else {
                names[i] = stack.getHoverName().getString();
                kinds[i] = StrinovaTaczCompat.iconKind(stack);
            }
        }

        // 1 号槽是角色主武器：优先用选角界面当前角色对应的 wiki 武器
        String agentWeapon = selectedAgentWeaponName();
        if (!agentWeapon.isEmpty()) {
            names[0] = agentWeapon;
        }

        // 贴图名：没有可用武器名时按槽位用默认官方图标
        String[] iconNames = names.clone();
        String[] defaultIcons = {"警探", "雪鸮", "忍锋", "手雷", "闪光弹"};
        for (int i = 0; i < 5; i++) {
            if (StrinovaWeaponIcons.textureFor(iconNames[i]) == null) {
                iconNames[i] = defaultIcons[i];
            }
        }

        // 当前选中槽的弹药
        int magazine = -1;
        int reserve = -1;
        if (mock) {
            magazine = StrinovaHudState.getMagazine();
            reserve = StrinovaHudState.getReserve();
        } else if (player != null) {
            ItemStack held = hotbarStack(player, selected);
            if (StrinovaTaczCompat.isTaczGun(held)) {
                magazine = StrinovaTaczCompat.magazine(held);
                reserve = StrinovaTaczCompat.reserve(player, held);
            }
        }

        // 槽位列：和游戏一致——武器贴图完整显示、靠右对齐（右侧留一点空隙），
        // 右上角是 1~5 数字键；滚轮切枪只是在选中的那一行「贴图后面/下方」多一块灰黑色矩形。
        StrinovaTaczCompat.IconKind selectedKind = kinds[selected];
        if (selectedKind == null) {
            selectedKind = StrinovaTaczCompat.IconKind.EMPTY;
        }
        // 投掷物/道具：贴图留在槽位列里（靠右），矩形只负责显示武器名；
        // 枪械/近战：矩形里显示放大的武器剪影 + 右侧弹药数字 + 左下武器名
        boolean throwable = selectedKind == StrinovaTaczCompat.IconKind.GRENADE
                || selectedKind == StrinovaTaczCompat.IconKind.MEDKIT
                || selectedKind == StrinovaTaczCompat.IconKind.GENERIC
                || selectedKind == StrinovaTaczCompat.IconKind.EMPTY;

        int iconRight = x + CARD_WIDTH;
        int slotX = iconRight - ICON_GAP - ICON_W;
        int badgeX = iconRight + BADGE_GAP;
        int bottomY = y + HEIGHT - ICON_H - 3;
        int columnTop = y + 1;
        int columnBottom = y + HEIGHT - 1;

        // 图标列背后很淡的暗色衬底（游戏里有一层半透明底，避免白色剪影在亮背景上看不清）
        painter.rect(slotX - 2, columnTop, ICON_W + 4 + ICON_GAP, columnBottom - columnTop, 0x40101820);

        int selectedSlotY = bottomY - Math.max(0, Math.min(4, selected)) * SLOT_STEP;

        // 选中行的灰黑色矩形：右边缘贴着数字键，向下延伸放武器名
        int cardY = selectedSlotY - (CARD_HEIGHT - ICON_H) / 2;
        int cardX = x;
        painter.rect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0xCC2A2F36);
        painter.border(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0x66445566, 1);

        // 槽位贴图：枪械选中行的贴图由矩形里的大图代替，其余全部按原样绘制
        for (int i = 0; i < 5; i++) {
            int slotY = bottomY - i * SLOT_STEP;
            boolean isSelected = i == selected;
            if (isSelected && !throwable) {
                continue;
            }
            drawWeaponArt(painter, iconNames[i], kinds[i], slotX, slotY, ICON_W, ICON_H,
                    isSelected ? 0xFFFFFFFF : 0xDDDCE8F2, true);
        }

        // 图标列与数字键之间的细竖线
        painter.rect(iconRight, columnTop, 1, columnBottom - columnTop, 0x66B4C0CC);

        // 数字键徽标在右侧：选中的那个亮白高亮
        for (int i = 0; i < 5; i++) {
            int slotY = bottomY - i * SLOT_STEP;
            boolean isSelected = i == selected;
            int badgeY = slotY + (ICON_H - BADGE_HEIGHT) / 2;
            painter.rect(badgeX, badgeY, BADGE_WIDTH, BADGE_HEIGHT, 0x88101820);
            painter.border(badgeX, badgeY, BADGE_WIDTH, BADGE_HEIGHT,
                    isSelected ? 0xFFEAF6FF : 0x66445566, 1);
            String number = String.valueOf(i + 1);
            painter.text(number, badgeX + (BADGE_WIDTH - painter.textWidth(number)) / 2,
                    badgeY + 1,
                    isSelected ? 0xFFEAF6FF : 0xFF9FC6D8, false);
        }

        String selectedName = names[selected];
        if (selectedName == null || selectedName.isEmpty()) {
            selectedName = mock ? DEFAULT_SLOT_NAMES[selected] : "未装备";
        }

        if (throwable) {
            // 道具：名字居中写在贴图下方
            painter.text(selectedName,
                    slotX + Math.max(0, (ICON_W - painter.textWidth(selectedName)) / 2),
                    selectedSlotY + ICON_H + 3,
                    StrinovaProgrammaticPainter.COLOR_TEXT, false);
        } else {
            // 枪械/近战：矩形里放大剪影 + 左下名字 + 右侧弹药
            drawWeaponArt(painter, iconNames[selected], selectedKind, cardX + 6, cardY + 8,
                    CARD_WIDTH - 44, CARD_HEIGHT - 16, 0xFFEAF6FF, false);
            painter.text(selectedName, cardX + 6, cardY + CARD_HEIGHT - 12,
                    StrinovaProgrammaticPainter.COLOR_TEXT, false);
            if (magazine >= 0) {
                String magText = String.valueOf(magazine);
                float magScale = 1.9F;
                int magWidth = Math.round(painter.textWidth(magText) * magScale);
                int magColor = magazine <= 5
                        ? StrinovaProgrammaticPainter.COLOR_HP_LOW
                        : StrinovaProgrammaticPainter.COLOR_TEXT;
                painter.textScaled(magText, cardX + CARD_WIDTH - 10 - magWidth, cardY + 6,
                        magScale, magColor, true);
                if (reserve >= 0) {
                    painter.textRightAligned(String.valueOf(reserve), cardX + CARD_WIDTH - 12,
                            cardY + 30, AMMO_BLUE, false);
                }
            } else if (selectedKind == StrinovaTaczCompat.IconKind.KNIFE) {
                // 刀没有子弹，显示无限符号
                float infScale = 1.6F;
                int infWidth = Math.round(painter.textWidth("∞") * infScale);
                painter.textScaled("∞", cardX + CARD_WIDTH - 12 - infWidth, cardY + 14,
                        infScale, AMMO_BLUE, true);
            }
        }

        // 换弹进度（有换弹时卡片底部细条）
        float reload = mock ? StrinovaHudState.reloadProgress() : 0.0F;
        if (reload > 0.0F) {
            painter.bar(cardX + 4, cardY + CARD_HEIGHT - 3, CARD_WIDTH - 8, 2, reload,
                    StrinovaProgrammaticPainter.COLOR_WARN, 0x66222B33);
        }

        // 数字键旁边那条白色装饰线（sprite_211）：按官方 12x63 比例竖直居中在灰黑矩形右边缘，
        // 右侧紧挨数字键，和游戏一致
        int lineWidth = 6;
        int lineHeight = 32;
        int lineX = iconRight - 3;
        int lineY = cardY + (CARD_HEIGHT - lineHeight) / 2;
        painter.textureRegion(StrinovaWeaponIcons.selectedLine(), lineX, lineY, lineWidth, lineHeight,
                0, 0, 12, 63, 12, 63, 0xFFFFFFFF, 1.0F);
    }

    /** 当前选角角色的主武器名（香奈美 → 谢幕曲）。 */
    private static String selectedAgentWeaponName() {
        try {
            String id = StrinovaHudConfig.get().selectedAgentId;
            if (id != null && !id.isEmpty()) {
                StrinovaWikiData.Agent agent = StrinovaWikiData.byId(id);
                if (agent != null && agent.weapon() != null && agent.weapon().name() != null) {
                    return agent.weapon().name();
                }
            }
            // 兜底：从计分板自己的角色列反查（选角未落盘/服务端已同步时也能拿到）
            for (StrinovaScoreboardState.Entry entry : StrinovaScoreboardState.entries()) {
                if (!entry.self() || entry.agentName() == null || entry.agentName().isEmpty()) {
                    continue;
                }
                for (StrinovaAgentRoster.Agent agent : StrinovaAgentRoster.agents()) {
                    if (agent.name().equals(entry.agentName())) {
                        StrinovaWikiData.Agent wiki = StrinovaWikiData.byId(agent.id());
                        if (wiki != null && wiki.weapon() != null && wiki.weapon().name() != null) {
                            return wiki.weapon().name();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 优先用官方武器/投掷物剪影贴图；没有则回退程序化几何剪影。 */
    private static void drawWeaponArt(StrinovaHudPainter painter, String name,
                                      StrinovaTaczCompat.IconKind kind, int x, int y,
                                      int maxWidth, int maxHeight, int color, boolean rightAlign) {
        net.minecraft.resources.ResourceLocation texture = StrinovaWeaponIcons.textureFor(name);
        if (texture != null) {
            // 贴图是按 alpha 裁剪的原始剪影，宽高比各不相同，必须等比适配，不能拉伸
            int[] box = StrinovaWeaponIcons.fit(texture, x, y, maxWidth, maxHeight);
            if (rightAlign) {
                // 靠右对齐，右侧留一点空隙（和游戏一致：投掷物/道具贴图贴着数字键一侧）
                int shifted = x + maxWidth - ICON_GAP - box[2];
                box[0] = Math.max(x, shifted);
            }
            int uv = StrinovaWeaponIcons.UV_SIZE;
            painter.textureRegion(texture, box[0], box[1], box[2], box[3],
                    0.0F, 0.0F, uv, uv, uv, uv, color, 1.0F);
            return;
        }
        int size = Math.max(8, Math.min(maxWidth, maxHeight));
        int iconX = rightAlign ? x + maxWidth - size - ICON_GAP : x;
        drawWeaponIcon(painter, kind, iconX, y + (maxHeight - size) / 2, size, color);
    }

    private static ItemStack hotbarStack(LocalPlayer player, int index) {
        if (player == null || index < 0 || index > 4) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> items = player.getInventory().items;
        if (index >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = items.get(index);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static String mockSlotName(int index) {
        List<StrinovaHudState.WeaponSlot> slots = StrinovaHudState.getWeaponSlots();
        if (slots != null) {
            for (StrinovaHudState.WeaponSlot slot : slots) {
                if (slot.index() == index && slot.name() != null && !slot.name().isEmpty()) {
                    return slot.name();
                }
            }
        }
        return DEFAULT_SLOT_NAMES[Math.max(0, Math.min(DEFAULT_SLOT_NAMES.length - 1, index))];
    }

    private static StrinovaTaczCompat.IconKind mockIconKind(String name) {
        if (name == null) {
            return StrinovaTaczCompat.IconKind.EMPTY;
        }
        if (name.contains("雷")) {
            return StrinovaTaczCompat.IconKind.GRENADE;
        }
        if (name.contains("刀") || name.contains("忍锋")) {
            return StrinovaTaczCompat.IconKind.KNIFE;
        }
        if (name.contains("手枪") || name.contains("副")) {
            return StrinovaTaczCompat.IconKind.PISTOL;
        }
        return StrinovaTaczCompat.IconKind.RIFLE;
    }

    // ==================== 程序化几何武器剪影 ====================

    private static void drawWeaponIcon(StrinovaHudPainter painter, StrinovaTaczCompat.IconKind kind,
                                       int x, int y, int size, int color) {
        if (kind == null) {
            kind = StrinovaTaczCompat.IconKind.EMPTY;
        }
        switch (kind) {
            case RIFLE -> {
                rect(painter, x, y, size, 0.18F, 0.38F, 0.50F, 0.12F, color);
                rect(painter, x, y, size, 0.68F, 0.40F, 0.28F, 0.06F, color);
                rect(painter, x, y, size, 0.03F, 0.42F, 0.16F, 0.10F, color);
                rect(painter, x, y, size, 0.30F, 0.50F, 0.10F, 0.16F, color);
                rect(painter, x, y, size, 0.44F, 0.50F, 0.11F, 0.16F, color);
                rect(painter, x, y, size, 0.44F, 0.28F, 0.18F, 0.05F, color);
            }
            case PISTOL -> {
                rect(painter, x, y, size, 0.22F, 0.31F, 0.52F, 0.12F, color);
                rect(painter, x, y, size, 0.70F, 0.33F, 0.20F, 0.06F, color);
                rect(painter, x, y, size, 0.30F, 0.43F, 0.14F, 0.28F, color);
                rect(painter, x, y, size, 0.46F, 0.40F, 0.08F, 0.04F, color);
            }
            case KNIFE -> {
                line(painter, x, y, size, 0.16F, 0.72F, 0.72F, 0.30F, color);
                line(painter, x, y, size, 0.18F, 0.76F, 0.74F, 0.34F, color);
                rect(painter, x, y, size, 0.66F, 0.30F, 0.18F, 0.07F, color);
            }
            case GRENADE -> {
                rect(painter, x, y, size, 0.30F, 0.34F, 0.34F, 0.36F, color);
                rect(painter, x, y, size, 0.38F, 0.24F, 0.18F, 0.09F, color);
                line(painter, x, y, size, 0.56F, 0.22F, 0.68F, 0.30F, color);
                rect(painter, x, y, size, 0.36F, 0.42F, 0.06F, 0.06F, 0x66000000);
                rect(painter, x, y, size, 0.50F, 0.42F, 0.06F, 0.06F, 0x66000000);
                rect(painter, x, y, size, 0.36F, 0.54F, 0.06F, 0.06F, 0x66000000);
            }
            case MEDKIT -> {
                rect(painter, x, y, size, 0.22F, 0.28F, 0.56F, 0.44F, color);
                rect(painter, x, y, size, 0.44F, 0.34F, 0.12F, 0.32F, 0xFF0A111C);
                rect(painter, x, y, size, 0.34F, 0.44F, 0.32F, 0.12F, 0xFF0A111C);
            }
            case BOW -> {
                line(painter, x, y, size, 0.28F, 0.18F, 0.52F, 0.50F, color);
                line(painter, x, y, size, 0.52F, 0.50F, 0.28F, 0.82F, color);
                line(painter, x, y, size, 0.28F, 0.18F, 0.28F, 0.82F, color);
                line(painter, x, y, size, 0.28F, 0.50F, 0.82F, 0.50F, color);
            }
            case GENERIC -> rect(painter, x, y, size, 0.26F, 0.26F, 0.48F, 0.48F, color);
            default -> {
                rect(painter, x, y, size, 0.26F, 0.48F, 0.48F, 0.04F, 0x88EAF6FF);
            }
        }
    }

    private static void rect(StrinovaHudPainter painter, int x, int y, int size,
                             float u, float v, float w, float h, int color) {
        int px = x + Math.round(u * size);
        int py = y + Math.round(v * size);
        int pw = Math.max(1, Math.round(w * size));
        int ph = Math.max(1, Math.round(h * size));
        painter.rect(px, py, pw, ph, color);
    }

    private static void line(StrinovaHudPainter painter, int x, int y, int size,
                             float x1, float y1, float x2, float y2, int color) {
        painter.line(x + Math.round(x1 * size), y + Math.round(y1 * size),
                x + Math.round(x2 * size), y + Math.round(y2 * size), color);
    }
}
