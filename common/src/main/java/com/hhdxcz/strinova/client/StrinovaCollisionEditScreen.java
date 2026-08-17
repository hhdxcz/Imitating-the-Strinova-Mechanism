package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision.CollisionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 碰撞箱编辑界面。
 * 支持同步模式（SYNC）和飞行模式（FLY）的碰撞箱参数精细调整，
 * 包括偏移量、尺寸、步长循环、分段/通用碰撞类型切换、分区增删等。
 */
public class StrinovaCollisionEditScreen extends StrinovaCollisionPreviewScreen {
    private static final int LEFT_PADDING = 20;
    private static final int LEFT_TOP = 40;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_GAP_X = 8;
    private static final int FIELD_ROW_SPACING = 36;
    private static final int SECTION_GAP = 20;
    private static final int TITLE_GAP = 28;
    private static final int PREVIEW_HEADER_HEIGHT = 68;
    private static final int MIN_FIELD_WIDTH = 92;
    private static final int LABEL_GAP_Y = 10;
    private static final int PART_SELECTOR_HEIGHT = 20;
    private static final int PART_SELECTOR_GAP = 16;
    private static final int SECTION_PADDING = 6;
    private static final int SECTION_BG = 0x22000000;
    private static final int SEPARATOR_COLOR = 0xFF444444;

    private final StrinovaCollisionBoxTuning.Tuning syncTuning;
    private final StrinovaCollisionBoxTuning.Tuning flyTuning;
    private final UUID playerId;

    private EditBox syncOffsetX;
    private EditBox syncOffsetY;
    private EditBox syncOffsetZ;
    private EditBox syncSizeX;
    private EditBox syncSizeY;
    private EditBox syncSizeZ;
    private EditBox flyOffsetX;
    private EditBox flyOffsetY;
    private EditBox flyOffsetZ;
    private EditBox flySizeX;
    private EditBox flySizeY;
    private EditBox flySizeZ;

    private Button modeButton;
    private Button stepButton;
    private Button saveButton;
    private Button cancelButton;
    private Button resetCurrentButton;

    private int leftX;
    private int leftWidth;
    private int syncTitleY;
    private int flyTitleY;
    private int syncSectionTop;
    private int syncSectionBottom;
    private int flySectionTop;
    private int flySectionBottom;

    private double step = 0.05D;
    private CollisionType collisionType;

    private final List<LabeledField> labeledFields = new ArrayList<>();
    private final List<EditBox> syncFields = new ArrayList<>();
    private final List<EditBox> flyFields = new ArrayList<>();
    private final List<EditBox> allFields = new ArrayList<>();

    private List<StrinovaCompoundCollision.Part> syncParts;
    private List<StrinovaCompoundCollision.Part> flyParts;
    private int selectedPartIndex;
    private Button partPrevButton;
    private Button partNextButton;
    private Button addPartButton;
    private Button removePartButton;
    private int syncPartSelectorY;
    private int flyPartSelectorY;

    /** 构造编辑界面，初始化同步和飞行的调参数据。 */
    public StrinovaCollisionEditScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        super(parent, previewPlayer, StrinovaCollisionPreviewState.Mode.SYNC);
        this.playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        StrinovaCollisionBoxTuning.Tuning baseSyncAbs = getBaseSyncAbs(previewPlayer);
        StrinovaCollisionBoxTuning.Tuning baseFlyAbs = getBaseFlyAbs(previewPlayer);

        StrinovaCollisionBoxTuning.Tuning syncStored = StrinovaCollisionBoxTuning.getCustomSync(playerId);
        StrinovaCollisionBoxTuning.Tuning flyStored = StrinovaCollisionBoxTuning.getCustomFly(playerId);

        this.syncTuning = syncStored == null ? baseSyncAbs : toAbsSync(baseSyncAbs, syncStored);
        this.flyTuning = flyStored == null ? baseFlyAbs : toAbsFly(baseFlyAbs, flyStored);

