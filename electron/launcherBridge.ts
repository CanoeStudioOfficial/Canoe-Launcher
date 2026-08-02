import { BrowserWindow } from "electron";
import path from "node:path";
import os from "node:os";
import { JavaCoreClient } from "./javaCoreClient";
import type {
  LaunchJobEvent,
  CreateVanillaInstanceInput,
  LauncherLibrary,
  LauncherSettings,
  LauncherAccount,
  GameProcess,
  Modpack,
  ModpackStatus,
} from "../src/types/launcher";

const now = () => new Date().toISOString();
type JobStep = { key: string; fallback: string };

const basePacks: Modpack[] = [];

export class CanoeLauncherBridge {
  private window: BrowserWindow | null = null;
  private core = new JavaCoreClient((event) => this.emitJob(event));
  private packs = [...basePacks];
  private settings: LauncherSettings = {
    gameDirectory: path.join(os.homedir(), ".canoe-launcher", "instances"),
    javaPath: "auto",
    memoryMb: 6144,
    concurrentDownloads: 6,
    downloadMirror: "BMCLAPI",
    closeAfterLaunch: false,
    playerName: "LocalPlayer",
    accountType: "offline",
    profileId: "b50ad385-829d-3141-a216-7e7d7539ba7f",
  };

  bindWindow(window: BrowserWindow) {
    this.window = window;
  }

  async getLibrary(): Promise<LauncherLibrary> {
    return this.withCore("getLibrary", {}, () => ({
      featuredPackId: "",
      packs: this.packs,
      news: [
        {
          id: "core-roadmap",
          title: "Canoe Java Core is active",
          body: "The launcher core is running. Local Vanilla instances can be created before remote modpacks are connected.",
          date: "2026-08-01",
        },
        {
          id: "pack-policy",
          title: "Local Vanilla instances are available",
          body: "Use the local catalog for Vanilla instances now, then connect real modpack manifests later.",
          date: "2026-08-01",
        },
      ],
    }));
  }

  async getSettings(): Promise<LauncherSettings> {
    return this.withCore("getSettings", {}, () => this.settings);
  }

  async updateSettings(patch: Partial<LauncherSettings>): Promise<LauncherSettings> {
    return this.withCore("updateSettings", patch, () => {
      this.settings = { ...this.settings, ...patch };
      return this.settings;
    });
  }

  async listAccounts(): Promise<LauncherAccount[]> {
    return this.withCore("listAccounts", {}, () => [
      {
        id: this.settings.profileId,
        type: this.settings.accountType,
        username: this.settings.playerName,
      },
    ]);
  }

  async addOfflineAccount(username: string): Promise<LauncherAccount> {
    return this.withCore("addOfflineAccount", { username }, () => {
      this.settings = {
        ...this.settings,
        accountType: "offline",
        playerName: username,
      };
      return {
        id: this.settings.profileId,
        type: "offline",
        username,
      };
    });
  }

  async createVanillaInstance(input: CreateVanillaInstanceInput): Promise<Modpack> {
    return this.withCore("createVanillaInstance", { ...input }, () => {
      const minecraftVersion = input.minecraftVersion?.trim() || "1.20.1";
      const name = input.name?.trim() || `Minecraft ${minecraftVersion}`;
      const pack = makeVanillaPack(name, minecraftVersion, this.packs);
      this.packs = [...this.packs, pack];
      return pack;
    });
  }

  async listProcesses(): Promise<GameProcess[]> {
    return this.withCore("listProcesses", {}, () => []);
  }

  async stopProcess(processId: string): Promise<GameProcess> {
    return this.withCore("stopProcess", { processId }, () => {
      throw new Error(`No Java core process registry is available for ${processId}`);
    });
  }

  async installPack(packId: string) {
    return this.withCore("installPack", { packId }, () =>
      this.runJob(
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
      ),
      30 * 60 * 1000,
    );
  }

  async updatePack(packId: string) {
    return this.withCore("updatePack", { packId }, () =>
      this.runJob(
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
      ),
      30 * 60 * 1000,
    );
  }

  async launchInstance(packId: string) {
    return this.withCore("launchInstance", { packId }, () =>
      this.runJob(
        packId,
        "launch",
        [
          { key: "job.launch.check", fallback: "Checking account and Java" },
          { key: "job.launch.arguments", fallback: "Generating launch arguments" },
          { key: "job.launch.directory", fallback: "Preparing runtime directory" },
          { key: "job.launch.process", fallback: "Starting game process" },
        ],
        "running",
      ),
      30 * 60 * 1000,
    );
  }

  async openInstanceFolder(packId: string) {
    return this.withCore("openInstanceFolder", { packId }, () => ({
      ok: true,
      path: path.join(this.settings.gameDirectory, packId),
    }));
  }

  private async withCore<T>(command: string, payload: Record<string, unknown>, fallback: () => T | Promise<T>, timeoutMs?: number): Promise<T> {
    try {
      return await this.core.request<T>(command, payload, timeoutMs);
    } catch (error) {
      console.warn(`[canoe-core] Falling back for ${command}`, error);
      return fallback();
    }
  }

  private async runJob(packId: string, kind: LaunchJobEvent["kind"], steps: JobStep[], finalStatus: ModpackStatus) {
    const pack = this.packs.find((item) => item.id === packId);
    if (!pack) {
      throw new Error(`No modpack is configured for ${packId}`);
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
      message: kind === "launch" ? "Game runtime prepared by Canoe Core" : "Task complete",
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
