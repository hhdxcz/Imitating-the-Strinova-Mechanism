package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.paper.StrinovaWallBlacklist;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StrinovaWallBlacklistScreen extends Screen {
    private static final int MATCH_LIMIT = 5;
    private static final int MATCH_ROW_HEIGHT = 22;
    private final Screen parent;
    private EditBox blockInput;
    private final List<Button> matchButtons = new ArrayList<>();
    private final List<ResourceLocation> matches = new ArrayList<>();
    private String lastQuery = "";
    private String status = "";
    private int matchScrollOffset = 0;
    private int blacklistScrollOffset = 0;

    public StrinovaWallBlacklistScreen(Screen parent) {
        super(Component.translatable("config.strinova.wall_blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = 44;
        this.blockInput = new EditBox(this.font, centerX - 150, top + 28, 300, 20, Component.translatable("config.strinova.wall_blacklist.input"));
        this.blockInput.setMaxLength(120);
        addRenderableWidget(this.blockInput);

        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.add"), b -> executeAdd())
                .bounds(centerX - 150, top + 56, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.remove"), b -> executeRemove())
                .bounds(centerX - 74, top + 56, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.list"), b -> executeList())
                .bounds(centerX + 2, top + 56, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.clear"), b -> executeClear())
                .bounds(centerX + 78, top + 56, 72, 20)
                .build());

        int matchY = top + 84;
        for (int i = 0; i < MATCH_LIMIT; i++) {
            final int index = i;
            Button btn = Button.builder(Component.literal(""), b -> applyMatch(index))
                    .bounds(centerX - 150, matchY + i * MATCH_ROW_HEIGHT, 300, 20)
                    .build();
            btn.visible = false;
            matchButtons.add(addRenderableWidget(btn));
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX - 40, this.height - 28, 80, 20)
                .build());
        refreshMatches();
        refreshStatusFromClientList();
        setFocused(blockInput);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.blockInput != null) {
            this.blockInput.tick();
            String q = this.blockInput.getValue();
            if (!q.equals(lastQuery)) {
                refreshMatches();
            }
        }
    }

    private void refreshMatches() {
        matches.clear();
        lastQuery = blockInput == null ? "" : blockInput.getValue();
        String q = lastQuery.trim().toLowerCase(Locale.ROOT);
        matchScrollOffset = 0;
        if (q.isEmpty()) {
            for (Button button : matchButtons) {
                button.visible = false;
            }
            return;
        }
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            String text = id.toString().toLowerCase(Locale.ROOT);
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String name = blockName(id).toLowerCase(Locale.ROOT);
            if (text.contains(q) || path.contains(q) || name.contains(q)) {
                matches.add(id);
            }
        }
        matches.sort(Comparator.comparing(ResourceLocation::toString));
        refreshMatchButtons();
    }

    private void refreshMatchButtons() {
        int maxOffset = Math.max(0, matches.size() - MATCH_LIMIT);
        if (matchScrollOffset < 0) {
            matchScrollOffset = 0;
        } else if (matchScrollOffset > maxOffset) {
            matchScrollOffset = maxOffset;
        }
        int show = Math.min(MATCH_LIMIT, Math.max(0, matches.size() - matchScrollOffset));
        for (int i = 0; i < MATCH_LIMIT; i++) {
            Button button = matchButtons.get(i);
            if (i < show) {
                ResourceLocation id = matches.get(matchScrollOffset + i);
                button.visible = true;
                button.setMessage(Component.literal(blockName(id) + " | " + id));
            } else {
                button.visible = false;
            }
        }
    }

    private void applyMatch(int index) {
        int realIndex = matchScrollOffset + index;
        if (realIndex < 0 || realIndex >= matches.size() || blockInput == null) {
            return;
        }
        blockInput.setValue(matches.get(realIndex).toString());
        refreshMatches();
    }

    private void executeAdd() {
        String id = readBlockId();
        if (id == null) {
            status = Component.translatable("config.strinova.wall_blacklist.invalid_input").getString();
            return;
        }
        sendCommand("wa wall blacklist add " + id);
    }

    private void executeRemove() {
        String id = readBlockId();
        if (id == null) {
            status = Component.translatable("config.strinova.wall_blacklist.invalid_input").getString();
            return;
        }
        sendCommand("wa wall blacklist remove " + id);
    }

    private void executeList() {
        sendCommand("wa wall blacklist list");
    }

    private void executeClear() {
        sendCommand("wa wall blacklist clear");
    }

    private String readBlockId() {
        if (blockInput == null) {
            return null;
        }
        String raw = blockInput.getValue().trim();
        if (raw.isEmpty()) {
            return null;
        }
        ResourceLocation id = resolveInputToId(raw);
        return id == null ? null : id.toString();
    }

    private ResourceLocation resolveInputToId(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null && BuiltInRegistries.BLOCK.containsKey(parsed)) {
            return parsed;
        }
        String query = raw.toLowerCase(Locale.ROOT);
        String normalizedQuery = normalizeLookup(query);
        ResourceLocation byPath = null;
        ResourceLocation byName = null;
        ResourceLocation byContains = null;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            String full = id.toString().toLowerCase(Locale.ROOT);
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String name = blockName(id).toLowerCase(Locale.ROOT);
            if (full.equals(query) || path.equals(query)) {
                return id;
            }
            String fullNormalized = normalizeLookup(full);
            String pathNormalized = normalizeLookup(path);
            String nameNormalized = normalizeLookup(name);
            if (!normalizedQuery.isEmpty() && (fullNormalized.equals(normalizedQuery) || pathNormalized.equals(normalizedQuery) || nameNormalized.equals(normalizedQuery))) {
                return id;
            }
            if (byName == null && name.equals(query)) {
                byName = id;
            }
            if (byPath == null && path.contains(query)) {
                byPath = id;
            }
            if (byContains == null && (full.contains(query) || name.contains(query)
                    || (!normalizedQuery.isEmpty() && (fullNormalized.contains(normalizedQuery) || pathNormalized.contains(normalizedQuery) || nameNormalized.contains(normalizedQuery))))) {
                byContains = id;
            }
        }
        if (byName != null) {
            return byName;
        }
        if (byPath != null) {
            return byPath;
        }
        return byContains;
    }

    private static String normalizeLookup(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private String blockName(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null) {
            return id.toString();
        }
        String name = block.getName().getString();
        return name == null || name.isEmpty() ? id.toString() : name;
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            status = Component.translatable("command.strinova.client.no_player").getString();
            return;
        }
        minecraft.player.connection.sendCommand(command);
        refreshStatusFromClientList();
    }

    private void refreshStatusFromClientList() {
        int size = StrinovaWallBlacklist.listClient().size();
        status = Component.translatable("config.strinova.wall_blacklist.count", size).getString();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.blockInput != null && this.blockInput.isFocused() && this.blockInput.keyPressed(keyCode, scanCode, modifiers)) {
            refreshMatches();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.blockInput != null && this.blockInput.isFocused() && this.blockInput.charTyped(codePoint, modifiers)) {
            refreshMatches();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInMatchArea(mouseX, mouseY) && matches.size() > MATCH_LIMIT) {
            int maxOffset = Math.max(0, matches.size() - MATCH_LIMIT);
            int step = delta > 0 ? -1 : 1;
            int next = Math.max(0, Math.min(maxOffset, matchScrollOffset + step));
            if (next != matchScrollOffset) {
                matchScrollOffset = next;
                refreshMatchButtons();
            }
            return true;
        }
        if (isInBlacklistArea(mouseX, mouseY)) {
            List<ResourceLocation> list = StrinovaWallBlacklist.listClient();
            int maxRows = getBlacklistMaxRows();
            int maxOffset = Math.max(0, list.size() - maxRows);
            if (maxOffset <= 0) {
                blacklistScrollOffset = 0;
                return true;
            }
            int step = delta > 0 ? -1 : 1;
            int next = Math.max(0, Math.min(maxOffset, blacklistScrollOffset + step));
            if (next != blacklistScrollOffset) {
                blacklistScrollOffset = next;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isInMatchArea(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int top = 44;
        int x = centerX - 150;
        int y = top + 84;
        int h = MATCH_LIMIT * MATCH_ROW_HEIGHT;
        return mouseX >= x && mouseX <= x + 300 && mouseY >= y && mouseY <= y + h;
    }

    private boolean isInBlacklistArea(double mouseX, double mouseY) {
        int x = this.width / 2 - 150;
        int y = 216;
        int w = 300;
        int h = this.height - y - 40;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + Math.max(0, h);
    }

    private int getBlacklistMaxRows() {
        int listY = 204;
        return Math.max(1, (this.height - listY - 84) / 10);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshStatusFromClientList();
        renderBackground(graphics);
        int left = this.width / 2 - 170;
        int top = 32;
        int right = this.width / 2 + 170;
        int bottom = this.height - 36;
        graphics.fill(left, top, right, bottom, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.input"), this.width / 2 - 150, top + 16, 0xB0B0B0, false);
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.tip"), this.width / 2 - 150, this.height - 52, 0x909090, false);
        graphics.drawString(this.font, Component.literal(status), this.width / 2 - 150, this.height - 64, 0xA0E0A0, false);

        List<ResourceLocation> list = StrinovaWallBlacklist.listClient();
        list.sort(ResourceLocation::compareTo);
        int listY = 204;
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.current"), this.width / 2 - 150, listY, 0xFFFFFF, false);
        int maxRows = getBlacklistMaxRows();
        int maxOffset = Math.max(0, list.size() - maxRows);
        if (blacklistScrollOffset > maxOffset) {
            blacklistScrollOffset = maxOffset;
        }
        if (blacklistScrollOffset < 0) {
            blacklistScrollOffset = 0;
        }
        int show = Math.min(maxRows, Math.max(0, list.size() - blacklistScrollOffset));
        for (int i = 0; i < show; i++) {
            ResourceLocation id = list.get(blacklistScrollOffset + i);
            graphics.drawString(this.font, blockName(id) + " | " + id, this.width / 2 - 150, listY + 12 + i * 10, 0xC8C8C8, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