        List<StrinovaCompoundCollision.Part> rawSync = StrinovaCompoundCollision.getSyncParts(playerId);
        this.syncParts = rawSync == null ? new ArrayList<>(StrinovaCompoundCollision.defaultSyncParts()) : new ArrayList<>(rawSync);
        this.flyParts = new ArrayList<>(StrinovaCompoundCollision.getFlyParts(playerId));
        this.selectedPartIndex = 0;
        this.collisionType = StrinovaCompoundCollision.getCollisionType(playerId);
    }

    /** 初始化界面：左侧编辑面板 + 右侧 3D 预览。 */
    @Override
    protected void init() {
        super.init();
        clearWidgets();

        leftX = LEFT_PADDING;
        int rightX = this.width / 2 + 10;
        int rightWidth = this.width - rightX - LEFT_PADDING;
        leftWidth = Math.max(260, this.width / 2 - LEFT_PADDING * 2);

        int previewTop = LEFT_TOP;
        int previewHeight = this.height - 122;
        this.previewLeft = rightX;
        this.previewTop = previewTop + PREVIEW_HEADER_HEIGHT;
        this.previewWidth = rightWidth;
        this.previewHeight = previewHeight - PREVIEW_HEADER_HEIGHT;

        labeledFields.clear();
        syncFields.clear();
        flyFields.clear();
        allFields.clear();
        selectedPartIndex = Math.max(0, Math.min(selectedPartIndex, currentPartsList().size() - 1));
        buildEditors(leftX, previewTop);

        if (playerId != null) {
            StrinovaCollisionPreviewState.setCollisionType(playerId, collisionType);
        }

        modeButton = addRenderableWidget(Button.builder(collisionTypeMessage(), b -> {
                    toggleCollisionType();
                })
                .bounds(leftX, this.height - 28, 92, 20)
                .build());
        Button flyModeButton = addRenderableWidget(Button.builder(Component.translatable("config.strinova.collision_preview.toggle_mode"), b -> {
                    togglePreviewMode();
                    onModeChanged();
                })
                .bounds(leftX + 100, this.height - 28, 120, 20)
                .build());
        stepButton = addRenderableWidget(Button.builder(stepMessage(), b -> cycleStep())
                .bounds(leftX + 228, this.height - 28, 92, 20)
                .build());
        resetCurrentButton = addRenderableWidget(Button.builder(Component.translatable("config.strinova.collision_edit.reset_current"), b -> resetCurrentMode())
                .bounds(leftX + 328, this.height - 28, 110, 20)
                .build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> close(false))
                .bounds(this.width - 188, this.height - 28, 80, 20)
                .build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> close(true))
                .bounds(this.width - 104, this.height - 28, 80, 20)
                .build());

        onModeChanged();
    }

    /** 构建左侧编辑面板：根据当前模式显示同步或飞行的编辑区域。 */
    private void buildEditors(int leftX, int top) {
        int y = top + 2;
        boolean isGeneric = collisionType == CollisionType.GENERIC;
        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;

        if (sync) {

            syncSectionTop = y - SECTION_PADDING;
            syncTitleY = y;
            syncPartSelectorY = syncTitleY;
            if (!isGeneric) {
                Component syncTitle = Component.translatable("config.strinova.collision_edit.section.sync");
                int titleWidth = this.font.width(syncTitle);
                buildPartSelectorRow(leftX + 4 + titleWidth + 8, syncTitleY - 2, true);
            }
            y += TITLE_GAP;
            int syncCols = computeColumns(3);
            int syncFieldWidth = computeFieldWidth(syncCols);
            y = addGrid(y, leftX, syncCols, syncFieldWidth, syncFields, buildFieldSpecsForSync());
            syncOffsetX = syncFields.get(0);
            syncOffsetY = syncFields.get(1);
            syncOffsetZ = syncFields.get(2);
            syncSizeX = syncFields.get(3);
            syncSizeY = syncFields.get(4);
            syncSizeZ = syncFields.get(5);
            syncSectionBottom = y + SECTION_PADDING;
            flySectionTop = flySectionBottom = syncSectionBottom;
            flyTitleY = syncSectionBottom;
            flyPartSelectorY = syncSectionBottom;
        } else {

            flySectionTop = y - SECTION_PADDING;
            flyTitleY = y;
            y += TITLE_GAP;
            flyPartSelectorY = y;
            int flyCols = computeColumns(3);
            int flyFieldWidth = computeFieldWidth(flyCols);
            y = addGrid(y, leftX, flyCols, flyFieldWidth, flyFields, buildFieldSpecsForFly());
            flyOffsetX = flyFields.get(0);
            flyOffsetY = flyFields.get(1);
            flyOffsetZ = flyFields.get(2);
            flySizeX = flyFields.get(3);
            flySizeY = flyFields.get(4);
            flySizeZ = flyFields.get(5);
            flySectionBottom = y + SECTION_PADDING;
            syncSectionTop = syncSectionBottom = flySectionBottom;
            syncTitleY = flySectionBottom;
            syncPartSelectorY = flySectionBottom;
        }
    }

    /** 构建分区选择器按钮行：上一个/下一个/添加/删除分区。 */
    private void buildPartSelectorRow(int x, int y, boolean sync) {
        List<StrinovaCompoundCollision.Part> parts = sync ? syncParts : flyParts;

        partPrevButton = Button.builder(Component.literal("<"), b -> cyclePart(-1))
                .bounds(x, y, 18, PART_SELECTOR_HEIGHT)
                .build();
        addRenderableWidget(partPrevButton);

        partNextButton = Button.builder(Component.literal(">"), b -> cyclePart(1))
                .bounds(x + 20, y, 18, PART_SELECTOR_HEIGHT)
                .build();
        addRenderableWidget(partNextButton);

        addPartButton = Button.builder(Component.literal("+"), b -> addPart())
                .bounds(x + 42, y, 18, PART_SELECTOR_HEIGHT)
                .build();
        addRenderableWidget(addPartButton);

        removePartButton = Button.builder(Component.literal("-"), b -> removePart())
                .bounds(x + 62, y, 18, PART_SELECTOR_HEIGHT)
                .build();
        addRenderableWidget(removePartButton);
    }

    /** 更新分区选择器按钮的可用状态。 */
    private void updatePartSelectorButtons() {
        if (partPrevButton != null) {
            partPrevButton.active = selectedPartIndex > 0;
        }
        if (partNextButton != null) {
            partNextButton.active = selectedPartIndex < currentPartsList().size() - 1;
        }
        if (removePartButton != null) {
            removePartButton.active = currentPartsList().size() > 1;
        }
    }

    /** 构建同步模式的字段规格列表。 */
    private List<FieldSpec> buildFieldSpecsForSync() {
        if (collisionType == CollisionType.GENERIC) {
            return List.of(
                    new FieldSpec("config.strinova.collision_edit.offset_x", syncTuning.offsetX()),
                    new FieldSpec("config.strinova.collision_edit.offset_y", syncTuning.offsetY()),
                    new FieldSpec("config.strinova.collision_edit.offset_z", syncTuning.offsetZ()),
                    new FieldSpec("config.strinova.collision_edit.size_x", syncTuning.sizeX()),
                    new FieldSpec("config.strinova.collision_edit.size_y", syncTuning.sizeY()),
                    new FieldSpec("config.strinova.collision_edit.size_z", syncTuning.sizeZ())
            );
        }
        int idx = Math.max(0, Math.min(selectedPartIndex, syncParts.size() - 1));
        StrinovaCompoundCollision.Part part = syncParts.get(idx);
        return List.of(
                new FieldSpec("config.strinova.collision_edit.offset_x", part.offsetX()),
                new FieldSpec("config.strinova.collision_edit.offset_y", part.offsetY()),
                new FieldSpec("config.strinova.collision_edit.offset_z", part.offsetZ()),
                new FieldSpec("config.strinova.collision_edit.size_x", part.sizeX()),
                new FieldSpec("config.strinova.collision_edit.size_y", part.sizeY()),
                new FieldSpec("config.strinova.collision_edit.size_z", part.sizeZ())
        );
    }

    /** 构建飞行模式的字段规格列表。 */
    private List<FieldSpec> buildFieldSpecsForFly() {
        return List.of(
                new FieldSpec("config.strinova.collision_edit.offset_x", flyTuning.offsetX()),
                new FieldSpec("config.strinova.collision_edit.offset_y", flyTuning.offsetY()),
                new FieldSpec("config.strinova.collision_edit.offset_z", flyTuning.offsetZ()),
                new FieldSpec("config.strinova.collision_edit.size_x", flyTuning.sizeX()),
                new FieldSpec("config.strinova.collision_edit.size_y", flyTuning.sizeY()),
                new FieldSpec("config.strinova.collision_edit.size_z", flyTuning.sizeZ())
        );
    }

    /** 按网格布局添加一组编辑字段。 */
    private int addGrid(int startY, int leftX, int cols, int fieldWidth, List<EditBox> group, List<FieldSpec> specs) {
        int y = startY;
        int i = 0;
        for (FieldSpec spec : specs) {
            int col = i % cols;
            int row = i / cols;
            int x = leftX + col * (fieldWidth + FIELD_GAP_X);
            int fy = y + row * FIELD_ROW_SPACING;
            addField(group, x, fy, fieldWidth, spec.labelKey(), spec.value());
            i++;
        }
        int rows = (specs.size() + cols - 1) / cols;
        return y + rows * FIELD_ROW_SPACING;
    }

    /** 根据可用宽度计算最佳列数。 */
    private int computeColumns(int desired) {
        int width3 = (MIN_FIELD_WIDTH * 3) + (FIELD_GAP_X * 2);
        int width2 = (MIN_FIELD_WIDTH * 2) + FIELD_GAP_X;
        if (desired >= 3 && leftWidth >= width3) {
            return 3;
        }
        if (desired >= 2 && leftWidth >= width2) {
            return 2;
        }
        return 1;
    }

    /** 根据列数计算每个字段的宽度。 */
    private int computeFieldWidth(int columns) {
        int totalGap = (columns - 1) * FIELD_GAP_X;
        return Math.max(MIN_FIELD_WIDTH, (leftWidth - totalGap) / columns);
    }

    /** 添加一个编辑字段：带标签的数值输入框，支持数字过滤和实时预览更新。 */
    private EditBox addField(List<EditBox> group, int x, int y, int width, String labelKey, double value) {
        Component label = Component.translatable(labelKey);
        EditBox box = new EditBox(this.font, x, y, width, FIELD_HEIGHT, label);
        box.setValue(format(value));
        box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*(\\.\\d*)?"));
        box.setResponder(v -> {
            if (playerId == null) {
                return;
            }
            if (isUsableNumber(v)) {
                refreshPreviewFromFields();
            }
        });
        this.addRenderableWidget(box);
        labeledFields.add(new LabeledField(box, label));
        allFields.add(box);
        group.add(box);
        return box;
    }

    /** 渲染界面：左侧编辑面板 + 右侧 3D 预览。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(this.width / 2, 0, this.width, this.height, 0xBB121212);
        graphics.fill(this.width / 2 + 2, 36, this.width / 2 + 4, this.height - 36, 0xFF4CC7FF);

        graphics.fill(leftX - 2, 12, leftX + leftWidth + 2, 42, SECTION_BG);
        graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.hint"), leftX, 16, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.hint_scroll"), leftX, 28, 0xA0A0A0, false);

        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;
        boolean isGeneric = collisionType == CollisionType.GENERIC;

        if (sync) {

            drawSectionBg(graphics, syncSectionTop, syncSectionBottom, true);
            Component syncTitle = isGeneric
                    ? Component.translatable("config.strinova.collision_edit.section.sync_generic")
                    : Component.translatable("config.strinova.collision_edit.section.sync");
            graphics.drawString(this.font, syncTitle, leftX + 4, syncTitleY, 0xFFFFFF, false);

            if (!isGeneric) {
                int titleWidth = this.font.width(syncTitle);
                int textX = leftX + 4 + titleWidth + 8 + 80 + 4;
                int textY = syncPartSelectorY + PART_SELECTOR_HEIGHT - 15;
                List<StrinovaCompoundCollision.Part> parts = syncParts;
                int idx = Math.max(0, Math.min(selectedPartIndex, parts.size() - 1));
                String partInfo = "[" + (idx + 1) + "/" + parts.size() + "] " + parts.get(idx).name();
                graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.part"), textX, textY, 0x888888, false);
                graphics.drawString(this.font, Component.literal(partInfo), textX + this.font.width(Component.translatable("config.strinova.collision_edit.part")) + 4, textY, 0xCCCCCC, false);
            }
        } else {

            drawSectionBg(graphics, flySectionTop, flySectionBottom, true);
            Component flyTitle = Component.translatable("config.strinova.collision_edit.section.fly_generic");
            graphics.drawString(this.font, flyTitle, leftX + 4, flyTitleY, 0xFFFFFF, false);
        }

        for (LabeledField field : labeledFields) {
            EditBox box = field.box();
            if (!box.visible) {
                continue;
            }
            graphics.drawString(this.font, field.label(), box.getX(), box.getY() - LABEL_GAP_Y, 0xB0B0B0, false);
        }

        renderPreviewArea(graphics, previewLeft() - 6, previewTop() - 6, previewWidth() + 12, previewHeight() + 12);
        renderPreviewDescription(graphics, previewLeft(), 40, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 绘制编辑区域的背景和边框。 */
    private void drawSectionBg(GuiGraphics graphics, int top, int bottom, boolean active) {
        int bgColor = active ? 0x33000000 : 0x18000000;
        int borderColor = active ? 0xFF666666 : 0xFF444444;
        graphics.fill(leftX - 2, top, leftX + leftWidth + 2, bottom, bgColor);

        graphics.fill(leftX - 2, top, leftX + leftWidth + 2, top + 1, borderColor);

        graphics.fill(leftX - 2, bottom - 1, leftX + leftWidth + 2, bottom, borderColor);

        graphics.fill(leftX - 2, top, leftX - 1, bottom, borderColor);

        graphics.fill(leftX + leftWidth + 1, top, leftX + leftWidth + 2, bottom, borderColor);
    }

    /** 关闭界面，可选择是否保存编辑结果。 */
    @Override
    public void onClose() {
        close(false);
    }

    private void close(boolean save) {
        if (save) {
            saveEditors();

            if (previewPlayer() != null) {
                previewPlayer().setPos(previewPlayer().getX(), previewPlayer().getY(), previewPlayer().getZ());
            }
        }
        StrinovaCollisionPreviewState.clear(playerId);
        Minecraft minecraft = this.minecraft;
        if (minecraft != null) {
            minecraft.setScreen(parentScreen());
        }
    }

    /** 获取当前模式对应的分区列表。 */
    private List<StrinovaCompoundCollision.Part> currentPartsList() {
        return currentMode() == StrinovaCollisionPreviewState.Mode.FLY ? flyParts : syncParts;
    }

    /** 获取当前模式对应的编辑字段组。 */
    private List<EditBox> currentFieldGroup() {
        return currentMode() == StrinovaCollisionPreviewState.Mode.FLY ? flyFields : syncFields;
    }

    /** 将字段值写回当前选中的分区对象。 */
    private void updateSelectedPartFromFields() {
        List<StrinovaCompoundCollision.Part> parts = currentPartsList();
        int idx = Math.max(0, Math.min(selectedPartIndex, parts.size() - 1));
        StrinovaCompoundCollision.Part old = parts.get(idx);

        List<EditBox> fields = currentFieldGroup();
        if (fields.size() < 6) return;

        double ox = parse(fields.get(0), old.offsetX());
        double oy = parse(fields.get(1), old.offsetY());
        double oz = parse(fields.get(2), old.offsetZ());
        double sx = parse(fields.get(3), old.sizeX());
        double sy = parse(fields.get(4), old.sizeY());
        double sz = parse(fields.get(5), old.sizeZ());

        StrinovaCompoundCollision.Part updated = new StrinovaCompoundCollision.Part(
                old.name(), ox, oy, oz,
                Math.max(0.01D, sx), Math.max(0.01D, sy), Math.max(0.01D, sz),
                old.ysmBoneName()
        );
        parts.set(idx, updated);
    }

    /** 用当前选中分区的数据刷新字段显示。 */
    private void refreshFieldsFromSelectedPart() {
        List<StrinovaCompoundCollision.Part> parts = currentPartsList();
        int idx = Math.max(0, Math.min(selectedPartIndex, parts.size() - 1));
        StrinovaCompoundCollision.Part part = parts.get(idx);

        List<EditBox> fields = currentFieldGroup();
        if (fields.size() >= 6) {
            setBox(fields.get(0), part.offsetX());
            setBox(fields.get(1), part.offsetY());
            setBox(fields.get(2), part.offsetZ());
            setBox(fields.get(3), part.sizeX());
            setBox(fields.get(4), part.sizeY());
            setBox(fields.get(5), part.sizeZ());
        }
    }

    /** 循环切换分区选择。 */
    private void cyclePart(int direction) {
        List<StrinovaCompoundCollision.Part> parts = currentPartsList();
        if (parts.isEmpty()) return;
        updateSelectedPartFromFields();
        int newIdx = selectedPartIndex + direction;
        if (newIdx < 0) newIdx = parts.size() - 1;
        if (newIdx >= parts.size()) newIdx = 0;
        selectedPartIndex = newIdx;
        refreshFieldsFromSelectedPart();
        updatePartSelectorButtons();
        refreshPreviewFromFields();
    }

    /** 添加新分区。 */
    private void addPart() {
        updateSelectedPartFromFields();
        List<StrinovaCompoundCollision.Part> parts = currentPartsList();
        String name = "part_" + (parts.size() + 1);
        parts.add(new StrinovaCompoundCollision.Part(name, 0.0, 0.0, 0.0, 0.30, 0.30, 0.30, null));
        selectedPartIndex = parts.size() - 1;
        refreshFieldsFromSelectedPart();
        updatePartSelectorButtons();
        refreshPreviewFromFields();
    }

    /** 删除当前选中的分区（至少保留一个）。 */
    private void removePart() {
        List<StrinovaCompoundCollision.Part> parts = currentPartsList();
        if (parts.size() <= 1) return;
        parts.remove(selectedPartIndex);
        selectedPartIndex = Math.max(0, Math.min(selectedPartIndex, parts.size() - 1));
        refreshFieldsFromSelectedPart();
        updatePartSelectorButtons();
        refreshPreviewFromFields();
    }

    /** 根据字段值刷新预览显示的碰撞箱。 */
    private void refreshPreviewFromFields() {
        if (playerId == null) {
            return;
        }
        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;

        if (!sync) {

            if (flyFields.size() >= 6) {
                double ox = parse(flyFields.get(0), 0.0);
                double oy = parse(flyFields.get(1), 0.0);
                double oz = parse(flyFields.get(2), 0.0);
                double sx = parse(flyFields.get(3), 0.6);
                double sy = parse(flyFields.get(4), 1.8);
                double sz = parse(flyFields.get(5), 0.6);
                setPreviewTuning(new StrinovaCollisionBoxTuning.Tuning(ox, oy, oz, sx, sy, sz));
            }
            return;
        }

        if (collisionType == CollisionType.GENERIC) {

            if (syncFields.size() >= 6) {
                double ox = parse(syncFields.get(0), 0.0);
                double oy = parse(syncFields.get(1), 0.0);
                double oz = parse(syncFields.get(2), 0.0);
                double sx = parse(syncFields.get(3), 0.6);
                double sy = parse(syncFields.get(4), 1.8);
                double sz = parse(syncFields.get(5), 0.6);
                setPreviewTuning(new StrinovaCollisionBoxTuning.Tuning(ox, oy, oz, sx, sy, sz));
            }
            return;
        }
        updateSelectedPartFromFields();
        StrinovaCollisionBoxTuning.Tuning union = computeUnionTuning();
        setPreviewTuning(union);

        StrinovaCollisionPreviewState.setParts(playerId, new ArrayList<>(currentPartsList()));
        StrinovaCollisionPreviewState.setSelectedPartIndex(playerId, selectedPartIndex);
    }

    /** 计算所有分区的合并包围盒。 */
    private StrinovaCollisionBoxTuning.Tuning computeUnionTuning() {
        List<StrinovaCompoundCollision.Part> parts = currentPartsList();
        if (parts.isEmpty()) {
            return new StrinovaCollisionBoxTuning.Tuning(0.0, 0.0, 0.0, 0.6, 1.8, 0.6);
        }
        AABB union = StrinovaCompoundCollision.unionAABB(parts, 0.0, 0.0, 0.0);
        double cx = (union.minX + union.maxX) * 0.5;
        double cy = (union.minY + union.maxY) * 0.5;
        double cz = (union.minZ + union.maxZ) * 0.5;
        double sx = union.getXsize();
        double sy = union.getYsize();
        double sz = union.getZsize();
        return new StrinovaCollisionBoxTuning.Tuning(cx, cy, cz, sx, sy, sz);
    }

    /** 保存编辑结果：将当前字段值写入持久化存储。 */
    private void saveEditors() {
        if (playerId == null) {
            return;
        }
        StrinovaCompoundCollision.setCollisionType(playerId, collisionType);

        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;

        if (!sync) {

            if (flyFields.size() >= 6) {
                double ox = parse(flyFields.get(0), 0.0);
                double oy = parse(flyFields.get(1), 0.0);
                double oz = parse(flyFields.get(2), 0.0);
                double sx = parse(flyFields.get(3), 0.6);
                double sy = parse(flyFields.get(4), 1.8);
                double sz = parse(flyFields.get(5), 0.6);
                StrinovaCollisionBoxTuning.Tuning baseAbs = getBaseFlyAbs(previewPlayer());
                double syStored = Math.max(0.02D, sy) - baseAbs.sizeY();
                StrinovaCollisionBoxTuning.setFlyOffset(playerId, ox, oy, oz);
                StrinovaCollisionBoxTuning.setFlySize(playerId, sx - baseAbs.sizeX(), syStored, sz - baseAbs.sizeZ());
            }
            return;
        }

        if (collisionType == CollisionType.GENERIC) {

            if (syncFields.size() >= 6) {
                double ox = parse(syncFields.get(0), 0.0);
                double oy = parse(syncFields.get(1), 0.0);
                double oz = parse(syncFields.get(2), 0.0);
                double sx = parse(syncFields.get(3), 0.6);
                double sy = parse(syncFields.get(4), 1.8);
                double sz = parse(syncFields.get(5), 0.6);
                StrinovaCollisionBoxTuning.Tuning baseAbs = getBaseSyncAbs(previewPlayer());
                double syStored = Math.max(0.05D, sy) - baseAbs.sizeY();
                StrinovaCollisionBoxTuning.setSyncOffset(playerId, ox, oy, oz);
                StrinovaCollisionBoxTuning.setSyncSize(playerId, sx - baseAbs.sizeX(), syStored, sz - baseAbs.sizeZ());
            }
            StrinovaCompoundCollision.setSyncParts(playerId, null);
            return;
        }

        updateSelectedPartFromFields();
        StrinovaCompoundCollision.setSyncParts(playerId, new ArrayList<>(syncParts));

        StrinovaCollisionBoxTuning.Tuning baseSyncAbs = getBaseSyncAbs(previewPlayer());
        StrinovaCollisionBoxTuning.Tuning baseFlyAbs = getBaseFlyAbs(previewPlayer());

        StrinovaCollisionBoxTuning.Tuning syncUnion = computeUnionForMode(true);
        StrinovaCollisionBoxTuning.Tuning flyUnion = computeUnionForMode(false);

        StrinovaCollisionBoxTuning.Tuning syncStored = toStoredSync(baseSyncAbs, syncUnion);
        StrinovaCollisionBoxTuning.Tuning flyStored = toStoredFly(baseFlyAbs, flyUnion);

        StrinovaCollisionBoxTuning.setSyncOffset(playerId, syncStored.offsetX(), syncStored.offsetY(), syncStored.offsetZ());
        StrinovaCollisionBoxTuning.setSyncSize(playerId, syncStored.sizeX(), syncStored.sizeY(), syncStored.sizeZ());
        StrinovaCollisionBoxTuning.setFlyOffset(playerId, flyStored.offsetX(), flyStored.offsetY(), flyStored.offsetZ());
        StrinovaCollisionBoxTuning.setFlySize(playerId, flyStored.sizeX(), flyStored.sizeY(), flyStored.sizeZ());
    }

    /** 计算指定模式下所有分区的合并包围盒。 */
    private StrinovaCollisionBoxTuning.Tuning computeUnionForMode(boolean sync) {
        List<StrinovaCompoundCollision.Part> parts = sync ? syncParts : flyParts;
        if (parts.isEmpty()) {
            return new StrinovaCollisionBoxTuning.Tuning(0.0, 0.0, 0.0, 0.6, 1.8, 0.6);
        }
        AABB union = StrinovaCompoundCollision.unionAABB(parts, 0.0, 0.0, 0.0);
        double cx = (union.minX + union.maxX) * 0.5;
        double cy = (union.minY + union.maxY) * 0.5;
        double cz = (union.minZ + union.maxZ) * 0.5;
        return new StrinovaCollisionBoxTuning.Tuning(cx, cy, cz,
                union.getXsize(), union.getYsize(), union.getZsize());
    }

    /** 解析编辑框中的双精度浮点数值，解析失败时返回回退值。 */
    private double parse(EditBox box, double fallback) {
        if (box == null) {
            return fallback;
        }
        String value = box.getValue();
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 判断字符串是否为有效的可用数值。 */
    private static boolean isUsableNumber(String s) {
        if (s == null) {
            return false;
        }
        String v = s.trim();
        if (v.isEmpty() || "-".equals(v) || ".".equals(v) || "-.".equals(v)) {
            return false;
        }
        try {
            double d = Double.parseDouble(v);
            return Double.isFinite(d);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** 格式化双精度浮点数为两位小数字符串。 */
    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** 设置编辑框的数值。 */
    private void setBox(EditBox box, double value) {
        if (box == null) {
            return;
        }
        box.setValue(format(value));
    }

    /** 模式切换时的回调：重建编辑区域并刷新预览。 */
    private void onModeChanged() {

        clearEditors();
        labeledFields.clear();
        buildEditors(leftX, previewTop);
        if (playerId != null) {
            StrinovaCollisionPreviewState.setCollisionType(playerId, collisionType);
        }
        refreshPreviewFromFields();
    }

    /** 清除所有编辑字段。 */
    private void clearEditors() {
        syncFields.clear();
        flyFields.clear();
        allFields.clear();
        for (LabeledField f : labeledFields) {
            removeWidget(f.box());
        }
        clearPartSelectorButtons();
    }

    /** 清除分区选择器按钮。 */
    private void clearPartSelectorButtons() {
        if (partPrevButton != null) { removeWidget(partPrevButton); partPrevButton = null; }
        if (partNextButton != null) { removeWidget(partNextButton); partNextButton = null; }
        if (addPartButton != null) { removeWidget(addPartButton); addPartButton = null; }
        if (removePartButton != null) { removeWidget(removePartButton); removePartButton = null; }
    }

    /** 设置一组编辑字段的可见性和可用性。 */
    private void setGroupActive(List<EditBox> group, boolean active) {
        for (EditBox box : group) {
            box.active = active;
            box.visible = active;
        }
    }

    /** 循环切换编辑步长。 */
    private void cycleStep() {
        double next = step == 0.01D ? 0.05D
                : step == 0.05D ? 0.10D
                : step == 0.10D ? 0.25D
                : step == 0.25D ? 0.50D
                : step == 0.50D ? 1.00D
                : 0.01D;
        step = next;
        if (stepButton != null) {
            stepButton.setMessage(stepMessage());
        }
    }

    /** 获取当前步长的按钮文本。 */
    private Component stepMessage() {
        return Component.translatable("config.strinova.collision_edit.step", format(step));
    }

    /** 重置当前模式的所有参数为默认值。 */
    private void resetCurrentMode() {
        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;

        if (!sync) {

            StrinovaCollisionBoxTuning.Tuning base = getBaseFlyAbs(previewPlayer());
            if (flyFields.size() >= 6) {
                setBox(flyFields.get(0), base.offsetX());
                setBox(flyFields.get(1), base.offsetY());
                setBox(flyFields.get(2), base.offsetZ());
                setBox(flyFields.get(3), base.sizeX());
                setBox(flyFields.get(4), base.sizeY());
                setBox(flyFields.get(5), base.sizeZ());
            }
            refreshPreviewFromFields();
            return;
        }

        if (collisionType == CollisionType.GENERIC) {
            StrinovaCollisionBoxTuning.Tuning base = getBaseSyncAbs(previewPlayer());
            if (syncFields.size() >= 6) {
                setBox(syncFields.get(0), base.offsetX());
                setBox(syncFields.get(1), base.offsetY());
                setBox(syncFields.get(2), base.offsetZ());
                setBox(syncFields.get(3), base.sizeX());
                setBox(syncFields.get(4), base.sizeY());
                setBox(syncFields.get(5), base.sizeZ());
            }
            refreshPreviewFromFields();
            return;
        }
        List<StrinovaCompoundCollision.Part> defaults = StrinovaCompoundCollision.defaultSyncParts();
        syncParts = new ArrayList<>(defaults);
        selectedPartIndex = 0;
        refreshFieldsFromSelectedPart();
        updatePartSelectorButtons();
        refreshPreviewFromFields();
    }

    /** 在分段碰撞和通用碰撞类型之间切换。 */
    private void toggleCollisionType() {
        collisionType = collisionType == CollisionType.SEGMENTED ? CollisionType.GENERIC : CollisionType.SEGMENTED;
        if (modeButton != null) {
            modeButton.setMessage(collisionTypeMessage());
        }
        clearEditors();
        labeledFields.clear();
        buildEditors(leftX, previewTop);
        if (playerId != null) {
            StrinovaCollisionPreviewState.setCollisionType(playerId, collisionType);
        }
        refreshPreviewFromFields();
    }

    /** 获取碰撞类型按钮的文本。 */
    private Component collisionTypeMessage() {
        boolean isGeneric = collisionType == CollisionType.GENERIC;
        return Component.translatable(isGeneric
                ? "config.strinova.collision_edit.collision_type.generic"
                : "config.strinova.collision_edit.collision_type.segmented");
    }

    /** 获取玩家站立姿态的同步碰撞箱基础绝对尺寸。 */
    private static StrinovaCollisionBoxTuning.Tuning getBaseSyncAbs(AbstractClientPlayer player) {
        if (player == null) {
            StrinovaCollisionBoxTuning.Tuning base = StrinovaCollisionBoxTuning.getSync(null);
            return new StrinovaCollisionBoxTuning.Tuning(0.0D, base.sizeY() * 0.5D, 0.0D, base.sizeX(), base.sizeY(), base.sizeZ());
        }
        EntityDimensions d = player.getDimensions(Pose.STANDING);
        double width = d.width;
        double height = d.height;
        return new StrinovaCollisionBoxTuning.Tuning(0.0D, height * 0.5D, 0.0D, width, height, width);
    }

    /** 获取玩家飞行模式的基础碰撞箱绝对尺寸。 */
    private static StrinovaCollisionBoxTuning.Tuning getBaseFlyAbs(AbstractClientPlayer player) {
        if (player == null) {
            return new StrinovaCollisionBoxTuning.Tuning(0.0D, 0.0D, 0.0D, 0.9D, 0.12D, 1.2D);
        }
        AABB box = player.getBoundingBox();
        EntityDimensions standing = player.getDimensions(Pose.STANDING);
        double sizeX = box.getXsize();
        double sizeY = box.getYsize();
        double sizeZ = box.getZsize();
        double standingHeight = Math.max(sizeY, standing.height);
        double standingWidth = Math.max(Math.max(sizeX, sizeZ), standing.width);
        double length = standingHeight;
        double bodyWidth = standingWidth;
        double thickness = Math.max(0.02D, standingWidth * 0.2D);

        double longHalf = Math.max(0.25D, length * 0.5D);
        double shortHalf = Math.max(0.05D, bodyWidth * 0.5D);

        boolean longAxisX = false;

        double halfX = longAxisX ? longHalf : shortHalf;
        double halfZ = longAxisX ? shortHalf : longHalf;
        return new StrinovaCollisionBoxTuning.Tuning(0.0D, StrinovaCollisionBoxTuning.getFlyBaseMinYOffset() + thickness * 0.5D, 0.0D, halfX * 2.0D, thickness, halfZ * 2.0D);
    }

    /** 将存储的同步调参转换为绝对坐标。 */
    private static StrinovaCollisionBoxTuning.Tuning toAbsSync(StrinovaCollisionBoxTuning.Tuning baseAbs, StrinovaCollisionBoxTuning.Tuning stored) {
        double finalSizeY = Math.max(0.05D, baseAbs.sizeY() + stored.sizeY());
        return new StrinovaCollisionBoxTuning.Tuning(
                stored.offsetX(),
                stored.offsetY() + finalSizeY * 0.5D,
                stored.offsetZ(),
                baseAbs.sizeX() + stored.sizeX(),
                finalSizeY,
                baseAbs.sizeZ() + stored.sizeZ()
        );
    }

    /** 将绝对同步调参转换为存储格式。 */
    private static StrinovaCollisionBoxTuning.Tuning toStoredSync(StrinovaCollisionBoxTuning.Tuning baseAbs, StrinovaCollisionBoxTuning.Tuning abs) {
        double finalSizeY = Math.max(0.05D, abs.sizeY());
        double minYOffset = abs.offsetY() - finalSizeY * 0.5D;
        return new StrinovaCollisionBoxTuning.Tuning(
                abs.offsetX(),
                minYOffset,
                abs.offsetZ(),
                abs.sizeX() - baseAbs.sizeX(),
                finalSizeY - baseAbs.sizeY(),
                abs.sizeZ() - baseAbs.sizeZ()
        );
    }

    /** 将存储的飞行调参转换为绝对坐标。 */
    private static StrinovaCollisionBoxTuning.Tuning toAbsFly(StrinovaCollisionBoxTuning.Tuning baseAbs, StrinovaCollisionBoxTuning.Tuning stored) {
        double finalSizeY = Math.max(0.02D, baseAbs.sizeY() + stored.sizeY());
        return new StrinovaCollisionBoxTuning.Tuning(
                stored.offsetX(),
                StrinovaCollisionBoxTuning.getFlyBaseMinYOffset() + stored.offsetY() + finalSizeY * 0.5D,
                stored.offsetZ(),
                baseAbs.sizeX() + stored.sizeX(),
                finalSizeY,
                baseAbs.sizeZ() + stored.sizeZ()
        );
    }

    /** 将绝对飞行调参转换为存储格式。 */
    private static StrinovaCollisionBoxTuning.Tuning toStoredFly(StrinovaCollisionBoxTuning.Tuning baseAbs, StrinovaCollisionBoxTuning.Tuning abs) {
        double finalSizeY = Math.max(0.02D, abs.sizeY());
        double minYOffset = abs.offsetY() - finalSizeY * 0.5D - StrinovaCollisionBoxTuning.getFlyBaseMinYOffset();
        return new StrinovaCollisionBoxTuning.Tuning(
                abs.offsetX(),
                minYOffset,
                abs.offsetZ(),
                abs.sizeX() - baseAbs.sizeX(),
                finalSizeY - baseAbs.sizeY(),
                abs.sizeZ() - baseAbs.sizeZ()
        );
    }

    /** 鼠标滚轮在编辑字段上时，按步长增减数值。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInPreview(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        EditBox hovered = findHoveredField(mouseX, mouseY);
        if (hovered == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        double base = parse(hovered, 0.0D);
        double next = base + Math.copySign(step, delta);
        setBox(hovered, next);
        refreshPreviewFromFields();
        return true;
    }

    /** 查找鼠标悬停的编辑字段。 */
    private EditBox findHoveredField(double mouseX, double mouseY) {
        for (EditBox box : allFields) {
            if (box.visible && box.isMouseOver(mouseX, mouseY)) {
                return box;
            }
        }
        return null;
    }

    /** 带标签的编辑字段记录。 */
    private record LabeledField(EditBox box, Component label) {
    }

    /** 字段规格记录。 */
    private record FieldSpec(String labelKey, double value) {
    }
}