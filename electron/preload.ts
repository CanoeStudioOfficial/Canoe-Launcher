import { contextBridge, ipcRenderer } from "electron";
import type { CreateVanillaInstanceInput, LaunchJobEvent, LauncherSettings } from "../src/types/launcher";

const api = {
  getLibrary: () => ipcRenderer.invoke("launcher:get-library"),
  getSettings: () => ipcRenderer.invoke("launcher:get-settings"),
  listAccounts: () => ipcRenderer.invoke("launcher:list-accounts"),
  listProcesses: () => ipcRenderer.invoke("launcher:list-processes"),
  updateSettings: (patch: Partial<LauncherSettings>) => ipcRenderer.invoke("launcher:update-settings", patch),
  addOfflineAccount: (username: string) => ipcRenderer.invoke("launcher:add-offline-account", username),
  startMicrosoftLogin: () => ipcRenderer.invoke("launcher:start-microsoft-login"),
  pollMicrosoftLogin: (deviceCode: string) => ipcRenderer.invoke("launcher:poll-microsoft-login", deviceCode),
  createVanillaInstance: (input: CreateVanillaInstanceInput) => ipcRenderer.invoke("launcher:create-vanilla-instance", input),
  installPack: (packId: string) => ipcRenderer.invoke("launcher:install-pack", packId),
  updatePack: (packId: string) => ipcRenderer.invoke("launcher:update-pack", packId),
  launchInstance: (packId: string) => ipcRenderer.invoke("launcher:launch-instance", packId),
  stopProcess: (processId: string) => ipcRenderer.invoke("launcher:stop-process", processId),
  openInstanceFolder: (packId: string) => ipcRenderer.invoke("launcher:open-folder", packId),
  openExternal: (url: string) => ipcRenderer.invoke("shell:open-external", url),
  onJobEvent: (callback: (event: LaunchJobEvent) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, payload: LaunchJobEvent) => callback(payload);
    ipcRenderer.on("launcher:job-event", listener);
    return () => ipcRenderer.removeListener("launcher:job-event", listener);
  },
};

contextBridge.exposeInMainWorld("canoeLauncher", api);

export type CanoeLauncherApi = typeof api;
