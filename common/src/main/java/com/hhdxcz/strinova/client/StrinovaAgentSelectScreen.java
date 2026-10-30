package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import com.hhdxcz.strinova.client.hud.StrinovaScoreboardState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 超弦体选角界面（按真实截图 P2 重排）。
 * <p>布局结构：</p>
 * <ul>
 *   <li>顶部阶段条：左「我方 进攻/防守」红条、中「选择角色 + 回合倒计时」、右「敌方 进攻/防守」蓝条；</li>
 *   <li>左侧竖列：5 名队友卡片（角色名首字头像 + 玩家名 + 小字角色名，本人金色、阵亡灰显）；</li>
 *   <li>中央：选中角色的大号立绘占位（定位色渐变 + 巨型首字 + 底部名字条）；</li>
 *   <li>右侧：定位标签 + Q/E/C 技能占位、主动技能描述、「武器选择 / 外观选择」页签、5 项武器占位面板；</li>
 *   <li>底部：2 行 × 12 列 = 24 个超弦体头像格（选中高亮 + P1 标记，已锁定角色显示绿色勾）+ 大号「锁定角色」按钮。</li>
 * </ul>
 * 全部使用 {@link GuiGraphics} 程序化绘制（无贴图）；数据来自 {@link StrinovaAgentRoster}、
 * {@link StrinovaScoreboardState}（队友）与 {@link StrinovaMatchState}（倒计时/攻防），
 * 锁定结果写入 {@link StrinovaHudConfig#selectedAgentId} 并落盘。
 */
public class StrinovaAgentSelectScreen extends Screen {

    // ---- 配色（对齐 HUD 顶部比分面板与真实 UI） ----
    /** 我方（进攻方）红色。 */
    private static final int COLOR_ALLY = 0xFFBC3B47;
    /** 敌方（防守方）蓝色。 */
    private static final int COLOR_ENEMY = 0xFF4B77DC;
    /** 本人高亮金色。 */
    private static final int COLOR_GOLD = 0xFFFFD24A;
    /** 主文字色。 */
    private static final int COLOR_TEXT = 0xFFEAF6FF;
    /** 次要文字色。 */
    private static final int COLOR_DIM = 0xFF9AA8B8;
    /** 已锁定绿勾色。 */
    private static final int COLOR_LOCKED = 0xFF5FE08C;
    /** 阵亡/空位灰。 */
    private static final int COLOR_DEAD = 0xFF6E7682;
    /** 面板底色（半透明深蓝黑）。 */
    private static final int COLOR_PANEL = 0x8C0A111C;
    /** 更深的面板底色（顶部中央条/立绘名字条）。 */
    private static final int COLOR_PANEL_DARK = 0xB30A111C;
    /** 面板描边色。 */
    private static final int COLOR_BORDER = 0x55335F73;

    // ---- 结构常量 ----
    /** 底部头像网格列数（2 行 × 12 列 = 24）。 */
    private static final int GRID_COLUMNS = 12;
    /** 底部头像网格行数。 */
    private static final int GRID_ROWS = 2;
    /** 左侧队友卡片槽位数量。 */
    private static final int TEAM_SLOTS = 5;
    /** 左侧队友列顶部标题区高度。 */
    private static final int TEAM_HEADER_H = 14;
    /** 右侧 3 个技能占位方块上的按键字母。 */
    private static final String[] SKILL_KEYS = {"Q", "E", "C"};
    /** 武器面板 5 行的 lang key（战术道具两项共用同一 key）。 */
    private static final String[] WEAPON_KEYS = {
            "config.strinova.agent.weapon_primary",
            "config.strinova.agent.weapon_secondary",
            "config.strinova.agent.weapon_melee",
            "config.strinova.agent.weapon_gadget",
            "config.strinova.agent.weapon_gadget"
    };

    private final Screen parent;
    /** 全部超弦体（顺序与图鉴一致，底部头像网格按此排列）。 */
    private final List<StrinovaAgentRoster.Agent> agents = StrinovaAgentRoster.agents();
    /** 当前选中的超弦体下标（头像网格高亮 + P1 标记 + 中央立绘/右侧面板数据源）。 */
    private int selectedIndex;
    /** 上次锁定（写入配置）的角色 id，用于在头像网格中显示绿色勾。 */
    private String lockedAgentId = "";
    /** 右侧页签状态：true=武器选择，false=外观选择。 */
    private boolean weaponTab = true;

    // ---- 布局缓存（init 时按窗口尺寸计算，render / mouseClicked 共用） ----
    private int margin;
    private int gap;
    private int barX;
    private int barY;
    private int barW;
    private int barH;
    private int mainTop;
    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;
    private int centerX;
    private int centerY;
    private int centerW;
    private int centerH;
    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int cellW;
    private int cellH;
    private int cellGap;
    /** 网格整体在区域内的居中偏移。 */
    private int cellOffsetX;
    private int lockX;
    private int lockY;
    private int lockW;
    private int lockH;
    /** 中央立绘是否显示（窗口太窄时隐藏，按任务要求小尺寸可隐藏立绘）。 */
    private boolean showPortrait;

    // ---- 右侧面板内部布局 ----
    private int rightPad;
    private int skillIconSize;
    private int roleRowY;
    private int skillRowY;
    private int qLineY;
    private int descY;
    private int descLines;
    private int tabY;
    private int tabH;
    private int tabW;
    private int weaponTabX;
    private int appearanceTabX;
    private int weaponTop;
    private int weaponRowH;

    public StrinovaAgentSelectScreen(Screen parent) {
        super(Component.translatable("config.strinova.agent.title"));
        this.parent = parent;
    }

    /** 初始化：读取当前选角/已锁定角色，计算自适应布局并添加「锁定角色」按钮。 */
    @Override
    protected void init() {
        this.clearWidgets();
        StrinovaHudConfig config = StrinovaHudConfig.get();
        // 当前选中：优先读取配置中的已锁定角色，无效/为空时回退到默认角色
        StrinovaAgentRoster.Agent current = StrinovaAgentRoster.byId(config.selectedAgentId);
        if (current == null) {
            current = StrinovaAgentRoster.defaultAgent();
        }
        this.selectedIndex = current == null ? 0 : Math.max(0, this.agents.indexOf(current));
        this.lockedAgentId = config.selectedAgentId == null ? "" : config.selectedAgentId;

        this.computeLayout();
        this.layoutRightPanel();

        this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.agent.lock"),
                        button -> this.lockSelection())
                .bounds(this.lockX, this.lockY, this.lockW, this.lockH)
                .build());
    }

    /**
     * 计算整页自适应布局：
     * 顶部阶段条占满宽度；主区域分「左队友列 / 中央立绘 / 右侧信息面板」三栏；
     * 底部为 2×12 头像网格 + 右下方大号锁定按钮（与右侧面板同列对齐）。
     * 所有尺寸均带上下限，320×240 ~ 1920×1080 均不越界。
     */
    private void computeLayout() {
        this.margin = clamp(4, 8, this.width / 80);
        this.gap = this.margin >= 6 ? 8 : 6;
        this.barH = clamp(20, 30, Math.round(this.height * 0.075F));
        this.barX = this.margin;
        this.barY = this.margin;
        this.barW = this.width - 2 * this.margin;
        this.mainTop = this.barY + this.barH + this.gap;

        // 右栏（信息面板 + 锁定按钮）与左栏（队友列）宽度按比例分配并设上下限
        int rightWidth = clamp(120, 240, Math.round(this.width * 0.29F));
        int leftWidth = clamp(88, 150, Math.round(this.width * 0.19F));
        int contentWidth = this.width - 2 * this.margin;
        int gridRegionW = contentWidth - rightWidth - this.gap;

        // 底部 2×12 头像网格：格子近似正方形，宽高都有上下限
        this.cellGap = clamp(1, 3, gridRegionW / 200);
        int cellWidth = (gridRegionW - (GRID_COLUMNS - 1) * this.cellGap) / GRID_COLUMNS;
        this.cellW = clamp(12, 64, cellWidth);
        this.cellH = clamp(16, 30, this.cellW);
        this.gridH = GRID_ROWS * this.cellH + (GRID_ROWS - 1) * this.cellGap;
        this.gridY = this.height - this.margin - this.gridH;
        this.gridX = this.margin;
        this.gridW = gridRegionW;
        int realGridW = GRID_COLUMNS * this.cellW + (GRID_COLUMNS - 1) * this.cellGap;
        this.cellOffsetX = this.gridX + Math.max(0, (this.gridW - realGridW) / 2);

        int mainHeight = Math.max(40, this.gridY - this.gap - this.mainTop);
        this.leftW = leftWidth;
        this.leftX = this.margin;
        this.leftY = this.mainTop;
        this.leftH = mainHeight;
        this.rightW = rightWidth;
        this.rightX = this.margin + gridRegionW + this.gap;
        this.rightY = this.mainTop;
        this.rightH = mainHeight;
        this.centerX = this.leftX + this.leftW + this.gap;
        this.centerY = this.mainTop;
        this.centerW = Math.max(40, gridRegionW - this.leftW - this.gap);
        this.centerH = mainHeight;
        this.lockX = this.rightX;
        this.lockY = this.gridY;
        this.lockW = this.rightW;
        this.lockH = this.gridH;
        // 中央立绘：窗口太窄（≤84px）时隐藏，避免巨型字挤在窄条里
        this.showPortrait = this.centerW >= 84;
    }

    /** 计算右侧信息面板内部各区块的位置：内容整体在面板内垂直居中，小尺寸自动压缩描述行数与行高。 */
    private void layoutRightPanel() {
        this.rightPad = clamp(4, 8, this.rightW / 20);
        this.skillIconSize = this.rightH < 200 ? 16 : this.rightH < 320 ? 20 : 24;
        this.descLines = this.rightH < 200 ? 1 : this.rightH < 320 ? 2 : 3;
        this.weaponRowH = this.rightH < 200 ? 13 : this.rightH < 320 ? 16 : 20;
        this.tabH = clamp(14, 20, this.rightH / 12);
        this.tabW = Math.max(20, (this.rightW - 2 * this.rightPad - 4) / 2);

        int contentH = 16
                + (this.skillIconSize + 4)
                + 12
                + (this.descLines * 11 + 6)
                + (this.tabH + 6)
                + 5 * this.weaponRowH;
        // 极端窄高比时继续压缩，保证不压出面板
        while (contentH > this.rightH - 2 * this.rightPad && this.weaponRowH > 10) {
            this.weaponRowH--;
            contentH -= 5;
        }
        while (contentH > this.rightH - 2 * this.rightPad && this.descLines > 0) {
            this.descLines--;
            contentH -= 11;
        }

        int contentTop = this.rightY + Math.max(this.rightPad, (this.rightH - contentH) / 2);
        int y = contentTop;
        this.roleRowY = y;
        y += 16;
        this.skillRowY = y;
        y += this.skillIconSize + 4;
        this.qLineY = y;
        y += 12;
        this.descY = y;
        y += this.descLines * 11 + 6;
        this.tabY = y;
        this.weaponTabX = this.rightX + this.rightPad;
        this.appearanceTabX = this.weaponTabX + this.tabW + 4;
        y += this.tabH + 6;
        this.weaponTop = y;
    }

    // ------------------------------------------------------------------
    // 数据查询与小工具
    // ------------------------------------------------------------------

    /** 当前选中的超弦体；图鉴为空时返回 null（正常数据下不会发生）。 */
    private StrinovaAgentRoster.Agent selectedAgent() {
        if (this.agents.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(this.selectedIndex, this.agents.size() - 1));
        return this.agents.get(index);
    }

    /** 按角色显示名反查图鉴强调色（队友卡片/头像用），查不到时返回 fallback。 */
    private static int agentAccent(String agentName, int fallback) {
        if (agentName == null || agentName.isEmpty()) {
            return fallback;
        }
        for (StrinovaAgentRoster.Agent agent : StrinovaAgentRoster.agents()) {
            if (agent.name().equals(agentName)) {
                return agent.accentColor();
            }
        }
        return fallback;
    }

    /** 取字符串首字（头像方块文字）；空串返回 "?"。CJK 名字均为 BMP 字符，substring(0,1) 安全。 */
    private static String firstChar(String text) {
        if (text == null || text.isEmpty()) {
            return "?";
        }
        return text.substring(0, 1);
    }

    /** 文本超宽时截断并追加 ".."。 */
    private String truncate(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "..";
        int keep = Math.max(0, maxWidth - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, keep) + ellipsis;
    }

    /** 给颜色替换 alpha 通道（保留 RGB）。 */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((clamp(0, 255, alpha) & 0xFF) << 24);
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    /** 鼠标点击：先交给按钮控件，再处理右侧页签与底部头像网格。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        // 右侧页签：武器选择 / 外观选择（外观仅占位，点击只切换页签）
        if (this.isInside((int) mouseX, (int) mouseY, this.weaponTabX, this.tabY, this.tabW, this.tabH)) {
            this.weaponTab = true;
            return true;
        }
        if (this.isInside((int) mouseX, (int) mouseY, this.appearanceTabX, this.tabY, this.tabW, this.tabH)) {
            this.weaponTab = false;
            return true;
        }
        // 底部头像网格：点击切换选中
        int index = this.gridIndexAt((int) mouseX, (int) mouseY);
        if (index >= 0) {
            this.selectedIndex = index;
            return true;
        }
        return false;
    }

    /** 判断坐标是否落在指定矩形内。 */
    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 返回鼠标命中的头像格下标；未命中返回 -1。 */
    private int gridIndexAt(int mouseX, int mouseY) {
        for (int i = 0; i < this.agents.size(); i++) {
            int row = i / GRID_COLUMNS;
            int col = i % GRID_COLUMNS;
            if (row >= GRID_ROWS) {
                break;
            }
            int x = this.cellOffsetX + col * (this.cellW + this.cellGap);
            int y = this.gridY + row * (this.cellH + this.cellGap);
            if (this.isInside(mouseX, mouseY, x, y, this.cellW, this.cellH)) {
                return i;
            }
        }
        return -1;
    }

    /** 锁定角色：把当前选中角色 id 写入客户端配置并落盘，然后返回父界面。 */
    private void lockSelection() {
        StrinovaAgentRoster.Agent agent = this.selectedAgent();
        if (agent != null) {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            config.selectedAgentId = agent.id();
            config.save();
            this.lockedAgentId = agent.id();
        }
        this.backToParent();
    }

    /** 返回父界面（ESC/返回不保存选角）。 */
    private void backToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** ESC / 关闭：不保存直接返回父界面。 */
    @Override
    public void onClose() {
        this.backToParent();
    }

    /** 选角界面不暂停游戏。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // 渲染主流程
    // ------------------------------------------------------------------

    /** 渲染整页：背景 → 顶部阶段条 → 三栏主区域 → 底部头像网格 → 锁定按钮 → 悬停提示。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        StrinovaAgentRoster.Agent agent = this.selectedAgent();
        int accent = agent == null ? COLOR_ENEMY : agent.accentColor();
        int hoveredCell = this.gridIndexAt(mouseX, mouseY);

        this.renderTopBar(graphics);
        this.renderTeamColumn(graphics, mouseX, mouseY);
        this.renderPortrait(graphics, agent, accent);
        this.renderRightPanel(graphics, agent, accent, mouseX, mouseY);
        this.renderAgentGrid(graphics, hoveredCell);
        this.renderLockFrame(graphics, accent);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停提示：头像格显示角色全名/定位/阵营；队友卡显示玩家+角色+状态
        if (hoveredCell >= 0 && hoveredCell < this.agents.size()) {
            StrinovaAgentRoster.Agent hovered = this.agents.get(hoveredCell);
            Component tip = Component.literal(hovered.name())
                    .append(Component.literal(" · "))
                    .append(Component.translatable(StrinovaAgentRoster.roleNameKey(hovered.role())))
                    .append(Component.literal(" · "))
                    .append(Component.translatable(StrinovaAgentRoster.factionNameKey(hovered.faction())));
            this.renderHoverTooltip(graphics, tip, mouseX, mouseY);
        } else {
            StrinovaScoreboardState.Entry hoveredMate = this.teamEntryAt(mouseX, mouseY);
            if (hoveredMate != null) {
                Component tip = Component.literal(hoveredMate.name() == null ? "?" : hoveredMate.name())
                        .append(Component.literal(" · "))
                        .append(Component.literal(hoveredMate.agentName() == null ? "?" : hoveredMate.agentName()));
                if (hoveredMate.status() != StrinovaScoreboardState.Status.ALIVE) {
                    tip = tip.copy().append(Component.literal(" · "))
                            .append(Component.translatable(hoveredMate.status().labelKey()));
                }
                this.renderHoverTooltip(graphics, tip, mouseX, mouseY);
            }
        }
    }

    // ------------------------------------------------------------------
    // 顶部阶段条
    // ------------------------------------------------------------------

    /** 顶部阶段条：左右红/蓝斜切底条 + 中央「选择角色 + 大号倒计时」。 */
    private void renderTopBar(GuiGraphics graphics) {
        int sideW = clamp(70, 150, Math.round(this.barW * 0.26F));
        int slant = clamp(4, 12, this.barH / 2);
        int sideH = Math.max(12, this.barH - 4);
        int sideY = this.barY + (this.barH - sideH) / 2;

        // 中央深色底板（贯穿整条，左右被红/蓝条覆盖）
        graphics.fill(this.barX, this.barY, this.barX + this.barW, this.barY + this.barH, COLOR_PANEL_DARK);
        this.border(graphics, this.barX, this.barY, this.barW, this.barH, COLOR_BORDER);

        boolean alliesAttack = StrinovaMatchState.areAlliesAttacking();
        this.renderPhaseSlab(graphics, this.barX, sideY, sideW, sideH, slant, COLOR_ALLY, true);
        this.renderPhaseSlab(graphics, this.barX + this.barW - sideW, sideY, sideW, sideH, slant, COLOR_ENEMY, false);

        Component leftLabel = Component.translatable("hud.strinova.match.allies")
                .append(Component.literal(" "))
                .append(Component.translatable(alliesAttack
                        ? "hud.strinova.match.attack" : "hud.strinova.match.defense"));
        Component rightLabel = Component.translatable("hud.strinova.match.enemies")
                .append(Component.literal(" "))
                .append(Component.translatable(alliesAttack
                        ? "hud.strinova.match.defense" : "hud.strinova.match.attack"));
        this.drawCenteredText(graphics, this.truncate(leftLabel.getString(), sideW - 10),
                this.barX + sideW / 2, this.barY + (this.barH - 9) / 2, COLOR_TEXT, true);
        this.drawCenteredText(graphics, this.truncate(rightLabel.getString(), sideW - 10),
                this.barX + this.barW - sideW / 2, this.barY + (this.barH - 9) / 2, COLOR_TEXT, true);

        // 中央：选择角色 + 大号倒计时（宽度不够时缩小字号）
        int centerLeft = this.barX + sideW + 6;
        int centerRight = this.barX + this.barW - sideW - 6;
        int centerMid = (centerLeft + centerRight) / 2;
        Component selecting = Component.translatable("config.strinova.agent.selecting");
        String time = StrinovaMatchState.formattedTime();
        int labelW = this.font.width(selecting);
        float timeScale = this.barH >= 28 ? 1.9F : 1.5F;
        int totalW = labelW + 8 + Math.round(this.font.width(time) * timeScale);
        if (totalW > centerRight - centerLeft) {
            timeScale = 1.2F;
            totalW = labelW + 6 + Math.round(this.font.width(time) * timeScale);
        }
        int startX = Math.max(centerLeft, centerMid - totalW / 2);
        this.drawText(graphics, selecting.getString(), startX, this.barY + (this.barH - 9) / 2, COLOR_DIM);
        this.drawScaledText(graphics, time, startX + labelW + 6,
                this.barY + (this.barH - 9 * timeScale) / 2.0F, timeScale, COLOR_TEXT, true);
    }

    /** 绘制左/右斜切阵营底条：渐变矩形主体 + 内侧斜切三角 + 顶部高亮线。 */
    private void renderPhaseSlab(GuiGraphics graphics, int x, int y, int w, int h, int slant,
                                 int color, boolean alignLeft) {
        int coreW = Math.max(1, w - slant);
        int coreX = alignLeft ? x : x + slant;
        graphics.fillGradient(coreX, y, coreX + coreW, y + h,
                withAlpha(color, 0x99), withAlpha(color, 0x2E));
        graphics.fill(coreX, y, coreX + coreW, y + 1, color);
        for (int i = 0; i < slant; i++) {
            int columnX = alignLeft ? coreX + coreW + i : coreX - 1 - i;
            int top = y + (slant - i);
            graphics.fill(columnX, top, columnX + 1, y + h, withAlpha(color, 0x66));
        }
    }

    // ------------------------------------------------------------------
    // 左侧队友列
    // ------------------------------------------------------------------

    /** 左侧队友列：顶部「队友」标题 + 5 名队友卡片（本人金色、阵亡灰显，头像方块使用定位色）。 */
    private void renderTeamColumn(GuiGraphics graphics, int mouseX, int mouseY) {
        this.drawPanel(graphics, this.leftX, this.leftY, this.leftW, this.leftH);
        // 顶部标题「队友」+ 分隔线
        this.drawText(graphics, Component.translatable("config.strinova.agent.teammates").getString(),
                this.leftX + 6, this.leftY + 5, COLOR_DIM);
        graphics.fill(this.leftX + 4, this.leftY + TEAM_HEADER_H,
                this.leftX + this.leftW - 4, this.leftY + TEAM_HEADER_H + 1, COLOR_BORDER);

        List<StrinovaScoreboardState.Entry> allies = StrinovaScoreboardState.allies();
        int cardGap = 4;
        int cardH = this.teamCardHeight();
        int startY = this.teamStartY(cardH, cardGap);
        int cardX = this.leftX + 4;
        int cardW = this.leftW - 8;
        for (int i = 0; i < TEAM_SLOTS; i++) {
            StrinovaScoreboardState.Entry entry = i < allies.size() ? allies.get(i) : null;
            int y = startY + i * (cardH + cardGap);
            boolean hovered = this.isInside(mouseX, mouseY, cardX, y, cardW, cardH);
            this.renderTeamCard(graphics, cardX, y, cardW, cardH, entry, hovered);
        }
    }

    /** 队友卡高度：按左栏可用高度（扣除标题区）均分并限制在 24~64 之间。 */
    private int teamCardHeight() {
        int areaH = this.leftH - 8 - TEAM_HEADER_H;
        return clamp(24, 64, (areaH - (TEAM_SLOTS - 1) * 4) / TEAM_SLOTS);
    }

    /** 队友卡组起始 Y：卡片总高度小于可用区域时垂直居中。 */
    private int teamStartY(int cardH, int cardGap) {
        int totalH = TEAM_SLOTS * cardH + (TEAM_SLOTS - 1) * cardGap;
        int areaTop = this.leftY + 4 + TEAM_HEADER_H;
        int areaBottom = this.leftY + this.leftH - 4;
        return areaTop + Math.max(0, (areaBottom - areaTop - totalH) / 2);
    }

    /** 绘制单张队友卡：角色首字头像方块 + 玩家名 + 小字角色名；本人金色描边、阵亡灰显。 */
    private void renderTeamCard(GuiGraphics graphics, int x, int y, int w, int h,
                                StrinovaScoreboardState.Entry entry, boolean hovered) {
        boolean self = entry != null && entry.self();
        boolean dead = entry != null && entry.status() != StrinovaScoreboardState.Status.ALIVE;
        int accent = entry == null ? COLOR_DEAD : agentAccent(entry.agentName(), COLOR_ENEMY);
        if (dead) {
            accent = COLOR_DEAD;
        }

        int background = self ? 0x44FFD24A : (hovered ? 0x33FFFFFF : 0x55101820);
        graphics.fill(x, y, x + w, y + h, background);
        this.border(graphics, x, y, w, h, self ? COLOR_GOLD : 0x44FFFFFF);

        int avatar = clamp(14, 30, Math.min(h - 6, w / 3));
        int avatarX = x + 4;
        int avatarY = y + (h - avatar) / 2;
        graphics.fill(avatarX, avatarY, avatarX + avatar, avatarY + avatar, 0xCC0A111C);
        graphics.fill(avatarX, avatarY, avatarX + 2, avatarY + avatar, accent);
        this.border(graphics, avatarX, avatarY, avatar, avatar, 0x66FFFFFF);
        String avatarText = entry == null ? "?" : firstChar(entry.agentName());
        this.drawCenteredText(graphics, avatarText, avatarX + avatar / 2,
                avatarY + (avatar - 9) / 2, dead ? COLOR_DEAD : COLOR_TEXT, false);

        int textX = avatarX + avatar + 5;
        int textW = x + w - textX - 4;
        int nameY = y + h / 2 - 9;
        int subY = y + h / 2 + 1;
        if (entry == null) {
            this.drawText(graphics, this.truncate("-", textW), textX, nameY, COLOR_DEAD);
            this.drawText(graphics, this.truncate("?", textW), textX, subY, COLOR_DEAD);
            return;
        }
        String playerName = entry.name() == null ? "?" : entry.name();
        this.drawText(graphics, this.truncate(playerName, textW), textX, nameY,
                self ? COLOR_GOLD : (dead ? COLOR_DEAD : COLOR_TEXT));
        String sub = dead
                ? Component.translatable(entry.status().labelKey()).getString()
                : (entry.agentName() == null ? "?" : entry.agentName());
        this.drawText(graphics, this.truncate(sub, textW), textX, subY,
                dead ? COLOR_DEAD : accent);
    }

    /** 返回鼠标命中的队友条目（用于悬停提示），未命中返回 null。 */
    private StrinovaScoreboardState.Entry teamEntryAt(int mouseX, int mouseY) {
        List<StrinovaScoreboardState.Entry> allies = StrinovaScoreboardState.allies();
        int cardGap = 4;
        int cardH = this.teamCardHeight();
        int startY = this.teamStartY(cardH, cardGap);
        int cardX = this.leftX + 4;
        int cardW = this.leftW - 8;
        for (int i = 0; i < Math.min(TEAM_SLOTS, allies.size()); i++) {
            int y = startY + i * (cardH + cardGap);
            if (this.isInside(mouseX, mouseY, cardX, y, cardW, cardH)) {
                return allies.get(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 中央立绘占位
    // ------------------------------------------------------------------

    /** 中央立绘占位：定位色渐变 + 巨型名字首字 + 底部角色名/定位条。 */
    private void renderPortrait(GuiGraphics graphics, StrinovaAgentRoster.Agent agent, int accent) {
        if (agent == null || this.centerW <= 0 || this.centerH <= 0) {
            return;
        }
        this.drawPanel(graphics, this.centerX, this.centerY, this.centerW, this.centerH);
        String name = agent.name() == null ? "?" : agent.name();
        int nameBlockH = clamp(26, 56, this.centerH / 5);

        if (!this.showPortrait) {
            // 窄屏：隐藏立绘，只显示名字与定位
            this.drawCenteredText(graphics, this.truncate(name, this.centerW - 8),
                    this.centerX + this.centerW / 2, this.centerY + this.centerH / 2 - 12, COLOR_TEXT, true);
            this.drawCenteredText(graphics,
                    this.truncate(Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role())).getString(),
                            this.centerW - 8),
                    this.centerX + this.centerW / 2, this.centerY + this.centerH / 2 + 2, accent, true);
            return;
        }

        int charTop = this.centerY;
        int charBottom = this.centerY + this.centerH - nameBlockH;
        // 立绘区渐变：上方定位色 → 下方深色
        graphics.fillGradient(this.centerX, charTop, this.centerX + this.centerW, charBottom,
                withAlpha(accent, 0xAA), 0xFF05070C);
        this.border(graphics, this.centerX, this.centerY, this.centerW, this.centerH, COLOR_BORDER);
        graphics.fill(this.centerX, charTop, this.centerX + 2, charBottom, accent);
        graphics.fill(this.centerX + this.centerW - 2, charTop, this.centerX + this.centerW, charBottom, accent);
        // 顶部装饰线
        graphics.fill(this.centerX + this.centerW / 4, charTop + 6,
                this.centerX + this.centerW * 3 / 4, charTop + 7, withAlpha(accent, 0x99));

        float scale = Math.min(Math.min(this.centerW * 0.50F, (charBottom - charTop) * 0.55F) / 9.0F, 12.0F);
        scale = Math.max(1.0F, scale);
        int charCx = this.centerX + this.centerW / 2;
        int charCy = charTop + (charBottom - charTop) / 2;
        this.drawGiantChar(graphics, firstChar(name), charCx + Math.round(scale), charCy + Math.round(scale),
                scale, 0x55000000);
        this.drawGiantChar(graphics, firstChar(name), charCx, charCy, scale, withAlpha(COLOR_TEXT, 0xE6));

        // 底部名字条：角色名（大号）+ 定位 · 阵营（小字）
        int nameTop = this.centerY + this.centerH - nameBlockH;
        graphics.fill(this.centerX, nameTop, this.centerX + this.centerW, this.centerY + this.centerH,
                COLOR_PANEL_DARK);
        graphics.fill(this.centerX, nameTop, this.centerX + this.centerW, nameTop + 1, accent);
        float nameScale = Math.min(2.0F,
                Math.max(1.0F, (this.centerW - 12.0F) / Math.max(1, this.font.width(name))));
        nameScale = Math.min(nameScale, Math.max(1.0F, (nameBlockH - 13.0F) / 9.0F));
        this.drawScaledCenteredText(graphics, name, charCx, nameTop + 2.0F, nameScale, COLOR_TEXT, true);
        Component roleLine = Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role()))
                .append(Component.literal(" · "))
                .append(Component.translatable(StrinovaAgentRoster.factionNameKey(agent.faction())));
        this.drawCenteredText(graphics, this.truncate(roleLine.getString(), this.centerW - 8),
                charCx, nameTop + nameBlockH - 11, accent, false);
    }

    // ------------------------------------------------------------------
    // 右侧信息面板
    // ------------------------------------------------------------------

    /** 右侧信息面板：定位标签、技能占位、主动技能描述、武器/外观页签与武器列表。 */
    private void renderRightPanel(GuiGraphics graphics, StrinovaAgentRoster.Agent agent,
                                  int accent, int mouseX, int mouseY) {
        this.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH);
        if (agent == null) {
            return;
        }
        int contentX = this.rightX + this.rightPad;
        int contentW = this.rightW - 2 * this.rightPad;

        // 1) 定位标签（定位色 chip）+ 阵营小字
        String roleName = Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role())).getString();
        String factionName = Component.translatable(StrinovaAgentRoster.factionNameKey(agent.faction())).getString();
        int chipW = this.font.width(roleName) + 8;
        graphics.fill(contentX, this.roleRowY, contentX + chipW, this.roleRowY + 12, withAlpha(accent, 0xCC));
        this.border(graphics, contentX, this.roleRowY, chipW, 12, accent);
        this.drawCenteredText(graphics, roleName, contentX + chipW / 2, this.roleRowY + 2, 0xFF0A111C, false);
        this.drawText(graphics, this.truncate(factionName, contentW - chipW - 6),
                contentX + chipW + 6, this.roleRowY + 2, COLOR_DIM);

        // 2) 「技能」标签 + Q/E/C 三个技能占位方块
        String skillLabel = Component.translatable("config.strinova.agent.skills").getString();
        this.drawText(graphics, skillLabel, contentX, this.skillRowY + (this.skillIconSize - 9) / 2, COLOR_DIM);
        int iconX = contentX + this.font.width(skillLabel) + 6;
        for (int i = 0; i < SKILL_KEYS.length; i++) {
            int x = iconX + i * (this.skillIconSize + 4);
            if (x + this.skillIconSize > contentX + contentW) {
                break;
            }
            int y = this.skillRowY;
            graphics.fill(x, y, x + this.skillIconSize, y + this.skillIconSize, 0xCC0A111C);
            this.border(graphics, x, y, this.skillIconSize, this.skillIconSize,
                    i == 0 ? accent : withAlpha(accent, 0x66));
            this.drawCenteredText(graphics, SKILL_KEYS[i], x + this.skillIconSize / 2,
                    y + (this.skillIconSize - 9) / 2, i == 0 ? accent : COLOR_DIM, false);
        }

        // 3) 「Q 主动: 定位」技能占位 + 定位描述（按高度显示 1~3 行）
        Component qLine = Component.translatable("config.strinova.agent.skill_active")
                .append(Component.literal(": "))
                .append(Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role())));
        this.drawText(graphics, this.truncate(qLine.getString(), contentW), contentX, this.qLineY, COLOR_TEXT);
        List<FormattedCharSequence> desc = this.font.split(
                Component.translatable(StrinovaAgentRoster.roleDescKey(agent.role())), contentW);
        for (int i = 0; i < desc.size() && i < this.descLines; i++) {
            graphics.drawString(this.font, desc.get(i), contentX, this.descY + i * 11, COLOR_DIM, false);
        }

        // 4) 「武器选择 / 外观选择」页签
        this.renderTab(graphics, this.weaponTabX, this.tabY, this.tabW, this.tabH,
                "config.strinova.agent.weapon_select", this.weaponTab, accent, mouseX, mouseY);
        this.renderTab(graphics, this.appearanceTabX, this.tabY, this.tabW, this.tabH,
                "config.strinova.agent.appearance_select", !this.weaponTab, accent, mouseX, mouseY);

        // 5) 页签内容：武器列表 5 项 / 外观占位
        if (this.weaponTab) {
            this.renderWeaponRows(graphics, contentX, contentW, accent);
        } else {
            this.renderAppearancePlaceholder(graphics, contentX, contentW, accent);
        }
    }

    /** 绘制一个页签：选中时定位色底 + 高亮描边，未选中为深色。 */
    private void renderTab(GuiGraphics graphics, int x, int y, int w, int h, String labelKey,
                           boolean active, int accent, int mouseX, int mouseY) {
        boolean hovered = this.isInside(mouseX, mouseY, x, y, w, h);
        int background = active ? withAlpha(accent, 0xB0) : (hovered ? 0x44FFFFFF : 0x55000000);
        graphics.fill(x, y, x + w, y + h, background);
        this.border(graphics, x, y, w, h, active ? accent : 0x44FFFFFF);
        String label = this.truncate(Component.translatable(labelKey).getString(), w - 6);
        this.drawCenteredText(graphics, label, x + w / 2, y + (h - 9) / 2, active ? 0xFF0A111C : COLOR_DIM, false);
    }

    /** 武器面板 5 项：几何占位图标 + 名称（战术道具两项带序号）。 */
    private void renderWeaponRows(GuiGraphics graphics, int contentX, int contentW, int accent) {
        for (int i = 0; i < WEAPON_KEYS.length; i++) {
            int y = this.weaponTop + i * this.weaponRowH;
            if (y + this.weaponRowH > this.rightY + this.rightH - this.rightPad + 2) {
                break;
            }
            int iconSize = clamp(10, 20, this.weaponRowH - 3);
            graphics.fill(contentX, y, contentX + contentW, y + this.weaponRowH - 1, 0x40000000);
            int iconY = y + (this.weaponRowH - iconSize) / 2;
            graphics.fill(contentX + 2, iconY, contentX + 2 + iconSize, iconY + iconSize, 0xAA0A111C);
            this.border(graphics, contentX + 2, iconY, iconSize, iconSize, withAlpha(accent, 0x66));
            this.drawWeaponIcon(graphics, contentX + 2, iconY, iconSize, i, accent);
            Component label = Component.translatable(WEAPON_KEYS[i]);
            if (i >= 3) {
                label = label.copy().append(Component.literal(" " + (i - 2)));
            }
            this.drawText(graphics,
                    this.truncate(label.getString(), contentW - iconSize - 10),
                    contentX + iconSize + 7, y + (this.weaponRowH - 9) / 2,
                    i == 0 ? COLOR_TEXT : COLOR_DIM);
        }
    }

    /** 外观页签占位内容：菱形几何图形 + 标签（外观系统待接入）。 */
    private void renderAppearancePlaceholder(GuiGraphics graphics, int contentX, int contentW, int accent) {
        int top = this.weaponTop;
        int bottom = this.rightY + this.rightH - this.rightPad;
        if (bottom - top <= 12) {
            return;
        }
        graphics.fill(contentX, top, contentX + contentW, bottom, 0x40000000);
        this.border(graphics, contentX, top, contentW, bottom - top, 0x33FFFFFF);
        int cx = contentX + contentW / 2;
        int cy = top + (bottom - top) / 2 - 6;
        int radius = clamp(6, 16, Math.min(contentW, bottom - top) / 4);
        this.drawDiamond(graphics, cx, cy, radius, withAlpha(accent, 0xAA));
        this.drawDiamond(graphics, cx, cy, radius / 2, withAlpha(accent, 0xDD));
        this.drawCenteredText(graphics,
                this.truncate(Component.translatable("config.strinova.agent.appearance_select").getString(),
                        contentW - 8),
                cx, Math.min(bottom - 11, cy + radius + 6), COLOR_DIM, false);
    }

    /** 武器占位图标：主武器/副武器用枪形、近战用斜刀刃、战术道具用方形十字（纯矩形拼绘）。 */
    private void drawWeaponIcon(GuiGraphics graphics, int x, int y, int size, int index, int color) {
        int inset = Math.max(2, size / 6);
        switch (index) {
            case 0 -> {
                // 主武器：长枪管 + 枪托
                graphics.fill(x + inset, y + size / 2 - 1, x + size - inset, y + size / 2 + 1, color);
                graphics.fill(x + inset, y + size / 2 + 1, x + size / 2, y + size - inset, color);
                graphics.fill(x + size - inset - 2, y + size / 2 - 3, x + size - inset, y + size / 2 - 1, color);
            }
            case 1 -> {
                // 副武器：短枪管 + 握把
                graphics.fill(x + size / 3, y + size / 2 - 1, x + size - inset, y + size / 2 + 1, color);
                graphics.fill(x + size / 3, y + size / 2 + 1, x + size / 2 + 1, y + size - inset, color);
            }
            case 2 -> {
                // 近战武器：斜向刀刃
                int steps = Math.max(3, size - inset * 2);
                for (int i = 0; i < steps; i++) {
                    int px = x + inset + i;
                    int py = y + size - inset - i;
                    graphics.fill(px, py, px + 2, py + 2, color);
                }
            }
            default -> {
                // 战术道具：方形 + 十字
                int cx = x + size / 2;
                int cy = y + size / 2;
                int box = Math.max(2, size / 4);
                graphics.fill(cx - box, cy - box, cx + box, cy + box, color);
                graphics.fill(cx - 1, cy - size / 3, cx + 1, cy + size / 3, color);
                graphics.fill(cx - size / 3, cy - 1, cx + size / 3, cy + 1, color);
            }
        }
    }

    // ------------------------------------------------------------------
    // 底部头像网格
    // ------------------------------------------------------------------

    /** 底部 2×12 超弦体头像格：定位色底 + 名字首字，选中高亮 + P1 标记，已锁定绿勾。 */
    private void renderAgentGrid(GuiGraphics graphics, int hoveredCell) {
        this.drawPanel(graphics, this.gridX, this.gridY, this.gridW, this.gridH);
        for (int i = 0; i < this.agents.size(); i++) {
            int row = i / GRID_COLUMNS;
            int col = i % GRID_COLUMNS;
            if (row >= GRID_ROWS) {
                break;
            }
            int x = this.cellOffsetX + col * (this.cellW + this.cellGap);
            int y = this.gridY + row * (this.cellH + this.cellGap);
            this.renderAgentCell(graphics, x, y, this.agents.get(i), i == this.selectedIndex, i == hoveredCell);
        }
    }

    /** 绘制单个超弦体头像格。 */
    private void renderAgentCell(GuiGraphics graphics, int x, int y,
                                 StrinovaAgentRoster.Agent agent, boolean selected, boolean hovered) {
        int cellAccent = agent.accentColor();
        String agentId = agent.id() == null ? "" : agent.id();
        boolean locked = agentId.equals(this.lockedAgentId);
        int background = selected
                ? withAlpha(cellAccent, 0xCC)
                : (hovered ? 0x55FFFFFF : withAlpha(cellAccent, 0x66));
        graphics.fill(x, y, x + this.cellW, y + this.cellH, background);
        this.border(graphics, x, y, this.cellW, this.cellH,
                selected ? 0xFFFFFFFF : withAlpha(cellAccent, 0xBB));
        if (selected) {
            // 选中：外侧再包一圈定位色描边，形成高亮描边效果
            graphics.fill(x - 1, y - 1, x + this.cellW + 1, y, cellAccent);
            graphics.fill(x - 1, y + this.cellH, x + this.cellW + 1, y + this.cellH + 1, cellAccent);
            graphics.fill(x - 1, y, x, y + this.cellH, cellAccent);
            graphics.fill(x + this.cellW, y, x + this.cellW + 1, y + this.cellH, cellAccent);
        }
        this.drawCenteredText(graphics, firstChar(agent.name()), x + this.cellW / 2,
                y + (this.cellH - 9) / 2, selected ? 0xFF0A111C : COLOR_TEXT, false);
        // 选中者左上角 P1 标记（格子太窄时退化为金色小方块，避免遮挡居中首字）
        if (selected) {
            if (this.cellW >= 20 && this.cellH >= 26) {
                graphics.fill(x + 1, y + 1, x + 15, y + 10, 0xCC0A111C);
                graphics.drawString(this.font, "P1", x + 2, y + 1, COLOR_GOLD, false);
            } else {
                graphics.fill(x + 1, y + 1, x + 5, y + 5, COLOR_GOLD);
            }
        }
        // 已锁定：右下角绿色勾（纯矩形拼绘，避免字体缺字形）
        if (locked) {
            int checkSize = clamp(6, 10, Math.min(this.cellW, this.cellH) - 4);
            int checkX = x + this.cellW - checkSize - 2;
            int checkY = y + this.cellH - checkSize - 2;
            graphics.fill(checkX - 1, checkY - 1, checkX + checkSize + 1, checkY + checkSize + 1, 0xAA0A111C);
            this.drawCheck(graphics, checkX, checkY, checkSize, COLOR_LOCKED);
        }
    }

    // ------------------------------------------------------------------
    // 锁定按钮 / 装饰 / 提示
    // ------------------------------------------------------------------

    /** 锁定按钮外框：定位色描边 + 上下强调条（按钮本身由 super.render 绘制）。 */
    private void renderLockFrame(GuiGraphics graphics, int accent) {
        graphics.fill(this.lockX - 2, this.lockY - 2, this.lockX + this.lockW + 2,
                this.lockY + this.lockH + 2, withAlpha(accent, 0x55));
        graphics.fill(this.lockX - 2, this.lockY - 2, this.lockX + this.lockW + 2,
                this.lockY, withAlpha(accent, 0xFF));
        graphics.fill(this.lockX - 2, this.lockY + this.lockH, this.lockX + this.lockW + 2,
                this.lockY + this.lockH + 2, withAlpha(accent, 0xFF));
    }

    /** 鼠标附近的简易提示框（贴边时自动向内偏移）。 */
    private void renderHoverTooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        int boxWidth = this.font.width(text) + 8;
        int boxHeight = this.font.lineHeight + 4;
        int x = Math.min(mouseX + 8, this.width - boxWidth - 4);
        int y = Math.min(mouseY + 8, this.height - boxHeight - 4);
        graphics.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 0xFF2A2A3A);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0100010);
        graphics.drawString(this.font, text, x + 4, y + 2, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------
    // 通用绘制辅助
    // ------------------------------------------------------------------

    /** 面板：半透明深底 + 描边。 */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        this.border(graphics, x, y, w, h, COLOR_BORDER);
    }

    /** 1px 矩形描边。 */
    private void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 左对齐文本（无阴影）。 */
    private void drawText(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color, false);
    }

    /** 水平居中文本。 */
    private void drawCenteredText(GuiGraphics graphics, String text, int centerX, int y,
                                  int color, boolean shadow) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, shadow);
    }

    /** 缩放文本（左上角对齐 x/y）。 */
    private void drawScaledText(GuiGraphics graphics, String text, float x, float y, float scale,
                                int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    /** 缩放文本（以 centerX 水平居中，y 为顶部）。 */
    private void drawScaledCenteredText(GuiGraphics graphics, String text, int centerX, float y,
                                        float scale, int color, boolean shadow) {
        float width = this.font.width(text) * scale;
        this.drawScaledText(graphics, text, centerX - width / 2.0F, y, scale, color, shadow);
    }

    /** 巨型首字（立绘占位）：以 (centerX, centerY) 为中心绘制缩放单字。 */
    private void drawGiantChar(GuiGraphics graphics, String text, int centerX, int centerY,
                               float scale, int color) {
        int width = this.font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, -width / 2, -4, color, false);
        graphics.pose().popPose();
    }

    /** 纯矩形拼绘的绿色对勾（避免默认字体缺 ✔ 字形）。 */
    private void drawCheck(GuiGraphics graphics, int x, int y, int size, int color) {
        int stroke = Math.max(1, size / 5);
        int half = Math.max(2, size / 2);
        int startX = x + Math.max(1, size / 8);
        int midY = y + size / 2;
        for (int i = 0; i < half; i++) {
            graphics.fill(startX + i, midY + i, startX + i + stroke, midY + i + stroke, color);
        }
        int rightStartX = startX + half;
        int rightSteps = Math.max(1, x + size - stroke - rightStartX);
        for (int i = 0; i <= rightSteps; i++) {
            graphics.fill(rightStartX + i, midY + half - i,
                    rightStartX + i + stroke, midY + half - i + stroke, color);
        }
    }

    /** 纯矩形拼绘的实心菱形（外观页签占位）。 */
    private void drawDiamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int safeRadius = Math.max(1, radius);
        for (int dy = -safeRadius; dy <= safeRadius; dy++) {
            int half = safeRadius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }
}
