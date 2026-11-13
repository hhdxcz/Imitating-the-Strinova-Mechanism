package com.hhdxcz.strinova.client.hud;

import net.minecraft.resources.ResourceLocation;

/**
 * 给顶部面板图层叠加整体透明度的 painter 代理；变换调用原样透传。
 */
final class StrinovaAlphaPainter implements StrinovaHudPainter {

    private final StrinovaHudPainter delegate;
    private final float opacity;

    StrinovaAlphaPainter(StrinovaHudPainter delegate, float opacity) {
        this.delegate = delegate;
        this.opacity = Math.max(0.0F, Math.min(1.0F, opacity));
    }

    private int alpha(int argb) {
        int a = (argb >>> 24) & 0xFF;
        return (Math.round(a * this.opacity) << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public int textWidth(String text) {
        return this.delegate.textWidth(text);
    }

    @Override
    public int textHeight() {
        return this.delegate.textHeight();
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        this.delegate.text(text, x, y, alpha(argb), shadow);
    }

    @Override
    public void textScaled(String text, float x, float y, float scale, int argb, boolean shadow) {
        this.delegate.textScaled(text, x, y, scale, alpha(argb), shadow);
    }

    @Override
    public void textRightAligned(String text, int rightX, int y, int argb, boolean shadow) {
        this.delegate.textRightAligned(text, rightX, y, alpha(argb), shadow);
    }

    @Override
    public void rect(int x, int y, int width, int height, int argb) {
        this.delegate.rect(x, y, width, height, alpha(argb));
    }

    @Override
    public void panel(int x, int y, int width, int height, int accentArgb) {
        this.delegate.panel(x, y, width, height, alpha(accentArgb));
    }

    @Override
    public void border(int x, int y, int width, int height, int argb, int thickness) {
        this.delegate.border(x, y, width, height, alpha(argb), thickness);
    }

    @Override
    public void bar(int x, int y, int width, int height, float ratio, int fillArgb, int backgroundArgb) {
        this.delegate.bar(x, y, width, height, ratio, alpha(fillArgb), alpha(backgroundArgb));
    }

    @Override
    public void icon(String iconId, int x, int y, int size, int argb) {
        this.delegate.icon(iconId, x, y, size, alpha(argb));
    }

    @Override
    public void line(int x1, int y1, int x2, int y2, int argb) {
        this.delegate.line(x1, y1, x2, y2, alpha(argb));
    }

    @Override
    public void texture(ResourceLocation texture, int x, int y, int width, int height, int argb, float alpha) {
        this.delegate.texture(texture, x, y, width, height, alpha(argb), alpha * this.opacity);
    }

    @Override
    public void textureRegion(ResourceLocation texture, int x, int y, int width, int height,
                              float u, float v, int uWidth, int vHeight, int texWidth, int texHeight,
                              int argb, float alpha) {
        this.delegate.textureRegion(texture, x, y, width, height, u, v, uWidth, vHeight,
                texWidth, texHeight, alpha(argb), alpha * this.opacity);
    }

    @Override
    public void pushTransform(float centerX, float centerY, float rotationDeg,
                              boolean mirrorX, boolean mirrorY) {
        this.delegate.pushTransform(centerX, centerY, rotationDeg, mirrorX, mirrorY);
    }

    @Override
    public void popTransform() {
        this.delegate.popTransform();
    }
}
