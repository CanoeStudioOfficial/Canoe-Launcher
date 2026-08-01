# Canoe Launcher

Canoe Launcher is a cross-platform Minecraft modpack launcher for Canoe Studio packs. It starts from a modern Electron + Vue desktop shell and keeps the launcher core behind a clear adapter so HMCL-based launch, install, auth, Java detection, and repair flows can be connected without rewriting the UI.

## Stack

- Electron for the desktop runtime
- Vue 3 + TypeScript + Vite for the renderer
- IPC launcher adapter for pack install, update, launch, logs, and settings
- GPLv3 project license, compatible with HMCL-derived work when attribution and source obligations are preserved

## Scripts

```bash
npm install
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

This first version is a product-quality shell with mocked core behavior:

- Featured Canoe modpack library
- Installed instances page
- Install, update, and launch progress events
- Settings for Java, memory, downloads, mirrors, and launch behavior
- English, Simplified Chinese, and Traditional Chinese localization
- A documented HMCL adapter boundary

The next implementation step is replacing the mock adapter in `electron/launcherBridge.ts` with a Java subprocess bridge that calls HMCLCore/HMCL-derived services.
