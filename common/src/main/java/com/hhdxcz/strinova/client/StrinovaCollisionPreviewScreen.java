package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

/**
 * 碰撞箱预览界面基类。
 * 提供玩家的 3D 模型预览、碰撞箱渲染、鼠标旋转/缩放等通用功能，
 * 支持同步模式（SYNC）和飞行模式（FLY）两种预览模式。
 */
public class StrinovaCollisionPreviewScreen extends Screen {
    private static final int PANEL_PADDING = 24;
    private static final int PANEL_TOP = 44;

    /** 半透明填充面渲染类型，用于渲染复合碰撞箱的填充面。 */
    private static final RenderType COLLISION_FILLED_QUADS = RenderType.create(
            "strinova_collision_filled",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );
    private static final double FLY_PREVIEW_RENDER_Y_OFFSET = 0.8D;
    private static final double FLY_PREVIEW_RENDER_Z_OFFSET = 2.0D;

    protected final Screen parent;
    protected final AbstractClientPlayer previewPlayer;
    private final StrinovaCollisionPreviewState.Mode initialMode;
    private float rotX = 18.0F;
    private float rotY = -25.0F;
    private float zoom = 1.0F;
    private boolean dragging;
    protected int previewLeft;
    protected int previewTop;
    protected int previewWidth;
    protected int previewHeight;
    private Button modeButton;

