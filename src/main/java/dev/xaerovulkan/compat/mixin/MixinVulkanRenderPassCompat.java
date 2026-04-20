package dev.xaerovulkan.compat.mixin;

import dev.xaerovulkan.compat.XaeroVulkanCompat;
import dev.xaerovulkan.compat.render.VulkanCompatRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin targeting VulkanMod's render pass manager.
 *
 * <p>This mixin sits on the VulkanMod side to catch cases where VulkanMod
 * ends a render pass while Xaero's code is still mid-draw. Rather than
 * letting VulkanMod blindly end the pass (which would leave Xaero's subsequent
 * draw commands issued against a closed pass), we defer the end until Xaero
 * signals completion.</p>
 *
 * <p><b>Target class:</b> {@code net.vulkanmod.vulkan.framebuffer.RenderPass}
 * (VulkanMod's internal render-pass lifecycle class).</p>
 */
@Mixin(targets = "net.vulkanmod.vulkan.framebuffer.RenderPass", remap = false)
public abstract class MixinVulkanRenderPassCompat {

    /**
     * Intercept VulkanMod's {@code endRenderPass()} call.
     * If we are currently inside a Xaero guarded section, cancel the end
     * and let {@link VulkanCompatRenderManager#endXaeroRenderSection()} do it.
     */
    @Inject(
            method = "endRenderPass",
            at = @At("HEAD"),
            cancellable = true,
            require = 0   // Non-fatal if VulkanMod is absent or API changed
    )
    private void xvcompat$interceptEndRenderPass(CallbackInfo ci) {
        if (!XaeroVulkanCompat.VULKAN_ACTIVE) return;

        if (VulkanCompatRenderManager.isInsideXaeroSection()) {
            // Defer the end — Xaero is still drawing. The render pass will be
            // properly closed by endXaeroRenderSection() when Xaero finishes.
            XaeroVulkanCompat.LOGGER.debug(
                    "[VulkanCompatRenderManager] Deferred endRenderPass() while inside Xaero section.");
            ci.cancel();
        }
    }
}
