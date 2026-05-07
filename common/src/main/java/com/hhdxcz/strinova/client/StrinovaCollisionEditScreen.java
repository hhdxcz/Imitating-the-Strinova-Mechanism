package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
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

public class StrinovaCollisionEditScreen extends StrinovaCollisionPreviewScreen {
    private static final int LEFT_PADDING = 20;
    private static final int LEFT_TOP = 52;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_GAP_X = 8;
    private static final int FIELD_ROW_SPACING = 36;
    private static final int SECTION_GAP = 20;
    private static final int TITLE_GAP = 20;
    private static final int PREVIEW_HEADER_HEIGHT = 68;
    private static final int MIN_FIELD_WIDTH = 92;
    private static final int LABEL_GAP_Y = 10;

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

    private double step = 0.05D;

    private final List<LabeledField> labeledFields = new ArrayList<>();
    private final List<EditBox> syncFields = new ArrayList<>();
    private final List<EditBox> flyFields = new ArrayList<>();
    private final List<EditBox> allFields = new ArrayList<>();

    public StrinovaCollisionEditScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        super(parent, previewPlayer, StrinovaCollisionPreviewState.Mode.SYNC);
        this.playerId = previewPlayer == null ? null : previewPlayer.getUUID();
        StrinovaCollisionBoxTuning.Tuning baseSyncAbs = getBaseSyncAbs(previewPlayer);
        StrinovaCollisionBoxTuning.Tuning baseFlyAbs = getBaseFlyAbs(previewPlayer);

        StrinovaCollisionBoxTuning.Tuning syncStored = StrinovaCollisionBoxTuning.getCustomSync(playerId);
        StrinovaCollisionBoxTuning.Tuning flyStored = StrinovaCollisionBoxTuning.getCustomFly(playerId);

