package com.hhdxcz.strinova.client.hud;

import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * 战斗 HUD 统一入口：
 * <ul>
 *   <li>注册 Architectury 的跨平台 HUD 事件（Fabric/Forge 通用）；</li>
 *   <li>每帧同步客户端状态 → 测量布局 → 交给各部件绘制；</li>
 *   <li>为布局编辑器提供元素坐标、预览绘制与模拟数据开关。</li>
 * </ul>
 */
public final class StrinovaHudRenderer {

    private static final List<StrinovaHudWidget> WIDGETS = List.of(
            new StrinovaVitalsWidget(),
            new StrinovaAmmoWidget(),
            new StrinovaSkillsWidget(),
            new StrinovaStateWidget(),
            new StrinovaCrosshairWidget(),
            new StrinovaKillFeedWidget(),
            new StrinovaDamageNumbersWidget(),
            new StrinovaMatchHeaderWidget()
    );

    private static final Map<StrinovaHudLayout.Element, StrinovaHudWidget> WIDGET_BY_ELEMENT =
            new EnumMap<>(StrinovaHudLayout.Element.class);
    private static final Map<StrinovaHudLayout.Element, int[]> LAST_BOUNDS =
            new EnumMap<>(StrinovaHudLayout.Element.class);

    private static boolean registered;
    private static volatile boolean editorRendering;

    static {
        for (StrinovaHudWidget widget : WIDGETS) {
            WIDGET_BY_ELEMENT.put(widget.element(), widget);
        }
    }

    private StrinovaHudRenderer() {
    }