    /** 使用默认模式创建预览界面。 */
    public StrinovaCollisionPreviewScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        this(parent, previewPlayer, null);
    }

    /** 使用指定初始模式创建预览界面。 */
    public StrinovaCollisionPreviewScreen(Screen parent, AbstractClientPlayer previewPlayer, StrinovaCollisionPreviewState.Mode initialMode) {
        super(Component.translatable("config.strinova.collision_preview.title"));
        this.parent = parent;
        this.previewPlayer = previewPlayer;
        this.initialMode = initialMode;
    }

    /** 初始化界面：计算预览区域大小、设置渲染状态、添加按钮。 */
    @Override
    protected void init() {
        this.previewLeft = PANEL_PADDING;
        this.previewTop = PANEL_TOP;
        this.previewWidth = Math.max(160, this.width - (PANEL_PADDING * 2));
        this.previewHeight = Math.max(120, this.height - 112);
        if (initialMode != null && previewPlayer != null) {
            StrinovaCollisionPreviewState.setMode(previewPlayer.getUUID(), initialMode);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.colorMask(true, true, true, true);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width - 104, this.height - 28, 80, 20)
                .build());
        modeButton = addRenderableWidget(Button.builder(Component.literal(""), b -> toggleMode())
                .bounds(24, this.height - 28, 160, 20)
                .build());
        updateModeButton();
    }

    /** 更新模式切换按钮的文本。 */
    protected final void updateModeButton() {
        if (modeButton != null) {
            modeButton.setMessage(Component.translatable("config.strinova.collision_preview.toggle_mode"));
        }
    }

    /** 获取当前预览模式。 */
    protected final StrinovaCollisionPreviewState.Mode currentMode() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        return StrinovaCollisionPreviewState.getMode(playerId);
    }

    /** 获取当前预览的碰撞箱调参。 */
    protected final StrinovaCollisionBoxTuning.Tuning currentTuning() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        return StrinovaCollisionPreviewState.getTuning(playerId);
    }

    /** 检查是否有可用的预览玩家。 */
    protected final boolean hasPreviewPlayer() {

        return previewPlayer != null && minecraft != null;
    }

    /** 渲染预览区域：绘制玩家模型和碰撞箱。 */
    protected final void renderPreviewArea(GuiGraphics graphics, int x, int y, int w, int h) {

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

            StrinovaCollisionPreviewState.Mode mode = currentMode();
            boolean isFly = mode == StrinovaCollisionPreviewState.Mode.FLY;

            if (isFly) {
                localPose.translate(0.0D, 0.0D, -FLY_PREVIEW_RENDER_Z_OFFSET);
            }
            renderPlayerModel(graphics, localPose, mode);
            boolean isGeneric = StrinovaCollisionPreviewState.getCollisionType(
                    previewPlayer == null ? null : previewPlayer.getUUID()) == StrinovaCompoundCollision.CollisionType.GENERIC;

            if (isFly || isGeneric) {
                renderCollisionBox(graphics, localPose, currentTuning());
            } else {
                renderCompoundParts(graphics, localPose);
            }
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

    /** 渲染预览区域的描述信息：模式、偏移量、尺寸等。 */
    protected final void renderPreviewDescription(GuiGraphics graphics, int x, int y, int color) {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        StrinovaCollisionPreviewState.Mode mode = StrinovaCollisionPreviewState.getMode(playerId);
        StrinovaCollisionBoxTuning.Tuning tuning = StrinovaCollisionPreviewState.getTuning(playerId);

        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();

            try {
                graphics.drawString(this.font, this.title, x, y, color, false);

                Component modeText = Component.translatable("config.strinova.collision_preview.mode",
                        Component.translatable(mode == StrinovaCollisionPreviewState.Mode.SYNC ? "config.strinova.collision_preview.mode.sync" : "config.strinova.collision_preview.mode.fly"));
                graphics.drawString(this.font, modeText, x, y + 14, 0xB0B0B0, false);

                if (tuning != null) {
                    Component offsetText = Component.literal(String.format(Locale.ROOT,
                            "offset=(%.2f, %.2f, %.2f)", tuning.offsetX(), tuning.offsetY(), tuning.offsetZ()));
                    graphics.drawString(this.font, offsetText, x, y + 28, 0xA0A0A0, false);

                    Component sizeText = Component.literal(String.format(Locale.ROOT,
                            "size=(%.2f, %.2f, %.2f)", tuning.sizeX(), tuning.sizeY(), tuning.sizeZ()));
                    graphics.drawString(this.font, sizeText, x, y + 40, 0xA0A0A0, false);
                }

                graphics.drawString(this.font, Component.translatable("config.strinova.collision_preview.tip"), x, y + 56, 0x707070, false);
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

    /** 切换预览模式（SYNC / FLY）。 */
    protected final void togglePreviewMode() {
        toggleMode();
    }

    /** 设置预览用的碰撞箱调参。 */
    protected final void setPreviewTuning(StrinovaCollisionBoxTuning.Tuning tuning) {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        StrinovaCollisionPreviewState.setTuning(playerId, tuning);
    }

    /** 渲染玩家模型，在飞行模式下会设置预览上下文。 */
    protected final void renderPlayerModel(GuiGraphics graphics, PoseStack poseStack, StrinovaCollisionPreviewState.Mode mode) {
        if (minecraft == null || previewPlayer == null) {
            return;
        }

        AbstractClientPlayer dummy = createPreviewDummy(previewPlayer);
            if (dummy == null) {
                dummy = previewPlayer;
            }

            UUID playerId = dummy.getUUID();
        boolean previewFly = mode == StrinovaCollisionPreviewState.Mode.FLY;
        try {

            dummy.setYRot(0.0F);
            dummy.setYBodyRot(0.0F);
            dummy.setYHeadRot(0.0F);
            dummy.setXRot(0.0F);

            dummy.setSprinting(false);

            StrinovaCollisionPreviewFlyContext.setPreviewActive(playerId);
            StrinovaCollisionPreviewFlyContext.setPreviewFly(playerId, previewFly);

            if (previewFly) {
                dummy.setSharedFlagOnFire(false);
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();

            graphics.flush();
            minecraft.getEntityRenderDispatcher().setRenderShadow(false);
            if (previewFly) {
                poseStack.pushPose();
                poseStack.translate(0.0D, 0.0D, 3.0D);
            }
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
            if (previewFly) {
                poseStack.popPose();
            }
        } catch (Exception e) {

            StrinovaMod.LOGGER.error("Failed to render preview player", e);
        } finally {

            StrinovaCollisionPreviewFlyContext.clear();
            minecraft.getEntityRenderDispatcher().setRenderShadow(true);
            RenderSystem.disableBlend();
        }
        graphics.flush();
    }

    /** 返回父界面。 */
    protected final Screen parentScreen() {
        return parent;
    }

    /** 返回当前预览的玩家。 */
    protected final AbstractClientPlayer previewPlayer() {
        return previewPlayer;
    }

    /** 创建用于预览的虚拟玩家（子类可覆盖以实现自定义外观）。 */
    protected AbstractClientPlayer createPreviewDummy(AbstractClientPlayer sourcePlayer) {

        return sourcePlayer;
    }

    /** 渲染碰撞箱线框：根据调参构建 AABB 并绘制双层线框。 */
    protected final void renderCollisionBox(GuiGraphics graphics, PoseStack poseStack, StrinovaCollisionBoxTuning.Tuning tuning) {
        AABB box = buildBox(tuning);
        if (box == null) {
            return;
        }
        if (currentMode() == StrinovaCollisionPreviewState.Mode.FLY) {
            box = box.move(0.0D, FLY_PREVIEW_RENDER_Y_OFFSET, FLY_PREVIEW_RENDER_Z_OFFSET);
        }

        float alpha = Math.max(0.25F, Math.min(1.0F, 0.25F + zoom * 0.35F));

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.lines());

        LevelRenderer.renderLineBox(poseStack, consumer, box,
                0.08F, 0.30F * alpha, 1.0F, 1.0F);

        LevelRenderer.renderLineBox(poseStack, consumer, box,
                0.16F, 0.60F * alpha, 1.0F, 1.0F);

        graphics.flush();
        RenderSystem.enableDepthTest();
    }

    /** 根据调参构建 AABB 碰撞箱。 */
    protected static AABB buildBox(StrinovaCollisionBoxTuning.Tuning tuning) {
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

    /** 点击预览区域时开始拖拽旋转。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInPreview(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 拖拽时旋转预览视角。 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            rotY += (float) dragX * 0.5F;
            rotX = Math.max(-80.0F, Math.min(80.0F, rotX + (float) dragY * 0.5F));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 释放鼠标结束拖拽。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 滚轮在预览区域内时缩放视角。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInPreview(mouseX, mouseY)) {
            zoom = Math.max(0.5F, Math.min(2.5F, zoom + (float) delta * 0.1F));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 判断鼠标坐标是否在预览区域内。 */
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

    /** 获取当前变化分区列表。 */
    protected final List<StrinovaCompoundCollision.Part> currentParts() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        return StrinovaCollisionPreviewState.getParts(playerId);
    }

    /** 获取当前选中的分区索引。 */
    protected final int currentSelectedPartIndex() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        return StrinovaCollisionPreviewState.getSelectedPartIndex(playerId);
    }

    /** 渲染复合碰撞箱分区：半透明填充面 + 线框，选中分区高亮为黄色。 */
    protected final void renderCompoundParts(GuiGraphics graphics, PoseStack poseStack) {
        List<StrinovaCompoundCollision.Part> parts = currentParts();
        if (parts == null || parts.isEmpty()) return;
        int selected = currentSelectedPartIndex();

        graphics.flush();
        RenderSystem.disableDepthTest();

        VertexConsumer filledConsumer = graphics.bufferSource().getBuffer(COLLISION_FILLED_QUADS);
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i < parts.size(); i++) {
            StrinovaCompoundCollision.Part part = parts.get(i);
            AABB box = part.toAABB(0.0D, 0.0D, 0.0D);
            if (box == null) continue;

            float r, g, b;
            if (i == selected) {
                r = 1.0F; g = 0.85F; b = 0.1F;
            } else {
                r = 0.3F; g = 0.7F; b = 1.0F;
            }
            float alpha = i == selected ? 0.35F : 0.18F;

            drawFilledAABB(filledConsumer, mat, box, r, g, b, alpha);
        }
        graphics.flush();

        VertexConsumer lineConsumer = graphics.bufferSource().getBuffer(RenderType.lines());
        for (int i = 0; i < parts.size(); i++) {
            StrinovaCompoundCollision.Part part = parts.get(i);
            AABB box = part.toAABB(0.0D, 0.0D, 0.0D);
            if (box == null) continue;

            float r, g, b;
            if (i == selected) {
                r = 1.0F; g = 0.85F; b = 0.1F;
            } else {
                r = 0.3F; g = 0.7F; b = 1.0F;
            }
            float alpha = i == selected ? 0.95F : 0.50F;

            LevelRenderer.renderLineBox(poseStack, lineConsumer, box,
                    0.06F, r * alpha, g * alpha, b * alpha);
            LevelRenderer.renderLineBox(poseStack, lineConsumer, box,
                    0.02F, r * alpha * 0.7F, g * alpha * 0.7F, b * alpha * 0.7F);
        }

        graphics.flush();
        RenderSystem.enableDepthTest();
    }

    /** 绘制 AABB 的六个填充面。 */
    private static void drawFilledAABB(VertexConsumer vc, Matrix4f mat, AABB box,
                                        float r, float g, float b, float a) {
        float x0 = (float) box.minX, x1 = (float) box.maxX;
        float y0 = (float) box.minY, y1 = (float) box.maxY;
        float z0 = (float) box.minZ, z1 = (float) box.maxZ;

        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y1, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y0, z1).color(r, g, b, a).endVertex();

        vc.vertex(mat, x1, y0, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y0, z0).color(r, g, b, a).endVertex();

        vc.vertex(mat, x0, y0, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y0, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y0, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();

        vc.vertex(mat, x0, y1, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y1, z1).color(r, g, b, a).endVertex();

        vc.vertex(mat, x1, y0, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y1, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();

        vc.vertex(mat, x0, y0, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y0, z1).color(r, g, b, a).endVertex();
    }

    /** 在 SYNC 和 FLY 模式之间切换。 */
    private void toggleMode() {
        UUID playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        StrinovaCollisionPreviewState.Mode next = StrinovaCollisionPreviewState.getMode(playerId) == StrinovaCollisionPreviewState.Mode.SYNC
                ? StrinovaCollisionPreviewState.Mode.FLY
                : StrinovaCollisionPreviewState.Mode.SYNC;

        StrinovaCollisionPreviewState.setMode(playerId, next);
        updateModeButton();
    }

    /** 关闭界面返回父界面。 */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}