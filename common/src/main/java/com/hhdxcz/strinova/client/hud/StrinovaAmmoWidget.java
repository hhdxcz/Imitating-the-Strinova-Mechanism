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

    /** 展开卡片宽度。 */
    private static final int CARD_WIDTH = 166;
    private static final int BADGE_WIDTH = 11;
    private static final int BADGE_HEIGHT = 10;
    private static final int WIDTH = 204;
    private static final int HEIGHT = 148;
    /** 槽位图标尺寸（游戏里没有小方框，图标直接浮在 HUD 上）。 */
    private static final int SLOT = 22;
    private static final int SLOT_STEP = 24;
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

        // 槽位列：和游戏一致——图标直接浮在 HUD 上（没有小方框），数字键在分隔线右侧
        int slotX = x + CARD_WIDTH + 3;
        int dividerX = slotX + SLOT;
        int badgeX = dividerX + 2;
        int bottomY = y + HEIGHT - SLOT;
        int columnTop = bottomY - 4 * SLOT_STEP - 2;
        int columnBottom = y + HEIGHT;

        // 图标列背后很淡的暗色衬底（游戏里有一层半透明底，避免白色剪影在亮背景上看不清）
        painter.rect(slotX - 2, columnTop, SLOT + 4, columnBottom - columnTop, 0x40101820);
        // 图标列与数字键之间的细竖线
        painter.rect(dividerX, columnTop, 1, columnBottom - columnTop, 0x66B4C0CC);

        int selectedSlotY = bottomY;
        for (int i = 0; i < 5; i++) {
            int slotY = bottomY - i * SLOT_STEP;
            if (i == selected) {
                selectedSlotY = slotY;
            }
            boolean isSelected = i == selected;
            // 选中的那一格不画小图标：游戏里该位置被展开的武器卡片取代，只剩右侧数字键
            if (!isSelected) {
                drawWeaponArt(painter, iconNames[i], kinds[i], slotX, slotY, SLOT, SLOT, 0xDDDCE8F2);
            }

            // 数字键徽标在分隔线右侧：只有选中的那个亮白高亮
            int badgeY = slotY + (SLOT - BADGE_HEIGHT) / 2;
            painter.rect(badgeX, badgeY, BADGE_WIDTH, BADGE_HEIGHT, 0x88101820);
            painter.border(badgeX, badgeY, BADGE_WIDTH, BADGE_HEIGHT,
                    isSelected ? 0xFFEAF6FF : 0x66445566, 1);
            String number = String.valueOf(i + 1);
            painter.text(number, badgeX + (BADGE_WIDTH - painter.textWidth(number)) / 2,
                    badgeY + 1,
                    isSelected ? 0xFFEAF6FF : 0xFF9FC6D8, false);
        }

        // 当前武器卡片：从选中的那一层向左展开（不固定在最下面），右边缘紧贴槽位列
        int cardY = Math.max(y, Math.min(y + HEIGHT - CARD_HEIGHT,
                selectedSlotY - (CARD_HEIGHT - SLOT) / 2));
        int cardX = x;
        painter.rect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0xCC2A2F36);
        painter.border(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0x66445566, 1);

        // 卡片内容（先画内容，最后再画右侧连接卡槽的白色装饰线，压在卡片右边沿上）
        // 大号武器剪影
        StrinovaTaczCompat.IconKind selectedKind = kinds[selected];
        if (selectedKind == null) {
            selectedKind = StrinovaTaczCompat.IconKind.EMPTY;
        }
        drawWeaponArt(painter, iconNames[selected], selectedKind, cardX + 6, cardY + 8,
                CARD_WIDTH - 82, CARD_HEIGHT - 16, 0xFFEAF6FF);

        // 左下：武器名
        String selectedName = names[selected];
        if (selectedName == null || selectedName.isEmpty()) {
            selectedName = mock ? DEFAULT_SLOT_NAMES[selected] : "未装备";
        }
        painter.text(selectedName, cardX + 6, cardY + CARD_HEIGHT - 12,
                StrinovaProgrammaticPainter.COLOR_TEXT, false);

        // 右侧：弹匣（白，大号）+ 备弹（蓝，稍小）
        if (magazine >= 0) {
            String magText = String.valueOf(magazine);
            float magScale = 1.9F;
            int magWidth = Math.round(painter.textWidth(magText) * magScale);
            float magX = cardX + CARD_WIDTH - 6 - magWidth;
            int magColor = magazine <= 5
                    ? StrinovaProgrammaticPainter.COLOR_HP_LOW
                    : StrinovaProgrammaticPainter.COLOR_TEXT;
            painter.textScaled(magText, magX, cardY + 8, magScale, magColor, true);
            if (reserve >= 0) {
                painter.textRightAligned(String.valueOf(reserve), cardX + CARD_WIDTH - 8,
                        cardY + 30, AMMO_BLUE, false);
            }
        }

        // 换弹进度（有换弹时卡片底部细条）
        float reload = mock ? StrinovaHudState.reloadProgress() : 0.0F;
        if (reload > 0.0F) {
            painter.bar(cardX + 4, cardY + CARD_HEIGHT - 3, CARD_WIDTH - 8, 2, reload,
                    StrinovaProgrammaticPainter.COLOR_WARN, 0x66222B33);
        }

        // 卡片右边缘连接槽位列的白色装饰线（sprite_211）：按官方 12x63 比例并竖直居中在卡片上，
        // 而不是被拉成整张卡片高；卡片上下夹紧时白线也跟着卡片走（和游戏一致）
        int lineWidth = 6;
        int lineHeight = 32;
        int lineX = cardX + CARD_WIDTH - 3;
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
                                      int maxWidth, int maxHeight, int color) {
        net.minecraft.resources.ResourceLocation texture = StrinovaWeaponIcons.textureFor(name);
        if (texture != null) {
            // 贴图是按 alpha 裁剪的原始剪影，宽高比各不相同，必须等比适配，不能拉伸
            int[] box = StrinovaWeaponIcons.fit(texture, x, y, maxWidth, maxHeight);
            int uv = StrinovaWeaponIcons.UV_SIZE;
            painter.textureRegion(texture, box[0], box[1], box[2], box[3],
                    0.0F, 0.0F, uv, uv, uv, uv, color, 1.0F);
            return;
        }
        drawWeaponIcon(painter, kind, x, y, Math.max(8, Math.min(maxWidth, maxHeight)), color);
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
