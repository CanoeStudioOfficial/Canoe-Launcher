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

`electron/launcherBridge.ts` talks to `electron/javaCoreClient.ts`, which starts the Java core jar and sends newline-delimited JSON requests over stdin/stdout. If the Java jar is missing during development, the TypeScript fallback keeps the UI usable.

`launcher-core` is a Gradle-managed Java subproject. Its build is controlled by the root Gradle Wrapper, so `npm run core:build` and `gradlew.bat :launcher-core:build` produce the same jar.

## Launcher Core Direction

Recommended next step:

1. Keep the Java core self-developed and use HMCL only as an architectural reference.
2. Expose a small line-delimited JSON protocol.
3. Let Electron spawn the Java core and translate IPC calls to core commands.
4. Keep account secrets, Java detection, download validation, and process launch in the core process.
5. Keep Vue focused on state presentation and user decisions.

This keeps the modern UI independent from launcher internals while Canoe Core owns Minecraft installation, account, argument, and process workflows.
