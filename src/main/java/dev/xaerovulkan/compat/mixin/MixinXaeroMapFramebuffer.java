package dev.xaerovulkan.compat.mixin;

import dev.xaerovulkan.compat.XaeroVulkanCompat;
import dev.xaerovulkan.compat.render.VulkanCompatRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin targeting Xaero's custom Framebuffer wrapper.
 *
 * <p>Xaero's mods maintain their own {@code XaeroFramebuffer} class that wraps
 * OpenGL FBO creation, binding, and blitting. Under VulkanMod, any attempt to
 * bind a non-main framebuffer mid render-pass triggers a Vulkan validation error
 * and in many cases a hard crash.</p>
 *
 * <p>This mixin intercepts every {@code bind()} / {@code unbind()} call and
 * delegates to {@link VulkanCompatRenderManager} to manage render-pass state.</p>
 *
 * <p><b>Target class:</b> {@code xaero.common.misc.graphics.XaeroFramebuffer}
 * (used by both Minimap and World Map).</p>
 */
@Mixin(targets = "xaero.common.misc.graphics.XaeroFramebuffer", remap = false)
public abstract class MixinXaeroMapFramebuffer {

    /**
     * Before Xaero binds its off-screen framebuffer, end any active Vulkan
     * render pass so the FBO swap is legal.
     */
    @Inject(
            method = "bind",
            at = @At("HEAD"),
            require = 0
    )
    private void xvcompat$onBindHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.onBeforeFramebufferBind();
        }
    }

    /**
     * After Xaero unbinds its framebuffer and restores the main target,
     * re-open the Vulkan render pass.
     */
    @Inject(
            method = "unbind",
            at = @At("RETURN"),
            require = 0
    )
    private void xvcompat$onUnbindReturn(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.onAfterFramebufferUnbind();
        }
    }

    /**
     * {@code blit()} copies the offscreen FBO onto the main framebuffer.
     * We need to ensure the main framebuffer color attachment is in
     * TRANSFER_DST_OPTIMAL layout before the blit, then transition it back.
     * The render manager handles this via the flush/resume cycle.
     */
    @Inject(
            method = "blit",
            at = @At("HEAD"),
            require = 0
    )
    private void xvcompat$onBlitHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.onBeforeFramebufferBind();
        }
    }

    @Inject(
            method = "blit",
            at = @At("RETURN"),
            require = 0
    )
    private void xvcompat$onBlitReturn(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.onAfterFramebufferUnbind();
        }
    }
}
