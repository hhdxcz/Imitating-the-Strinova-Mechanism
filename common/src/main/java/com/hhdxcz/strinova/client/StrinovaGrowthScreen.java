package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;
import com.hhdxcz.strinova.client.hud.StrinovaGrowthState;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudState;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 「弦能增幅网络」成长/加点界面（B 键，P3）。
 * 顶部栏：标题·角色名 / 拥有能量点 / 准备阶段倒计时 / 关闭按钮；
 * 左侧：角色立绘占位（渐变 + 名字首字大字）+ 角色名 + 定位标签；
 * 中间：7 类成长两列排布，每类 2 个升级项，点击消耗能量点购买；
 * 右侧：5 个武器槽（主武器/副武器/近战/战术道具×2）；
 * 底部：3 个觉醒（图标 + 名称 + 激活状态 + 自动换行描述）。
 *
 * <p>纯 {@link GuiGraphics} 程序化绘制，不依赖任何贴图；小窗口会自动隐藏立绘、觉醒描述
 * 与武器名称等次要信息，保证 320×240 ~ 高分辨率下都不溢出。</p>
 */
public class StrinovaGrowthScreen extends Screen {

    // ==================== 配色（Strinova 风格：青蓝 / 品红 / 金色点缀） ====================
    private static final int COLOR_BG_TOP = 0xFF0A1018;
    private static final int COLOR_BG_BOTTOM = 0xFF141D2B;
    private static final int COLOR_HEADER_BG = 0xF0161F2C;
    private static final int COLOR_CARD_BG = 0xCC101823;
    private static final int COLOR_CYAN = 0xFF35D6E8;
    private static final int COLOR_MAGENTA = 0xFFFF4FA3;
    private static final int COLOR_GOLD = 0xFFFFD166;
    private static final int COLOR_DIAMOND = 0xFF4FA3FF;
    private static final int COLOR_TEXT = 0xFFEAF2FA;
    private static final int COLOR_TEXT_DIM = 0xFF9AA8B8;
    private static final int COLOR_TEXT_DISABLED = 0xFF5E6874;
    private static final int COLOR_SUCCESS = 0xFF54E0A0;
    private static final int COLOR_BORDER = 0x4035D6E8;
    /** 7 类成长各自的强调色（类别标题条与下划线）。 */
    private static final int[] CATEGORY_ACCENTS = {
            0xFF35D6E8, 0xFFFFD166, 0xFF54E0A0, 0xFF4FA3FF, 0xFF9B6BFF, 0xFFFF9F43, 0xFFFF4FA3
    };

    /** 每列最多摆 4 个成长类别（7 类 → 左列 4、右列 3）。 */
    private static final int CATEGORY_ROWS = 4;
    /** 武器槽数量（1 主武器 / 2 副武器 / 3 近战 / 4~5 战术道具）。 */
    private static final int WEAPON_SLOT_COUNT = 5;
    /** 字体行高 9 + 行距 2。 */
    private static final int LINE_STEP = 11;
    /** 成长类别标题区高度。 */
    private static final int ITEM_HEADER_HEIGHT = 12;

    private final Screen parent;
    /** 当前角色（由客户端配置 selectedAgentId 决定，失效时回退默认角色）。 */
    private StrinovaAgentRoster.Agent agent;

    // ==================== 布局（computeLayout 依据窗口尺寸计算） ====================
    private int gap;
    private int innerLeft;
    private int innerRight;
    private int headerTop;
    private int headerHeight;
    private int closeButtonSize;
    private int contentTop;
    private int contentBottom;

    private boolean showPortrait;
    private int portraitX;
    private int portraitY;
    private int portraitWidth;
    private int portraitHeight;

    private int weaponsX;
    private int weaponsY;
    private int weaponsWidth;
    private boolean compactWeapons;

    private int categoriesLeft;
    private int categoriesTop;
    private int categoriesRight;
    private int categoriesBottom;
    /** 成长网格实际起始 Y（行高有上限，超大窗口时整块垂直居中）。 */
    private int categoriesGridTop;
    private int categoryColumnWidth;
    private int categoryColumnGap;
    private int categoryRowGap;
    private int categoryBlockHeight;
    private int itemRowHeight;
    private int itemRowGap;

    private int awakenTop;
    private int awakenHeight;
    private boolean showAwakenDescription;

    // ==================== 控件与悬停状态 ====================
    private Button closeButton;
    private int hoveredCategory = -1;
    private int hoveredItem = -1;
    private int hoveredAwakening = -1;

    public StrinovaGrowthScreen(Screen parent) {
        super(Component.translatable("config.strinova.growth.title"));
        this.parent = parent;
    }

