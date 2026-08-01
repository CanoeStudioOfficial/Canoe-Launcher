import type { CanoeLauncherApi } from "../../electron/preload";

declare global {
  interface Window {
    canoeLauncher?: CanoeLauncherApi;
  }
}

export {};
