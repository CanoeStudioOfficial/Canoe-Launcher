export type ModLoader = "Vanilla" | "Forge" | "Fabric" | "NeoForge" | "Quilt";
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
  playerName: string;
  accountType: "offline" | "microsoft";
  profileId: string;
  selectedAccountId: string;
  microsoftClientId: string;
}

export interface CreateVanillaInstanceInput {
  name: string;
  minecraftVersion: string;
}

export interface LauncherAccount {
  id: string;
  type: "offline" | "microsoft";
  username: string;
  createdAt?: string;
  expiresAt?: string;
  xuid?: string;
}

export type MicrosoftLoginStatus = "pending" | "slow_down" | "complete" | "failed";

export interface MicrosoftLoginStart {
  deviceCode: string;
  userCode: string;
  verificationUri: string;
  expiresIn: number;
  interval: number;
  message?: string;
  expiresAt: string;
}

export interface MicrosoftLoginPollResult {
  status: MicrosoftLoginStatus;
  account?: LauncherAccount;
  message?: string;
  errorCode?: string;
  errorMessage?: string;
  intervalDelta?: number;
}

export interface GameProcess {
  processId: string;
  packId: string;
  pid: number;
  status: "running" | "exited" | "stopped";
  exitCode?: number | null;
  startedAt: string;
  exitedAt?: string | null;
  runDirectory: string;
  logFile: string;
  command: string[];
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
