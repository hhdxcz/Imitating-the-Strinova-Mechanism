package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.paper.StrinovaWallBlacklist;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 穿墙方块黑名单管理界面。
 * 支持搜索方块、切换视图（全部/仅黑名单）、切换黑名单状态、分享导入等功能。
 */
public class StrinovaWallBlacklistScreen extends Screen {
    private static final int PANEL_TOP = 20;
    private static final int SEARCH_TOP = 44;
    private static final int SEARCH_HEIGHT = 20;
    private static final int BUTTON_ROW_Y = 70;
    private static final int LIST_TOP = 96;
    private static final int ENTRY_HEIGHT = 22;

    private final Screen parent;

    private EditBox searchBox;
    private Button viewToggleButton;

    private final List<ResourceLocation> allBlocks = new ArrayList<>();
    private final List<ResourceLocation> displayed = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean showOnlyBlacklist = false;
    private String lastQuery = "";
    private String status = "";
    private Set<ResourceLocation> cachedBlacklist = new HashSet<>();

    private int panelLeft;
    private int panelRight;
    private int listBottom;

    public StrinovaWallBlacklistScreen(Screen parent) {
        super(Component.translatable("config.strinova.wall_blacklist.title"));
        this.parent = parent;
    }

    /** 初始化界面组件：搜索框、功能按钮、构建方块列表并刷新显示。 */
    @Override
    protected void init() {
        this.panelLeft = this.width / 2 - 180;
        this.panelRight = this.width / 2 + 180;
        this.listBottom = this.height - 86;

        int contentX = this.panelLeft + 12;
        int contentWidth = this.panelRight - this.panelLeft - 24;

        this.searchBox = new EditBox(this.font, contentX, SEARCH_TOP, contentWidth, SEARCH_HEIGHT,
                Component.translatable("config.strinova.wall_blacklist.search"));
        this.searchBox.setMaxLength(120);
        addRenderableWidget(this.searchBox);

        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.share"), b -> share())
                .bounds(contentX, BUTTON_ROW_Y, 56, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.import"), b -> openImport())
                .bounds(contentX + 62, BUTTON_ROW_Y, 56, 20)
                .build());
        this.viewToggleButton = addRenderableWidget(Button.builder(
                        Component.translatable(viewToggleKey()), b -> toggleView())
                .bounds(contentX + 124, BUTTON_ROW_Y, 128, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.clear"), b -> clearAll())
                .bounds(contentX + 258, BUTTON_ROW_Y, 56, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 40, this.height - 28, 80, 20)
                .build());

        buildAllBlocks();
        this.cachedBlacklist = new HashSet<>(StrinovaWallBlacklist.listClient());
        refreshDisplayed();
        setFocused(this.searchBox);
    }

    /** 收集所有注册的方块到列表中，过滤掉没有物品形式的方块。 */
    private void buildAllBlocks() {
        allBlocks.clear();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null || block.asItem() == Items.AIR) {
                continue;
            }
            allBlocks.add(id);
        }
        allBlocks.sort(ResourceLocation::compareTo);
    }

    /** 根据当前搜索词和视图模式刷新显示列表。 */
    private void refreshDisplayed() {
        displayed.clear();
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        Set<ResourceLocation> blocked = new HashSet<>(StrinovaWallBlacklist.listClient());
        for (ResourceLocation id : allBlocks) {
            if (showOnlyBlacklist && !blocked.contains(id)) {
                continue;
            }
            if (!q.isEmpty()) {
                String full = id.toString().toLowerCase(Locale.ROOT);
                String name = blockName(id).toLowerCase(Locale.ROOT);
                if (!full.contains(q) && !name.contains(q)) {
                    continue;
                }
            }
            displayed.add(id);
        }
        int maxRows = Math.max(1, (listBottom - LIST_TOP) / ENTRY_HEIGHT);
        int maxOffset = Math.max(0, displayed.size() - maxRows);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
    }

    /** 获取方块的本地化显示名称。 */
    private String blockName(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null) {
            return id.toString();
        }
        String name = block.getName().getString();
        return name == null || name.isEmpty() ? id.toString() : name;
    }

    /** 切换视图模式：全部方块 / 仅黑名单方块。 */
    private void toggleView() {
        showOnlyBlacklist = !showOnlyBlacklist;
        if (viewToggleButton != null) {
            viewToggleButton.setMessage(Component.translatable(viewToggleKey()));
        }
        scrollOffset = 0;
        refreshDisplayed();
    }

    /** 获取当前视图切换按钮的本地化键。 */
    private String viewToggleKey() {
        return showOnlyBlacklist
                ? "config.strinova.wall_blacklist.show_all"
                : "config.strinova.wall_blacklist.show_blacklist";
    }

    /** 将当前黑名单编码为分享码并复制到剪贴板。 */
    private void share() {
        List<ResourceLocation> list = StrinovaWallBlacklist.listClient();
        String code = StrinovaWallBlacklist.encodeShareCode(list);
        if (minecraft != null && minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(code);
        }
        status = Component.translatable("config.strinova.wall_blacklist.shared", list.size()).getString();
    }

    /** 打开黑名单导入界面。 */
    private void openImport() {
        if (minecraft != null) {
            minecraft.setScreen(new StrinovaWallBlacklistImportScreen(this));
        }
    }

    /** 发送清空黑名单的命令。 */
    private void clearAll() {
        sendCommand("strinova wall blacklist clear");
    }

    /** 切换指定方块的加入/移出黑名单状态。 */
    private void toggleBlock(ResourceLocation id) {
        boolean selected = StrinovaWallBlacklist.listClient().contains(id);
        sendCommand(selected
                ? "strinova wall blacklist remove " + id
                : "strinova wall blacklist add " + id);
    }

    /** 向服务端发送命令。 */
    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            status = Component.translatable("command.strinova.client.no_player").getString();
            return;
        }
        minecraft.player.connection.sendCommand(command);
    }

    /** 每帧更新：检测搜索词变化和黑名单变化，自动刷新列表。 */
    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) {
            searchBox.tick();
            String q = searchBox.getValue();
            if (!q.equals(lastQuery)) {
                lastQuery = q;
                scrollOffset = 0;
                refreshDisplayed();
            }
        }
        Set<ResourceLocation> current = new HashSet<>(StrinovaWallBlacklist.listClient());
        if (!current.equals(cachedBlacklist)) {
            cachedBlacklist = current;
            refreshDisplayed();
        }
    }

    /** 鼠标点击在列表区域内时，切换对应方块的黑名单状态。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInList(mouseX, mouseY)) {
            int index = (int) ((mouseY - LIST_TOP) / ENTRY_HEIGHT);
            int real = scrollOffset + index;
            if (real >= 0 && real < displayed.size()) {
                toggleBlock(displayed.get(real));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标滚轮在列表区域内时，滚动列表。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInList(mouseX, mouseY)) {
            int maxRows = Math.max(1, (listBottom - LIST_TOP) / ENTRY_HEIGHT);
            int maxOffset = Math.max(0, displayed.size() - maxRows);
            if (maxOffset > 0) {
                int step = delta > 0 ? -1 : 1;
                scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + step));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 判断鼠标坐标是否在列表区域内。 */
    private boolean isInList(double mouseX, double mouseY) {
        return mouseX >= panelLeft + 4 && mouseX <= panelRight - 4
                && mouseY >= LIST_TOP && mouseY <= listBottom;
    }

    /** 渲染界面：面板背景、标题、方块列表及底部状态信息。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(panelLeft, PANEL_TOP, panelRight, this.height - 40, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, PANEL_TOP + 8, 0xFFFFFF);

        int contentX = panelLeft + 12;
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.search"),
                contentX, SEARCH_TOP - 11, 0xB0B0B0, false);

        renderList(graphics, mouseX, mouseY);

        int count = StrinovaWallBlacklist.listClient().size();
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.count", count),
                contentX, this.height - 78, 0xA0E0A0, false);
        String middle = status.isEmpty()
                ? Component.translatable("config.strinova.wall_blacklist.hint").getString()
                : status;
        graphics.drawString(this.font, Component.literal(middle),
                contentX, this.height - 66, 0xE0E0E0, false);
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.tip"),
                contentX, this.height - 54, 0x909090, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 渲染方块列表，每行显示方块图标、名称和注册 ID，黑名单方块带有绿色边框高亮。 */
    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listLeft = panelLeft + 4;
        int listRight = panelRight - 4;
        int maxRows = Math.max(1, (listBottom - LIST_TOP) / ENTRY_HEIGHT);
        int maxOffset = Math.max(0, displayed.size() - maxRows);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        int show = Math.min(maxRows, displayed.size() - scrollOffset);

        Set<ResourceLocation> blocked = new HashSet<>(StrinovaWallBlacklist.listClient());

        for (int i = 0; i < show; i++) {
            ResourceLocation id = displayed.get(scrollOffset + i);
            int y = LIST_TOP + i * ENTRY_HEIGHT;
            boolean selected = blocked.contains(id);
            boolean hovered = mouseX >= listLeft && mouseX <= listRight
                    && mouseY >= y && mouseY < y + ENTRY_HEIGHT;

            int bg = selected ? 0x1A2E7D32 : (hovered ? 0x20FFFFFF : 0x12000000);
            graphics.fill(listLeft, y, listRight, y + ENTRY_HEIGHT, bg);

            if (selected) {
                int green = 0xFF3FBF3F;
                graphics.fill(listLeft, y, listRight, y + 1, green);
                graphics.fill(listLeft, y + ENTRY_HEIGHT - 1, listRight, y + ENTRY_HEIGHT, green);
                graphics.fill(listLeft, y, listLeft + 1, y + ENTRY_HEIGHT, green);
                graphics.fill(listRight - 1, y, listRight, y + ENTRY_HEIGHT, green);
            }

            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null && block.asItem() != Items.AIR) {
                graphics.renderItem(new ItemStack(block.asItem()), listLeft + 3, y + 3);
            }

            int textX = listLeft + 24;
            graphics.drawString(this.font, Component.literal(blockName(id)), textX, y + 2, 0xFFFFFF, false);
            graphics.drawString(this.font, Component.literal(id.toString()), textX, y + 12, 0x909090, false);
        }
    }

    /** 关闭界面时返回父界面。 */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}