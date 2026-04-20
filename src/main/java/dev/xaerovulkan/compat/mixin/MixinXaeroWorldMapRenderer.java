package dev.xaerovulkan.compat.mixin;

import dev.xaerovulkan.compat.XaeroVulkanCompat;
import dev.xaerovulkan.compat.render.VulkanCompatRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin targeting Xaero's World Map screen renderer.
 *
 * <p>The World Map is a full-screen GUI that uploads chunk tile textures via
 * {@code glTexImage2D} / {@code glTexSubImage2D} while simultaneously issuing
 * draw calls. Under VulkanMod, texture uploads must happen outside the render
 * pass, and tile draw calls need their descriptor sets re-bound after an
 * upload. Without this patch the world map either crashes or shows corrupted
 * tiles (pink/black squares).</p>
 *
 * <p><b>Target class:</b> {@code xaero.map.gui.GuiMap} (the world map screen).</p>
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class MixinXaeroWorldMapRenderer {

    /**
     * Wrap the entire world-map screen render with Vulkan pass guards.
     * Target: {@code render(MatrixStack, int, int, float)} — Minecraft's
     * Screen#render override in GuiMap.
     */
    @Inject(
            method = "render",
            at = @At("HEAD"),
            require = 0
    )
    private void xvcompat$onWorldMapRenderHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.beginXaeroRenderSection();
        }
    }

    @Inject(
            method = "render",
            at = @At("RETURN"),
            require = 0
    )
    private void xvcompat$onWorldMapRenderReturn(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.endXaeroRenderSection();
        }
    }

    /**
     * Intercept texture upload (tile bake) calls. Xaero calls
     * {@code uploadTileToGPU()} from a worker thread via a task queue;
     * we must ensure no Vulkan render pass is active on the main thread
     * during this synchronization point.
     *
     * <p>Target: {@code uploadTileToGpu(...)} (exact name from Xaero's source).</p>
     */
    @Inject(
            method = "uploadTileToGpu",
            at = @At("HEAD"),
            require = 0
    )
    private void xvcompat$onTileUploadHead(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.onBeforeFramebufferBind();
        }
    }

    @Inject(
            method = "uploadTileToGpu",
            at = @At("RETURN"),
            require = 0
    )
    private void xvcompat$onTileUploadReturn(CallbackInfo ci) {
        if (XaeroVulkanCompat.VULKAN_ACTIVE) {
            VulkanCompatRenderManager.onAfterFramebufferUnbind();
        }
    }
}
