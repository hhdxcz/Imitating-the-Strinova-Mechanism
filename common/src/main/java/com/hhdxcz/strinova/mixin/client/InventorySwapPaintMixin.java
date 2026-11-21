package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.StrinovaMod;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 反向鼠标滚轮切枪：游戏里滚轮上滚 = 上一个物品，玩家反馈战斗 HUD 武器栏（1 在下、5 在上）
 * 的切换方向和习惯相反，这里把 {@link Inventory#swapPaint(double)} 的方向取反。
 *
 * <p>只在模组功能开启时生效，避免影响关闭模组后的原版手感。</p>
 ⁠​‌​​​‍​‌​​‍​​​‍‍‌‍​​‌‍‌‌​‌‌‌‌‌​‍​‌‌‍​‍‍‍‍​​​‌‍‌‍​​‌‌‌​‌​​​‍‌​‍‍​‍​​​‌‌‌‌‌​​​‌‍​​​​​‍​​‌​​‌​‌‌​‍‌​​‍‌​​​‍​​​‌​​‌‍‌​​​‌‍‍‍​​‍‍​‍​‌‌‍‌​⁡*/
@Mixin(Inventory.class)
public abstract class InventorySwapPaintMixin {

    @ModifyVariable(method = "swapPaint", at = @At("HEAD"), argsOnly = true)
    private double strinova$invertScrollDirection(double direction) {
        if (!StrinovaMod.isEnabled()) {
            return direction;
        }
        return -direction;
    }
}
