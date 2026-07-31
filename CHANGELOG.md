# 🚀 IceBoatRacing v3.0 Release Notes

**IceBoatRacing v3.0** is a ground-up modernization and feature expansion! This update makes the plugin **100% standalone**, extends support from **Minecraft 1.21.x up to 26.2 (latest Paper builds)**, introduces smooth ice staircase climbing, minimalist scoreboards, bStats metrics, and advanced replay systems!

---

## 🌟 Major Highlights

### ⚡ 1. Modern Minecraft & Bleeding-Edge Paper Support (1.21.x - 26.2)
- Full support for **Minecraft 1.21 up to 26.2** server builds.
- Built on Java 21 with native Adventure MiniMessage and Component text formatting.
- Updated PacketEvents dependency to `2.13.0` to handle Paper build versioning smoothly.

### 📦 2. 100% Standalone (Zero External Dependencies)
- **Built-in PacketEvents Engine**: Completely removed ProtocolLib dependency. PacketEvents is shaded into the plugin for zero-lag packet handling.
- **Native `TextDisplay` Hologram System**: Replaced external hologram dependencies (ProtocolLib / HolographicDisplays / DecentHolograms) with native Minecraft `TextDisplay` entities.
- **Ice/Water Transparency Fix**: Enabled `seeThrough(true)` on holograms so leaderboards remain 100% visible through packed ice, blue ice, stained glass, and water shaders without depth clipping.

### 🧊 3. Smooth Ice Staircase Climbing
- **Paper Native Step-Height**: Boats now feature native `1.25f` step height on Paper servers.
- **Ice-Restricted Staircases**: Boats can now glide smoothly up 1+ block step-ups on **Ice blocks** (`Ice`, `Packed Ice`, `Blue Ice`, `Frosted Ice`) like stairs without bumping or losing speed.
- **Track Boundary Integrity**: Non-ice blocks (stone, wood, concrete, obsidian) continue to act as solid track walls to keep racers on course.

### 📊 4. Minimalist Scoreboard (No Red Numbers)
- **Paper `NumberFormat.blank()`**: Removed all red score numbers (`15`, `14`, `13`...) on the right side of the sidebar objective.
- **Minimalist HUD**: Redesigned both Lobby and Race scoreboards into a sleek, clean, uncluttered layout showing stats, speed (km/h), checkpoints, laps, and top standings.

### 📋 5. Organized GUI & Setup Checklist
- **Restructured GUIs**: Reorganized Main Menu, Arena Selector, Arena Editor, and Cosmetics Hub into clean 9-slot grid layouts with distinct color accents.
- **Arena Setup Progress Checklist**: Added a dedicated Setup Checklist GUI (`openArenaSetupStatusMenu`) in the editor displaying setup status for Spawns, Checkpoints, Finish Line, Lobby, and Leaderboard with 1-click quick action buttons.

### 🏎️ 6. Replays, Parties & Race Enhancements
- **Party System**: Create parties up to 8 members (`/race party`) and join races together.
- **Replay System**: Record and rewatch past race replays (`/race replay watch`).
- **Enhanced Record Ghosts**: Race against server best times represented by animated packet boats.
- **Traffic Light Countdown**: Visual particle traffic lights (Red ➔ Yellow ➔ Green) at race start.
- **Elimination Race Mode**: Last place racer eliminated each lap.

### ⚡ 7. Performance Optimizations & bStats
- **Zero-Allocation Geometry Math**: Replaced vector object allocations in raytracing with primitive double arithmetic.
- **$O(1)$ Pre-Mapped Ranking Sorting**: Pre-calculated sorting maps to optimize 20-tick scoreboard updates.
- **Non-Blocking Async I/O**: `saveStats()` and `saveArenasConfig()` run off-thread via `AsyncIO`.
- **bStats Metrics**: Added bStats integration (`pluginId = 33031`).

---

## 🛠️ Installation & Setup

1. Drop `IceBoatRacing-3.0.jar` into your server's `plugins/` directory.
2. **Restart** your server (do not use `/reload`).
3. Enjoy a 100% standalone, modern ice boat racing experience!
