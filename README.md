# Xaero's Vulkan Compat

**Compatibility mod for Minecraft 1.21.1 (Fabric)**

Fixes crashes and rendering issues when using **Xaero's Minimap** and/or **Xaero's World Map** together with **VulkanMod**.

---

## ⚠️ Prerequisites

| Mod | Required? |
|-----|-----------|
| [Fabric Loader](https://fabricmc.net/) ≥ 0.16.5 | ✅ Yes |
| [Fabric API](https://modrinth.com/mod/fabric-api) | ✅ Yes |
| [VulkanMod](https://modrinth.com/mod/vulkanmod) | Optional (mod is dormant without it) |
| [Xaero's Minimap](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap) | Optional |
| [Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map) | Optional |

> **This mod does nothing if VulkanMod is not installed.** It is safe to keep in your modpack even for players who don't use Vulkan.

---

## 🐛 Issues Fixed

### Xaero's Minimap
| Bug | Root Cause | Fix Applied |
|-----|-----------|-------------|
| Black / missing minimap | FBO blit outside Vulkan render pass | Mixin wraps render with pass guards |
| Entity radar icons invisible | Depth buffer in wrong image layout | Depth-read safety flag + layout transition |
| `VK_ERROR_DEVICE_LOST` crash | Draw commands after render pass end | Render pass deferred close |

### Xaero's World Map
| Bug | Root Cause | Fix Applied |
|-----|-----------|-------------|
| Pink / black map tiles | Texture upload during active render pass | Flush pipeline before `glTexImage2D` |
| Crash opening world map | FBO bind inside render pass | Framebuffer bind/unbind guards |
| Corrupted overlay on resize | Stale pipeline cache | Periodic revalidation (1/sec) |

---

## 🛠️ How It Works

VulkanMod restructures Minecraft's rendering as explicit **Vulkan render passes**. Xaero's mods were written against OpenGL's stateless model and perform operations that are **illegal inside a Vulkan render pass**:

- Binding custom framebuffers (`glBindFramebuffer`)
- Uploading textures (`glTexImage2D`, `glTexSubImage2D`)
- Reading the depth buffer for radar
- Blitting between framebuffers

This mod inserts **Mixin-based guards** around all such operations:

```
[Vanilla frame begin]
  → Vulkan render pass starts (VulkanMod)
    ┌─ Xaero render detected ──────────────────────────────┐
    │  endRenderPass()    ← MixinVulkanRenderPassCompat     │
    │  [Xaero draws here – safe outside render pass]        │
    │  beginRenderPass()  ← VulkanCompatRenderManager       │
    └───────────────────────────────────────────────────────┘
  → Vulkan render pass ends normally
[Vanilla frame end]
```

---

## 📦 Building from Source

```bash
git clone https://github.com/example/xaero-vulkan-compat
cd xaero-vulkan-compat
./gradlew build
# Output: build/libs/xaero-vulkan-compat-1.0.0.jar
```

> **Note:** Xaero's mods and VulkanMod are `compileOnly` dependencies. Their JARs are **not** redistributed with this mod and must be provided separately in your dev environment.

---

## 📂 Project Structure

```
src/main/java/dev/xaerovulkan/compat/
├── XaeroVulkanCompat.java               ← Mod entrypoint
├── render/
│   └── VulkanCompatRenderManager.java   ← Central render-pass coordinator
├── util/
│   └── CompatibilityChecker.java        ← Mod detection helpers
└── mixin/
    ├── MixinXaeroMinimapRenderer.java   ← Minimap HUD draw guard
    ├── MixinXaeroWorldMapRenderer.java  ← World map screen guard + tile upload
    ├── MixinXaeroMapFramebuffer.java    ← FBO bind/unbind/blit guard
    ├── MixinXaeroRadarRenderer.java     ← Entity radar depth-read safety
    └── MixinVulkanRenderPassCompat.java ← VulkanMod pass deferral
```

---

## 🔧 Compatibility Notes

- Tested with **VulkanMod 0.4.x** on NVIDIA (Vulkan 1.3) and AMD (Vulkan 1.2) drivers.
- Intel Arc GPUs: use VulkanMod ≥ 0.4.1 for best results.
- The mod uses **runtime reflection** to call VulkanMod internals — if VulkanMod's API changes, patches degrade gracefully to no-ops rather than crashing.
- **Sodium / Iris**: not required and not conflicting.

---

## 📜 License

MIT — see [LICENSE](LICENSE).
