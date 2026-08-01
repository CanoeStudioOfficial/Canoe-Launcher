export type ModLoader = "Forge" | "Fabric" | "NeoForge" | "Quilt";
export type ModpackStatus = "remote" | "installed" | "updateAvailable" | "running";

export interface Modpack {
  id: string;
  name: string;
  studio: string;
  summary: string;
  description: string;
  version: string;
  latestVersion: string;
  minecraftVersion: string;
  loader: ModLoader;
  loaderVersion: string;
  recommendedMemoryMb: number;
  sizeGb: number;
  status: ModpackStatus;
  tags: string[];
  cover: string;
  accent: string;
  changelog: string[];
}

export interface LauncherNews {
  id: string;
  title: string;
  body: string;
  date: string;
}

export interface LauncherLibrary {
  featuredPackId: string;
  packs: Modpack[];
  news: LauncherNews[];
}

export interface LauncherSettings {
  gameDirectory: string;
  javaPath: string;
  memoryMb: number;
  concurrentDownloads: number;
  downloadMirror: "Official" | "BMCLAPI" | "MCBBS";
  closeAfterLaunch: boolean;
}

export interface LaunchJobEvent {
  jobId: string;
  packId: string;
  kind: "install" | "update" | "launch" | "repair";
  status: "queued" | "running" | "complete" | "failed";
  progress: number;
  message: string;
  messageKey?: string;
  timestamp: string;
}
