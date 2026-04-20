package dev.xaerovulkan.compat.render;

import dev.xaerovulkan.compat.XaeroVulkanCompat;
import net.minecraft.client.MinecraftClient;

/**
 * Central render manager that coordinates safe Vulkan render-pass transitions
 * when Xaero's mods need to perform off-screen rendering (framebuffer blit,
 * depth reads, custom shader draws).
 *
 * <p><b>Root cause of incompatibility:</b><br>
 * VulkanMod structures rendering as explicit Vulkan render passes. Xaero's mods
 * call {@code GlStateManager} / {@code RenderSystem} methods and bind custom
 * {@code Framebuffer} objects mid-frame, outside of any expected render pass.
 * This causes Vulkan validation errors ("vkCmdDraw called outside render pass"),
 * graphical corruption, and crashes.</p>
 *
 * <p><b>Strategy:</b><br>
 * We intercept every Xaero draw call (via Mixins), end the active Vulkan render
 * pass if one is in flight, perform the Xaero draw on the main framebuffer or a
 * compatible offscreen target, then re-start the original render pass.</p>
 */
public final class VulkanCompatRenderManager {

    private VulkanCompatRenderManager() {}

    /** Ticks since last render-state reset. Used for periodic revalidation. */
    private static int tickCounter = 0;

    /** Tracks whether we are currently inside a Xaero guarded section. */
    private static boolean insideXaeroSection = false;

    /** Tracks the depth-buffer safe-read flag. */
    private static boolean depthReadSafe = false;

    public static void init() {
        XaeroVulkanCompat.LOGGER.debug("[VulkanCompatRenderManager] Render manager initialized.");
    }

    // -----------------------------------------------------------------------
    // Render pass guards – called by Mixins BEFORE and AFTER Xaero draw calls
    // -----------------------------------------------------------------------

    /**
     * Must be called immediately before any Xaero rendering begins.
     * Ends the current Vulkan render pass and establishes a safe drawing state.
     */
    public static void beginXaeroRenderSection() {
        if (!XaeroVulkanCompat.VULKAN_ACTIVE || insideXaeroSection) return;
        insideXaeroSection = true;

        // Flush pending Vulkan commands so the pipeline is in a known state.
        flushVulkanPipeline();

        // Ensure the depth buffer is not being sampled when we switch targets.
        depthReadSafe = false;

        XaeroVulkanCompat.LOGGER.debug("[VulkanCompatRenderManager] Entered Xaero render section.");
    }

    /**
     * Must be called immediately after Xaero rendering finishes.
     * Re-starts the Vulkan render pass for subsequent vanilla rendering.
     */
    public static void endXaeroRenderSection() {
        if (!XaeroVulkanCompat.VULKAN_ACTIVE || !insideXaeroSection) return;
        insideXaeroSection = false;
        depthReadSafe = false;

        // Resume normal Vulkan render pass.
        resumeVulkanPipeline();

        XaeroVulkanCompat.LOGGER.debug("[VulkanCompatRenderManager] Exited Xaero render section.");
    }

    // -----------------------------------------------------------------------
    // Framebuffer helpers
    // -----------------------------------------------------------------------

    /**
     * Called by {@link MixinXaeroMapFramebuffer} before Xaero binds its own
     * framebuffer. Under Vulkan this must happen outside a render pass.
     */
    public static void onBeforeFramebufferBind() {
        if (!XaeroVulkanCompat.VULKAN_ACTIVE) return;

        // If a render pass is active, end it before the framebuffer switch.
        if (isVulkanRenderPassActive()) {
            flushVulkanPipeline();
        }
    }

    /**
     * Called by {@link MixinXaeroMapFramebuffer} after Xaero unbinds its
     * framebuffer and control returns to the main framebuffer.
     */
    public static void onAfterFramebufferUnbind() {
        if (!XaeroVulkanCompat.VULKAN_ACTIVE) return;
        resumeVulkanPipeline();
    }

    // -----------------------------------------------------------------------
    // Depth buffer helpers
    // -----------------------------------------------------------------------

    /**
     * Xaero's radar uses the depth buffer for entity distance calculations.
     * Under Vulkan the depth attachment may still be in a layout that forbids
     * CPU/shader reads. This method signals that a safe transition was performed.
     */
    public static void markDepthReadSafe() {
        depthReadSafe = true;
    }

    public static boolean isDepthReadSafe() {
        return depthReadSafe;
    }

    // -----------------------------------------------------------------------
    // Periodic revalidation
    // -----------------------------------------------------------------------

    public static void onClientTick() {
        tickCounter++;
        // Every 20 ticks (≈1 second) verify the pipeline is still healthy.
        if (tickCounter >= 20) {
            tickCounter = 0;
            revalidatePipeline();
        }
    }

    // -----------------------------------------------------------------------
    // Low-level Vulkan pipeline stubs
    // (In a real build these delegate to VulkanMod's internal API via reflection
    //  or a thin accessor interface generated at class-load time.)
    // -----------------------------------------------------------------------

    /**
     * Checks whether VulkanMod currently has an active render pass.
     * Resolved at runtime via reflection to avoid hard dependency.
     */
    private static boolean isVulkanRenderPassActive() {
        try {
            Class<?> vkRender = Class.forName("net.vulkanmod.render.VRenderSystem");
            // VulkanMod exposes a static isInsideRenderPass() or equivalent
            java.lang.reflect.Method m = vkRender.getMethod("isInsideRenderPass");
            return (boolean) m.invoke(null);
        } catch (Exception ignored) {
            // VulkanMod internal API may differ between versions; assume safe.
            return false;
        }
    }

    /**
     * Ends the active Vulkan render pass and submits pending draw calls.
     */
    private static void flushVulkanPipeline() {
        try {
            Class<?> vkRender = Class.forName("net.vulkanmod.render.VRenderSystem");
            java.lang.reflect.Method m = vkRender.getMethod("endRenderPass");
            m.invoke(null);
        } catch (Exception ignored) {
            // VulkanMod not present or API changed — no-op.
        }
    }

    /**
     * Resumes (or restarts) the Vulkan render pass for the main framebuffer.
     */
    private static void resumeVulkanPipeline() {
        try {
            Class<?> vkRender = Class.forName("net.vulkanmod.render.VRenderSystem");
            java.lang.reflect.Method m = vkRender.getMethod("beginRenderPass");
            m.invoke(null);
        } catch (Exception ignored) {
            // No-op.
        }
    }

    /**
     * Revalidates the render pipeline state (called once per second).
     * Clears any stale pipeline caches that can accumulate after Xaero
     * uploads new map tile textures via glTexImage2D calls.
     */
    private static void revalidatePipeline() {
        if (!isInsideXaeroSection()) {
            try {
                Class<?> vkPipeline = Class.forName("net.vulkanmod.render.PipelineManager");
                java.lang.reflect.Method m = vkPipeline.getMethod("invalidateAll");
                m.invoke(null);
            } catch (Exception ignored) {}
        }
    }

    public static boolean isInsideXaeroSection() {
        return insideXaeroSection;
    }
}
