package com.hhdxcz.strinova.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityCollisionAccessor {

    @Accessor("horizontalCollision")
    boolean strinova$isHorizontalCollision();

    @Accessor("minorHorizontalCollision")
    boolean strinova$isMinorHorizontalCollision();
}
