package dev.xaerovulkan.compat.util;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Utility class that checks whether optional dependency mods are present
 * in the current game instance using the Fabric Loader API.
 */
public final class CompatibilityChecker {

    private CompatibilityChecker() {}

    /** Returns true if VulkanMod is installed. */
    public static boolean isVulkanModLoaded() {
        return FabricLoader.getInstance().isModLoaded("vulkanmod");
    }

    /** Returns true if Xaero's Minimap is installed. */
    public static boolean isXaeroMinimapLoaded() {
        return FabricLoader.getInstance().isModLoaded("xaerominimap");
    }

    /** Returns true if Xaero's World Map is installed. */
    public static boolean isXaeroWorldMapLoaded() {
        return FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    }

    /**
     * Returns a human-readable summary of detected mods, useful for
     * logging and crash reports.
     */
    public static String getCompatibilitySummary() {
        return String.format(
                "VulkanMod=%b | XaeroMinimap=%b | XaeroWorldMap=%b",
                isVulkanModLoaded(),
                isXaeroMinimapLoaded(),
                isXaeroWorldMapLoaded()
        );
    }
}
