import type { GameProcess, LaunchJobEvent, LauncherAccount, LauncherLibrary, LauncherSettings } from "@/types/launcher";

const mockLibrary: LauncherLibrary = {
  featuredPackId: "",
  packs: [],
  news: [
    {
      id: "browser-mode",
      title: "Browser preview mode",
      body: "This preview is running without Electron IPC.",
      date: "2026-08-01",
    },
  ],
};

const mockSettings: LauncherSettings = {
  gameDirectory: "%USERPROFILE%\\.canoe-launcher\\instances",
  javaPath: "auto",
  memoryMb: 6144,
  concurrentDownloads: 6,
  downloadMirror: "BMCLAPI",
  closeAfterLaunch: false,
  playerName: "LocalPlayer",
  accountType: "offline",
  profileId: "b50ad385-829d-3141-a216-7e7d7539ba7f",
};

export const launcherClient = {
  async getLibrary(): Promise<LauncherLibrary> {
    return window.canoeLauncher?.getLibrary() ?? mockLibrary;
  },

  async getSettings(): Promise<LauncherSettings> {
    return window.canoeLauncher?.getSettings() ?? mockSettings;
  },

  async updateSettings(patch: Partial<LauncherSettings>): Promise<LauncherSettings> {
    if (window.canoeLauncher) {
      return window.canoeLauncher.updateSettings(patch);
    }

    Object.assign(mockSettings, patch);
    return mockSettings;
  },

  async listAccounts(): Promise<LauncherAccount[]> {
    return window.canoeLauncher?.listAccounts() ?? [
      {
        id: mockSettings.profileId,
        type: mockSettings.accountType,
        username: mockSettings.playerName,
      },
    ];
  },

  async addOfflineAccount(username: string): Promise<LauncherAccount> {
    if (window.canoeLauncher) {
      return window.canoeLauncher.addOfflineAccount(username);
    }

    mockSettings.playerName = username;
    mockSettings.accountType = "offline";
    return {
      id: mockSettings.profileId,
      type: "offline",
      username,
    };
  },

  async listProcesses(): Promise<GameProcess[]> {
    return window.canoeLauncher?.listProcesses() ?? [];
  },

  async stopProcess(processId: string): Promise<GameProcess> {
    if (window.canoeLauncher) {
      return window.canoeLauncher.stopProcess(processId);
    }

    throw new Error(`No process registry is available for ${processId}`);
  },

  async installPack(packId: string): Promise<LaunchJobEvent> {
    return runOrMock("installPack", packId, "install");
  },

  async updatePack(packId: string): Promise<LaunchJobEvent> {
    return runOrMock("updatePack", packId, "update");
  },

  async launchInstance(packId: string): Promise<LaunchJobEvent> {
    return runOrMock("launchInstance", packId, "launch");
  },

  async openInstanceFolder(packId: string): Promise<{ ok: boolean; path: string }> {
    return window.canoeLauncher?.openInstanceFolder(packId) ?? { ok: true, path: packId };
  },

  onJobEvent(callback: (event: LaunchJobEvent) => void) {
    return window.canoeLauncher?.onJobEvent(callback) ?? (() => undefined);
  },
};

async function runOrMock(
  method: "installPack" | "updatePack" | "launchInstance",
  packId: string,
  kind: LaunchJobEvent["kind"],
): Promise<LaunchJobEvent> {
  if (window.canoeLauncher) {
    return window.canoeLauncher[method](packId);
  }

  return {
    jobId: `${kind}-${Date.now()}`,
    packId,
    kind,
    status: "complete",
    progress: 100,
    message: "Preview task complete",
    messageKey: "job.preview.complete",
    timestamp: new Date().toISOString(),
  };
}
