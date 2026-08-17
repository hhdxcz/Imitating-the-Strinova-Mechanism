package com.hhdxcz.strinova.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 实体碰撞访问器：暴露 Entity 的水平碰撞状态字段
@Mixin(Entity.class)
public interface EntityCollisionAccessor {

    @Accessor("horizontalCollision")
    boolean strinova$isHorizontalCollision();

    @Accessor("minorHorizontalCollision")
    boolean strinova$isMinorHorizontalCollision();
}