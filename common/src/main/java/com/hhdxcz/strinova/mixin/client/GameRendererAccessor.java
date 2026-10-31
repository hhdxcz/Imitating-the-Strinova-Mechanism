package com.hhdxcz.strinova.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// GameRenderer 访问器：loadEffect 是包内可见方法，Mixin invoker 绕过可见性且兼容生产环境映射
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("loadEffect")
    void strinova$loadEffect(ResourceLocation location);
}
