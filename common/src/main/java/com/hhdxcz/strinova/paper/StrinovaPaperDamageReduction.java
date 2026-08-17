package com.hhdxcz.strinova.paper;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理纸片化伤害减免比例，使用原子操作保证线程安全。
 * 减免值范围 [0.0, 1.0]，0 表示无减免，1 表示完全免疫。
 */
public final class StrinovaPaperDamageReduction {

    private static final AtomicLong VALUE_BITS = new AtomicLong(Double.doubleToRawLongBits(0.0D));

    private StrinovaPaperDamageReduction() {
    }

    /**
     * 获取当前的伤害减免比例。
     */
    public static double get() {
        return Double.longBitsToDouble(VALUE_BITS.get());
    }

    /**
     * 设置伤害减免比例，自动钳制到 [0.0, 1.0] 范围。
     * @return 钳制后的实际值
     */
    public static double set(double value) {
        double clamped = value;
        if (clamped < 0.0D) {
            clamped = 0.0D;
        } else if (clamped > 1.0D) {
            clamped = 1.0D;
        }
        VALUE_BITS.set(Double.doubleToRawLongBits(clamped));
        return clamped;
    }

    /**
     * 对伤害值应用减免计算。
     * @param amount 原始伤害值
     * @return 减免后的伤害值
     */
    public static float apply(float amount) {
        if (amount <= 0.0F) {
            return amount;
        }
        double reduction = get();
        if (reduction <= 0.0D) {
            return amount;
        }
        if (reduction >= 1.0D) {
            return 0.0F;
        }
        float out = (float) (amount * (1.0D - reduction));
        return Math.max(0.0F, out);
    }
}