    /** 客户端入口调用一次：注册 HUD 事件。 */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        StrinovaHudState.clearCombatData();
        ClientGuiEvent.RENDER_HUD.register(StrinovaHudRenderer::onRenderHud);
    }

    /** 布局编辑器绘制预览期间为 true（此时空状态部件也会显示占位区域）。 */
    public static boolean isEditorRendering() {
        return editorRendering;
    }

    private static void onRenderHud(GuiGraphics graphics, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options == null || minecraft.options.hideGui) {
            return;
        }
        // 客户端握手诊断：进入世界一段时间仍未收到服务端启用状态 → 服务端未安装/未启用模组
        if (minecraft.player.tickCount > 100
                && com.hhdxcz.strinova.StrinovaMod.lastRemoteEnabledMs() == 0L) {
            try {
                com.hhdxcz.strinova.client.StrinovaClientNotices.notifyServerMissingMod();
            } catch (Throwable ignored) {
            }
        }
        StrinovaHudConfig config = StrinovaHudConfig.get();
        // TAB 计分板优先：即使 HUD 总开关关闭，按住 TAB 也要能显示计分板
        if (StrinovaScoreboardOverlay.isActive(minecraft)) {
            StrinovaScoreboardOverlay.render(graphics, new StrinovaProgrammaticPainter(graphics));
            return;
        }
        if (!config.hudEnabled) {
            return;
        }
        // 服务端对局数据优先：3 秒内收到过对局同步则不再用模拟数据/MC 血量覆盖
        boolean serverMatch = StrinovaHudState.isServerMatchActive()
                && (StrinovaHudState.now() - StrinovaHudState.lastServerSyncMs() < 3000L);
        if (!serverMatch) {
            StrinovaHudMockData.tick();
        }
        if (!com.hhdxcz.strinova.StrinovaMod.isEnabled()
                && !StrinovaHudMockData.isEnabled() && !serverMatch) {
            return;
        }
        syncFromPlayer(minecraft, serverMatch);
        renderAll(graphics, config);
    }

    /** 战斗系统落地前：生命值取 MC 真实血量映射；服务端对局激活时以同步包为准，不再覆盖。 */
    private static void syncFromPlayer(Minecraft minecraft, boolean serverMatch) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!serverMatch && !StrinovaHudMockData.isEnabled()) {
            float maxHealth = Math.max(1.0F, player.getMaxHealth());
            StrinovaHudState.setHealth(player.getHealth() / maxHealth * 150.0F, 150.0F);
            StrinovaHudState.setArmor(0.0F, 80.0F);
            // 显示手持武器名（兼容 TACZ 等枪械模组；弹药数值待枪械系统接入）
            var held = player.getMainHandItem();
            if (!held.isEmpty()) {
                StrinovaHudState.setWeapon(held.getHoverName().getString(),
                        StrinovaHudState.getFireMode(),
                        StrinovaHudState.getMagazine(),
                        StrinovaHudState.getReserve());
            }
        }

        float spread = 0.0F;
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        spread += (float) Math.min(0.6D, horizontal * 0.45D);
        if (player.isSprinting()) {
            spread += 0.2F;
        }
        if (!player.onGround()) {
            spread += 0.1F;
        }
        if (player.isUsingItem()) {
            spread += 0.15F;
        }
        StrinovaHudState.setCrosshairSpread(spread);
    }

    private static void renderAll(GuiGraphics graphics, StrinovaHudConfig config) {
        StrinovaHudPainter painter = new StrinovaProgrammaticPainter(graphics);
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        for (StrinovaHudWidget widget : WIDGETS) {
            if (!config.isElementEnabled(widget.element())) {
                LAST_BOUNDS.remove(widget.element());
                continue;
            }
            int width = Math.max(0, widget.measureWidth(painter));
            int height = Math.max(0, widget.measureHeight(painter));
            if (width <= 0 || height <= 0) {
                LAST_BOUNDS.remove(widget.element());
                continue;
            }
            int[] position = config.layout().resolve(widget.element(), screenWidth, screenHeight, width, height);
            LAST_BOUNDS.put(widget.element(), new int[]{position[0], position[1], width, height});
            widget.render(painter, position[0], position[1]);
        }
    }

    /** 布局编辑器：绘制所有元素的预览 + 选中/悬停高亮框。 */
    public static void renderEditorPreview(GuiGraphics graphics, StrinovaHudLayout.Element dragging,
                                           StrinovaHudLayout.Element hovered) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        editorRendering = true;
        try {
            StrinovaHudPainter painter = new StrinovaProgrammaticPainter(graphics);
            for (StrinovaHudWidget widget : WIDGETS) {
                StrinovaHudLayout.Element element = widget.element();
                // 编辑器中始终显示所有元素（含被关闭的），保证不会被“隐形拖动”
                int width = widget.measureWidth(painter);
                int height = widget.measureHeight(painter);
                if (width <= 0 || height <= 0) {
                    continue;
                }
                int[] position = config.layout().resolve(element, screenWidth, screenHeight, width, height);
                LAST_BOUNDS.put(element, new int[]{position[0], position[1], width, height});
                widget.render(painter, position[0], position[1]);
                if (!config.isElementEnabled(element)) {
                    painter.border(position[0] - 3, position[1] - 3, width + 6, height + 6, 0x44FFFFFF, 1);
                    painter.text("[OFF]", position[0] + 2, position[1] - 10, 0x88FFD166, false);
                }
                int color = dragging == element ? 0xFFFF4FA3
                        : hovered == element ? 0xFFFFFFFF : 0x66FFFFFF;
                painter.border(position[0] - 3, position[1] - 3, width + 6, height + 6, color, 1);
            }
        } finally {
            editorRendering = false;
        }
    }

    /** 布局编辑器可拖动的元素列表。 */
    public static List<StrinovaHudLayout.Element> editableElements() {
        return new java.util.ArrayList<>(WIDGET_BY_ELEMENT.keySet());
    }

    /** 元素当前的屏幕坐标 {x, y, width, height}，供布局编辑器做命中检测。 */
    public static int[] elementBounds(StrinovaHudLayout.Element element) {
        int[] cached = LAST_BOUNDS.get(element);
        if (cached != null) {
            return cached.clone();
        }
        StrinovaHudWidget widget = WIDGET_BY_ELEMENT.get(element);
        if (widget == null) {
            return new int[]{0, 0, 0, 0};
        }
        StrinovaProgrammaticPainter measure = new StrinovaProgrammaticPainter(null);
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = widget.measureWidth(measure);
        int height = widget.measureHeight(measure);
        int[] position = StrinovaHudConfig.get().layout().resolve(element, screenWidth, screenHeight, width, height);
        return new int[]{position[0], position[1], width, height};
    }

    public static boolean isMockEnabled() {
        return StrinovaHudMockData.isEnabled();
    }

    public static boolean toggleMock() {
        return StrinovaHudMockData.toggle();
    }

    public static void triggerTestHit() {
        StrinovaHudMockData.triggerTestHit();
    }

    public static void triggerTestKill() {
        StrinovaHudMockData.triggerTestKill();
    }
}
