# Java Core Protocol

Canoe Launcher talks to the Java launcher core through newline-delimited JSON over stdin/stdout.

## Request

```json
{"id":"request-id","type":"request","command":"getLibrary","payload":{}}
```

## Response

```json
{"type":"response","id":"request-id","ok":true,"payload":{},"error":null}
```

## Event

```json
{"type":"event","event":"job","payload":{"jobId":"...","packId":"...","kind":"install","status":"running","progress":50,"messageKey":"job.install.prepareLibraries"}}
```

## Commands

- `ping`
- `getLibrary`
- `getSettings`
- `updateSettings`
- `listAccounts`
- `addOfflineAccount`
- `startMicrosoftLogin`
- `pollMicrosoftLogin`
- `createVanillaInstance`
- `installPack`
- `updatePack`
- `launchInstance`
- `listProcesses`
- `stopProcess`
- `openInstanceFolder`

## Core Boundary

`launcher-core/src/main/java/studio/canoe/launcher/core/CanoeCoreFacade.java` is the stable boundary for launcher operations. It does not import HMCL classes; HMCL remains a design reference only.

The current self-developed core owns:

- local catalog persistence
- local Vanilla instance creation
- Mojang version metadata download
- Fabric loader profile download
- library, native, asset, and client jar verification
- offline account persistence
- Microsoft device-code sign-in, Xbox/XSTS exchange, Minecraft profile lookup, and token refresh
- launch-argument generation
- game process start/list/stop registration

Microsoft sign-in requires a public OAuth client ID configured as `microsoftClientId` in launcher settings or `CANOE_MICROSOFT_CLIENT_ID` in the Java core environment.

The next extension targets are Forge, NeoForge, deeper account lifecycle controls, and signed Canoe modpack manifests.
