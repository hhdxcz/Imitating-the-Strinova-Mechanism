package com.hhdxcz.strinova.client.hud;

import java.util.EnumMap;
import java.util.Map;

/**
 * HUD 元素布局模型：锚点 + 偏移量。
 * 与绘制实现完全解耦：以后从程序化绘制换成贴图图集，只需要替换 Painter，布局与数据不用改。
 */
public final class StrinovaHudLayout {

    /** HUD 元素 ID，布局编辑器按此拖动。 */
    public enum Element {
        HEALTH_ARMOR,
        AMMO,
        SKILLS,
        STATE,
        CROSSHAIR,
        KILL_FEED,
        DAMAGE_NUMBERS,
        MATCH_HEADER
    }

    /** 屏幕锚点：元素内容相对屏幕的哪个位置对齐。 */
    public enum Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    /** 单个元素的布局：锚点 + 偏移。 */
    public static final class Placement {
        public Anchor anchor = Anchor.BOTTOM_LEFT;
        public int offsetX;
        public int offsetY;

        public Placement() {
        }

        public Placement(Anchor anchor, int offsetX, int offsetY) {
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public Placement copy() {
            return new Placement(anchor, offsetX, offsetY);
        }
    }

    private final Map<Element, Placement> placements = new EnumMap<>(Element.class);

    public StrinovaHudLayout() {
        reset();
    }

    /** 恢复默认布局（贴近卡拉彼丘原版习惯：顶部比分、左下血甲、底部技能、右下武器栏、右上击杀播报）。 */
    public void reset() {
        placements.clear();
        placements.put(Element.HEALTH_ARMOR, new Placement(Anchor.BOTTOM_LEFT, 12, -56));
        placements.put(Element.AMMO, new Placement(Anchor.BOTTOM_RIGHT, -10, -40));
        placements.put(Element.SKILLS, new Placement(Anchor.BOTTOM_CENTER, 0, -80));
        placements.put(Element.STATE, new Placement(Anchor.BOTTOM_LEFT, 12, -132));
        placements.put(Element.CROSSHAIR, new Placement(Anchor.CENTER, 0, 0));
        placements.put(Element.KILL_FEED, new Placement(Anchor.TOP_RIGHT, -12, 58));
        placements.put(Element.DAMAGE_NUMBERS, new Placement(Anchor.CENTER, 0, -40));
        placements.put(Element.MATCH_HEADER, new Placement(Anchor.TOP_CENTER, 0, 6));
    }

    /** 补齐缺失元素（老配置文件升级用）。 */
    public void ensureDefaults() {
        StrinovaHudLayout defaults = new StrinovaHudLayout();
        for (Element element : Element.values()) {
            placements.computeIfAbsent(element, key -> defaults.placements.get(key).copy());
        }
        for (Placement placement : placements.values()) {
            if (placement.anchor == null) {
                placement.anchor = Anchor.BOTTOM_LEFT;
            }
        }
    }

    public Placement get(Element element) {
        Placement placement = placements.get(element);
        if (placement == null) {
            ensureDefaults();
            placement = placements.get(element);
        }
        return placement;
    }

    public Anchor anchor(Element element) {
        return get(element).anchor;
    }

    public int offsetX(Element element) {
        return get(element).offsetX;
    }

    public int offsetY(Element element) {
        return get(element).offsetY;
    }

    public void setAnchor(Element element, Anchor anchor) {
        if (anchor != null) {
            get(element).anchor = anchor;
        }
    }

    public void setOffset(Element element, int offsetX, int offsetY) {
        Placement placement = get(element);
        placement.offsetX = offsetX;
        placement.offsetY = offsetY;
    }

    /**
     * 计算元素内容左上角的屏幕坐标。
     *
     * @param contentWidth  元素测量宽度
     * @param contentHeight 元素测量高度
     * @return {x, y}
     */
    public int[] resolve(Element element, int screenWidth, int screenHeight, int contentWidth, int contentHeight) {
        Placement placement = get(element);
        int baseX = switch (placement.anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> 0;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (screenWidth - contentWidth) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - contentWidth;
        };
        int baseY = switch (placement.anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
            case CENTER -> (screenHeight - contentHeight) / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - contentHeight;
        };
        return new int[]{baseX + placement.offsetX, baseY + placement.offsetY};
    }
}
