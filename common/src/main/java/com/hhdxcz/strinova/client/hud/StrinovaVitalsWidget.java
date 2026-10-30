package com.hhdxcz.strinova.client.hud;

import java.util.List;

/** 生命 / 护甲部件（左下，对齐真实布局：角色头像 + 生命数字/白条 + 护甲数字/紫条 + 增益提示）。 */
public final class StrinovaVitalsWidget implements StrinovaHudWidget {

    private static final int WIDTH = 248;
    private static final int HEIGHT = 68;
    private static final int AVATAR = 34;
    private static final int COLOR_ARMOR = 0xFFB06BFF;
    private static final int COLOR_HP = 0xFFEAF6FF;

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.HEALTH_ARMOR;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        return WIDTH;
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        return HEIGHT;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        float health = StrinovaHudState.getHealth();
        float maxHealth = Math.max(1.0F, StrinovaHudState.getMaxHealth());
        float armor = StrinovaHudState.getArmor();
        float maxArmor = StrinovaHudState.getMaxArmor();

        StrinovaAgentRoster.Agent agent = StrinovaAgentRoster.byId(StrinovaHudConfig.get().selectedAgentId);
        if (agent == null) {
            agent = StrinovaAgentRoster.defaultAgent();
        }

        // 角色头像方块（无贴图时用首字占位）
        painter.rect(x + 2, y + 6, AVATAR, AVATAR, 0xAA101820);
        painter.rect(x + 2, y + 6, 2, AVATAR, agent.accentColor());
        painter.border(x + 2, y + 6, AVATAR, AVATAR, 0x88FFFFFF, 1);
        String initial = agent.name().isEmpty() ? "?" : agent.name().substring(0, 1);
        painter.textScaled(initial, x + 2 + (AVATAR - painter.textWidth(initial) * 1.5F) / 2.0F, y + 11,
                1.5F, StrinovaProgrammaticPainter.COLOR_TEXT, true);

        int contentX = x + 2 + AVATAR + 8;
        int barX = contentX + 16;
        int barWidth = Math.max(60, x + WIDTH - 46 - barX);
        int numberRight = x + WIDTH - 2;

        // 生命：十字图标 + 白色血条 + 大号数字
        int hpY = y + 9;
        painter.rect(contentX, hpY + 3, 9, 2, COLOR_HP);
        painter.rect(contentX + 3, hpY, 2, 9, COLOR_HP);
        painter.bar(barX, hpY + 2, barWidth, 7, health / maxHealth, COLOR_HP, 0x66222B33);
        String hpText = String.valueOf(Math.round(health));
        float hpScale = 1.5F;
        painter.textScaled(hpText, numberRight - painter.textWidth(hpText) * hpScale, hpY - 2,
                hpScale, COLOR_HP, true);

        // 护甲：盾形图标 + 紫色护甲条 + 数字
        int armorY = y + 31;
        drawShield(painter, contentX + 4, armorY + 4, 5, COLOR_ARMOR);
        float armorRatio = maxArmor > 0.0F ? armor / maxArmor : 0.0F;
        painter.bar(barX, armorY + 1, barWidth, 7, armorRatio, COLOR_ARMOR, 0x66222B33);
        String armorText = maxArmor > 0.0F ? String.valueOf(Math.round(armor)) : "--";
        float armorScale = 1.25F;
        painter.textScaled(armorText, numberRight - painter.textWidth(armorText) * armorScale, armorY - 3,
                armorScale, 0xFFD9A8FF, true);

        // 增益提示（护甲恢复 / 静心凝势 等）
        List<StrinovaHudState.BuffEntry> buffs = StrinovaHudState.getBuffs();
        int buffY = y + 53;
        int buffX = contentX;
        for (int i = 0; i < buffs.size() && i < 2; i++) {
            StrinovaHudState.BuffEntry buff = buffs.get(i);
            painter.text("▲ " + buff.name(), buffX, buffY, buff.color(), false);
            buffX += painter.textWidth("▲ " + buff.name()) + 10;
        }
    }

    /** 绘制一个 5px 半径的小盾形图标（程序化菱形近似）。 */
    private static void drawShield(StrinovaHudPainter painter, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int width = radius - Math.abs(dy);
            painter.rect(cx - width, cy + dy, width * 2 + 1, 1, color);
        }
    }
}
