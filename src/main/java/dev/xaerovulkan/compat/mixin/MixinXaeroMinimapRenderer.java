package dev.xaerovulkan.compat.mixin;

import dev.xaerovulkan.compat.XaeroVulkanCompat;
import dev.xaerovulkan.compat.render.VulkanCompatRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin targeting Xaero's Minimap HUD renderer.
 *
 * <p>The minimap renders its overlay each frame by blitting an offscreen
 * framebuffer onto the HUD layer. Under VulkanMod this blit happens outside
 * the expected Vulkan render pass scope, causing:
 * <ul>
 *   <li>Black / missing minimap</li>
 *   <li>VK_ERROR_DEVICE_LOST crash on some drivers</li>
 *   <li>Validation layer error: vkCmdBlitImage outside render pass</li>
 * </ul>
 * We wrap the entire minimap draw call with explicit render-pass guards.</p>
 *
 * <p><b>Target class:</b> {@code xaero.minimap.render.MinimapRenderer}
 * (deobfuscated name from Xaero's public API).</p>
 */
@Mixin(targets = "xaero.minimap.render.MinimapRenderer", remap = false)
public abstract class MixinXaeroMinimapRenderer {

    /**
     * Injected at the head of {@code render(MatrixStack, float, ...)} —
     * the main per-frame HUD draw method.
     *
     * <p>Ends any active Vulkan render pass so the minimap framebuffer
     * can be safely bound and blitted.</p>
     */
    @Inject(
            method = "render",
            at = @At("HEAD"),
            require = 0   // Non-fatal if Xaero Minimap is absent
    )
    private void xvcompat$onRenderHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.beginXaeroRenderSection();
        }
    }

    /**
     * Injected at RETURN of {@code render(...)} to restore the Vulkan
     * render pass after Xaero has finished drawing.
     */
    @Inject(
            method = "render",
            at = @At("RETURN"),
            require = 0
    )
    private void xvcompat$onRenderReturn(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.endXaeroRenderSection();
        }
    }

    /**
     * Intercepts the radar/entity overlay draw, which reads the depth buffer
     * to determine entity visibility. Under Vulkan, depth must be in
     * DEPTH_STENCIL_READ_ONLY_OPTIMAL layout before any read.
     *
     * <p>Target: {@code renderEntityRadar(...)} inside MinimapRenderer.</p>
     */
    @Inject(
            method = "renderEntityRadar",
            at = @At("HEAD"),
            require = 0
    )
    private void xvcompat$onRadarHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.markDepthReadSafe();
        }
    }
}
