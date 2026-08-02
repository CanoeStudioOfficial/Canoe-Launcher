import type {
  CreateVanillaInstanceInput,
  GameProcess,
  LaunchJobEvent,
  LauncherAccount,
  LauncherLibrary,
  LauncherSettings,
  MicrosoftLoginPollResult,
  MicrosoftLoginStart,
  Modpack,
} from "@/types/launcher";

const mockLibrary: LauncherLibrary = {
  featuredPackId: "",
  packs: [],
  news: [
    {
      id: "browser-mode",
      title: "Browser preview mode",
      body: "This preview is running without Electron IPC, but local Vanilla instance creation is still simulated.",
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
  selectedAccountId: "b50ad385-829d-3141-a216-7e7d7539ba7f",
  microsoftClientId: "",
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
    mockSettings.selectedAccountId = mockSettings.profileId;
    return {
      id: mockSettings.profileId,
      type: "offline",
      username,
    };
  },

  async startMicrosoftLogin(): Promise<MicrosoftLoginStart> {
    if (window.canoeLauncher) {
      return window.canoeLauncher.startMicrosoftLogin();
    }

    throw new Error("Microsoft login requires the desktop app and Canoe Java Core.");
  },

  async pollMicrosoftLogin(deviceCode: string): Promise<MicrosoftLoginPollResult> {
    if (window.canoeLauncher) {
      return window.canoeLauncher.pollMicrosoftLogin(deviceCode);
    }

    throw new Error(`Microsoft login polling requires Canoe Java Core: ${deviceCode.slice(0, 8)}...`);
  },

  async createVanillaInstance(input: CreateVanillaInstanceInput): Promise<Modpack> {
    if (window.canoeLauncher) {
      return window.canoeLauncher.createVanillaInstance(input);
    }

    const minecraftVersion = input.minecraftVersion.trim() || "1.20.1";
    const name = input.name.trim() || `Minecraft ${minecraftVersion}`;
    const pack = makeVanillaPack(name, minecraftVersion, mockLibrary.packs);
    mockLibrary.packs.push(pack);
    mockLibrary.featuredPackId ||= pack.id;
    return pack;
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

  async openExternal(url: string): Promise<void> {
    if (window.canoeLauncher) {
      await window.canoeLauncher.openExternal(url);
      return;
    }

    window.open(url, "_blank", "noopener,noreferrer");
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

function makeVanillaPack(name: string, minecraftVersion: string, existing: Modpack[]): Modpack {
  const idRoot = slug(`${name}-${minecraftVersion}`) || "minecraft-instance";
  let id = idRoot;
  let suffix = 2;
  while (existing.some((pack) => pack.id === id)) {
    id = `${idRoot}-${suffix}`;
    suffix += 1;
  }

  return {
    id,
    name,
    studio: "Local",
    summary: "A locally created Vanilla Minecraft instance.",
    description: "Created by Canoe Launcher. Canoe Core can install Minecraft metadata, client, libraries, assets, and launch arguments for this instance.",
    version: minecraftVersion,
    latestVersion: minecraftVersion,
    minecraftVersion,
    loader: "Vanilla",
    loaderVersion: "",
    recommendedMemoryMb: 4096,
    sizeGb: 0,
    status: "remote",
    tags: ["Local", "Vanilla", minecraftVersion],
    cover: "/assets/canoe-mark.svg",
    accent: "#2f7a5f",
    changelog: ["Local Vanilla instance created", "Ready for runtime installation"],
  };
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}
