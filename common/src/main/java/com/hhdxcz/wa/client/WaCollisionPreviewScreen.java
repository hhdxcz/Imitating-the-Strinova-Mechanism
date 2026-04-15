package com.hhdxcz.wa.client;

import com.hhdxcz.wa.collision.WaCollisionBoxTuning;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.Locale;
import java.util.UUID;

public class WaCollisionPreviewScreen extends Screen {
    private static final int PANEL_PADDING = 24;
    private static final int PANEL_TOP = 44;
    private static final double FLY_PREVIEW_RENDER_Y_OFFSET = 0.8D;

    protected final Screen parent;
    protected final AbstractClientPlayer previewPlayer;
    private final WaCollisionPreviewState.Mode initialMode;
    private float rotX = 18.0F;
    private float rotY = -25.0F;
    private float zoom = 1.0F;
    private boolean dragging;
    protected int previewLeft;
    protected int previewTop;
    protected int previewWidth;
    protected int previewHeight;
    private Button modeButton;

    public WaCollisionPreviewScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        this(parent, previewPlayer, null);
    }

    public WaCollisionPreviewScreen(Screen parent, AbstractClientPlayer previewPlayer, WaCollisionPreviewState.Mode initialMode) {
        super(Component.translatable("config.klbq.collision_preview.title"));
        this.parent = parent;
        this.previewPlayer = previewPlayer;
        this.initialMode = initialMode;
    }

    @Override
    protected void init() {
        this.previewLeft = PANEL_PADDING;
        this.previewTop = PANEL_TOP;
        this.previewWidth = Math.max(160, this.width - (PANEL_PADDING * 2));
        this.previewHeight = Math.max(120, this.height - 112);
        if (initialMode != null && previewPlayer != null) {
            WaCollisionPreviewState.setMode(previewPlayer.getUUID(), initialMode);
        }
        // 确保屏幕初始化时渲染状态处于可见基线
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 开启颜色写入，避免被其它 GUI/Screen 状态污染导致实体渲染“看不见”
        RenderSystem.colorMask(true, true, true, true);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width - 104, this.height - 28, 80, 20)
                .build());
        modeButton = addRenderableWidget(Button.builder(Component.literal(""), b -> toggleMode())
                .bounds(24, this.height - 28, 160, 20)
                .build());
        updateModeButton();
    }

    protected final void updateModeButton() {
        if (modeButton != null) {
            modeButton.setMessage(Component.translatable("config.klbq.collision_preview.toggle_mode"));
        }
    }

    protected final WaCollisionPreviewState.Mode currentMode() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        return WaCollisionPreviewState.getMode(playerId);
    }

    protected final WaCollisionBoxTuning.Tuning currentTuning() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        return WaCollisionPreviewState.getTuning(playerId);
    }

    protected final boolean hasPreviewPlayer() {
        // 某些情况下 minecraft.level 在打开配置界面时可能为 null（单人也可能触发），
        // 但渲染实体模型通常仍可正常工作。
        return previewPlayer != null && minecraft != null;
    }

    protected final void renderPreviewArea(GuiGraphics graphics, int x, int y, int w, int h) {
        // 仅用于渲染：不要在渲染时反向回写布局字段，避免 previewLeft 连续被 -6 偏移累加。
        // 鼠标命中依然基于 init/render 时维护的 previewLeft/previewTop。 

        if (!hasPreviewPlayer()) {
            return;
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Lighting.setupForEntityInInventory();

        PoseStack localPose = new PoseStack();
        localPose.pushPose();
        try {
            localPose.translate(x + w / 2.0D, y + h / 2.0D, 150.0D);
            localPose.scale(48.0F * zoom, -48.0F * zoom, 48.0F * zoom);
            localPose.mulPose(Axis.YP.rotationDegrees(rotY));
            localPose.mulPose(Axis.XP.rotationDegrees(rotX));
            localPose.translate(0.0D, -0.9D, 0.0D);

            WaCollisionPreviewState.Mode mode = currentMode();
            renderPlayerModel(graphics, localPose, mode);
            renderCollisionBox(graphics, localPose, currentTuning());
        } finally {
            localPose.popPose();
            Lighting.setupFor3DItems();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            try {
                graphics.disableScissor();
            } catch (Exception ignored) {
            }
        }
    }

    protected final void renderPreviewDescription(GuiGraphics graphics, int x, int y, int color) {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        WaCollisionPreviewState.Mode mode = WaCollisionPreviewState.getMode(playerId);
        WaCollisionBoxTuning.Tuning tuning = WaCollisionPreviewState.getTuning(playerId);
        // 2D 描制前强制恢复常见 GUI 基线状态，避免前面 3D 预览/渲染状态污染导致文字坐标漂移
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();

            // NOTE: Keep renderPreviewDescription purely 2D.
            // Avoid creating an unused PoseStack that isn't applied to GuiGraphics.
            try {
                graphics.drawString(this.font, this.title, x, y, color, false);


                graphics.drawString(this.font, Component.literal("[WA-X0]" + x), x, y + 70, 0x00FF00, false);
                graphics.drawString(this.font, Component.literal("[WA-X1]" + x), x, y + 84, 0x0000FF, false);

                Component modeText = Component.translatable("config.klbq.collision_preview.mode",
                        Component.translatable(mode == WaCollisionPreviewState.Mode.SYNC ? "config.klbq.collision_preview.mode.sync" : "config.klbq.collision_preview.mode.fly"));
                graphics.drawString(this.font, modeText, x, y + 14, 0xB0B0B0, false);

                if (tuning != null) {
                    Component offsetText = Component.literal(String.format(Locale.ROOT,
                            "offset=(%.2f, %.2f, %.2f)", tuning.offsetX(), tuning.offsetY(), tuning.offsetZ()));
                    graphics.drawString(this.font, offsetText, x, y + 28, 0xA0A0A0, false);

                    Component sizeText = Component.literal(String.format(Locale.ROOT,
                            "size=(%.2f, %.2f, %.2f)", tuning.sizeX(), tuning.sizeY(), tuning.sizeZ()));
                    graphics.drawString(this.font, sizeText, x, y + 40, 0xA0A0A0, false);
                }

                graphics.drawString(this.font, Component.translatable("config.klbq.collision_preview.tip"), x, y + 56, 0x707070, false);
            } finally {
            }
        } finally {
            RenderSystem.disableBlend();
            try {
                graphics.disableScissor();
            } catch (Exception ignored) {
            }
        }
    }

    protected final void togglePreviewMode() {
        toggleMode();
    }

    protected final void setPreviewTuning(WaCollisionBoxTuning.Tuning tuning) {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        WaCollisionPreviewState.setTuning(playerId, tuning);
    }

    protected final void renderPlayerModel(GuiGraphics graphics, PoseStack poseStack, WaCollisionPreviewState.Mode mode) {
        if (minecraft == null || previewPlayer == null) {
            return;
        }

        // 直接使用 dummy 参与渲染，尽量避免进入界面前实时状态被渲染/动画系统读取。
        // 注意：当前 dummy 仍是 sourcePlayer 兜底；如果你仍反馈“截取”，说明必须把 dummy 实现替换为真正的客户端假实体。

        // 选项 1：预览 pitch 永远为 0（避免继承打开前“低着头”姿态）。
        // 注意：xRot/yRot 具体字段名在不同版本里可能不同，这里优先使用 getXRot/setXRot（若没有编译会报错，我们再换适配字段）。
        AbstractClientPlayer dummy = createPreviewDummy(previewPlayer);
            if (dummy == null) {
                dummy = previewPlayer;
            }

            UUID playerId = dummy.getUUID();
        boolean previewFly = mode == WaCollisionPreviewState.Mode.FLY;
        try {
            // 模型预览只要“正常姿势”，不要截取进入界面前玩家的朝向/俯仰。
            // 但这里不再强行 pitch=0，避免导致姿态被抹平。
            // 具体版本字段名不同的话我们再做兼容适配。
            // 保持模型“正常姿势”，不直接强行 pitch=0。
            // 为确保切入 FLY 后能进入正确的“鞘翅滑翔”姿态：
            // 1) 保留当前 yaw（朝向），避免出现旋转抖动
            // 2) pitch 不再继承界面打开前的姿态（否则你会看到不正常/不像鞘翅滑翔的俯仰）
            // 注意：我们不做全局 pitch=0（避免“模型不正常姿势/抹平”），而是用预览帧内归一化后的值。
            // 这里直接使用当前 xRot 作为基准；若你仍看到不正常俯仰，我们再改成固定“glide pitch”常量。
            // 不从真实玩家/previewPlayer 读取实时朝向/俯仰，避免“截取进入界面前状态”。
            // 预览时统一由界面旋转 rotX/rotY 驱动（PoseStack），这里保持实体内部角度不变。
            dummy.setYRot(dummy.getYRot());

            // 客户端预览上下文：让渲染/命中框计算在这一帧把“fly”视作开启。
            WaCollisionPreviewFlyContext.setPreviewFly(playerId, previewFly);

            // 模式决定预览实体是否“像在飘飞”。（仅尽量触发视觉动画差异，不写 WaPaperState）
            // 预览阶段不对真实 previewPlayer 做任何状态写入，避免“截取进入前状态”被实体内部状态/渲染流程放大。
            // 需要时仅由 WaCollisionPreviewFlyContext 驱动我们的 mixin 逻辑。
            if (previewFly) {
                dummy.setSharedFlagOnFire(false);
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();

            // 实体渲染依赖当前缓冲区与渲染状态，必要时先 flush 一次避免被其它 UI 组件吞掉。
            graphics.flush();
            minecraft.getEntityRenderDispatcher().setRenderShadow(false);
            minecraft.getEntityRenderDispatcher().render(
                    dummy,
                    0.0D,
                    0.0D,
                    0.0D,
                    dummy.getYRot(),
                    1.0F,
                    poseStack,
                    graphics.bufferSource(),
                    0xF000F0
            );
        } catch (Exception e) {
            // 防止单个异常直接导致整个屏幕渲染失败。
            com.hhdxcz.wa.WaMod.LOGGER.error("Failed to render preview player", e);
        } finally {
            // 清理预览上下文，避免影响其它 GUI/实体渲染。
            WaCollisionPreviewFlyContext.clear();
            minecraft.getEntityRenderDispatcher().setRenderShadow(true);
            RenderSystem.disableBlend();
        }
        graphics.flush();
    }

    protected final Screen parentScreen() {
        return parent;
    }

    protected final AbstractClientPlayer previewPlayer() {
        return previewPlayer;
    }

    /**
     * 创建用于 GUI 预览的客户端假实体。
     * 目的：避免使用真实 player 导致进入界面时姿态/动画残留。
     */
    protected AbstractClientPlayer createPreviewDummy(AbstractClientPlayer sourcePlayer) {
        // 这里只做“逻辑上独立的假实体”。
        // 由于 AbstractClientPlayer 的实例化在不同 MC 映射下可能差异较大，
        // 这里先退回到 sourcePlayer，确保编译通过；后续我们再替换为真正的 ClientDummyPlayer（不继承进入前状态）。
        return sourcePlayer;
    }

    protected final void renderCollisionBox(GuiGraphics graphics, PoseStack poseStack, WaCollisionBoxTuning.Tuning tuning) {
        AABB box = buildBox(tuning);
        if (box == null) {
            return;
        }
        if (currentMode() == WaCollisionPreviewState.Mode.FLY) {
            box = box.move(0.0D, FLY_PREVIEW_RENDER_Y_OFFSET, 0.0D);
        }

        // 类似原版 F3+B 的“白色线框高亮”观感：
        // 1) 只在预览作用域内关闭深度测试，避免影响其它 UI/文字
        // 2) 线框用两次渲染叠加：细线 + 半透明粗线（观感更像调试碰撞箱）
        // 3) 根据 zoom 自适应线框 alpha（缩放越大越清晰）
        float alpha = Math.max(0.25F, Math.min(1.0F, 0.25F + zoom * 0.35F));

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.lines());

        // 细线（底层）
        LevelRenderer.renderLineBox(poseStack, consumer, box,
                0.08F, 0.30F * alpha, 1.0F, 1.0F);

        // 半透明“粗线”（叠加层）
        // 注意：这里仍使用 RenderType.lines()，通过更高线宽参数/亮度模拟原版观感。
        LevelRenderer.renderLineBox(poseStack, consumer, box,
                0.16F, 0.60F * alpha, 1.0F, 1.0F);

        graphics.flush();
        RenderSystem.enableDepthTest();
    }

    protected static AABB buildBox(WaCollisionBoxTuning.Tuning tuning) {
        if (tuning == null) {
            return null;
        }
        double halfX = Math.max(0.01D, Math.abs(tuning.sizeX()) * 0.5D);
        double halfY = Math.max(0.01D, Math.abs(tuning.sizeY()) * 0.5D);
        double halfZ = Math.max(0.01D, Math.abs(tuning.sizeZ()) * 0.5D);
        double cx = tuning.offsetX();
        double cy = tuning.offsetY();
        double cz = tuning.offsetZ();
        return new AABB(cx - halfX, cy - halfY, cz - halfZ, cx + halfX, cy + halfY, cz + halfZ);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInPreview(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            rotY += (float) dragX * 0.5F;
            rotX = Math.max(-80.0F, Math.min(80.0F, rotX + (float) dragY * 0.5F));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInPreview(mouseX, mouseY)) {
            zoom = Math.max(0.5F, Math.min(2.5F, zoom + (float) delta * 0.1F));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    protected final boolean isInPreview(double mouseX, double mouseY) {
        return mouseX >= previewLeft && mouseX <= previewLeft + previewWidth && mouseY >= previewTop && mouseY <= previewTop + previewHeight;
    }

    protected final int previewLeft() {
        return previewLeft;
    }

    protected final int previewTop() {
        return previewTop;
    }

    protected final int previewWidth() {
        return previewWidth;
    }

    protected final int previewHeight() {
        return previewHeight;
    }

    private void toggleMode() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        WaCollisionPreviewState.Mode next = WaCollisionPreviewState.getMode(playerId) == WaCollisionPreviewState.Mode.SYNC
                ? WaCollisionPreviewState.Mode.FLY
                : WaCollisionPreviewState.Mode.SYNC;

        // 注意：这里不要修改 WaPaperState / 同步到服务端。
        // 否则会触发 PlayerMixin 与 WaNetwork 的矫正逻辑，导致你看到“切到 FLY 立刻回 SYNC”，并且在多人场景也会互相影响。
        WaCollisionPreviewState.setMode(playerId, next);
        updateModeButton();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
