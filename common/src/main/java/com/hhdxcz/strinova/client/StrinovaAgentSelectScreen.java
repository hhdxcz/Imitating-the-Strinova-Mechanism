package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;
import com.hhdxcz.strinova.client.hud.StrinovaAgentSelectUi;
import com.hhdxcz.strinova.client.hud.StrinovaHudTextures;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import com.hhdxcz.strinova.client.hud.StrinovaScoreboardState;
import com.hhdxcz.strinova.client.hud.StrinovaWikiData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
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
    /** 右侧 4 个技能卡上的按键字母（主动 / 被动 / 终极 / 战术）。 */
    private static final String[] SKILL_KEYS = {"Q", "P", "X", "E"};
    /** 4 个技能对应的 wiki 数据键。 */
    private static final String[] SKILL_KINDS = {"active", "passive", "ultimate", "tactical"};
    /** 4 个技能的中文/英文标签 key。 */
    private static final String[] SKILL_LABEL_KEYS = {
            "config.strinova.agent.skill_active",
            "config.strinova.agent.skill_passive",
            "config.strinova.agent.skill_ultimate",
            "config.strinova.agent.skill_tactical"
    };
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
    /** 左侧立绘是否已“选择后出现”：未点选前显示官方 HalfEnterEmpty 空头像。 */
    private boolean portraitRevealed;
    /** 上次锁定（写入配置）的角色 id，用于在头像网格中显示绿色勾。 */
    private String lockedAgentId = "";
    /** 右侧页签状态：true=武器选择，false=外观选择。 */
    private boolean weaponTab = true;
    /** 右侧当前展示详情的技能下标（0 主动 / 1 被动 / 2 终极 / 3 战术）。 */
    private int selectedSkill;

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
    /** 中央立绘是否显示（配置显隐 + 窗口太窄时自动隐藏）。 */
    private boolean showPortrait;
    /** 配置中是否启用立绘（用于区分「窄屏回退」与「配置隐藏」）。 */
    private boolean portraitEnabled;

    // ---- 可视化自定义（StrinovaHudConfig#agentSelectUi，编辑器实时修改） ----
    /** 顶部阶段条是否显示。 */
    private boolean showTopBar;
    /** 左侧队友列是否显示。 */
    private boolean showTeam;
    /** 右侧信息面板是否显示。 */
    private boolean showRightPanel;
    /** 底部 2×12 头像网格是否显示。 */
    private boolean showGrid;
    /** 底部锁定按钮是否显示。 */
    private boolean showLockButton;
    /** 底部阵营限制提示是否显示。 */
    private boolean showNotice;
    /** 中央角色简介是否显示。 */
    private boolean showIntro;
    /** 中央个性语录是否显示。 */
    private boolean showQuote;
    /** 右侧技能卡是否显示。 */
    private boolean showSkills;
    /** 右侧武器区是否显示。 */
    private boolean showWeaponPanel;
    /** 右侧页签是否显示。 */
    private boolean showTabs;
    /** 面板不透明度（来自 agentSelectUi.opacity，0.2~1.0）。 */
    private float panelOpacity = 1.0F;
    /** 底部限制提示行 Y。 */
    private int noticeY;
    /** 右上角「自定义」按钮位置/尺寸。 */
    private int customizeX;
    private int customizeY;
    private int customizeW;
    private int customizeH;

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
    /** 顶部角色头衔块（定位图标 + 定位 + 角色名）。 */
    private int headerY;
    private int headerH;
    /** 武器区：主武器大卡 + 2×2 小卡。 */
    private int weaponMainH;
    private int weaponCellH;
    private int weaponGridY;
    private int weaponGridBottom;
    /** 右侧技能图标行起始 X / 身份行 Y（layoutRightPanel 计算）。 */
    private int skillIconStartX;
    private int identityY;
    /** 技能卡命中区域（renderRightPanel 时更新）。 */
    private final int[][] skillIconRects = new int[4][];
    /** 主武器行命中区域（renderWeaponRows 时更新，用于悬停提示）。 */
    private int[] primaryWeaponRect;

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
        this.portraitRevealed = !this.lockedAgentId.isEmpty();

        this.computeLayout();
        this.layoutRightPanel();

        // 底部「锁定角色」按钮：被配置隐藏时不创建，释放底部空间
        if (this.showLockButton && this.lockW > 0 && this.lockH > 0) {
            this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.agent.lock"),
                            button -> this.lockSelection())
                    .bounds(this.lockX, this.lockY, this.lockW, this.lockH)
                    .build());
        }
        // 右上角「自定义」按钮：打开选角界面可视化编辑器
        this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.ui.customize"),
                        button -> Minecraft.getInstance().setScreen(new StrinovaAgentSelectEditScreen(this)))
                .bounds(this.customizeX, this.customizeY, this.customizeW, this.customizeH)
                .build());
    }

    /**
     * 计算整页自适应布局（读取 agentSelectUi 的比例/显隐配置）：
     * 顶部阶段条占满宽度；主区域分「左队友列 / 中央立绘 / 右侧信息面板」三栏；
     * 底部为 2×12 头像网格 + 大号锁定按钮，最下方为阵营限制提示。
     * 任一区域被隐藏时其余区域回收空间；所有尺寸均带上下限，320×240 ~ 1920×1080 均不越界。
     */
    private void computeLayout() {
        StrinovaAgentSelectUi ui = ui();
        this.showTopBar = ui.showTopBar;
        this.showTeam = ui.showTeam;
        this.showRightPanel = ui.showRightPanel;
        this.showGrid = ui.showGrid;
        this.showLockButton = ui.showLockButton;
        this.showNotice = ui.showNotice;
        this.showIntro = ui.showIntro;
        this.showQuote = ui.showQuote;
        this.showSkills = ui.showSkills;
        this.showWeaponPanel = ui.showWeaponPanel;
        this.showTabs = ui.showTabs;
        this.panelOpacity = Math.max(0.20F, Math.min(1.0F, ui.opacity));
        this.portraitEnabled = ui.showPortrait;

        this.margin = clamp(4, 8, this.width / 80);
        this.gap = this.margin >= 6 ? 8 : 6;
        this.barH = this.showTopBar ? clamp(18, 30, Math.round(this.height * 0.075F)) : 0;
        this.barX = this.margin;
        this.barY = this.margin;
        this.barW = this.width - 2 * this.margin;

        // 右上角「自定义」按钮：优先放进顶部阶段条内，阶段条隐藏时放在顶部右角
        this.customizeW = clamp(34, 58, this.width / 15);
        this.customizeH = 16;
        this.customizeX = this.width - this.margin - this.customizeW;
        this.customizeY = this.showTopBar
                ? this.barY + Math.max(0, (this.barH - this.customizeH) / 2)
                : this.margin;
        this.mainTop = this.showTopBar
                ? this.barY + this.barH + this.gap
                : this.margin + this.customizeH + this.gap;

        int contentWidth = this.width - 2 * this.margin;
        this.noticeY = this.height - this.margin - 9;
        int bottomLimit = this.height - this.margin - (this.showNotice ? 12 : 0);

        // 底部锁定按钮高度：读取配置并按窗口高度再收紧，避免小窗口占满底部
        this.lockH = this.showLockButton
                ? Math.min(clamp(18, 40, Math.round(ui.lockHeight)),
                Math.max(12, Math.round(this.height * 0.12F)))
                : 0;
        this.lockY = bottomLimit - this.lockH;
        int lockBottom = this.showLockButton ? this.lockY : bottomLimit;

        // 三栏宽度：左队友列 / 右信息面板均按屏幕宽度比例，并带像素上下限
        int leftWidth = this.showTeam
                ? clamp(56, 220, Math.round(this.width * ui.teamWidthRatio)) : 0;
        int rightWidth = this.showRightPanel
                ? clamp(90, 380, Math.round(this.width * ui.rightWidthRatio)) : 0;
        int sideGaps = (this.showTeam ? this.gap : 0) + (this.showRightPanel ? this.gap : 0);
        int centerSpace = contentWidth - leftWidth - rightWidth - sideGaps;
        if (centerSpace < 40) {
            // 小窗口：先压右栏、再压左栏，保证中央区至少 40px
            int deficit = 40 - centerSpace;
            if (this.showRightPanel) {
                int shrink = Math.min(deficit, Math.max(0, rightWidth - 64));
                rightWidth -= shrink;
                deficit -= shrink;
            }
            if (deficit > 0 && this.showTeam) {
                leftWidth = Math.max(36, leftWidth - deficit);
            }
        }

        // 底部 2×12 头像网格：右栏隐藏时整行回收宽度
        int gridRegionW = Math.max(40, contentWidth
                - (this.showRightPanel && rightWidth > 0 ? rightWidth + this.gap : 0));
        this.cellGap = clamp(1, 3, gridRegionW / 200);
        int cellWidth = (gridRegionW - (GRID_COLUMNS - 1) * this.cellGap) / GRID_COLUMNS;
        this.cellW = clamp(10, 64, cellWidth);
        this.cellH = clamp(12, 30, this.cellW);
        this.gridH = this.showGrid ? GRID_ROWS * this.cellH + (GRID_ROWS - 1) * this.cellGap : 0;
        this.gridY = lockBottom - this.gridH;
        this.gridX = this.margin;
        this.gridW = gridRegionW;
        int realGridW = GRID_COLUMNS * this.cellW + (GRID_COLUMNS - 1) * this.cellGap;
        this.cellOffsetX = this.gridX + Math.max(0, (this.gridW - realGridW) / 2);

        // 三栏垂直范围：底部网格/锁定条隐藏时回收空间
        int mainBottom = (this.showGrid ? this.gridY : lockBottom) - this.gap;
        int mainHeight = Math.max(40, mainBottom - this.mainTop);
        this.leftW = leftWidth;
        this.leftX = this.margin;
        this.leftY = this.mainTop;
        this.leftH = mainHeight;
        this.rightW = rightWidth;
        this.rightX = this.width - this.margin - rightWidth;
        this.rightY = this.mainTop;
        this.rightH = mainHeight;
        this.centerX = this.leftX + (this.showTeam ? this.leftW + this.gap : 0);
        int centerRight = this.rightX - (this.showRightPanel ? this.gap : 0);
        this.centerW = Math.max(30, centerRight - this.centerX);
        this.centerY = this.mainTop;
        this.centerH = mainHeight;

        // 锁定按钮：居中放在立绘下方、头像网格上方（对齐真实 UI）
        this.lockW = this.showLockButton ? Math.max(60, this.centerW) : 0;
        this.lockX = this.centerX + Math.max(0, (this.centerW - this.lockW) / 2);

        // 中央立绘：配置隐藏；窗口太窄（<84px）时也自动隐藏，避免巨型字挤在窄条里
        this.showPortrait = this.portraitEnabled && this.centerW >= 84;
    }

    /** 计算右侧信息面板布局：角色头衔块、身份、技能卡、技能详情、武器区（主武器大卡 + 2×2 小卡）、底部页签。 */
    private void layoutRightPanel() {
        if (this.rightW <= 0 || this.rightH <= 0) {
            return;
        }
        this.rightPad = clamp(4, 8, this.rightW / 20);
        this.headerH = this.rightH < 200 ? 16 : clamp(22, 32, Math.round(this.rightH * 0.10F));
        this.skillIconSize = this.rightH < 200 ? 14 : this.rightH < 320 ? 18 : 22;
        this.descLines = this.rightH < 200 ? 1 : this.rightH < 320 ? 2 : 3;
        this.weaponRowH = this.rightH < 200 ? 12 : this.rightH < 320 ? 15 : 19;
        this.tabH = this.showTabs ? clamp(14, 20, this.rightH / 12) : 0;
        this.tabW = Math.max(20, (this.rightW - 2 * this.rightPad - 4) / 2);

        int identityH = 11;
        this.weaponMainH = clamp(22, 40, this.weaponRowH * 2);
        this.weaponCellH = clamp(16, 32, this.weaponRowH * 2);
        int weaponGap = 4;
        int skillAreaH = this.showSkills
                ? this.skillIconSize + 4 + 12 + this.descLines * 11 + 6 : 0;
        int weaponAreaH = this.showWeaponPanel
                ? this.weaponMainH + weaponGap + this.weaponCellH * 2 + weaponGap : 0;
        int tabAreaH = this.tabH > 0 ? this.tabH + 6 : 0;
        int contentH = this.headerH + identityH + skillAreaH + weaponAreaH + tabAreaH;
        // 极端窄高比时压缩武器区与描述行数，保证不压出面板
        while (contentH > this.rightH - 2 * this.rightPad && this.showWeaponPanel
                && this.weaponCellH > 12) {
            this.weaponCellH--;
            this.weaponMainH = Math.max(18, this.weaponMainH - 1);
            contentH -= 4;
        }
        while (contentH > this.rightH - 2 * this.rightPad && this.descLines > 0) {
            this.descLines--;
            contentH -= 11;
        }

        int contentTop = this.rightY + Math.max(this.rightPad, (this.rightH - contentH) / 2);
        int y = contentTop;
        this.headerY = y;
        y += this.headerH;
        this.identityY = y;
        y += identityH;
        this.skillRowY = y;
        if (this.showSkills) {
            y += this.skillIconSize + 4;
        }
        this.qLineY = y;
        if (this.showSkills) {
            y += 12;
        }
        this.descY = y;
        if (this.showSkills) {
            y += this.descLines * 11 + 6;
        }
        this.weaponTop = y;
        this.weaponGridY = this.weaponTop + this.weaponMainH + weaponGap;
        // 页签固定在面板底部（页签隐藏时武器区可延伸到面板底部）
        this.tabY = this.rightY + this.rightH - this.rightPad - this.tabH;
        this.weaponTabX = this.rightX + this.rightPad;
        this.appearanceTabX = this.weaponTabX + this.tabW + 4;
        this.weaponGridBottom = this.showTabs
                ? this.tabY - 4 : this.rightY + this.rightH - this.rightPad;
        this.skillIconStartX = this.rightX + this.rightPad;
    }

    // 数据查询与小工具
    // ------------------------------------------------------------------

    /** 当前选中的超弦体；图鉴为空时返回 null（正常数据下不会发生）。 */
    /** 当前选中角色对应的 wiki 数据（缺数据时为 null）。 */
    private StrinovaWikiData.Agent wikiAgent() {
        StrinovaAgentRoster.Agent agent = this.selectedAgent();
        return agent == null ? null : StrinovaWikiData.byId(agent.id());
    }

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

    /** 当前可视化自定义配置（导入配置会替换对象，因此每次现取）。 */
    private static StrinovaAgentSelectUi ui() {
        return StrinovaHudConfig.get().agentSelectUi;
    }

    /** 面板颜色按配置不透明度缩放 alpha（作用于面板底色/描边）。 */
    private int withOpacity(int color) {
        if (this.panelOpacity >= 0.999F) {
            return color;
        }
        int alpha = Math.round(((color >>> 24) & 0xFF) * this.panelOpacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** 定位强调色：agentSelectUi.accentColor 非 0 时统一覆盖为该色。 */
    private static int configAccent(int base) {
        int override = ui().accentColor;
        return override != 0 ? override : base;
    }

    /** 中央角色名颜色（nameColor 非 0 时覆盖）。 */
    private static int nameColor() {
        int override = ui().nameColor;
        return override != 0 ? override : COLOR_TEXT;
    }

    /** 右侧头衔块角色名颜色（headerNameColor 非 0 时覆盖）。 */
    private static int headerNameColor() {
        int override = ui().headerNameColor;
        return override != 0 ? override : COLOR_TEXT;
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
        // 右侧页签：武器选择 / 外观选择（外观仅占位，点击只切换页签；页签隐藏时不响应）
        if (this.showTabs && this.showRightPanel) {
            if (this.isInside((int) mouseX, (int) mouseY, this.weaponTabX, this.tabY, this.tabW, this.tabH)) {
                this.weaponTab = true;
                return true;
            }
            if (this.isInside((int) mouseX, (int) mouseY,
                    this.appearanceTabX, this.tabY, this.tabW, this.tabH)) {
                this.weaponTab = false;
                return true;
            }
        }
        // 右侧技能卡：点选后展示对应技能名称与描述（技能卡隐藏时不响应）
        if (this.showSkills && this.showRightPanel) {
            for (int i = 0; i < this.skillIconRects.length; i++) {
                int[] rect = this.skillIconRects[i];
                if (rect != null
                        && this.isInside((int) mouseX, (int) mouseY, rect[0], rect[1], rect[2], rect[3])) {
                    this.selectedSkill = i;
                    return true;
                }
            }
        }
        // 底部头像网格：点击切换选中
        int index = this.gridIndexAt((int) mouseX, (int) mouseY);
        if (index >= 0) {
            this.selectedIndex = index;
            this.portraitRevealed = true;
            this.selectedSkill = 0;
            return true;
        }
        return false;
    }

    /** 判断坐标是否落在指定矩形内。 */
    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 返回鼠标命中的头像格下标；网格隐藏或未命中返回 -1。 */
    private int gridIndexAt(int mouseX, int mouseY) {
        if (!this.showGrid || this.gridH <= 0 || this.cellW <= 0 || this.cellH <= 0) {
            return -1;
        }
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
            // 选角锁定后立即上报服务端（C2S_PROFILE），TAB 计分板/头像才能拿到角色名
            StrinovaClientProfileSync.markDirty();
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

    /** 渲染整页：背景 → 顶部阶段条 → 三栏主区域 → 底部头像网格 → 锁定按钮 → 限制提示 → 悬停提示。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        StrinovaAgentSelectUi ui = ui();
        // 背景额外压暗（0~0.8）
        if (ui.backgroundDim > 0.001F) {
            int dimAlpha = clamp(0, 255, Math.round(ui.backgroundDim * 255.0F));
            graphics.fill(0, 0, this.width, this.height, dimAlpha << 24);
        }
        StrinovaAgentRoster.Agent agent = this.selectedAgent();
        // 强调色：accentColor 非 0 时覆盖角色定位色
        int accent = ui.accentColor != 0 ? ui.accentColor
                : (agent == null ? COLOR_ENEMY : agent.accentColor());
        int hoveredCell = this.gridIndexAt(mouseX, mouseY);

        if (this.showTopBar) {
            this.renderTopBar(graphics);
        }
        if (this.showTeam) {
            this.renderTeamColumn(graphics, mouseX, mouseY);
        }
        this.renderPortrait(graphics, agent, accent);
        if (this.showRightPanel) {
            this.renderRightPanel(graphics, agent, accent, mouseX, mouseY);
        }
        if (this.showGrid) {
            this.renderAgentGrid(graphics, hoveredCell);
        }
        if (this.showLockButton) {
            this.renderLockFrame(graphics, accent);
        }
        if (this.showNotice) {
            this.renderNotice(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停提示：头像格 → 队友卡 → 技能卡 → 主武器
        this.renderHoverTips(graphics, hoveredCell, mouseX, mouseY);
    }

    /** 底部阵营限制提示（对齐真实 UI 的底部居中小字）。 */
    private void renderNotice(GuiGraphics graphics) {
        String text = StrinovaMatchState.areAlliesAttacking()
                ? "阵营限制：进攻方无法选择欧泊角色"
                : "阵营限制：防守方无法选择剪刀手角色";
        String fitted = this.truncate(text, this.width - 2 * this.margin - 8);
        this.drawCenteredText(graphics, fitted, this.width / 2, this.noticeY, COLOR_DIM, false);
    }

    /** 悬停提示分发：头像格 / 队友卡优先，其次技能卡、主武器行。 */
    private void renderHoverTips(GuiGraphics graphics, int hoveredCell, int mouseX, int mouseY) {
        if (hoveredCell >= 0 && hoveredCell < this.agents.size()) {
            StrinovaAgentRoster.Agent hovered = this.agents.get(hoveredCell);
            Component tip = Component.literal(hovered.name())
                    .append(Component.literal(" · "))
                    .append(Component.translatable(StrinovaAgentRoster.roleNameKey(hovered.role())))
                    .append(Component.literal(" · "))
                    .append(Component.translatable(StrinovaAgentRoster.factionNameKey(hovered.faction())));
            this.renderHoverTooltip(graphics, tip, mouseX, mouseY);
            return;
        }
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
            return;
        }
        StrinovaWikiData.Agent wiki = this.wikiAgent();
        if (wiki == null) {
            return;
        }
        // 技能卡：按键 · 技能类型 + 名称 + 描述（仅面板与技能卡都显示时）
        if (this.showRightPanel && this.showSkills) {
            for (int i = 0; i < this.skillIconRects.length; i++) {
                int[] rect = this.skillIconRects[i];
                if (rect == null || !this.isInside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
                    continue;
                }
                StrinovaWikiData.Skill skill = wiki.skill(SKILL_KINDS[i]);
                Component title = Component.literal(SKILL_KEYS[i] + " · "
                        + Component.translatable(SKILL_LABEL_KEYS[i]).getString());
                List<Component> lines = new ArrayList<>();
                if (skill.name() != null && !skill.name().isEmpty()) {
                    lines.add(Component.literal(skill.name()));
                }
                if (skill.desc() != null && !skill.desc().isEmpty()) {
                    lines.add(Component.literal(skill.desc()));
                }
                this.renderHoverTooltip(graphics, title, mouseX, mouseY, lines);
                return;
            }
        }
        // 主武器行：名称 + 类型 + 介绍（仅武器区显示时）
        if (this.showRightPanel && this.showWeaponPanel && this.primaryWeaponRect != null
                && wiki.weapon() != null
                && this.isInside(mouseX, mouseY, this.primaryWeaponRect[0], this.primaryWeaponRect[1],
                this.primaryWeaponRect[2], this.primaryWeaponRect[3])
                && wiki.weapon().name() != null && !wiki.weapon().name().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            if (wiki.weapon().type() != null && !wiki.weapon().type().isEmpty()) {
                lines.add(Component.literal(wiki.weapon().type()));
            }
            if (wiki.weapon().desc() != null && !wiki.weapon().desc().isEmpty()) {
                lines.add(Component.literal(wiki.weapon().desc()));
            }
            this.renderHoverTooltip(graphics, Component.literal(wiki.weapon().name()), mouseX, mouseY, lines);
        }
    }

    /** 悬停提示：单行标题 + 可选多行自动换行说明。 */
    private void renderHoverTooltip(GuiGraphics graphics, Component title, int mouseX, int mouseY,
                                    List<Component> lines) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(this.font.split(line, 200));
        }
        int width = this.font.width(title);
        for (FormattedCharSequence line : wrapped) {
            width = Math.max(width, this.font.width(line));
        }
        width += 12;
        int height = 13 + wrapped.size() * 11;
        int x = clamp(4, Math.max(4, this.width - width - 4), mouseX + 10);
        int y = clamp(4, Math.max(4, this.height - height - 4), mouseY + 10);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3542);
        graphics.fill(x, y, x + width, y + height, 0xF00C121A);
        graphics.drawString(this.font, title, x + 6, y + 4, COLOR_TEXT, false);
        int lineY = y + 4 + 11;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(this.font, line, x + 6, lineY, COLOR_DIM, false);
            lineY += 11;
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
        graphics.fill(this.barX, this.barY, this.barX + this.barW, this.barY + this.barH,
                this.withOpacity(COLOR_PANEL_DARK));
        this.border(graphics, this.barX, this.barY, this.barW, this.barH, this.withOpacity(COLOR_BORDER));

        boolean alliesAttack = StrinovaMatchState.areAlliesAttacking();
        // 右上角给「自定义」按钮预留空间，避免遮挡敌方阵营标签
        int reserveRight = Math.min(this.barW / 2, this.customizeW + 6);
        int rightSlabX = this.barX + this.barW - reserveRight - sideW;
        this.renderPhaseSlab(graphics, this.barX, sideY, sideW, sideH, slant, COLOR_ALLY, true);
        this.renderPhaseSlab(graphics, rightSlabX, sideY, sideW, sideH, slant, COLOR_ENEMY, false);

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
                rightSlabX + sideW / 2, this.barY + (this.barH - 9) / 2, COLOR_TEXT, true);

        // 中央：选择角色 + 双方比分 + 大号倒计时（对齐真实 UI）
        int centerLeft = this.barX + sideW + 6;
        int centerRight = rightSlabX - 6;
        int centerMid = (centerLeft + centerRight) / 2;
        Component selecting = Component.translatable("config.strinova.agent.selecting");
        String time = StrinovaMatchState.formattedTime();
        String allyScore = String.valueOf(StrinovaMatchState.getAlliesScore());
        String enemyScore = String.valueOf(StrinovaMatchState.getEnemiesScore());
        int labelW = this.font.width(selecting);
        int scoreW = Math.max(this.font.width(allyScore), this.font.width(enemyScore));
        float timeScale = this.barH >= 28 ? 1.8F : 1.4F;
        int timeW = Math.round(this.font.width(time) * timeScale);
        int totalW = labelW + 10 + scoreW + 10 + timeW + 10 + scoreW;
        int startX = Math.max(centerLeft, centerMid - totalW / 2);
        int textY = this.barY + (this.barH - 9) / 2;
        if (totalW <= centerRight - centerLeft) {
            this.drawText(graphics, selecting.getString(), startX, textY, COLOR_DIM);
            startX += labelW + 10;
        }
        this.drawText(graphics, allyScore, startX, textY, 0xFFFF8A7A);
        startX += scoreW + 10;
        this.drawScaledText(graphics, time, startX,
                this.barY + (this.barH - 9 * timeScale) / 2.0F, timeScale, COLOR_TEXT, true);
        startX += timeW + 10;
        this.drawText(graphics, enemyScore, startX, textY, 0xFF8FAEFF);
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

    /** 队友卡高度：按左栏可用高度（扣除标题区）均分，小窗口下自动缩小以免越界。 */
    private int teamCardHeight() {
        int areaH = this.leftH - 8 - TEAM_HEADER_H;
        int fit = (areaH - (TEAM_SLOTS - 1) * 4) / TEAM_SLOTS;
        return Math.max(12, Math.min(64, fit));
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
        int accent = entry == null
                ? COLOR_DEAD : configAccent(agentAccent(entry.agentName(), COLOR_ENEMY));
        if (dead) {
            accent = COLOR_DEAD;
        }

        int background = self ? 0x44FFD24A : (hovered ? 0x33FFFFFF : 0x55101820);
        graphics.fill(x, y, x + w, y + h, background);
        this.border(graphics, x, y, w, h, self ? COLOR_GOLD : 0x44FFFFFF);

        int avatar = clamp(10, 30, Math.max(8, Math.min(h - 2, w / 3)));
        int avatarX = x + 4;
        int avatarY = y + Math.max(0, (h - avatar) / 2);
        graphics.fill(avatarX, avatarY, avatarX + avatar, avatarY + avatar, 0xCC0A111C);
        graphics.fill(avatarX, avatarY, avatarX + 2, avatarY + avatar, accent);
        this.border(graphics, avatarX, avatarY, avatar, avatar, 0x66FFFFFF);
        if (avatar >= 9) {
            String avatarText = entry == null ? "?" : firstChar(entry.agentName());
            this.drawCenteredText(graphics, avatarText, avatarX + avatar / 2,
                    avatarY + (avatar - 9) / 2, dead ? COLOR_DEAD : COLOR_TEXT, false);
        }

        int textX = avatarX + avatar + 5;
        int textW = x + w - textX - 4;
        if (textW < 8) {
            return;
        }
        // 小窗口：卡片太矮时只画一行玩家名，避免文字溢出卡片
        if (h < 20) {
            String smallName = entry == null ? "-" : (entry.name() == null ? "?" : entry.name());
            this.drawText(graphics, this.truncate(smallName, textW), textX, y + (h - 9) / 2,
                    entry == null ? COLOR_DEAD : (self ? COLOR_GOLD : (dead ? COLOR_DEAD : COLOR_TEXT)));
            return;
        }
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

    /** 返回鼠标命中的队友条目（用于悬停提示），队友列隐藏或未命中返回 null。 */
    private StrinovaScoreboardState.Entry teamEntryAt(int mouseX, int mouseY) {
        if (!this.showTeam || this.leftH <= 0 || this.leftW <= 0) {
            return null;
        }
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

    /**
     * 中央立绘占位：定位色渐变 + 巨型名字首字 + 底部角色名/定位条；
     * 简介（showIntro）与语录（showQuote）可独立显隐；立绘被配置隐藏时只保留中央面板与文本。
     */
    private void renderPortrait(GuiGraphics graphics, StrinovaAgentRoster.Agent agent, int accent) {
        if (agent == null || this.centerW <= 0 || this.centerH <= 0) {
            return;
        }
        this.drawPanel(graphics, this.centerX, this.centerY, this.centerW, this.centerH);
        String name = agent.name() == null ? "?" : agent.name();
        int nameBlockH = clamp(26, 56, this.centerH / 5);
        int charTop = this.centerY;
        int charBottom = this.centerY + this.centerH - nameBlockH;
        StrinovaWikiData.Agent wiki = this.wikiAgent();

        if (!this.showPortrait) {
            // 窄屏回退：只显示名字与定位；若是配置隐藏立绘，则中央区只保留面板 + 简介/语录
            if (this.portraitEnabled) {
                this.drawCenteredText(graphics, this.truncate(name, this.centerW - 8),
                        this.centerX + this.centerW / 2, this.centerY + this.centerH / 2 - 12,
                        this.nameColor(), true);
                this.drawCenteredText(graphics,
                        this.truncate(Component.translatable(
                                StrinovaAgentRoster.roleNameKey(agent.role())).getString(), this.centerW - 8),
                        this.centerX + this.centerW / 2, this.centerY + this.centerH / 2 + 2, accent, true);
            }
        } else {
            int charCx = this.centerX + this.centerW / 2;
            int charCy = charTop + (charBottom - charTop) / 2;
            // 立绘区渐变：上方定位色 → 下方深色
            graphics.fillGradient(this.centerX, charTop, this.centerX + this.centerW, charBottom,
                    withAlpha(accent, 0xAA), 0xFF05070C);
            this.border(graphics, this.centerX, this.centerY, this.centerW, this.centerH, COLOR_BORDER);
            graphics.fill(this.centerX, charTop, this.centerX + 2, charBottom, accent);
            graphics.fill(this.centerX + this.centerW - 2, charTop,
                    this.centerX + this.centerW, charBottom, accent);
            // 顶部装饰线
            graphics.fill(this.centerX + this.centerW / 4, charTop + 6,
                    this.centerX + this.centerW * 3 / 4, charTop + 7, withAlpha(accent, 0x99));

            // 官方选角左侧人物头像（RoleHUD），未选角时显示 HalfEnterEmpty
            ResourceLocation roleHud = this.portraitRevealed
                    ? StrinovaHudTextures.roleHudByAgentName(name) : null;
            if (roleHud == null) {
                roleHud = StrinovaHudTextures.ROLE_HUD_EMPTY;
            }
            int areaH = Math.max(8, charBottom - charTop - 6);
            int areaW = Math.max(8, this.centerW - 8);
            int portraitSize = Math.max(8, Math.min(areaW, areaH));
            graphics.blit(roleHud, charCx - portraitSize / 2, charCy - portraitSize / 2,
                    portraitSize, portraitSize, 0.0F, 0.0F, 256, 256, 256, 256);

            // 底部名字条：角色名（大号）+ 定位 · 阵营（小字）
            int nameTop = charBottom;
            graphics.fill(this.centerX, nameTop, this.centerX + this.centerW,
                    this.centerY + this.centerH, this.withOpacity(COLOR_PANEL_DARK));
            graphics.fill(this.centerX, nameTop, this.centerX + this.centerW, nameTop + 1, accent);
            float nameScale = Math.min(2.0F,
                    Math.max(1.0F, (this.centerW - 12.0F) / Math.max(1, this.font.width(name))));
            nameScale = Math.min(nameScale, Math.max(1.0F, (nameBlockH - 13.0F) / 9.0F));
            this.drawScaledCenteredText(graphics, name, charCx, nameTop + 2.0F, nameScale,
                    this.nameColor(), true);
            Component roleLine = Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role()))
                    .append(Component.literal(" · "))
                    .append(Component.translatable(StrinovaAgentRoster.factionNameKey(agent.faction())));
            this.drawCenteredText(graphics, this.truncate(roleLine.getString(), this.centerW - 8),
                    charCx, nameTop + nameBlockH - 11, accent, false);
        }

        // 简介 / 语录（可独立显隐；对齐真实 UI：简介在上、语录在下）
        int infoBottom = (this.showPortrait ? charBottom : this.centerY + this.centerH) - 4;
        int infoWidth = Math.max(10, this.centerW - 16);
        if (this.showQuote) {
            String quote = wiki != null && wiki.quote() != null && !wiki.quote().isEmpty()
                    ? wiki.quote()
                    : Component.translatable("config.strinova.agent.quote").getString() + "：……";
            List<FormattedCharSequence> quoteLines = this.font.split(
                    Component.literal("「" + quote + "」"), infoWidth);
            int drawLines = Math.min(2, quoteLines.size());
            int quoteTop = infoBottom - drawLines * 11;
            if (quoteTop > this.centerY + 2) {
                for (int i = quoteLines.size() - drawLines; i < quoteLines.size(); i++) {
                    graphics.drawString(this.font, quoteLines.get(i), this.centerX + 8,
                            quoteTop + (i - (quoteLines.size() - drawLines)) * 11, 0xFFAFC3D4, false);
                }
                infoBottom = quoteTop - 2;
            }
        }
        if (this.showIntro) {
            String intro = wiki != null && wiki.intro() != null && !wiki.intro().isEmpty()
                    ? wiki.intro() : "";
            if (!intro.isEmpty()) {
                List<FormattedCharSequence> introLines = this.font.split(
                        Component.literal(intro), infoWidth);
                int drawLines = Math.min(3, introLines.size());
                int introTop = infoBottom - drawLines * 11;
                if (introTop > this.centerY + 2) {
                    for (int i = 0; i < drawLines; i++) {
                        graphics.drawString(this.font, introLines.get(i), this.centerX + 8,
                                introTop + i * 11, 0xCCD8E4EE, false);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 右侧信息面板
    // ------------------------------------------------------------------

    /** 右侧信息面板：头衔块、身份、技能卡（可点选）、技能详情、武器区、底部页签；子项按配置显隐。 */
    private void renderRightPanel(GuiGraphics graphics, StrinovaAgentRoster.Agent agent,
                                  int accent, int mouseX, int mouseY) {
        this.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH);
        // 每帧重建命中区域，隐藏时不残留
        for (int i = 0; i < this.skillIconRects.length; i++) {
            this.skillIconRects[i] = null;
        }
        this.primaryWeaponRect = null;
        if (agent == null) {
            return;
        }
        int contentX = this.rightX + this.rightPad;
        int contentW = this.rightW - 2 * this.rightPad;
        if (contentW < 8) {
            return;
        }
        StrinovaWikiData.Agent wiki = this.wikiAgent();
        int panelBottom = this.rightY + this.rightH - this.rightPad;

        // 1) 顶部角色头衔块：定位图标 + 定位 · 阵营 + 角色名（对齐真实 UI）
        if (this.headerY + this.headerH <= panelBottom) {
            graphics.fill(this.rightX, this.headerY, this.rightX + this.rightW,
                    this.headerY + this.headerH, this.withOpacity(0xCC1A222E));
            graphics.fill(this.rightX, this.headerY, this.rightX + 2,
                    this.headerY + this.headerH, accent);
            this.drawFilledDiamond(graphics, contentX + 5, this.headerY + 5, 13, accent);
            String roleName = Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role())).getString();
            String factionName = Component.translatable(
                    StrinovaAgentRoster.factionNameKey(agent.faction())).getString();
            this.drawText(graphics, roleName, contentX + 23, this.headerY + 4, accent);
            int factionX = contentX + 23 + this.font.width(roleName) + 8;
            this.drawText(graphics, this.truncate(factionName, contentX + contentW - factionX),
                    factionX, this.headerY + 4, COLOR_DIM);
            float nameScale = Math.min(1.7F, Math.max(1.0F, (this.headerH - 14.0F) / 9.0F));
            this.drawScaledText(graphics,
                    this.truncate(agent.name(), Math.max(8, (int) (contentW / nameScale))),
                    contentX + 4, this.headerY + 13, nameScale, headerNameColor(), false);
        }

        // 2) 身份（来自 wiki 信息框，缺数据时回退为定位说明）
        if (this.identityY + 9 <= panelBottom) {
            String identity = wiki != null && wiki.identity() != null && !wiki.identity().isEmpty()
                    ? wiki.identity()
                    : Component.translatable(StrinovaAgentRoster.roleDescKey(agent.role())).getString();
            String identityLine = Component.translatable("config.strinova.agent.identity").getString()
                    + " · " + identity;
            this.drawText(graphics, this.truncate(identityLine, contentW), contentX, this.identityY, COLOR_DIM);
        }

        // 3) 4 个技能卡（Q 主动 / P 被动 / X 终极 / E 战术）+ 选中技能名称与描述
        if (this.showSkills && this.skillRowY + this.skillIconSize <= panelBottom) {
            for (int i = 0; i < SKILL_KEYS.length; i++) {
                int x = this.skillIconStartX + i * (this.skillIconSize + 4);
                if (x + this.skillIconSize > contentX + contentW) {
                    this.skillIconRects[i] = null;
                    break;
                }
                int y = this.skillRowY;
                boolean selected = i == this.selectedSkill;
                boolean hovered = this.isInside(mouseX, mouseY, x, y, this.skillIconSize, this.skillIconSize);
                graphics.fill(x, y, x + this.skillIconSize, y + this.skillIconSize,
                        selected ? withAlpha(accent, 0x88) : this.withOpacity(0xCC0A111C));
                this.border(graphics, x, y, this.skillIconSize, this.skillIconSize,
                        selected ? accent : (hovered ? withAlpha(accent, 0xAA) : withAlpha(accent, 0x55)));
                this.drawCenteredText(graphics, SKILL_KEYS[i], x + this.skillIconSize / 2,
                        y + (this.skillIconSize - 9) / 2, selected ? 0xFF0A111C : COLOR_DIM, false);
                this.skillIconRects[i] = new int[]{x, y, this.skillIconSize, this.skillIconSize};
            }

            StrinovaWikiData.Skill skill = wiki == null
                    ? new StrinovaWikiData.Skill("", "")
                    : wiki.skill(SKILL_KINDS[this.selectedSkill]);
            String skillName = skill.name() == null || skill.name().isEmpty()
                    ? Component.translatable(SKILL_LABEL_KEYS[this.selectedSkill]).getString()
                    : skill.name();
            String skillLine = Component.translatable(SKILL_LABEL_KEYS[this.selectedSkill]).getString()
                    + " · " + skillName;
            this.drawText(graphics, this.truncate(skillLine, contentW), contentX, this.qLineY, COLOR_TEXT);
            String descText = skill.desc() == null || skill.desc().isEmpty()
                    ? Component.translatable(StrinovaAgentRoster.roleDescKey(agent.role())).getString()
                    : skill.desc();
            List<FormattedCharSequence> desc = this.font.split(Component.literal(descText), contentW);
            for (int i = 0; i < desc.size() && i < this.descLines; i++) {
                int lineY = this.descY + i * 11;
                if (lineY + 9 > panelBottom) {
                    break;
                }
                graphics.drawString(this.font, desc.get(i), contentX, lineY, COLOR_DIM, false);
            }
        }

        // 5) 武器区：主武器大卡 + 2×2 小卡 / 外观占位
        if (this.showWeaponPanel) {
            if (this.weaponTab) {
                this.renderWeaponPanel(graphics, contentX, contentW, accent, wiki);
            } else {
                this.renderAppearancePlaceholder(graphics, contentX, contentW, accent);
            }
        }

        // 6) 「武器选择 / 外观选择」页签（固定在面板底部）
        if (this.showTabs) {
            this.renderTab(graphics, this.weaponTabX, this.tabY, this.tabW, this.tabH,
                    "config.strinova.agent.weapon_select", this.weaponTab, accent, mouseX, mouseY);
            this.renderTab(graphics, this.appearanceTabX, this.tabY, this.tabW, this.tabH,
                    "config.strinova.agent.appearance_select", !this.weaponTab, accent, mouseX, mouseY);
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

    /** 武器面板：主武器大卡 + 副武器 / 近战 / 战术道具 2×2 小卡（对齐真实 UI）。 */
    private void renderWeaponPanel(GuiGraphics graphics, int contentX, int contentW, int accent,
                                   StrinovaWikiData.Agent wiki) {
        this.primaryWeaponRect = null;
        int panelBottom = this.rightY + this.rightH - this.rightPad;
        if (contentW < 10 || this.weaponTop + this.weaponMainH > panelBottom) {
            return;
        }
        boolean hasPrimary = wiki != null && wiki.weapon() != null
                && wiki.weapon().name() != null && !wiki.weapon().name().isEmpty();
        int mainH = this.weaponMainH;
        graphics.fill(contentX, this.weaponTop, contentX + contentW, this.weaponTop + mainH,
                this.withOpacity(0x40000000));
        this.border(graphics, contentX, this.weaponTop, contentW, mainH, withAlpha(accent, 0x88));
        this.drawText(graphics, "1 " + Component.translatable(WEAPON_KEYS[0]).getString(),
                contentX + 5, this.weaponTop + 3, COLOR_DIM);
        int iconSize = clamp(14, 30, mainH - 12);
        int iconX = contentX + (contentW - iconSize) / 2;
        int iconY = this.weaponTop + (mainH - iconSize) / 2 + 2;
        this.drawWeaponIcon(graphics, iconX, iconY, iconSize, 0, withAlpha(accent, 0xFF));
        String primaryName = hasPrimary
                ? wiki.weapon().name()
                : Component.translatable("hud.strinova.weapon.none").getString();
        String primaryType = hasPrimary && wiki.weapon().type() != null ? wiki.weapon().type() : "";
        int typeWidth = primaryType.isEmpty() ? 0 : this.font.width(primaryType) + 6;
        this.drawText(graphics, this.truncate(primaryName, contentW - typeWidth - 10),
                contentX + 5, this.weaponTop + mainH - 11, hasPrimary ? COLOR_TEXT : COLOR_DEAD);
        if (!primaryType.isEmpty()) {
            this.drawText(graphics, primaryType,
                    contentX + contentW - 5 - this.font.width(primaryType),
                    this.weaponTop + mainH - 11, COLOR_DIM);
        }
        this.primaryWeaponRect = new int[]{contentX, this.weaponTop, contentW, mainH};

        int gap = 4;
        int cellW = (contentW - gap) / 2;
        for (int i = 1; i < WEAPON_KEYS.length; i++) {
            int col = (i - 1) % 2;
            int row = (i - 1) / 2;
            int x = contentX + col * (cellW + gap);
            int y = this.weaponGridY + row * (this.weaponCellH + gap);
            if (y + this.weaponCellH > this.weaponGridBottom + 2) {
                break;
            }
            graphics.fill(x, y, x + cellW, y + this.weaponCellH, 0x33000000);
            this.border(graphics, x, y, cellW, this.weaponCellH, 0x33FFFFFF);
            String typeLabel = Component.translatable(WEAPON_KEYS[i]).getString();
            if (i >= 3) {
                typeLabel = typeLabel + " " + (i - 2);
            }
            this.drawText(graphics, Integer.toString(i + 1), x + 3, y + 2, COLOR_DIM);
            this.drawText(graphics, this.truncate(typeLabel, cellW - 14), x + 12, y + 2, COLOR_DEAD);
            int smallIcon = clamp(10, 18, this.weaponCellH - 16);
            this.drawWeaponIcon(graphics, x + (cellW - smallIcon) / 2, y + this.weaponCellH / 2 - 1,
                    smallIcon, i, withAlpha(accent, 0xCC));
        }
    }

    /** 外观页签占位内容：菱形几何图形 + 标签（外观系统待接入）。 */
    private void renderAppearancePlaceholder(GuiGraphics graphics, int contentX, int contentW, int accent) {
        int top = this.weaponTop;
        int panelBottom = this.rightY + this.rightH - this.rightPad;
        int bottom = Math.min(this.showTabs ? this.tabY - 4 : panelBottom, panelBottom);
        if (bottom - top <= 12 || contentW < 10) {
            return;
        }
        graphics.fill(contentX, top, contentX + contentW, bottom, this.withOpacity(0x40000000));
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
        // 头像格底色：gridCellColor 非 0 时覆盖；accentColor 非 0 时统一强调色
        int gridColor = ui().gridCellColor;
        int cellAccent = gridColor != 0 ? gridColor : configAccent(agent.accentColor());
        String agentId = agent.id() == null ? "" : agent.id();
        boolean locked = agentId.equals(this.lockedAgentId);
        int background = selected
                ? withAlpha(cellAccent, 0xCC)
                : (hovered ? 0x55FFFFFF : withAlpha(cellAccent, 0x66));
        graphics.fill(x, y, x + this.cellW, y + this.cellH, background);
        // 官方选角底部可点人物头像（SelectTalent 98×78）
        ResourceLocation talentTexture = StrinovaHudTextures.selectTalentByAgentName(agent.name());
        if (talentTexture != null) {
            graphics.blit(talentTexture, x, y, this.cellW, this.cellH,
                    0.0F, 0.0F, 98, 78, 98, 78);
        }
        this.border(graphics, x, y, this.cellW, this.cellH,
                selected ? 0xFFFFFFFF : withAlpha(cellAccent, 0xBB));
        if (selected) {
            // 选中：外侧再包一圈定位色描边，形成高亮描边效果
            graphics.fill(x - 1, y - 1, x + this.cellW + 1, y, cellAccent);
            graphics.fill(x - 1, y + this.cellH, x + this.cellW + 1, y + this.cellH + 1, cellAccent);
            graphics.fill(x - 1, y, x, y + this.cellH, cellAccent);
            graphics.fill(x + this.cellW, y, x + this.cellW + 1, y + this.cellH, cellAccent);
        }
        if (talentTexture == null) {
            this.drawCenteredText(graphics, firstChar(agent.name()), x + this.cellW / 2,
                    y + (this.cellH - 9) / 2, selected ? 0xFF0A111C : COLOR_TEXT, false);
        }
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

    /** 面板：半透明深底 + 描边（alpha 随配置不透明度变化）。 */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, this.withOpacity(COLOR_PANEL));
        this.border(graphics, x, y, w, h, this.withOpacity(COLOR_BORDER));
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
    /** 实心菱形（角色定位图标）。 */
    private void drawFilledDiamond(GuiGraphics graphics, int x, int y, int size, int color) {
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = size / 2;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    private void drawDiamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int safeRadius = Math.max(1, radius);
        for (int dy = -safeRadius; dy <= safeRadius; dy++) {
            int half = safeRadius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }
}
