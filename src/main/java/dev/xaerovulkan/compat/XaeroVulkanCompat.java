package dev.xaerovulkan.compat;

import dev.xaerovulkan.compat.render.VulkanCompatRenderManager;
import dev.xaerovulkan.compat.util.CompatibilityChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XaeroVulkanCompat - Compatibility mod between Xaero's Minimap/World Map and VulkanMod.
 *
 * <p>This mod patches rendering pipeline inconsistencies that arise when VulkanMod
 * replaces the default OpenGL renderer with a Vulkan backend. Xaero's mods rely on
 * legacy OpenGL framebuffer and shader state assumptions that break under Vulkan.</p>
 *
 * <p>Patches applied:
 * <ul>
 *   <li>Framebuffer lifecycle management (bind/unbind ordering for Vulkan render passes)</li>
 *   <li>Depth buffer access guards for minimap radar overlay</li>
 *   <li>Shader uniform synchronization for world map tile rendering</li>
 *   <li>Render state isolation to prevent Vulkan pipeline invalidation</li>
 * </ul>
 * </p>
 */
@Environment(EnvType.CLIENT)
public class XaeroVulkanCompat implements ClientModInitializer {

    public static final String MOD_ID = "xaero_vulkan_compat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Whether VulkanMod is present and active this session. */
    public static boolean VULKAN_ACTIVE = false;

    /** Whether Xaero's Minimap is loaded. */
    public static boolean XAERO_MINIMAP_PRESENT = false;

    /** Whether Xaero's World Map is loaded. */
    public static boolean XAERO_WORLDMAP_PRESENT = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[XaeroVulkanCompat] Initializing compatibility layer...");

        VULKAN_ACTIVE       = CompatibilityChecker.isVulkanModLoaded();
        XAERO_MINIMAP_PRESENT  = CompatibilityChecker.isXaeroMinimapLoaded();
        XAERO_WORLDMAP_PRESENT = CompatibilityChecker.isXaeroWorldMapLoaded();

        LOGGER.info("[XaeroVulkanCompat] VulkanMod active      : {}", VULKAN_ACTIVE);
        LOGGER.info("[XaeroVulkanCompat] Xaero Minimap present : {}", XAERO_MINIMAP_PRESENT);
        LOGGER.info("[XaeroVulkanCompat] Xaero WorldMap present: {}", XAERO_WORLDMAP_PRESENT);

        if (!VULKAN_ACTIVE) {
            LOGGER.warn("[XaeroVulkanCompat] VulkanMod not detected. " +
                    "Compatibility patches are loaded but will remain dormant (no performance impact).");
        }

        if (!XAERO_MINIMAP_PRESENT && !XAERO_WORLDMAP_PRESENT) {
            LOGGER.warn("[XaeroVulkanCompat] Neither Xaero's Minimap nor Xaero's World Map detected. " +
                    "This mod has nothing to patch.");
        }

        // Register render manager
        VulkanCompatRenderManager.init();

        // Tick hook: re-validate render state once per second in case Vulkan
        // pipeline is recreated (e.g., after window resize / fullscreen toggle).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (VULKAN_ACTIVE && client.world != null) {
                VulkanCompatRenderManager.onClientTick();
            }
        });

        LOGGER.info("[XaeroVulkanCompat] Initialization complete.");
    }
}
