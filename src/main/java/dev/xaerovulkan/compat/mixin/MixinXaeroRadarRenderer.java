package dev.xaerovulkan.compat.mixin;

import dev.xaerovulkan.compat.XaeroVulkanCompat;
import dev.xaerovulkan.compat.render.VulkanCompatRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin targeting Xaero's entity radar overlay renderer.
 *
 * <p>The radar draws entity icons on top of the minimap HUD. It uses a custom
 * shader that samples the depth buffer to determine whether an entity is above
 * or below the player. The depth-buffer sample requires the attachment to be in
 * a read-compatible Vulkan image layout, which vanilla Minecraft's Vulkan backend
 * transitions at a different point than Xaero expects.</p>
 *
 * <p>This mixin ensures that depth-read safety is established before the radar
 * shader runs, preventing black entity icons and potential driver hangs.</p>
 *
 * <p><b>Target class:</b> {@code xaero.minimap.radar.map.MapProcessor}
 * (handles per-frame entity position processing for the radar).</p>
 */
@Mixin(targets = "xaero.minimap.radar.map.MapProcessor", remap = false)
public abstract class MixinXaeroRadarRenderer {

    /**
     * Before the radar processes entity positions for this frame, mark the
     * depth buffer as safe to read. Under VulkanMod, {@link VulkanCompatRenderManager}
     * will have already transitioned the depth image to the correct layout
     * by the time this executes (via the per-frame beginXaeroRenderSection call).
     */
    @Inject(
            method = "process",
            at = @At("HEAD"),
            require = 0
    )
    private void xvcompat$onRadarProcessHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.markDepthReadSafe();
        }
    }

    /**
     * After radar processing completes, clear the depth-read flag so that
     * any subsequent non-Xaero code does not incorrectly assume the depth
     * buffer is still in a readable layout.
     */
    @Inject(
            method = "process",
            at = @At("RETURN"),
            require = 0
    )
    private void xvcompat$onRadarProcessReturn(CallbackInfo ci) {
        // No action needed here; VulkanCompatRenderManager.endXaeroRenderSection()
        // called from MixinXaeroMinimapRenderer already handles cleanup.
    }
}
