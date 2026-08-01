# Architecture

## Product Shape

Canoe Launcher is designed as a Canoe Studio modpack launcher rather than a general-purpose Minecraft launcher. The user-facing workflow is:

1. Browse Canoe modpacks.
2. Install or update one pack.
3. Launch a local instance.
4. Inspect progress, logs, and settings from the same app shell.

## Layers

### Renderer

`src/App.vue` owns the first MVP interface. It talks only to `launcherClient`, not to Electron APIs directly.

`src/i18n.ts` provides the first localization layer for English, Simplified Chinese, and Traditional Chinese. UI strings, pack display copy, news, status labels, and job progress messages are translated in the renderer.

### Preload

`electron/preload.ts` exposes a small `window.canoeLauncher` bridge with typed operations:

- `getLibrary`
- `getSettings`
- `updateSettings`
- `installPack`
- `updatePack`
- `launchInstance`
- `openInstanceFolder`
- `onJobEvent`

### Main Process

`electron/main.ts` registers IPC handlers and owns native desktop privileges. It currently delegates launcher work to `CanoeLauncherBridge`.

### Launcher Core Adapter

`electron/launcherBridge.ts` is intentionally replaceable. The current implementation simulates long-running tasks and emits progress events. The HMCL-based implementation should keep the same surface while moving the actual work into a Java subprocess or dedicated core package.

## HMCL Integration Direction

Recommended next step:

1. Create a Java core module that depends on the relevant HMCLCore packages under GPLv3-compatible terms.
2. Expose a small JSON-RPC or line-delimited JSON protocol.
3. Let Electron spawn the Java core and translate IPC calls to core commands.
4. Keep account secrets, Java detection, download validation, and process launch in the core process.
5. Keep Vue focused on state presentation and user decisions.

This keeps the modern UI independent from HMCL internals while still letting HMCL do the difficult Minecraft work.
