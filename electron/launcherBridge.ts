import { BrowserWindow } from "electron";
import path from "node:path";
import os from "node:os";
import type {
  LaunchJobEvent,
  LauncherLibrary,
  LauncherSettings,
  Modpack,
  ModpackStatus,
} from "../src/types/launcher";

const now = () => new Date().toISOString();
type JobStep = { key: string; fallback: string };

const basePacks: Modpack[] = [
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
];

export class CanoeLauncherBridge {
  private window: BrowserWindow | null = null;
  private packs = [...basePacks];
  private settings: LauncherSettings = {
    gameDirectory: path.join(os.homedir(), ".canoe-launcher", "instances"),
    javaPath: "auto",
    memoryMb: 6144,
    concurrentDownloads: 6,
    downloadMirror: "BMCLAPI",
    closeAfterLaunch: false,
  };

  bindWindow(window: BrowserWindow) {
    this.window = window;
  }

  getLibrary(): LauncherLibrary {
    return {
      featuredPackId: "canoe-origins",
      packs: this.packs,
      news: [
        {
          id: "core-roadmap",
          title: "HMCLCore adapter boundary is ready",
          body: "Install, update, launch, repair, and log flows are already routed through IPC.",
          date: "2026-08-01",
        },
        {
          id: "pack-policy",
          title: "Pack manifests use a signable format",
          body: "The launcher can later connect to Canoe Studio's own CDN and version index.",
          date: "2026-08-01",
        },
      ],
    };
  }

  getSettings(): LauncherSettings {
    return this.settings;
  }

  updateSettings(patch: Partial<LauncherSettings>): LauncherSettings {
    this.settings = { ...this.settings, ...patch };
    return this.settings;
  }

  async installPack(packId: string) {
    return this.runJob(
      packId,
      "install",
      [
        { key: "job.install.readManifest", fallback: "Reading modpack manifest" },
        { key: "job.install.checkVersion", fallback: "Checking Minecraft and loader versions" },
        { key: "job.install.prepareLibraries", fallback: "Preparing assets and libraries" },
        { key: "job.install.writeInstance", fallback: "Writing instance configuration" },
        { key: "job.install.finish", fallback: "Finishing installation" },
      ],
      "installed",
    );
  }

  async updatePack(packId: string) {
    return this.runJob(
      packId,
      "update",
      [
        { key: "job.update.compare", fallback: "Comparing local and remote manifests" },
        { key: "job.update.downloadDelta", fallback: "Downloading changed files" },
        { key: "job.update.verify", fallback: "Verifying hashes" },
        { key: "job.update.migrate", fallback: "Migrating instance configuration" },
        { key: "job.update.finish", fallback: "Finishing update" },
      ],
      "installed",
    );
  }

  async launchInstance(packId: string) {
    return this.runJob(
      packId,
      "launch",
      [
        { key: "job.launch.check", fallback: "Checking account and Java" },
        { key: "job.launch.arguments", fallback: "Generating launch arguments" },
        { key: "job.launch.directory", fallback: "Preparing runtime directory" },
        { key: "job.launch.process", fallback: "Starting game process" },
      ],
      "running",
    );
  }

  openInstanceFolder(packId: string) {
    return {
      ok: true,
      path: path.join(this.settings.gameDirectory, packId),
    };
  }

  private async runJob(packId: string, kind: LaunchJobEvent["kind"], steps: JobStep[], finalStatus: ModpackStatus) {
    const pack = this.packs.find((item) => item.id === packId);
    if (!pack) {
      throw new Error(`Unknown modpack: ${packId}`);
    }

    const jobId = `${kind}-${packId}-${Date.now()}`;

    this.emitJob({
      jobId,
      packId,
      kind,
      status: "running",
      progress: 0,
      message: steps[0].fallback,
      messageKey: steps[0].key,
      timestamp: now(),
    });

    for (let index = 0; index < steps.length; index += 1) {
      await delay(520);
      this.emitJob({
        jobId,
        packId,
        kind,
        status: "running",
        progress: Math.round(((index + 1) / steps.length) * 92),
        message: steps[index].fallback,
        messageKey: steps[index].key,
        timestamp: now(),
      });
    }

    this.packs = this.packs.map((item) => {
      if (item.id !== packId) {
        return item;
      }

      if (kind === "update") {
        return { ...item, version: item.latestVersion, status: finalStatus };
      }

      return { ...item, status: finalStatus };
    });

    const completeEvent: LaunchJobEvent = {
      jobId,
      packId,
      kind,
      status: "complete",
      progress: 100,
      message: kind === "launch" ? "Game process handed to launcher core" : "Task complete",
      messageKey: kind === "launch" ? "job.complete.launch" : "job.complete.generic",
      timestamp: now(),
    };

    this.emitJob(completeEvent);
    return completeEvent;
  }

  private emitJob(event: LaunchJobEvent) {
    this.window?.webContents.send("launcher:job-event", event);
  }
}

function delay(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
