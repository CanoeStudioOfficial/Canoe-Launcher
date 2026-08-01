import type { LaunchJobEvent, LauncherLibrary, LauncherSettings } from "@/types/launcher";

const mockLibrary: LauncherLibrary = {
  featuredPackId: "canoe-origins",
  packs: [
    {
      id: "canoe-origins",
      name: "Canoe: Origins",
      studio: "Canoe Studio",
      summary: "A long-term survival pack that blends exploration, light magic, and compact tech.",
      description: "Built around base building, dimension exploration, and lightweight automation for long-running multiplayer worlds.",
      version: "1.2.0",
      latestVersion: "1.3.0",
      minecraftVersion: "1.20.1",
      loader: "Forge",
      loaderVersion: "47.3.0",
      recommendedMemoryMb: 6144,
      sizeGb: 6.8,
      status: "updateAvailable",
      tags: ["Survival", "Exploration", "Magic", "Multiplayer"],
      cover: "/assets/pack-origins.svg",
      accent: "#3d8f6d",
      changelog: ["Added a Twilight questline", "Improved server sync config", "Fixed several shader compatibility issues"],
    },
    {
      id: "canoe-machina",
      name: "Canoe: Machina Age",
      studio: "Canoe Studio",
      summary: "A technology-focused pack for automation players.",
      description: "From early kinetic systems to late-game energy networks, with clear progression goals and stable performance.",
      version: "0.9.4",
      latestVersion: "0.9.4",
      minecraftVersion: "1.20.1",
      loader: "Fabric",
      loaderVersion: "0.16.9",
      recommendedMemoryMb: 8192,
      sizeGb: 7.4,
      status: "installed",
      tags: ["Tech", "Automation", "Quests", "Performance"],
      cover: "/assets/pack-machina.svg",
      accent: "#c97b37",
      changelog: ["Rebalanced ore processing rewards", "Added pre-launch configuration validation"],
    },
    {
      id: "canoe-wilderness",
      name: "Canoe: Wilderness Notes",
      studio: "Canoe Studio",
      summary: "A lighter pack focused on immersion, building, and terrain exploration.",
      description: "Designed for relaxed exploration and builders while keeping the vanilla rhythm and enriching world generation.",
      version: "0.4.1",
      latestVersion: "0.4.1",
      minecraftVersion: "1.21.1",
      loader: "NeoForge",
      loaderVersion: "21.1.90",
      recommendedMemoryMb: 4096,
      sizeGb: 4.2,
      status: "remote",
      tags: ["Building", "Terrain", "Lightweight", "Casual"],
      cover: "/assets/pack-wilderness.svg",
      accent: "#5f7ccf",
      changelog: ["First public test release", "Bundled shader configuration templates"],
    },
  ],
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
