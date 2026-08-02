# Canoe Launcher

Canoe Launcher is a cross-platform Minecraft modpack launcher for Canoe Studio packs. It starts from a modern Electron + Vue desktop shell and keeps the launcher core behind a clear subprocess protocol so the Minecraft runtime can evolve independently from the UI.

## Stack

- Electron for the desktop runtime
- Vue 3 + TypeScript + Vite for the renderer
- IPC launcher adapter for pack install, update, launch, logs, and settings
- Self-developed Java core inspired by mature launcher architecture, without direct HMCL class dependencies
- GPLv3 project license

## Scripts

```bash
npm install
npm run core:build
npm run dev
npm run typecheck
npm run build
```

On PowerShell systems that block `npm.ps1`, use `npm.cmd`:

```bash
npm.cmd install
npm.cmd run dev
```

## Current Scope

This first version is a product-quality shell with a Canoe Java core:

- Featured Canoe modpack library
- Installed instances page
- Install, update, and launch progress events
- Settings for Java, memory, downloads, mirrors, and launch behavior
- English, Simplified Chinese, and Traditional Chinese localization
- A documented Java subprocess protocol

The launcher now includes a Gradle-managed Java subprocess core under `launcher-core`. The current Java core owns the stdin/stdout protocol, settings storage, instance metadata, Mojang/Fabric metadata downloads, offline accounts, launch-argument generation, and game process registration.

The next implementation step is expanding Canoe Core's own Forge, NeoForge, and signed Canoe modpack manifest installers.