    /** 初始化界面：解析当前角色、计算自适应布局并添加最右侧关闭按钮。 */
    @Override
    protected void init() {
        this.clearWidgets();
        this.agent = resolveAgent();
        this.computeLayout();
        this.hoveredCategory = -1;
        this.hoveredItem = -1;
        this.hoveredAwakening = -1;
        this.closeButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.onClose())
                .bounds(this.closeButtonX(), this.closeButtonY(), this.closeButtonSize, this.closeButtonSize)
                .build());
    }

    /** 从客户端配置读取当前选角，无效或未选择时回退到默认角色。 */
    private static StrinovaAgentRoster.Agent resolveAgent() {
        StrinovaAgentRoster.Agent selected = StrinovaAgentRoster.byId(StrinovaHudConfig.get().selectedAgentId);
        return selected != null ? selected : StrinovaAgentRoster.defaultAgent();
    }

    /** 计算自适应布局：先按窗口尺寸决定顶部栏/底部觉醒区高度，再切分左立绘、中成长区、右武器栏。 */
    private void computeLayout() {
        int margin = clamp(this.width / 96, 4, 12);
        this.gap = this.height >= 300 ? 8 : 6;
        this.innerLeft = margin;
        this.innerRight = this.width - margin;

        this.headerTop = margin;
        this.headerHeight = this.height >= 300 ? 28 : 22;
        this.closeButtonSize = this.height >= 300 ? 20 : 18;

        // 底部觉醒区：空间足够时显示描述并加高，否则只保留图标+名称+状态
        this.showAwakenDescription = this.height >= 300 && this.width >= 520;
        this.awakenHeight = this.showAwakenDescription
                ? clamp((int) (this.height * 0.26F), 64, 160)
                : clamp(this.height / 9, 34, 54);
        this.awakenTop = this.height - margin - this.awakenHeight;

        this.contentTop = this.headerTop + this.headerHeight + this.gap;
        this.contentBottom = this.awakenTop - this.gap;

        // 左侧角色立绘：窗口过小时隐藏，把宽度让给成长网格
        this.showPortrait = this.width >= 620 && this.height >= 280;
        this.portraitWidth = this.showPortrait ? clamp(this.width / 5, 120, 210) : 0;
        this.portraitX = this.innerLeft;
        this.portraitY = this.contentTop;
        this.portraitHeight = Math.max(0, this.contentBottom - this.contentTop);

        // 右侧武器栏：小窗口只显示图标 + 数字键
        this.weaponsWidth = this.showPortrait ? 130 : (this.width >= 480 ? 96 : 56);
        this.compactWeapons = this.weaponsWidth < 80;
        this.weaponsX = this.innerRight - this.weaponsWidth;
        this.weaponsY = this.contentTop;

        // 中间成长网格：两列，每列最多 4 类；行高设上限，超大窗口时整块垂直居中避免过于稀疏
        this.categoriesLeft = this.innerLeft + (this.showPortrait ? this.portraitWidth + this.gap : 0);
        this.categoriesTop = this.contentTop;
        this.categoriesRight = this.weaponsX - this.gap;
        this.categoriesBottom = this.contentBottom;
        this.categoryColumnGap = this.width >= 560 ? 10 : 6;
        this.categoryRowGap = this.height >= 320 ? 8 : 4;
        this.categoryColumnWidth = Math.max(60,
                (this.categoriesRight - this.categoriesLeft - this.categoryColumnGap) / 2);
        this.categoryBlockHeight = clamp(
                (this.categoriesBottom - this.categoriesTop
                        - this.categoryRowGap * (CATEGORY_ROWS - 1)) / CATEGORY_ROWS, 24, 120);
        this.itemRowGap = 2;
        this.itemRowHeight = clamp(
                (this.categoryBlockHeight - ITEM_HEADER_HEIGHT - this.itemRowGap) / 2, 10, 36);
        int gridHeight = this.categoryBlockHeight * CATEGORY_ROWS
                + this.categoryRowGap * (CATEGORY_ROWS - 1);
        this.categoriesGridTop = this.categoriesTop
                + Math.max(0, (this.categoriesBottom - this.categoriesTop - gridHeight) / 2);
    }

    private int closeButtonX() {
        return this.innerRight - this.closeButtonSize - 2;
    }

    private int closeButtonY() {
        return this.headerTop + (this.headerHeight - this.closeButtonSize) / 2;
    }

    /** 渲染界面：深色底 → 顶部栏 → 左立绘 → 中成长网格 → 右武器栏 → 底部觉醒 → 控件/提示。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);
        this.renderTopBar(graphics);
        if (this.showPortrait) {
            this.renderPortrait(graphics);
        }
        this.renderCategories(graphics, mouseX, mouseY);
        this.renderWeapons(graphics);
        this.renderAwakenings(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderCloseGlyph(graphics, mouseX, mouseY);
        this.renderHoverTooltips(graphics, mouseX, mouseY);
    }

    // ==================== 顶部栏 ====================

    /** 渲染顶部栏：标题·角色名、中央能量点、右侧准备阶段倒计时与底部装饰线。 */
    private void renderTopBar(GuiGraphics graphics) {
        int headerBottom = this.headerTop + this.headerHeight;
        graphics.fill(this.innerLeft, this.headerTop, this.innerRight, headerBottom, COLOR_HEADER_BG);
        int midX = this.width / 2;
        graphics.fill(this.innerLeft, headerBottom - 1, midX, headerBottom, 0x8035D6E8);
        graphics.fill(midX, headerBottom - 1, this.innerRight, headerBottom, 0x60FF4FA3);
        graphics.fill(this.innerLeft, this.headerTop, this.innerLeft + 3, headerBottom, COLOR_CYAN);

        int textY = this.headerTop + (this.headerHeight - 9) / 2;

        // 中央能量点（价格购买后实时刷新）
        Component points = Component.translatable("config.strinova.growth.points",
                StrinovaGrowthState.energyPoints());
        graphics.drawCenteredString(this.font, points, this.width / 2, textY, COLOR_GOLD);

        // 左侧标题 + 角色名（空间不足时截断角色名部分）
        Component title = titleLine(this.agent);
        int titleX = this.innerLeft + 10;
        int titleMax = Math.max(24, this.width / 2 - this.font.width(points) / 2 - 10 - titleX);
        graphics.drawString(this.font, Component.literal(truncate(title.getString(), titleMax)),
                titleX, textY, COLOR_TEXT, false);

        // 右侧准备阶段 + 倒计时（右对齐到关闭按钮左侧，空间不足时截断）
        Component ready = Component.translatable("config.strinova.growth.ready_phase")
                .append(" " + StrinovaMatchState.formattedTime());
        int readyRight = this.closeButtonX() - 8;
        int readyMinX = this.width / 2 + this.font.width(points) / 2 + 12;
        int readyMaxWidth = Math.max(10, readyRight - readyMinX);
        String readyText = truncate(ready.getString(), readyMaxWidth);
        int readyX = Math.max(readyMinX, readyRight - this.font.width(readyText));
        graphics.drawString(this.font, readyText, readyX, textY, COLOR_CYAN, false);
    }

    /** 顶部标题文案：「弦能增幅网络 · 角色名」。 */
    private static Component titleLine(StrinovaAgentRoster.Agent agent) {
        Component title = Component.translatable("config.strinova.growth.title");
        if (agent == null) {
            return title;
        }
        return title.copy().append(Component.literal(" · " + agent.name()));
    }

    /** 在关闭按钮上绘制程序化 ✕（不依赖字体是否有该符号）。 */
    private void renderCloseGlyph(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.closeButton == null) {
            return;
        }
        int x = this.closeButton.getX();
        int y = this.closeButton.getY();
        int size = this.closeButtonSize;
        int pad = Math.max(5, size / 4);
        boolean hovered = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        int color = hovered ? COLOR_MAGENTA : COLOR_TEXT_DIM;
        int length = size - pad * 2;
        for (int i = 0; i < length; i++) {
            graphics.fill(x + pad + i, y + pad + i, x + pad + i + 1, y + pad + i + 1, color);
            graphics.fill(x + size - pad - 1 - i, y + pad + i,
                    x + size - pad - i, y + pad + i + 1, color);
        }
    }

    // ==================== 左侧角色立绘 ====================

    /** 渲染角色立绘占位：渐变底 + 定位色装饰 + 名字首字大号字 + 角色名 + 定位标签。 */
    private void renderPortrait(GuiGraphics graphics) {
        int accent = this.agent == null ? COLOR_CYAN : this.agent.accentColor();
        graphics.fillGradient(this.portraitX, this.portraitY,
                this.portraitX + this.portraitWidth, this.portraitY + this.portraitHeight,
                0xFF1C2735, 0xFF0C121B);
        graphics.fill(this.portraitX, this.portraitY,
                this.portraitX + this.portraitWidth, this.portraitY + 2, accent);
        graphics.fill(this.portraitX, this.portraitY,
                this.portraitX + 1, this.portraitY + this.portraitHeight, 0x60FFFFFF);

        // 名字首字放大绘制（PoseStack 缩放，避免依赖贴图）
        int centerX = this.portraitX + this.portraitWidth / 2;
        int charCenterY = this.portraitY + this.portraitHeight / 2 - 12;
        int scale = clamp(this.portraitWidth / 22, 3, 9);
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(this.font, initialOf(this.agent),
                centerX / scale, charCenterY / scale, accent & 0x80FFFFFF);
        graphics.pose().popPose();

        // 底部：角色名 + 定位标签
        int nameY = this.portraitY + this.portraitHeight - 40;
        Component name = this.agent == null ? Component.literal("?") : Component.literal(this.agent.name());
        graphics.drawCenteredString(this.font, name, centerX, nameY, COLOR_TEXT);
        if (this.agent != null) {
            Component role = Component.translatable(StrinovaAgentRoster.roleNameKey(this.agent.role()));
            int roleWidth = this.font.width(role) + 14;
            int roleX = centerX - roleWidth / 2;
            int roleY = nameY + 15;
            graphics.fill(roleX, roleY, roleX + roleWidth, roleY + 14, (accent & 0x00FFFFFF) | 0x33000000);
            graphics.fill(roleX, roleY, roleX + roleWidth, roleY + 1, accent);
            graphics.fill(roleX, roleY + 13, roleX + roleWidth, roleY + 14, accent);
            graphics.drawCenteredString(this.font, role, centerX, roleY + 3, accent);
        }
    }

    /** 取角色名的第一个字符（处理代理对），空名时返回问号。 */
    private static String initialOf(StrinovaAgentRoster.Agent agent) {
        if (agent == null || agent.name().isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(agent.name().codePointAt(0)));
    }

    // ==================== 中间成长类别网格 ====================

    /** 渲染 7 类成长：两列排布，每类 2 个升级项；同时更新鼠标悬停的项。 */
    private void renderCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        this.hoveredCategory = -1;
        this.hoveredItem = -1;
        List<StrinovaGrowthState.Category> categories = StrinovaGrowthState.categories();
        int energy = StrinovaGrowthState.energyPoints();
        for (int i = 0; i < categories.size(); i++) {
            int column = i / CATEGORY_ROWS;
            int row = i % CATEGORY_ROWS;
            int blockX = this.categoriesLeft + column * (this.categoryColumnWidth + this.categoryColumnGap);
            int blockY = this.categoriesGridTop + row * (this.categoryBlockHeight + this.categoryRowGap);
            this.renderCategory(graphics, categories.get(i), i, blockX, blockY, energy, mouseX, mouseY);
        }
    }

    /** 渲染单个成长类别：标题 + 2 个升级项。 */
    private void renderCategory(GuiGraphics graphics, StrinovaGrowthState.Category category, int categoryIndex,
                                int blockX, int blockY, int energy, int mouseX, int mouseY) {
        int accent = CATEGORY_ACCENTS[categoryIndex % CATEGORY_ACCENTS.length];
        int titleY = blockY + 1;
        graphics.fill(blockX, titleY + 1, blockX + 2, titleY + 8, accent);
        graphics.drawString(this.font, Component.translatable(category.nameKey()),
                blockX + 6, titleY, COLOR_TEXT, false);
        graphics.fill(blockX, titleY + 10, blockX + this.categoryColumnWidth, titleY + 11, accent & 0x55FFFFFF);

        List<StrinovaGrowthState.UpgradeItem> items = category.items();
        int itemsBlockHeight = this.itemRowHeight * items.size() + this.itemRowGap * Math.max(0, items.size() - 1);
        int itemsTop = blockY + ITEM_HEADER_HEIGHT
                + Math.max(0, (this.categoryBlockHeight - ITEM_HEADER_HEIGHT - itemsBlockHeight) / 2);
        for (int j = 0; j < items.size(); j++) {
            int itemY = itemsTop + j * (this.itemRowHeight + this.itemRowGap);
            boolean hovered = mouseX >= blockX && mouseX < blockX + this.categoryColumnWidth
                    && mouseY >= itemY && mouseY < itemY + this.itemRowHeight;
            if (hovered) {
                this.hoveredCategory = categoryIndex;
                this.hoveredItem = j;
            }
            this.renderUpgradeItem(graphics, items.get(j), blockX, itemY, energy, hovered);
        }
    }

    /** 渲染单个升级项：已购买高亮打勾、能量不足灰显、悬停高亮，右侧显示价格/激活状态。 */
    private void renderUpgradeItem(GuiGraphics graphics, StrinovaGrowthState.UpgradeItem item,
                                   int x, int y, int energy, boolean hovered) {
        boolean purchased = item.purchased();
        boolean affordable = !purchased && energy >= item.cost();
        int width = this.categoryColumnWidth;
        int height = this.itemRowHeight;

        int background;
        if (purchased) {
            background = 0x5535D6E8;
        } else if (hovered && affordable) {
            background = 0x33FFFFFF;
        } else if (affordable) {
            background = 0x1CFFFFFF;
        } else {
            background = 0x12000000;
        }
        graphics.fill(x, y, x + width, y + height, background);
        if (purchased || (hovered && affordable)) {
            graphics.fill(x, y, x + 2, y + height, purchased ? COLOR_CYAN : 0x80FFFFFF);
        }

        String statusText = Component.translatable("config.strinova.growth.activated").getString();
        int statusWidth = purchased
                ? this.font.width(statusText) + 14
                : this.font.width(String.valueOf(item.cost())) + 13;
        int nameWidth = Math.max(16, width - 12 - statusWidth);
        int textY = y + Math.max(1, (height - 9) / 2);
        int nameColor = purchased ? COLOR_TEXT : (affordable ? COLOR_TEXT : COLOR_TEXT_DISABLED);
        graphics.drawString(this.font,
                truncate(Component.translatable(item.nameKey()).getString(), nameWidth),
                x + 6, textY, nameColor, false);

        if (purchased) {
            int textRight = x + width - 6;
            graphics.drawString(this.font, statusText,
                    textRight - this.font.width(statusText), textY, COLOR_SUCCESS, false);
            drawCheck(graphics, textRight - this.font.width(statusText) - 11, textY, COLOR_SUCCESS);
        } else {
            int diamondX = x + width - 8;
            drawDiamond(graphics, diamondX, textY + 4, 3, COLOR_DIAMOND);
            String price = String.valueOf(item.cost());
            graphics.drawString(this.font, price,
                    diamondX - 8 - this.font.width(price), textY,
                    affordable ? COLOR_DIAMOND : COLOR_TEXT_DISABLED, false);
        }
    }

    // ==================== 右侧武器栏 ====================

    /** 渲染右侧 5 个武器槽：几何图标 + 名称（小窗口隐藏）+ 数字键 1~5，当前槽高亮。 */
    private void renderWeapons(GuiGraphics graphics) {
        int panelLeft = this.weaponsX - 4;
        graphics.fill(panelLeft, this.weaponsY, this.innerRight, this.contentBottom, 0x88101824);
        graphics.fill(panelLeft, this.weaponsY, panelLeft + 1, this.contentBottom, COLOR_BORDER);

        List<StrinovaHudState.WeaponSlot> slots = StrinovaHudState.getWeaponSlots();
        int slotGap = 3;
        int available = this.contentBottom - this.weaponsY - 8 - (WEAPON_SLOT_COUNT - 1) * slotGap;
        int slotHeight = clamp(available / WEAPON_SLOT_COUNT, 16, 44);
        int totalHeight = WEAPON_SLOT_COUNT * slotHeight + (WEAPON_SLOT_COUNT - 1) * slotGap;
        int startY = this.weaponsY + 4
                + Math.max(0, (this.contentBottom - this.weaponsY - 8 - totalHeight) / 2);
        for (int i = 0; i < WEAPON_SLOT_COUNT; i++) {
            int slotY = startY + i * (slotHeight + slotGap);
            // 槽位数据来自 StrinovaHudState（mock 或后续同步），缺失时用占位名称
            StrinovaHudState.WeaponSlot slot = i < slots.size() ? slots.get(i) : null;
            boolean selected = slot != null && slot.selected();
            String name = slot == null || slot.name() == null ? "" : slot.name();
            int slotRight = this.innerRight - 4;
            graphics.fill(this.weaponsX, slotY, slotRight, slotY + slotHeight,
                    selected ? 0x66203548 : 0x330B121A);
            if (selected) {
                graphics.fill(this.weaponsX, slotY, slotRight, slotY + 1, COLOR_CYAN);
                graphics.fill(this.weaponsX, slotY + slotHeight - 1, slotRight, slotY + slotHeight, COLOR_CYAN);
                graphics.fill(this.weaponsX, slotY, this.weaponsX + 1, slotY + slotHeight, COLOR_CYAN);
                graphics.fill(slotRight - 1, slotY, slotRight, slotY + slotHeight, COLOR_CYAN);
            }

            int iconSize = Math.max(8, Math.min(this.weaponsWidth - 24, slotHeight - 6));
            int iconX = this.weaponsX + 4;
            int iconY = slotY + (slotHeight - iconSize) / 2;
            drawWeaponIcon(graphics, i, iconX, iconY, iconSize, selected ? COLOR_CYAN : COLOR_TEXT_DIM);

            // 数字键帽 1~5
            int keySize = Math.min(13, slotHeight - 2);
            int keyX = slotRight - keySize - 3;
            int keyY = slotY + (slotHeight - keySize) / 2;
            graphics.fill(keyX, keyY, keyX + keySize, keyY + keySize, 0x44FFFFFF);
            graphics.drawString(this.font, String.valueOf(i + 1),
                    keyX + (keySize - 6) / 2, keyY + (keySize - 9) / 2, COLOR_TEXT_DIM, false);

            if (!this.compactWeapons) {
                int textX = iconX + iconSize + 6;
                int textMax = keyX - textX - 4;
                boolean hasName = name != null && !name.isEmpty();
                String label = hasName
                        ? name
                        : Component.translatable("hud.strinova.weapon.none").getString();
                graphics.drawString(this.font, truncate(label, Math.max(12, textMax)),
                        textX, slotY + (slotHeight - 9) / 2,
                        hasName ? (selected ? COLOR_TEXT : COLOR_TEXT_DIM) : COLOR_TEXT_DISABLED, false);
            }
        }
    }

    /** 程序化绘制武器几何图标：0 主武器（长枪）、1 副武器（手枪）、2 近战（斜刃）、3+ 战术道具（菱形手雷）。 */
    private static void drawWeaponIcon(GuiGraphics graphics, int index, int x, int y, int size, int color) {
        if (size < 8) {
            graphics.fill(x, y, x + size, y + size, color);
            return;
        }
        switch (index) {
            case 0 -> {
                // 主武器：长枪身 + 枪管 + 弹匣
                graphics.fill(x + size / 8, y + size * 35 / 100,
                        x + size * 85 / 100, y + size * 55 / 100, color);
                graphics.fill(x + size * 85 / 100, y + size * 42 / 100,
                        x + size, y + size * 48 / 100, color);
                graphics.fill(x + size * 25 / 100, y + size * 55 / 100,
                        x + size * 40 / 100, y + size * 85 / 100, color);
            }
            case 1 -> {
                // 副武器：短枪身 + 枪管 + 握把
                graphics.fill(x + size / 8, y + size * 32 / 100,
                        x + size * 75 / 100, y + size * 52 / 100, color);
                graphics.fill(x + size * 75 / 100, y + size * 38 / 100,
                        x + size * 95 / 100, y + size * 45 / 100, color);
                graphics.fill(x + size * 45 / 100, y + size * 52 / 100,
                        x + size * 60 / 100, y + size * 85 / 100, color);
            }
            case 2 -> {
                // 近战：斜向刀刃 + 护手
                int steps = Math.max(4, size * 6 / 10);
                for (int i = 0; i < steps; i++) {
                    int px = x + size * 10 / 100 + i;
                    int py = y + size * 85 / 100 - i;
                    graphics.fill(px, py, px + 2, py + 2, color);
                }
                graphics.fill(x + size * 12 / 100, y + size * 62 / 100,
                        x + size * 30 / 100, y + size * 78 / 100, color);
            }
            default -> {
                // 战术道具：菱形手雷 + 拉环
                int centerX = x + size / 2;
                int centerY = y + size * 55 / 100;
                int radius = Math.max(2, size / 4);
                for (int dy = -radius; dy <= radius; dy++) {
                    int half = radius - Math.abs(dy);
                    graphics.fill(centerX - half, centerY + dy,
                            centerX + half + 1, centerY + dy + 1, color);
                }
                graphics.fill(centerX - 1, y + size * 15 / 100,
                        centerX + 2, centerY - radius, color);
            }
        }
    }

    // ==================== 底部觉醒 ====================

    /** 渲染底部 3 个觉醒：图标 + 觉醒序号 + 名称 + 激活状态，空间足够时自动换行显示描述。 */
    private void renderAwakenings(GuiGraphics graphics, int mouseX, int mouseY) {
        List<StrinovaGrowthState.Awakening> awakenings = StrinovaGrowthState.awakenings();
        this.hoveredAwakening = -1;
        if (awakenings.isEmpty()) {
            return;
        }
        int cardGap = this.gap;
        int cardWidth = (this.innerRight - this.innerLeft
                - (awakenings.size() - 1) * cardGap) / awakenings.size();
        for (int i = 0; i < awakenings.size(); i++) {
            StrinovaGrowthState.Awakening awakening = awakenings.get(i);
            int x = this.innerLeft + i * (cardWidth + cardGap);
            this.renderAwakeningCard(graphics, awakening, i, x, cardWidth, mouseX, mouseY);
        }
    }

    /** 渲染单个觉醒卡片。 */
    private void renderAwakeningCard(GuiGraphics graphics, StrinovaGrowthState.Awakening awakening,
                                     int index, int x, int cardWidth, int mouseX, int mouseY) {
        boolean active = awakening.active();
        int accent = active ? COLOR_GOLD : 0xFF3A4756;
        graphics.fill(x, this.awakenTop, x + cardWidth, this.awakenTop + this.awakenHeight, COLOR_CARD_BG);
        graphics.fill(x, this.awakenTop, x + cardWidth, this.awakenTop + 2, accent);
        graphics.fill(x, this.awakenTop, x + 1, this.awakenTop + this.awakenHeight,
                active ? 0x8035D6E8 : 0x30FFFFFF);
        if (mouseX >= x && mouseX < x + cardWidth
                && mouseY >= this.awakenTop && mouseY < this.awakenTop + this.awakenHeight) {
            this.hoveredAwakening = index;
        }

        Component label = Component.translatable("config.strinova.growth.awakening")
                .append(" " + (index + 1));
        Component name = Component.translatable(awakening.nameKey());
        Component status = Component.translatable(active
                ? "config.strinova.growth.activated"
                : "config.strinova.growth.not_activated");
        int nameColor = active ? COLOR_GOLD : COLOR_TEXT;

        if (this.showAwakenDescription) {
            int iconSize = 22;
            int iconX = x + 8;
            int iconY = this.awakenTop + 8;
            drawAwakeningIcon(graphics, iconX, iconY, iconSize, active);
            int textX = iconX + iconSize + 8;
            int textMax = Math.max(24, x + cardWidth - 8 - textX);
            graphics.drawString(this.font, truncate(label.getString(), textMax),
                    textX, iconY, COLOR_TEXT_DIM, false);
            graphics.drawString(this.font, truncate(name.getString(), textMax),
                    textX, iconY + LINE_STEP, nameColor, false);
            graphics.drawString(this.font, truncate(status.getString(), textMax),
                    textX, iconY + LINE_STEP * 2, active ? COLOR_SUCCESS : COLOR_TEXT_DISABLED, false);
            int descTop = iconY + LINE_STEP * 3 + 2;
            int maxLines = Math.max(1,
                    (this.awakenTop + this.awakenHeight - 6 - descTop) / LINE_STEP);
            drawWrapped(graphics, Component.translatable(awakening.descKey()),
                    x + 8, descTop, cardWidth - 16, maxLines, 0xFFB4C0CC);
        } else {
            int iconSize = clamp(this.awakenHeight - 8, 12, 16);
            int iconX = x + 4;
            int iconY = this.awakenTop + (this.awakenHeight - iconSize) / 2;
            drawAwakeningIcon(graphics, iconX, iconY, iconSize, active);
            int textX = iconX + iconSize + 5;
            int textMax = Math.max(20, x + cardWidth - 5 - textX);
            int statusWidth = this.font.width(status);
            int labelMax = Math.max(10, textMax - statusWidth - 4);
            int lineY = this.awakenTop + (this.awakenHeight - LINE_STEP * 2) / 2;
            graphics.drawString(this.font, truncate(label.getString(), labelMax),
                    textX, lineY, COLOR_TEXT_DIM, false);
            graphics.drawString(this.font, status,
                    x + cardWidth - 5 - statusWidth, lineY,
                    active ? COLOR_SUCCESS : COLOR_TEXT_DISABLED, false);
            graphics.drawString(this.font, truncate(name.getString(), textMax),
                    textX, lineY + LINE_STEP, nameColor, false);
        }
    }

    /** 程序化绘制觉醒图标（菱形描边 + 内部淡色块），不占用字体符号。 */
    private static void drawAwakeningIcon(GuiGraphics graphics, int x, int y, int size, boolean active) {
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = Math.max(3, size / 2 - 1);
        int color = active ? COLOR_GOLD : 0xFF56626E;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX - half + 1, centerY + dy + 1, color);
            graphics.fill(centerX + half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
        graphics.fill(centerX - radius / 2, centerY - radius / 2,
                centerX + radius / 2 + 1, centerY + radius / 2 + 1,
                active ? 0x5535D6E8 : 0x2056626E);
    }

    // ==================== 交互 ====================

    /** 鼠标点击：先交给按钮控件；否则命中成长项时尝试购买（能量不足/已购买的项 purchase 内部会拒绝）。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        List<StrinovaGrowthState.Category> categories = StrinovaGrowthState.categories();
        for (int categoryIndex = 0; categoryIndex < categories.size(); categoryIndex++) {
            List<StrinovaGrowthState.UpgradeItem> items = categories.get(categoryIndex).items();
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                int[] rect = this.itemRect(categoryIndex, itemIndex);
                if (rect != null && insideRect(mouseX, mouseY, rect)) {
                    StrinovaGrowthState.purchase(categoryIndex, itemIndex);
                    return true;
                }
            }
        }
        return false;
    }

    /** 成长项 Rectangle：{x, y, width, height}，与 renderCategory 的排布保持一致。 */
    private int[] itemRect(int categoryIndex, int itemIndex) {
        List<StrinovaGrowthState.Category> categories = StrinovaGrowthState.categories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            return null;
        }
        List<StrinovaGrowthState.UpgradeItem> items = categories.get(categoryIndex).items();
        if (itemIndex < 0 || itemIndex >= items.size()) {
            return null;
        }
        int column = categoryIndex / CATEGORY_ROWS;
        int row = categoryIndex % CATEGORY_ROWS;
        int blockX = this.categoriesLeft + column * (this.categoryColumnWidth + this.categoryColumnGap);
        int blockY = this.categoriesGridTop + row * (this.categoryBlockHeight + this.categoryRowGap);
        int itemsBlockHeight = this.itemRowHeight * items.size()
                + this.itemRowGap * Math.max(0, items.size() - 1);
        int itemsTop = blockY + ITEM_HEADER_HEIGHT
                + Math.max(0, (this.categoryBlockHeight - ITEM_HEADER_HEIGHT - itemsBlockHeight) / 2);
        return new int[]{blockX, itemsTop + itemIndex * (this.itemRowHeight + this.itemRowGap),
                this.categoryColumnWidth, this.itemRowHeight};
    }

    private static boolean insideRect(double mouseX, double mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    // ==================== 悬停提示 ====================

    /** 悬停成长项显示完整名称/价格/激活状态；小窗口下悬停觉醒卡片补全描述。 */
    private void renderHoverTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredCategory >= 0 && this.hoveredItem >= 0) {
            List<StrinovaGrowthState.Category> categories = StrinovaGrowthState.categories();
            if (this.hoveredCategory < categories.size()) {
                List<StrinovaGrowthState.UpgradeItem> items = categories.get(this.hoveredCategory).items();
                if (this.hoveredItem < items.size()) {
                    this.renderItemTooltip(graphics, items.get(this.hoveredItem), mouseX, mouseY);
                    return;
                }
            }
        }
        if (!this.showAwakenDescription && this.hoveredAwakening >= 0) {
            List<StrinovaGrowthState.Awakening> awakenings = StrinovaGrowthState.awakenings();
            if (this.hoveredAwakening < awakenings.size()) {
                this.renderAwakeningTooltip(graphics, awakenings.get(this.hoveredAwakening), mouseX, mouseY);
            }
        }
    }

    /** 成长项提示框：第一行完整名称，第二行价格（蓝色菱形）或“已激活”。 */
    private void renderItemTooltip(GuiGraphics graphics, StrinovaGrowthState.UpgradeItem item,
                                   int mouseX, int mouseY) {
        Component name = Component.translatable(item.nameKey());
        String price = String.valueOf(item.cost());
        Component status = Component.translatable("config.strinova.growth.activated");
        int secondWidth = item.purchased()
                ? this.font.width(status)
                : this.font.width(price) + 13;
        int width = Math.max(this.font.width(name), secondWidth) + 14;
        int height = LINE_STEP * 2 + 8;
        int x = clamp(mouseX + 10, 4, this.width - width - 4);
        int y = clamp(mouseY + 10, 4, this.height - height - 4);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font, name, x + 7, y + 4, COLOR_TEXT, false);
        int secondY = y + 4 + LINE_STEP;
        if (item.purchased()) {
            graphics.drawString(this.font, status, x + 7, secondY, COLOR_SUCCESS, false);
        } else {
            graphics.drawString(this.font, price, x + 7, secondY,
                    item.cost() <= StrinovaGrowthState.energyPoints() ? COLOR_DIAMOND : COLOR_TEXT_DISABLED, false);
            drawDiamond(graphics, x + 7 + this.font.width(price) + 7, secondY + 4, 3, COLOR_DIAMOND);
        }
    }

    /** 觉醒提示框：名称 + 自动换行描述（小窗口隐藏卡片描述时补全）。 */
    private void renderAwakeningTooltip(GuiGraphics graphics, StrinovaGrowthState.Awakening awakening,
                                        int mouseX, int mouseY) {
        Component name = Component.translatable(awakening.nameKey());
        List<FormattedCharSequence> lines = this.font.split(
                Component.translatable(awakening.descKey()), 190);
        int textWidth = this.font.width(name);
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }
        int width = textWidth + 14;
        int height = LINE_STEP * (1 + lines.size()) + 8;
        int x = clamp(mouseX + 10, 4, this.width - width - 4);
        int y = clamp(mouseY + 10, 4, this.height - height - 4);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font, name, x + 7, y + 4,
                awakening.active() ? COLOR_GOLD : COLOR_TEXT, false);
        int lineY = y + 4 + LINE_STEP;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFB4C0CC, false);
            lineY += LINE_STEP;
        }
    }

    // ==================== 通用绘制工具 ====================

    /** 自动换行绘制文本，最多绘制 maxLines 行。 */
    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y,
                             int maxWidth, int maxLines, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, maxWidth);
        int lineY = y;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            graphics.drawString(this.font, lines.get(i), x, lineY, color, false);
            lineY += LINE_STEP;
        }
    }

    /** 程序化绘制蓝色菱形（能量点符号），不依赖字体是否含 ◆ 字符。 */
    private static void drawDiamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    /** 程序化绘制对勾（已购买标记），不依赖字体是否含 ✓ 字符。 */
    private static void drawCheck(GuiGraphics graphics, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + i, y + 4 + i, x + i + 1, y + 5 + i, color);
        }
        for (int i = 0; i < 4; i++) {
            graphics.fill(x + 3 + i, y + 6 - i, x + 4 + i, y + 7 - i, color);
        }
    }

    /** 文本超宽时截断并追加“..”。 */
    private String truncate(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "..";
        int keepWidth = Math.max(0, maxWidth - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, keepWidth) + ellipsis;
    }

    /** 将数值限制在 [min, max] 区间内。 */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 生命周期 ====================

    /** 关闭界面（ESC / ✕）：成长数据保存在客户端内存（StrinovaGrowthState 静态状态），返回父界面。 */
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** 成长界面不暂停游戏。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