        this.syncTuning = syncStored == null ? baseSyncAbs : toAbsSync(baseSyncAbs, syncStored);
        this.flyTuning = flyStored == null ? baseFlyAbs : toAbsFly(baseFlyAbs, flyStored);
    }

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
        buildEditors(leftX, previewTop);

        modeButton = addRenderableWidget(Button.builder(Component.translatable("config.strinova.collision_preview.toggle_mode"), b -> {
                    togglePreviewMode();
                    onModeChanged();
                })
                .bounds(leftX, this.height - 28, 120, 20)
                .build());
        stepButton = addRenderableWidget(Button.builder(stepMessage(), b -> cycleStep())
                .bounds(leftX + 128, this.height - 28, 92, 20)
                .build());
        resetCurrentButton = addRenderableWidget(Button.builder(Component.translatable("config.strinova.collision_edit.reset_current"), b -> resetCurrentMode())
                .bounds(leftX + 228, this.height - 28, 110, 20)
                .build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> close(false))
                .bounds(this.width - 188, this.height - 28, 80, 20)
                .build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> close(true))
                .bounds(this.width - 104, this.height - 28, 80, 20)
                .build());

        onModeChanged();
    }

    private void buildEditors(int leftX, int top) {
        int y = top + 2;

        syncTitleY = y;
        y += TITLE_GAP;
        int syncCols = computeColumns(3);
        int syncFieldWidth = computeFieldWidth(syncCols);
        y = addGrid(y, leftX, syncCols, syncFieldWidth, syncFields, List.of(
                new FieldSpec("config.strinova.collision_edit.offset_x", syncTuning.offsetX()),
                new FieldSpec("config.strinova.collision_edit.offset_y", syncTuning.offsetY()),
                new FieldSpec("config.strinova.collision_edit.offset_z", syncTuning.offsetZ()),
                new FieldSpec("config.strinova.collision_edit.size_x", syncTuning.sizeX()),
                new FieldSpec("config.strinova.collision_edit.size_y", syncTuning.sizeY()),
                new FieldSpec("config.strinova.collision_edit.size_z", syncTuning.sizeZ())
        ));
        syncOffsetX = syncFields.get(0);
        syncOffsetY = syncFields.get(1);
        syncOffsetZ = syncFields.get(2);
        syncSizeX = syncFields.get(3);
        syncSizeY = syncFields.get(4);
        syncSizeZ = syncFields.get(5);

        y += SECTION_GAP;

        flyTitleY = y;
        y += TITLE_GAP;
        int flyCols = computeColumns(3);
        int flyFieldWidth = computeFieldWidth(flyCols);
        addGrid(y, leftX, flyCols, flyFieldWidth, flyFields, List.of(
                new FieldSpec("config.strinova.collision_edit.offset_x", flyTuning.offsetX()),
                new FieldSpec("config.strinova.collision_edit.offset_y", flyTuning.offsetY()),
                new FieldSpec("config.strinova.collision_edit.offset_z", flyTuning.offsetZ()),
                new FieldSpec("config.strinova.collision_edit.size_x", flyTuning.sizeX()),
                new FieldSpec("config.strinova.collision_edit.size_y", flyTuning.sizeY()),
                new FieldSpec("config.strinova.collision_edit.size_z", flyTuning.sizeZ())
        ));
        flyOffsetX = flyFields.get(0);
        flyOffsetY = flyFields.get(1);
        flyOffsetZ = flyFields.get(2);
        flySizeX = flyFields.get(3);
        flySizeY = flyFields.get(4);
        flySizeZ = flyFields.get(5);
    }

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

    private int computeFieldWidth(int columns) {
        int totalGap = (columns - 1) * FIELD_GAP_X;
        return Math.max(MIN_FIELD_WIDTH, (leftWidth - totalGap) / columns);
    }

    private EditBox addField(List<EditBox> group, int x, int y, int width, String labelKey, double value) {
        Component label = Component.translatable(labelKey);
        EditBox box = new EditBox(this.font, x, y, width, FIELD_HEIGHT, label);
        box.setValue(format(normalized(value)));
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(this.width / 2, 0, this.width, this.height, 0xBB121212);
        graphics.fill(this.width / 2 + 2, 36, this.width / 2 + 4, this.height - 36, 0xFF4CC7FF);
        graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.hint"), 20, 16, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.hint_scroll"), 20, 28, 0xA0A0A0, false);

        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;
        graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.section.sync"), leftX, syncTitleY, sync ? 0xFFFFFF : 0x707070, false);
        graphics.drawString(this.font, Component.translatable("config.strinova.collision_edit.section.fly"), leftX, flyTitleY, sync ? 0x707070 : 0xFFFFFF, false);

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

    @Override
    public void onClose() {
        close(false);
    }

    private void close(boolean save) {
        if (save) {
            saveEditors();
        }
        StrinovaCollisionPreviewState.clear(playerId);
        Minecraft minecraft = this.minecraft;
        if (minecraft != null) {
            minecraft.setScreen(parentScreen());
        }
    }

    private void saveEditors() {
        if (playerId == null) {
            return;
        }

        StrinovaCollisionBoxTuning.Tuning baseSyncAbs = getBaseSyncAbs(previewPlayer());
        StrinovaCollisionBoxTuning.Tuning baseFlyAbs = getBaseFlyAbs(previewPlayer());

        double soxAbs = normalized(parse(syncOffsetX, syncTuning.offsetX()));
        double soyAbs = normalized(parse(syncOffsetY, syncTuning.offsetY()));
        double sozAbs = normalized(parse(syncOffsetZ, syncTuning.offsetZ()));
        double ssxAbs = normalized(parse(syncSizeX, syncTuning.sizeX()));
        double ssyAbs = normalized(parse(syncSizeY, syncTuning.sizeY()));
        double sszAbs = normalized(parse(syncSizeZ, syncTuning.sizeZ()));

        double foxAbs = normalized(parse(flyOffsetX, flyTuning.offsetX()));
        double foyAbs = normalized(parse(flyOffsetY, flyTuning.offsetY()));
        double fozAbs = normalized(parse(flyOffsetZ, flyTuning.offsetZ()));
        double fsxAbs = normalized(parse(flySizeX, flyTuning.sizeX()));
        double fsyAbs = normalized(parse(flySizeY, flyTuning.sizeY()));
        double fszAbs = normalized(parse(flySizeZ, flyTuning.sizeZ()));

        StrinovaCollisionBoxTuning.Tuning syncAbs = new StrinovaCollisionBoxTuning.Tuning(soxAbs, soyAbs, sozAbs, ssxAbs, ssyAbs, sszAbs);
        StrinovaCollisionBoxTuning.Tuning flyAbs = new StrinovaCollisionBoxTuning.Tuning(foxAbs, foyAbs, fozAbs, fsxAbs, fsyAbs, fszAbs);

        StrinovaCollisionBoxTuning.Tuning syncStored = toStoredSync(baseSyncAbs, syncAbs);
        StrinovaCollisionBoxTuning.Tuning flyStored = toStoredFly(baseFlyAbs, flyAbs);

        StrinovaCollisionBoxTuning.setSyncOffset(playerId, syncStored.offsetX(), syncStored.offsetY(), syncStored.offsetZ());
        StrinovaCollisionBoxTuning.setSyncSize(playerId, syncStored.sizeX(), syncStored.sizeY(), syncStored.sizeZ());
        StrinovaCollisionBoxTuning.setFlyOffset(playerId, flyStored.offsetX(), flyStored.offsetY(), flyStored.offsetZ());
        StrinovaCollisionBoxTuning.setFlySize(playerId, flyStored.sizeX(), flyStored.sizeY(), flyStored.sizeZ());

        StrinovaCollisionBoxTuning.Tuning syncAbsNormalized = toAbsSync(baseSyncAbs, StrinovaCollisionBoxTuning.getSync(playerId));
        StrinovaCollisionBoxTuning.Tuning flyAbsNormalized = toAbsFly(baseFlyAbs, StrinovaCollisionBoxTuning.getFly(playerId));
        setBox(syncOffsetX, syncAbsNormalized.offsetX());
        setBox(syncOffsetY, syncAbsNormalized.offsetY());
        setBox(syncOffsetZ, syncAbsNormalized.offsetZ());
        setBox(syncSizeX, syncAbsNormalized.sizeX());
        setBox(syncSizeY, syncAbsNormalized.sizeY());
        setBox(syncSizeZ, syncAbsNormalized.sizeZ());
        setBox(flyOffsetX, flyAbsNormalized.offsetX());
        setBox(flyOffsetY, flyAbsNormalized.offsetY());
        setBox(flyOffsetZ, flyAbsNormalized.offsetZ());
        setBox(flySizeX, flyAbsNormalized.sizeX());
        setBox(flySizeY, flyAbsNormalized.sizeY());
        setBox(flySizeZ, flyAbsNormalized.sizeZ());

        refreshPreviewFromFields();
    }

    private void refreshPreviewFromFields() {
        if (playerId == null) {
            return;
        }
        setPreviewTuning(getCurrentPreviewTuning());
    }

    private StrinovaCollisionBoxTuning.Tuning getCurrentPreviewTuning() {
        return currentMode() == StrinovaCollisionPreviewState.Mode.FLY
                ? new StrinovaCollisionBoxTuning.Tuning(
                normalized(parse(flyOffsetX, flyTuning.offsetX())),
                normalized(parse(flyOffsetY, flyTuning.offsetY())),
                normalized(parse(flyOffsetZ, flyTuning.offsetZ())),
                normalized(parse(flySizeX, flyTuning.sizeX())),
                normalized(parse(flySizeY, flyTuning.sizeY())),
                normalized(parse(flySizeZ, flyTuning.sizeZ())))
                : new StrinovaCollisionBoxTuning.Tuning(
                normalized(parse(syncOffsetX, syncTuning.offsetX())),
                normalized(parse(syncOffsetY, syncTuning.offsetY())),
                normalized(parse(syncOffsetZ, syncTuning.offsetZ())),
                normalized(parse(syncSizeX, syncTuning.sizeX())),
                normalized(parse(syncSizeY, syncTuning.sizeY())),
                normalized(parse(syncSizeZ, syncTuning.sizeZ())));
    }

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

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static double normalized(double v) {
        return StrinovaCollisionBoxTuning.normalizeForUi(v);
    }

    private void setBox(EditBox box, double value) {
        if (box == null) {
            return;
        }
        box.setValue(format(value));
    }

    private void onModeChanged() {
        boolean sync = currentMode() == StrinovaCollisionPreviewState.Mode.SYNC;
        setGroupActive(syncFields, sync);
        setGroupActive(flyFields, !sync);
        refreshPreviewFromFields();
    }

    private void setGroupActive(List<EditBox> group, boolean active) {
        for (EditBox box : group) {
            box.active = active;
            box.visible = active;
        }
    }

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

    private Component stepMessage() {
        return Component.translatable("config.strinova.collision_edit.step", format(step));
    }

    private void resetCurrentMode() {
        StrinovaCollisionBoxTuning.Tuning baseSyncAbs = getBaseSyncAbs(previewPlayer());
        StrinovaCollisionBoxTuning.Tuning baseFlyAbs = getBaseFlyAbs(previewPlayer());
        if (currentMode() == StrinovaCollisionPreviewState.Mode.FLY) {
            setBox(flyOffsetX, normalized(baseFlyAbs.offsetX()));
            setBox(flyOffsetY, normalized(baseFlyAbs.offsetY()));
            setBox(flyOffsetZ, normalized(baseFlyAbs.offsetZ()));
            setBox(flySizeX, normalized(baseFlyAbs.sizeX()));
            setBox(flySizeY, normalized(baseFlyAbs.sizeY()));
            setBox(flySizeZ, normalized(baseFlyAbs.sizeZ()));
        } else {
            setBox(syncOffsetX, normalized(baseSyncAbs.offsetX()));
            setBox(syncOffsetY, normalized(baseSyncAbs.offsetY()));
            setBox(syncOffsetZ, normalized(baseSyncAbs.offsetZ()));
            setBox(syncSizeX, normalized(baseSyncAbs.sizeX()));
            setBox(syncSizeY, normalized(baseSyncAbs.sizeY()));
            setBox(syncSizeZ, normalized(baseSyncAbs.sizeZ()));
        }
        refreshPreviewFromFields();
    }

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

        float yawDeg = player.getYRot();
        double yawRad = Math.toRadians(yawDeg);
        boolean longAxisX = Math.abs(Math.sin(yawRad)) > Math.abs(Math.cos(yawRad));

        double halfX = longAxisX ? longHalf : shortHalf;
        double halfZ = longAxisX ? shortHalf : longHalf;
        return new StrinovaCollisionBoxTuning.Tuning(0.0D, StrinovaCollisionBoxTuning.getFlyBaseMinYOffset() + thickness * 0.5D, 0.0D, halfX * 2.0D, thickness, halfZ * 2.0D);
    }

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
        double next = normalized(base + Math.copySign(step, delta));
        setBox(hovered, next);
        refreshPreviewFromFields();
        return true;
    }

    private EditBox findHoveredField(double mouseX, double mouseY) {
        for (EditBox box : allFields) {
            if (box.visible && box.isMouseOver(mouseX, mouseY)) {
                return box;
            }
        }
        return null;
    }

    private record LabeledField(EditBox box, Component label) {
    }

    private record FieldSpec(String labelKey, double value) {
    }
}
