package com.hhdxcz.strinova.paper;

import java.util.concurrent.atomic.AtomicLong;

public final class StrinovaPaperDamageReduction {

    private static final AtomicLong VALUE_BITS = new AtomicLong(Double.doubleToRawLongBits(0.0D));

    private StrinovaPaperDamageReduction() {
    }

    public static double get() {
        return Double.longBitsToDouble(VALUE_BITS.get());
    }

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

