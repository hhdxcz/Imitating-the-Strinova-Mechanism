package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;
import com.hhdxcz.strinova.client.hud.StrinovaGrowthState;
import com.hhdxcz.strinova.client.hud.StrinovaGrowthUi;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudState;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import com.hhdxcz.strinova.client.hud.StrinovaWikiData;
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
            0xFF35D6E8, 0xFFFFD166, 0xFF54E0A0, 0xFF4FA3FF, 0xFF9B6BFF, 0xFFFF9F43, 0xFFFF4FA3, 0xFF6ED0FF
    };

    /** 每列最多摆 4 个成长分支（8 分支 → 左列 4 武器网络、右列 4 技能/防御网络）。 */
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

    /** 顶部标题栏是否绘制（自定义显隐；隐藏时内容上移，但右上角按钮仍保留）。 */
    private boolean showHeaderBar;
    private boolean showPortrait;
    /** 右侧武器栏是否绘制（自定义显隐；隐藏时成长网格延伸到右边界）。 */
    private boolean showWeapons;
    /** 底部觉醒区是否绘制（自定义显隐；隐藏时成长网格延伸到下边界）。 */
    private boolean showAwakenings;
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
    private Button customizeButton;
    private int hoveredCategory = -1;
    private int hoveredItem = -1;
    private int hoveredAwakening = -1;

    public StrinovaGrowthScreen(Screen parent) {
        super(Component.translatable("config.strinova.growth.title"));
        this.parent = parent;
    }

    /** 初始化界面：解析当前角色、计算自适应布局并添加右上角「自定义」与关闭按钮。 */
    @Override
    protected void init() {
        this.clearWidgets();
        this.agent = resolveAgent();
        StrinovaGrowthState.selectAgent(this.agent == null ? "" : this.agent.id());
        this.computeLayout();
        this.hoveredCategory = -1;
        this.hoveredItem = -1;
        this.hoveredAwakening = -1;
        this.closeButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.onClose())
                .bounds(this.closeButtonX(), this.closeButtonY(), this.closeButtonSize, this.closeButtonSize)
                .build());
        this.addCustomizeButton();
    }

    /** 右上角「自定义」按钮：放在关闭按钮左侧，打开「弦能增幅网络」界面可视化编辑器。 */
    private void addCustomizeButton() {
        Component label = Component.translatable("config.strinova.ui.customize");
        int right = this.closeButtonX() - 4;
        int maxWidth = Math.max(20, right - this.innerLeft);
        int width = Math.min(maxWidth, this.font.width(label) + 12);
        this.customizeButton = this.addRenderableWidget(Button.builder(label, button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new StrinovaGrowthEditScreen(this));
            }
        }).bounds(right - width, this.closeButtonY(), width, this.closeButtonSize).build());
    }

    /** 从客户端配置读取当前选角，无效或未选择时回退到默认角色。 */
    private static StrinovaAgentRoster.Agent resolveAgent() {
        StrinovaAgentRoster.Agent selected = StrinovaAgentRoster.byId(StrinovaHudConfig.get().selectedAgentId);
        return selected != null ? selected : StrinovaAgentRoster.defaultAgent();
    }

    /**
     * 计算自适应布局：读取 {@code growthUi} 的显隐 / 比例 / 间距配置，
     * 先切出顶部栏与底部觉醒区，再按比例切分左立绘、中成长网格、右武器栏；
     * 任一区域被隐藏时其余区域自动延伸，小窗口下逐级隐藏次要区域并收窄行距，保证不越界。
     */
    private void computeLayout() {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        int margin = clamp(this.width / 96, 4, 12);
        this.gap = this.height >= 300 ? 8 : 6;
        this.innerLeft = margin;
        this.innerRight = Math.max(this.innerLeft + 1, this.width - margin);

        this.headerTop = margin;
        this.closeButtonSize = this.height >= 300 ? 20 : 18;
        this.showHeaderBar = ui.showHeader && this.width >= 320;
        this.headerHeight = this.showHeaderBar ? (this.height >= 300 ? 28 : 22) : 0;
        // 右上角按钮始终保留，内容区从按钮条下方开始（隐藏标题栏时内容上移）
        int controlHeight = Math.max(this.headerHeight, this.closeButtonSize);
        this.contentTop = this.headerTop + controlHeight + this.gap;

        // 底部觉醒区：高度 = 窗口高度 × awakenHeightRatio；空间不足时自动收窄，给网格留至少 4 行
        int gridMinHeight = CATEGORY_ROWS * 18 + (CATEGORY_ROWS - 1) * 2;
        int awakenMax = Math.max(34,
                this.height - this.contentTop - margin - gridMinHeight - this.gap * 2);
        this.showAwakenings = ui.showAwakenings && this.width >= 260 && this.height >= 220;
        if (this.showAwakenings) {
            this.awakenHeight = clamp(Math.round(this.height * ui.awakenHeightRatio),
                    34, Math.max(34, Math.min(awakenMax, this.height / 2)));
            this.showAwakenDescription = this.awakenHeight >= 56 && this.width >= 520;
        } else {
            this.awakenHeight = 0;
            this.showAwakenDescription = false;
        }
        this.awakenTop = this.showAwakenings
                ? this.height - margin - this.awakenHeight
                : this.height - margin;
        this.contentBottom = Math.max(this.contentTop + 8,
                this.showAwakenings ? this.awakenTop - this.gap : this.height - margin);

        // 左立绘 / 右武器栏：宽度 = 屏宽 × 配置比例；窗口过小时自动隐藏，把宽度让给成长网格
        int available = this.innerRight - this.innerLeft;
        int portraitRaw = Math.round(this.width * ui.portraitWidthRatio);
        int weaponsRaw = Math.round(this.width * ui.weaponsWidthRatio);
        this.showPortrait = ui.showPortrait && this.width >= 480 && this.height >= 240
                && portraitRaw >= 56;
        this.portraitWidth = this.showPortrait
                ? clamp(portraitRaw, 56, Math.max(56, available / 3))
                : 0;
        this.portraitX = this.innerLeft;
        this.portraitY = this.contentTop;
        this.portraitHeight = Math.max(0, this.contentBottom - this.contentTop);

        this.showWeapons = ui.showWeapons && this.width >= 400 && weaponsRaw >= 36;
        this.weaponsWidth = this.showWeapons
                ? clamp(weaponsRaw, 36, Math.max(36, available / 3))
                : 0;
        // 武器栏较窄时只显示图标 + 数字键，隐藏名称/槽位类型（留出图标空间）
        this.compactWeapons = !this.showWeapons || this.weaponsWidth < 64;
        this.weaponsX = this.showWeapons ? this.innerRight - this.weaponsWidth : this.innerRight;
        this.weaponsY = this.contentTop;

        // 中间成长网格：两列 × 4 行，左右边界按显隐自适应
        this.categoriesLeft = this.innerLeft + (this.showPortrait ? this.portraitWidth + this.gap : 0);
        this.categoriesTop = this.contentTop;
        this.categoriesRight = this.showWeapons ? this.weaponsX - this.gap : this.innerRight;
        this.categoriesBottom = this.contentBottom;

        // 极端窄窗口保护：网格过窄时先隐藏立绘、再隐藏武器栏，保证两列可读
        int minGridWidth = 2 * 48 + 6;
        if (this.categoriesRight - this.categoriesLeft < minGridWidth) {
            if (this.showPortrait) {
                this.showPortrait = false;
                this.portraitWidth = 0;
                this.categoriesLeft = this.innerLeft;
            }
            if (this.categoriesRight - this.categoriesLeft < minGridWidth && this.showWeapons) {
                this.showWeapons = false;
                this.weaponsWidth = 0;
                this.weaponsX = this.innerRight;
                this.categoriesRight = this.innerRight;
                this.compactWeapons = true;
            }
        }

        // 列 / 行间距读取自定义配置；空间不足时行间距自动收窄，避免 4 行越界
        this.categoryColumnGap = clamp(Math.round(ui.columnGap), 0, 32);
        int rowGapWanted = clamp(Math.round(ui.rowGap), 0, 32);
        int gridAvail = Math.max(8, this.categoriesBottom - this.categoriesTop);
        int rowGapEff = rowGapWanted;
        int blockHeight = (gridAvail - rowGapEff * (CATEGORY_ROWS - 1)) / CATEGORY_ROWS;
        if (blockHeight < 30 && rowGapEff > 2) {
            // 小窗口/大觉醒区时行距自适应收窄，优先保证 4 行完整可见
            rowGapEff = Math.min(rowGapEff, 2);
            blockHeight = (gridAvail - rowGapEff * (CATEGORY_ROWS - 1)) / CATEGORY_ROWS;
        }
        this.categoryRowGap = rowGapEff;
        this.categoryColumnWidth = Math.max(40,
                (this.categoriesRight - this.categoriesLeft - this.categoryColumnGap) / 2);
        this.categoryBlockHeight = clamp(blockHeight, 16, 120);
        this.itemRowGap = 2;
        // 行高下限压到 1：极小窗口下条目条也随之收缩，保证不撑出类别块
        this.itemRowHeight = clamp(
                (this.categoryBlockHeight - ITEM_HEADER_HEIGHT - this.itemRowGap) / 2, 1, 36);
        int gridHeight = this.categoryBlockHeight * CATEGORY_ROWS
                + this.categoryRowGap * (CATEGORY_ROWS - 1);
        this.categoriesGridTop = this.categoriesTop
                + Math.max(0, (this.categoriesBottom - this.categoriesTop - gridHeight) / 2);
    }

    private int closeButtonX() {
        return this.innerRight - this.closeButtonSize - 2;
    }

    private int closeButtonY() {
        int controlHeight = Math.max(this.headerHeight, this.closeButtonSize);
        return this.headerTop + (controlHeight - this.closeButtonSize) / 2;
    }

    /** 渲染界面：深色底（可叠加自定义压暗）→ 顶部栏 → 左立绘 → 中成长网格 → 右武器栏 → 底部觉醒 → 控件/提示。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        this.renderBackground(graphics);
        graphics.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);
        int dimAlpha = (int) (ui.backgroundDim * 255.0F);
        if (dimAlpha > 0) {
            graphics.fill(0, 0, this.width, this.height, (Math.min(255, dimAlpha) << 24));
        }
        if (this.showHeaderBar) {
            this.renderTopBar(graphics);
        }
        if (this.showPortrait) {
            this.renderPortrait(graphics);
        }
        this.renderCategories(graphics, mouseX, mouseY);
        if (this.showWeapons) {
            this.renderWeapons(graphics);
        }
        if (this.showAwakenings) {
            this.renderAwakenings(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderCloseGlyph(graphics, mouseX, mouseY);
        this.renderHoverTooltips(graphics, mouseX, mouseY);
    }

    // ==================== 顶部栏 ====================

    /** 渲染顶部栏：标题·角色名、中央能量点、右侧准备阶段倒计时与底部装饰线。 */
    private void renderTopBar(GuiGraphics graphics) {
        int headerBottom = this.headerTop + this.headerHeight;
        graphics.fill(this.innerLeft, this.headerTop, this.innerRight, headerBottom, this.cardColor(COLOR_HEADER_BG));
        int midX = this.width / 2;
        graphics.fill(this.innerLeft, headerBottom - 1, midX, headerBottom, 0x8035D6E8);
        graphics.fill(midX, headerBottom - 1, this.innerRight, headerBottom, 0x60FF4FA3);
        graphics.fill(this.innerLeft, this.headerTop, this.innerLeft + 3, headerBottom, COLOR_CYAN);

        int textY = this.headerTop + (this.headerHeight - 9) / 2;

        // 中央能量点（价格购买后实时刷新），左侧蓝色宝石与游戏一致
        Component points = Component.translatable("config.strinova.growth.points",
                StrinovaGrowthState.energyPoints());
        int pointsWidth = this.font.width(points);
        graphics.drawCenteredString(this.font, points, this.width / 2, textY, COLOR_GOLD);
        drawDiamond(graphics, this.width / 2 - pointsWidth / 2 - 9, textY + 4, 4, COLOR_DIAMOND);

        // 左侧标题 + 角色名（空间不足时截断角色名部分）
        Component title = titleLine(this.agent);
        int titleX = this.innerLeft + 10;
        int titleMax = Math.max(24, this.width / 2 - this.font.width(points) / 2 - 10 - titleX);
        graphics.drawString(this.font, Component.literal(truncate(title.getString(), titleMax)),
                titleX, textY, COLOR_TEXT, false);

        // 右侧准备阶段 + 倒计时（右对齐到「自定义」按钮左侧，空间不足时截断）
        Component ready = Component.translatable("config.strinova.growth.ready_phase")
                .append(" " + StrinovaMatchState.formattedTime());
        int controlLeft = this.customizeButton != null ? this.customizeButton.getX() : this.closeButtonX();
        int readyRight = controlLeft - 8;
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

    /** 渲染角色立绘占位：渐变底 + 定位色/自定义强调色装饰 + 名字首字大号字 + 角色名 + 定位标签 +（可选）数值/身份。 */
    private void renderPortrait(GuiGraphics graphics) {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        int accent = this.accentColor();
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
        int scale = clamp(this.portraitWidth / 22, 2, 9);
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(this.font, initialOf(this.agent),
                centerX / scale, charCenterY / scale, accent & 0x80FFFFFF);
        graphics.pose().popPose();

        // 底部：角色名 + 定位标签（立绘太窄时只画名字，避免文字越界）
        int nameY = Math.max(this.portraitY + 14, this.portraitY + this.portraitHeight - 40);
        boolean roomForText = this.portraitWidth >= 44 && this.portraitHeight >= 56;
        Component name = this.agent == null ? Component.literal("?") : Component.literal(this.agent.name());
        if (roomForText) {
            graphics.drawCenteredString(this.font, truncate(name.getString(), this.portraitWidth - 4),
                    centerX, nameY, COLOR_TEXT);
        }
        if (this.agent != null && roomForText && this.portraitWidth >= 60) {
            Component role = Component.translatable(StrinovaAgentRoster.roleNameKey(this.agent.role()));
            int roleWidth = this.font.width(role) + 14;
            int roleX = centerX - roleWidth / 2;
            int roleY = nameY + 15;
            graphics.fill(roleX, roleY, roleX + roleWidth, roleY + 14, (accent & 0x00FFFFFF) | 0x33000000);
            graphics.fill(roleX, roleY, roleX + roleWidth, roleY + 1, accent);
            graphics.fill(roleX, roleY + 13, roleX + roleWidth, roleY + 14, accent);
            graphics.drawCenteredString(this.font, role, centerX, roleY + 3, accent);
        }
        // 数值 / 身份：受自定义显隐控制；立绘太小时不绘制避免越界
        StrinovaWikiData.Agent wiki = this.wikiAgent();
        boolean roomForExtra = this.portraitWidth >= 72 && this.portraitHeight >= 96;
        if (wiki != null && roomForExtra) {
            if (ui.showStats && wiki.stats() != null && wiki.stats().hp() > 0) {
                String stats = Component.translatable("config.strinova.growth.stat.hp").getString() + " "
                        + wiki.stats().hp() + "   "
                        + Component.translatable("config.strinova.growth.stat.armor").getString() + " "
                        + wiki.stats().armor() + "   "
                        + Component.translatable("config.strinova.growth.stat.speed").getString() + " "
                        + wiki.stats().moveSpeed();
                graphics.drawCenteredString(this.font, truncate(stats, this.portraitWidth - 8),
                        centerX, nameY - 26, COLOR_TEXT_DISABLED);
            }
            if (ui.showIdentity && wiki.identity() != null && !wiki.identity().isEmpty()) {
                graphics.drawCenteredString(this.font, truncate(wiki.identity(), this.portraitWidth - 10),
                        centerX, nameY - 14, COLOR_TEXT_DIM);
            }
        }
    }

    /** 当前角色的 wiki 数据（缺数据时为 null）。 */
    private StrinovaWikiData.Agent wikiAgent() {
        return this.agent == null ? null : StrinovaWikiData.byId(this.agent.id());
    }

    /** 取角色名的第一个字符（处理代理对），空名时返回问号。 */
    private static String initialOf(StrinovaAgentRoster.Agent agent) {
        if (agent == null || agent.name().isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(agent.name().codePointAt(0)));
    }

    // ==================== 中间成长类别网格 ====================

    /** 渲染 8 个成长分支（4 武器 + 主动/被动/护甲/弦化）：两列排布，每类 1~2 个升级项；同时更新鼠标悬停的项。 */
    private void renderCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        this.hoveredCategory = -1;
        this.hoveredItem = -1;
        List<StrinovaGrowthState.Category> categories = StrinovaGrowthState.categories();
        if (categories.isEmpty()) {
            String hint = Component.translatable("config.strinova.growth.no_data").getString();
            graphics.drawCenteredString(this.font, hint,
                    (this.categoriesLeft + this.categoriesRight) / 2,
                    (this.categoriesTop + this.categoriesBottom) / 2 - 4, COLOR_TEXT_DISABLED);
            return;
        }
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
        drawBranchIcon(graphics, category.kind(), category.slot(), blockX, titleY - 1, 12, accent);
        graphics.drawString(this.font, truncate(category.label(), this.categoryColumnWidth - 20),
                blockX + 16, titleY, COLOR_TEXT, false);

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
        graphics.fill(x, y, x + width, y + height, this.cardColor(background));
        if (purchased || (hovered && affordable)) {
            graphics.fill(x, y, x + 2, y + height, purchased ? COLOR_CYAN : 0x80FFFFFF);
        }
        if (height < 9) {
            // 极小窗口下条目高度不足以放文字时只保留色条，避免文字越界
            return;
        }

        String statusText = Component.translatable("config.strinova.growth.upgraded").getString();
        int statusWidth = purchased
                ? this.font.width(statusText) + 6
                : this.font.width(String.valueOf(item.cost())) + 13;
        int nameWidth = Math.max(16, width - 12 - statusWidth);
        int textY = y + Math.max(1, (height - 9) / 2);
        int nameColor = (purchased || affordable) ? this.itemTextColor() : COLOR_TEXT_DISABLED;
        graphics.drawString(this.font,
                truncate(item.text() == null ? "" : item.text(), nameWidth),
                x + 6, textY, nameColor, false);

        if (purchased) {
            int textRight = x + width - 6;
            graphics.drawString(this.font, statusText,
                    textRight - this.font.width(statusText), textY, 0xFFB4C0CC, false);
        } else {
            int diamondX = x + width - 8;
            drawDiamond(graphics, diamondX, textY + 4, 4, COLOR_DIAMOND);
            String price = String.valueOf(item.cost());
            graphics.drawString(this.font, price,
                    diamondX - 8 - this.font.width(price), textY,
                    affordable ? this.priceTextColor() : COLOR_TEXT_DISABLED, false);
        }
    }

    // ==================== 右侧武器栏 ====================

    /** 渲染右侧 5 个武器槽：几何图标 + 名称（小窗口隐藏）+ 数字键 1~5，当前槽高亮。 */
    private void renderWeapons(GuiGraphics graphics) {
        int panelLeft = this.weaponsX - 4;
        graphics.fill(panelLeft, this.weaponsY, this.innerRight, this.contentBottom, this.cardColor(0x88101824));
        graphics.fill(panelLeft, this.weaponsY, panelLeft + 1, this.contentBottom, COLOR_BORDER);

        List<StrinovaHudState.WeaponSlot> slots = StrinovaHudState.getWeaponSlots();
        int panelWidth = this.innerRight - this.weaponsX;
        int slotGap = 3;
        int available = this.contentBottom - this.weaponsY - 8 - (WEAPON_SLOT_COUNT - 1) * slotGap;
        int slotHeight = clamp(available / WEAPON_SLOT_COUNT, 18, 48);
        int totalHeight = WEAPON_SLOT_COUNT * slotHeight + (WEAPON_SLOT_COUNT - 1) * slotGap;
        int startY = this.weaponsY + 4
                + Math.max(0, (this.contentBottom - this.weaponsY - 8 - totalHeight) / 2);
        String hint = Component.translatable("config.strinova.growth.weapon_hint").getString();
        for (int i = 0; i < WEAPON_SLOT_COUNT; i++) {
            int slotY = startY + i * (slotHeight + slotGap);
            StrinovaHudState.WeaponSlot slot = i < slots.size() ? slots.get(i) : null;
            boolean selected = slot != null && slot.selected();
            String name = slot == null || slot.name() == null ? "" : slot.name();
            if (name.isEmpty() && i == 0) {
                StrinovaWikiData.Agent wiki = this.wikiAgent();
                if (wiki != null && wiki.weapon() != null) {
                    name = wiki.weapon().name();
                }
            }
            int slotRight = this.innerRight - 4;
            graphics.fill(this.weaponsX, slotY, slotRight, slotY + slotHeight,
                    selected ? 0x66203548 : 0x2A0B121A);
            graphics.fill(this.weaponsX, slotY, slotRight, slotY + 1, selected ? COLOR_CYAN : 0x22FFFFFF);
            if (selected) {
                graphics.fill(this.weaponsX, slotY + slotHeight - 1, slotRight, slotY + slotHeight, COLOR_CYAN);
                graphics.fill(this.weaponsX, slotY, this.weaponsX + 1, slotY + slotHeight, COLOR_CYAN);
                graphics.fill(slotRight - 1, slotY, slotRight, slotY + slotHeight, COLOR_CYAN);
            }

            // 顶部：序号 + 槽位类型（对齐真实 UI 的「1 主武器」）
            String slotLabel = Component.translatable(WEAPON_SLOT_KEYS[i]).getString();
            graphics.drawString(this.font, String.valueOf(i + 1), this.weaponsX + 4, slotY + 3,
                    selected ? COLOR_CYAN : COLOR_TEXT_DIM, false);
            graphics.drawString(this.font, truncate(slotLabel, Math.max(10, panelWidth - 20)),
                    this.weaponsX + 14, slotY + 3, COLOR_TEXT_DISABLED, false);

            // 居中武器图标
            int iconSize = Math.max(8, Math.min(panelWidth - 26, slotHeight - 18));
            int iconX = this.weaponsX + (panelWidth - iconSize) / 2;
            int iconY = slotY + 12 + Math.max(0, (slotHeight - 14 - iconSize) / 2);
            drawWeaponIcon(graphics, i, iconX, iconY, iconSize,
                    selected ? COLOR_CYAN : (i == 0 ? 0xFFC8D4E0 : COLOR_TEXT_DIM));

            if (this.compactWeapons || slotHeight < 26) {
                continue;
            }
            int bottomY = slotY + slotHeight - 10;
            if (i >= 3) {
                // 战术道具槽：真实 UI 显示「现在可装备战术技能」
                graphics.drawString(this.font, truncate(hint, panelWidth - 8),
                        this.weaponsX + 4, bottomY, 0xFFFF6B6B, false);
            } else {
                boolean hasName = name != null && !name.isEmpty();
                String label = hasName
                        ? name
                        : Component.translatable("hud.strinova.weapon.none").getString();
                graphics.drawString(this.font, truncate(label, panelWidth - 8),
                        this.weaponsX + 4, bottomY,
                        hasName ? (selected ? COLOR_TEXT : COLOR_TEXT_DIM) : COLOR_TEXT_DISABLED, false);
            }
        }
    }

    /** 武器槽类型 lang key（与选角界面共用）。 */
    private static final String[] WEAPON_SLOT_KEYS = {
            "config.strinova.agent.weapon_primary",
            "config.strinova.agent.weapon_secondary",
            "config.strinova.agent.weapon_melee",
            "config.strinova.agent.weapon_gadget",
            "config.strinova.agent.weapon_gadget"
    };

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
        graphics.fill(x, this.awakenTop, x + cardWidth, this.awakenTop + this.awakenHeight,
                this.cardColor(COLOR_CARD_BG));
        graphics.fill(x, this.awakenTop, x + cardWidth, this.awakenTop + 2, accent);
        graphics.fill(x, this.awakenTop, x + 1, this.awakenTop + this.awakenHeight,
                active ? 0x8035D6E8 : 0x30FFFFFF);
        if (mouseX >= x && mouseX < x + cardWidth
                && mouseY >= this.awakenTop && mouseY < this.awakenTop + this.awakenHeight) {
            this.hoveredAwakening = index;
        }
        if (this.showAwakenDescription) {
            drawAwakeningNeeds(graphics, awakening, x + cardWidth - 5, this.awakenTop + 4);
        }

        Component label = Component.translatable("config.strinova.growth.awakening")
                .append(" " + (index + 1));
        Component name = Component.literal(awakening.name() == null ? "" : awakening.name());
        Component status = Component.translatable(active
                ? "config.strinova.growth.activated"
                : "config.strinova.growth.not_activated");
        int nameColor = this.awakenNameColor(active);
        if (!this.showAwakenDescription && this.awakenHeight < 20) {
            // 觉醒区被压到极矮时只保留图标与色条，避免文字越界
            int iconSize = clamp(this.awakenHeight - 4, 8, 12);
            drawAwakeningIcon(graphics, x + 4, this.awakenTop + (this.awakenHeight - iconSize) / 2,
                    iconSize, active);
            return;
        }

        if (this.showAwakenDescription) {
            int iconSize = 22;
            int iconX = x + 8;
            int iconY = this.awakenTop + 8;
            drawAwakeningIcon(graphics, iconX, iconY, iconSize, active);
            int textX = iconX + iconSize + 8;
            int textMax = Math.max(24, x + cardWidth - 8 - textX - 38);
            graphics.drawString(this.font, truncate(label.getString(), textMax),
                    textX, iconY, COLOR_TEXT_DIM, false);
            graphics.drawString(this.font, truncate(name.getString(), textMax),
                    textX, iconY + LINE_STEP, nameColor, false);
            graphics.drawString(this.font, truncate(status.getString(), textMax),
                    textX, iconY + LINE_STEP * 2, active ? COLOR_SUCCESS : COLOR_TEXT_DISABLED, false);
            int descTop = iconY + LINE_STEP * 3 + 2;
            int maxLines = Math.max(1,
                    (this.awakenTop + this.awakenHeight - 6 - descTop) / LINE_STEP);
            drawWrapped(graphics, Component.literal(awakening.desc() == null ? "" : awakening.desc()),
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

    /**
     * 在觉醒卡右上角绘制三色需求（对齐游戏）：蓝=武器网络方块、黄=技能网络圆点、绿=防御网络三角；
     * 每种形状重复「需求数量」次，已满足的需求点亮。
     */
    private static void drawAwakeningNeeds(GuiGraphics graphics, StrinovaGrowthState.Awakening awakening,
                                           int right, int top) {
        int[] needs = {awakening.needWeapon(), awakening.needSkill(), awakening.needDefense()};
        int[] colors = {0xFF4FA3FF, 0xFFFFD166, 0xFF54E0A0};
        int[] owned = {StrinovaGrowthState.purchasedWeapon(),
                StrinovaGrowthState.purchasedSkill(),
                StrinovaGrowthState.purchasedDefense()};
        int shape = 9;
        int gap = 3;
        int x = right;
        for (int i = 2; i >= 0; i--) {
            boolean met = needs[i] > 0 && owned[i] >= needs[i];
            int color = met ? colors[i] : 0x4456626E;
            for (int k = 0; k < needs[i]; k++) {
                x -= shape;
                switch (i) {
                    case 0 -> drawFilledSquare(graphics, x, top, shape, color);
                    case 1 -> drawFilledCircle(graphics, x, top, shape, color);
                    default -> drawFilledTriangle(graphics, x, top, shape, color);
                }
                x -= gap;
            }
        }
    }

    /** 分支类型图标（对齐游戏）：武器=蓝菱形、主动/被动=金圆、护甲=绿盾、弦化=绿三角。 */
    private static void drawBranchIcon(GuiGraphics graphics, String kind, int slot,
                                       int x, int y, int size, int fallback) {
        String type = kind == null ? "" : kind;
        int color;
        switch (type) {
            case "weapon" -> color = slot % 2 == 0 ? 0xFF5A9BFF : 0xFF4FA3FF;
            case "active", "passive" -> color = 0xFFE8B54D;
            case "armor" -> color = 0xFF57C785;
            case "string" -> color = 0xFF57C785;
            default -> color = fallback;
        }
        switch (type) {
            case "active", "passive" -> drawFilledCircle(graphics, x, y, size, color);
            case "armor" -> drawFilledShield(graphics, x, y, size, color);
            case "string" -> drawFilledTriangle(graphics, x, y, size, color);
            default -> drawFilledDiamond(graphics, x, y, size, color);
        }
    }

    /** 实心菱形。 */
    private static void drawFilledDiamond(GuiGraphics graphics, int x, int y, int size, int color) {
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = size / 2;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    /** 实心圆（按行近似）。 */
    private static void drawFilledCircle(GuiGraphics graphics, int x, int y, int size, int color) {
        int radius = size / 2;
        int centerX = x + radius;
        int centerY = y + radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    /** 实心方块。 */
    private static void drawFilledSquare(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + size, color);
    }

    /** 实心盾形（顶部平、底部收尖）。 */
    private static void drawFilledShield(GuiGraphics graphics, int x, int y, int size, int color) {
        int centerX = x + size / 2;
        int topY = y + 1;
        int bottomY = y + size - 1;
        int width = size;
        graphics.fill(centerX - width / 2, topY, centerX + width / 2, topY + 2, color);
        for (int dy = 2; dy < bottomY - topY; dy++) {
            int half = width / 2 - (dy * (width / 2)) / Math.max(1, bottomY - topY);
            graphics.fill(centerX - half, topY + dy, centerX + half, topY + dy + 1, color);
        }
    }

    /** 实心倒三角。 */
    private static void drawFilledTriangle(GuiGraphics graphics, int x, int y, int size, int color) {
        int centerX = x + size / 2;
        for (int dy = 0; dy < size; dy++) {
            int half = (size / 2) * (dy + 1) / size;
            graphics.fill(centerX - half, y + dy, centerX + half + 1, y + dy + 1, color);
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

    /** 悬停提示：成长项（含属性变化）、觉醒（含三色需求）、左侧立绘（身份/简介/语录）、武器栏（主武器信息）。 */
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
        if (this.hoveredAwakening >= 0) {
            List<StrinovaGrowthState.Awakening> awakenings = StrinovaGrowthState.awakenings();
            if (this.hoveredAwakening < awakenings.size()) {
                this.renderAwakeningTooltip(graphics, awakenings.get(this.hoveredAwakening), mouseX, mouseY);
                return;
            }
        }
        if (this.showPortrait && mouseX >= this.portraitX && mouseX < this.portraitX + this.portraitWidth
                && mouseY >= this.portraitY && mouseY < this.portraitY + this.portraitHeight) {
            this.renderAgentTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (this.showWeapons && mouseX >= this.weaponsX - 4 && mouseX < this.innerRight
                && mouseY >= this.weaponsY && mouseY < this.contentBottom) {
            StrinovaWikiData.Agent wiki = this.wikiAgent();
            if (wiki != null && wiki.weapon() != null && !isBlank(wiki.weapon().name())) {
                this.renderWeaponTooltip(graphics, wiki.weapon(), mouseX, mouseY);
            }
        }
    }

    /** 成长项提示框：名称 + 价格/已激活 + 自动换行的「属性变化」文案。 */
    private void renderItemTooltip(GuiGraphics graphics, StrinovaGrowthState.UpgradeItem item,
                                   int mouseX, int mouseY) {
        Component name = Component.literal(item.text() == null ? "" : item.text());
        Component status = Component.translatable("config.strinova.growth.activated");
        String price = String.valueOf(item.cost());
        List<FormattedCharSequence> changeLines = isBlank(item.change())
                ? List.of()
                : this.font.split(Component.literal(item.change()), 200);
        int width = Math.max(this.font.width(name),
                item.purchased() ? this.font.width(status) : this.font.width(price) + 13);
        for (FormattedCharSequence line : changeLines) {
            width = Math.max(width, this.font.width(line));
        }
        width += 14;
        int height = LINE_STEP * (2 + changeLines.size()) + 8;
        int x = clamp(mouseX + 10, 4, Math.max(4, this.width - width - 4));
        int y = clamp(mouseY + 10, 4, Math.max(4, this.height - height - 4));
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font, name, x + 7, y + 4, COLOR_TEXT, false);
        int secondY = y + 4 + LINE_STEP;
        if (item.purchased()) {
            graphics.drawString(this.font, status, x + 7, secondY, COLOR_SUCCESS, false);
        } else {
            graphics.drawString(this.font, price, x + 7, secondY,
                    item.cost() <= StrinovaGrowthState.energyPoints()
                            ? this.priceTextColor() : COLOR_TEXT_DISABLED, false);
            drawDiamond(graphics, x + 7 + this.font.width(price) + 7, secondY + 4, 3, COLOR_DIAMOND);
        }
        int lineY = secondY + LINE_STEP;
        for (FormattedCharSequence line : changeLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFB4C0CC, false);
            lineY += LINE_STEP;
        }
    }

    /** 觉醒提示框：名称 + 三色需求（含当前进度）+ 自动换行描述。 */
    private void renderAwakeningTooltip(GuiGraphics graphics, StrinovaGrowthState.Awakening awakening,
                                        int mouseX, int mouseY) {
        Component name = Component.literal(awakening.name() == null ? "" : awakening.name());
        Component need = needsLine(awakening);
        Component current = Component.translatable("config.strinova.growth.selected")
                .append(Component.literal(" " + StrinovaGrowthState.purchasedWeapon()
                        + " / " + StrinovaGrowthState.purchasedSkill()
                        + " / " + StrinovaGrowthState.purchasedDefense()));
        List<FormattedCharSequence> lines = isBlank(awakening.desc())
                ? List.of()
                : this.font.split(Component.literal(awakening.desc()), 200);
        int width = Math.max(this.font.width(name), Math.max(this.font.width(need), this.font.width(current)));
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, this.font.width(line));
        }
        width += 14;
        int height = LINE_STEP * (3 + lines.size()) + 8;
        int x = clamp(mouseX + 10, 4, Math.max(4, this.width - width - 4));
        int y = clamp(mouseY + 10, 4, Math.max(4, this.height - height - 4));
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font, name, x + 7, y + 4,
                this.awakenNameColor(awakening.active()), false);
        graphics.drawString(this.font, need, x + 7, y + 4 + LINE_STEP, COLOR_TEXT_DIM, false);
        graphics.drawString(this.font, current, x + 7, y + 4 + LINE_STEP * 2, COLOR_TEXT_DISABLED, false);
        int lineY = y + 4 + LINE_STEP * 3;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFB4C0CC, false);
            lineY += LINE_STEP;
        }
    }

    /** 立绘提示框：身份 + 角色简介 + 个性语录。 */
    private void renderAgentTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        StrinovaWikiData.Agent wiki = this.wikiAgent();
        if (wiki == null) {
            return;
        }
        List<FormattedCharSequence> intro = isBlank(wiki.intro())
                ? List.of()
                : this.font.split(Component.literal(wiki.intro()), 200);
        List<FormattedCharSequence> quote = isBlank(wiki.quote())
                ? List.of()
                : this.font.split(Component.literal("「" + wiki.quote() + "」"), 200);
        int width = 0;
        for (FormattedCharSequence line : intro) {
            width = Math.max(width, this.font.width(line));
        }
        for (FormattedCharSequence line : quote) {
            width = Math.max(width, this.font.width(line));
        }
        int headerWidth = this.font.width((wiki.identity() == null ? "" : wiki.identity()) + " " + wiki.name());
        width = Math.max(60, Math.max(width, headerWidth)) + 14;
        int height = LINE_STEP * (1 + intro.size() + quote.size()) + 8;
        int x = clamp(mouseX + 10, 4, Math.max(4, this.width - width - 4));
        int y = clamp(mouseY + 10, 4, Math.max(4, this.height - height - 4));
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font,
                (isBlank(wiki.identity()) ? "" : wiki.identity() + " · ") + wiki.name(),
                x + 7, y + 4, COLOR_TEXT, false);
        int lineY = y + 4 + LINE_STEP;
        for (FormattedCharSequence line : intro) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFB4C0CC, false);
            lineY += LINE_STEP;
        }
        for (FormattedCharSequence line : quote) {
            graphics.drawString(this.font, line, x + 7, lineY, COLOR_TEXT_DIM, false);
            lineY += LINE_STEP;
        }
    }

    /** 武器栏提示框：主武器名称 · 类型 + 自动换行介绍。 */
    private void renderWeaponTooltip(GuiGraphics graphics, StrinovaWikiData.Weapon weapon,
                                     int mouseX, int mouseY) {
        Component title = Component.literal(isBlank(weapon.name()) ? "" : weapon.name())
                .append(Component.literal(isBlank(weapon.type()) ? "" : " · " + weapon.type()));
        List<FormattedCharSequence> lines = isBlank(weapon.desc())
                ? List.of()
                : this.font.split(Component.literal(weapon.desc()), 200);
        int width = this.font.width(title);
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, this.font.width(line));
        }
        width += 14;
        int height = LINE_STEP * (1 + lines.size()) + 8;
        int x = clamp(mouseX + 10, 4, Math.max(4, this.width - width - 4));
        int y = clamp(mouseY + 10, 4, Math.max(4, this.height - height - 4));
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font, title, x + 7, y + 4, COLOR_TEXT, false);
        int lineY = y + 4 + LINE_STEP;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFB4C0CC, false);
            lineY += LINE_STEP;
        }
    }

    /** 觉醒三色需求文案：武器 / 技能 / 防御。 */
    private Component needsLine(StrinovaGrowthState.Awakening awakening) {
        return Component.translatable("config.strinova.growth.need")
                .append(Component.literal(" "))
                .append(Component.translatable("config.strinova.growth.need.weapon"))
                .append(Component.literal(" " + awakening.needWeapon()))
                .append(Component.literal(" · "))
                .append(Component.translatable("config.strinova.growth.need.skill"))
                .append(Component.literal(" " + awakening.needSkill()))
                .append(Component.literal(" · "))
                .append(Component.translatable("config.strinova.growth.need.defense"))
                .append(Component.literal(" " + awakening.needDefense()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
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

    /** 按配置不透明度缩放 ARGB 颜色的 alpha 通道（卡片 / 面板 / 条目底色用）。 */
    private int cardColor(int color) {
        float opacity = clampf(StrinovaHudConfig.get().growthUi.opacity, 0.20F, 1.0F);
        int alpha = Math.round(((color >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** 角色定位强调色：自定义 accentColor 非 0 时覆盖，否则用角色定位色。 */
    private int accentColor() {
        int override = StrinovaHudConfig.get().growthUi.accentColor;
        if (override != 0) {
            return override;
        }
        return this.agent == null ? COLOR_CYAN : this.agent.accentColor();
    }

    /** 升级项文字色：自定义 itemTextColor 非 0 时覆盖默认文字色。 */
    private int itemTextColor() {
        int override = StrinovaHudConfig.get().growthUi.itemTextColor;
        return override != 0 ? override : COLOR_TEXT;
    }

    /** 价格文字色：自定义 priceColor 非 0 时覆盖默认价格色。 */
    private int priceTextColor() {
        int override = StrinovaHudConfig.get().growthUi.priceColor;
        return override != 0 ? override : 0xFFFF8A7A;
    }

    /** 觉醒名称色：自定义 awakenNameColor 非 0 时覆盖（未激活时压暗，保留状态区分）。 */
    private int awakenNameColor(boolean active) {
        int override = StrinovaHudConfig.get().growthUi.awakenNameColor;
        if (override != 0) {
            return active ? override : ((override & 0x00FFFFFF) | 0xCC000000);
        }
        return active ? COLOR_GOLD : COLOR_TEXT;
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

    /** 将浮点数值限制在 [min, max] 区间内。 */
    private static float clampf(float value, float min, float max) {
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
