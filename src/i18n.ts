import { computed, ref, watch } from "vue";
import type { LauncherNews, Modpack } from "@/types/launcher";

export type Locale = "zh-CN" | "zh-TW" | "en";

const localeNames: Record<Locale, string> = {
  "zh-CN": "简体中文",
  "zh-TW": "繁體中文",
  en: "English",
};

const messages = {
  "zh-CN": {
    "nav.home": "总览",
    "nav.library": "整合包库",
    "nav.instances": "实例",
    "nav.settings": "设置",
    "a11y.mainNav": "主导航",
    "a11y.language": "语言",
    "account.player": "本地玩家",
    "account.profile": "离线档案",
    "search.placeholder": "搜索整合包、版本、加载器",
    "top.downloads": "下载队列",
    "top.notifications": "通知",
    "home.runtime": "运行状态",
    "home.instances": "实例",
    "home.modpacks": "整合包",
    "home.memoryUnit": "MB",
    "home.noTasks": "暂无运行任务",
    "home.modpackCatalog": "整合包库",
    "home.viewAll": "查看全部",
    "home.news": "公告",
    "library.title": "整合包库",
    "library.count": "{count} 个项目",
    "instances.title": "本地实例",
    "instances.count": "{count} 个可启动实例",
    "settings.title": "设置",
    "settings.gameDirectory": "游戏目录",
    "settings.java": "Java",
    "settings.memory": "内存",
    "settings.concurrentDownloads": "下载并发",
    "settings.downloadMirror": "下载源",
    "settings.closeAfterLaunch": "启动后关闭启动器",
    "settings.playerName": "玩家名",
    "settings.accountType": "账号类型",
    "settings.offlineAccount": "离线账号",
    "settings.microsoftAccount": "Microsoft 账号",
    "settings.autoDetect": "自动检测",
    "status.remote": "未安装",
    "status.installed": "已安装",
    "status.updateAvailable": "可更新",
    "status.running": "运行中",
    "action.install": "安装",
    "action.update": "更新",
    "action.launch": "启动",
    "action.processing": "处理中",
    "action.openFolder": "打开实例目录",
    "action.updateInstance": "更新实例",
    "spec.loader": "加载器",
    "spec.recommendedMemory": "推荐内存",
    "spec.size": "大小",
    "empty.catalogEyebrow": "目录尚未接入",
    "empty.noPacksTitle": "还没有真实整合包",
    "empty.noPacksBody": "当前不会再展示示例整合包。你可以先创建一个本地原版实例；等整合包清单、CDN 或导入流程准备好后，再把真实项目接进来。",
    "empty.noCatalogInline": "整合包库为空，没有绑定任何示例项目。",
    "empty.noInstancesTitle": "暂无本地实例",
    "empty.noInstancesBody": "安装真实整合包后，这里会显示可启动的本地实例。",
    "empty.reviewSettings": "查看启动器设置",
    "create.instanceName": "实例名称",
    "create.minecraftVersion": "Minecraft 版本",
    "create.vanilla": "创建原版实例",
    "loading": "正在载入",
    "job.install.readManifest": "读取整合包清单",
    "job.install.checkVersion": "检查 Minecraft 与加载器版本",
    "job.install.prepareLibraries": "准备运行时、资源和依赖库",
    "job.install.writeInstance": "写入实例配置",
    "job.install.finish": "完成安装",
    "job.update.compare": "比较本地与远程清单",
    "job.update.downloadDelta": "下载差异文件",
    "job.update.verify": "校验哈希",
    "job.update.migrate": "迁移实例配置",
    "job.update.finish": "完成更新",
    "job.launch.check": "检查账号、Java 与运行时",
    "job.launch.arguments": "生成启动参数",
    "job.launch.directory": "准备运行目录",
    "job.launch.process": "启动或预备游戏进程",
    "job.complete.launch": "Canoe Core 已准备游戏运行时",
    "job.complete.generic": "任务完成",
    "job.preview.complete": "预览任务完成",
  },
  "zh-TW": {
    "nav.home": "總覽",
    "nav.library": "整合包庫",
    "nav.instances": "實例",
    "nav.settings": "設定",
    "a11y.mainNav": "主導覽",
    "a11y.language": "語言",
    "account.player": "本機玩家",
    "account.profile": "離線檔案",
    "search.placeholder": "搜尋整合包、版本、載入器",
    "top.downloads": "下載佇列",
    "top.notifications": "通知",
    "home.runtime": "執行狀態",
    "home.instances": "實例",
    "home.modpacks": "整合包",
    "home.memoryUnit": "MB",
    "home.noTasks": "目前沒有執行任務",
    "home.modpackCatalog": "整合包庫",
    "home.viewAll": "查看全部",
    "home.news": "公告",
    "library.title": "整合包庫",
    "library.count": "{count} 個項目",
    "instances.title": "本機實例",
    "instances.count": "{count} 個可啟動實例",
    "settings.title": "設定",
    "settings.gameDirectory": "遊戲目錄",
    "settings.java": "Java",
    "settings.memory": "記憶體",
    "settings.concurrentDownloads": "下載並行數",
    "settings.downloadMirror": "下載來源",
    "settings.closeAfterLaunch": "啟動後關閉啟動器",
    "settings.playerName": "玩家名稱",
    "settings.accountType": "帳號類型",
    "settings.offlineAccount": "離線帳號",
    "settings.microsoftAccount": "Microsoft 帳號",
    "settings.autoDetect": "自動偵測",
    "status.remote": "未安裝",
    "status.installed": "已安裝",
    "status.updateAvailable": "可更新",
    "status.running": "執行中",
    "action.install": "安裝",
    "action.update": "更新",
    "action.launch": "啟動",
    "action.processing": "處理中",
    "action.openFolder": "開啟實例目錄",
    "action.updateInstance": "更新實例",
    "spec.loader": "載入器",
    "spec.recommendedMemory": "建議記憶體",
    "spec.size": "大小",
    "empty.catalogEyebrow": "目錄尚未接入",
    "empty.noPacksTitle": "還沒有真實整合包",
    "empty.noPacksBody": "目前不會再顯示示例整合包。你可以先建立一個本機原版實例；等整合包清單、CDN 或匯入流程準備好後，再把真實項目接進來。",
    "empty.noCatalogInline": "整合包庫為空，沒有綁定任何示例項目。",
    "empty.noInstancesTitle": "暫無本機實例",
    "empty.noInstancesBody": "安裝真實整合包後，這裡會顯示可啟動的本機實例。",
    "empty.reviewSettings": "查看啟動器設定",
    "create.instanceName": "實例名稱",
    "create.minecraftVersion": "Minecraft 版本",
    "create.vanilla": "建立原版實例",
    "loading": "正在載入",
    "job.install.readManifest": "讀取整合包清單",
    "job.install.checkVersion": "檢查 Minecraft 與載入器版本",
    "job.install.prepareLibraries": "準備執行時、資源和依賴庫",
    "job.install.writeInstance": "寫入實例設定",
    "job.install.finish": "完成安裝",
    "job.update.compare": "比較本機與遠端清單",
    "job.update.downloadDelta": "下載差異檔案",
    "job.update.verify": "校驗雜湊",
    "job.update.migrate": "遷移實例設定",
    "job.update.finish": "完成更新",
    "job.launch.check": "檢查帳號、Java 與執行時",
    "job.launch.arguments": "產生啟動參數",
    "job.launch.directory": "準備執行目錄",
    "job.launch.process": "啟動或預備遊戲程序",
    "job.complete.launch": "Canoe Core 已準備遊戲執行時",
    "job.complete.generic": "任務完成",
    "job.preview.complete": "預覽任務完成",
  },
  en: {
    "nav.home": "Overview",
    "nav.library": "Modpacks",
    "nav.instances": "Instances",
    "nav.settings": "Settings",
    "a11y.mainNav": "Main navigation",
    "a11y.language": "Language",
    "account.player": "Local Player",
    "account.profile": "Offline profile",
    "search.placeholder": "Search packs, versions, loaders",
    "top.downloads": "Download queue",
    "top.notifications": "Notifications",
    "home.runtime": "Runtime",
    "home.instances": "Instances",
    "home.modpacks": "Modpacks",
    "home.memoryUnit": "MB",
    "home.noTasks": "No active tasks",
    "home.modpackCatalog": "Modpack Catalog",
    "home.viewAll": "View all",
    "home.news": "News",
    "library.title": "Modpack Library",
    "library.count": "{count} projects",
    "instances.title": "Local Instances",
    "instances.count": "{count} launchable instances",
    "settings.title": "Settings",
    "settings.gameDirectory": "Game directory",
    "settings.java": "Java",
    "settings.memory": "Memory",
    "settings.concurrentDownloads": "Concurrent downloads",
    "settings.downloadMirror": "Download mirror",
    "settings.closeAfterLaunch": "Close launcher after launch",
    "settings.playerName": "Player name",
    "settings.accountType": "Account type",
    "settings.offlineAccount": "Offline account",
    "settings.microsoftAccount": "Microsoft account",
    "settings.autoDetect": "Auto detect",
    "status.remote": "Not installed",
    "status.installed": "Installed",
    "status.updateAvailable": "Update ready",
    "status.running": "Running",
    "action.install": "Install",
    "action.update": "Update",
    "action.launch": "Launch",
    "action.processing": "Working",
    "action.openFolder": "Open instance folder",
    "action.updateInstance": "Update instance",
    "spec.loader": "Loader",
    "spec.recommendedMemory": "Recommended memory",
    "spec.size": "Size",
    "empty.catalogEyebrow": "Catalog not connected",
    "empty.noPacksTitle": "No real modpacks yet",
    "empty.noPacksBody": "Sample packs are no longer shown. You can create a local Vanilla instance now; when your pack manifest, CDN, or import flow is ready, real projects can be wired in here.",
    "empty.noCatalogInline": "The modpack catalog is empty and no sample projects are bound.",
    "empty.noInstancesTitle": "No local instances",
    "empty.noInstancesBody": "Installed real modpacks will appear here as launchable instances.",
    "empty.reviewSettings": "Review launcher settings",
    "create.instanceName": "Instance name",
    "create.minecraftVersion": "Minecraft version",
    "create.vanilla": "Create Vanilla instance",
    "loading": "Loading",
    "job.install.readManifest": "Reading modpack manifest",
    "job.install.checkVersion": "Checking Minecraft and loader versions",
    "job.install.prepareLibraries": "Preparing runtime, assets, and libraries",
    "job.install.writeInstance": "Writing instance configuration",
    "job.install.finish": "Finishing installation",
    "job.update.compare": "Comparing local and remote manifests",
    "job.update.downloadDelta": "Downloading changed files",
    "job.update.verify": "Verifying hashes",
    "job.update.migrate": "Migrating instance configuration",
    "job.update.finish": "Finishing update",
    "job.launch.check": "Checking account, Java, and runtime",
    "job.launch.arguments": "Generating launch arguments",
    "job.launch.directory": "Preparing runtime directory",
    "job.launch.process": "Starting or staging the game process",
    "job.complete.launch": "Canoe Core prepared the game runtime",
    "job.complete.generic": "Task complete",
    "job.preview.complete": "Preview task complete",
  },
} as const;

const packCopy: Record<
  Locale,
  Record<string, Pick<Modpack, "name" | "studio" | "summary" | "description" | "tags" | "changelog">>
> = {
  "zh-CN": {},
  "zh-TW": {},
  en: {},
};

const localVanillaCopy: Record<Locale, Pick<Modpack, "studio" | "summary" | "description" | "tags" | "changelog">> = {
  "zh-CN": {
    studio: "本地",
    summary: "本地创建的原版 Minecraft 实例。",
    description: "由 Canoe Launcher 创建。Canoe Core 会为该实例安装 Minecraft 元数据、客户端、依赖库、资源文件并生成启动参数。",
    tags: ["本地", "原版"],
    changelog: ["已创建本地原版实例", "可以安装运行时"],
  },
  "zh-TW": {
    studio: "本機",
    summary: "本機建立的原版 Minecraft 實例。",
    description: "由 Canoe Launcher 建立。Canoe Core 會為該實例安裝 Minecraft 中繼資料、客戶端、依賴庫、資源檔並產生啟動參數。",
    tags: ["本機", "原版"],
    changelog: ["已建立本機原版實例", "可以安裝執行時"],
  },
  en: {
    studio: "Local",
    summary: "A locally created Vanilla Minecraft instance.",
    description: "Created by Canoe Launcher. Canoe Core can install Minecraft metadata, client, libraries, assets, and launch arguments for this instance.",
    tags: ["Local", "Vanilla"],
    changelog: ["Local Vanilla instance created", "Ready for runtime installation"],
  },
};

const newsCopy: Record<Locale, Record<string, Pick<LauncherNews, "title" | "body">>> = {
  "zh-CN": {
    "core-roadmap": {
      title: "Java 核心桥接已启用",
      body: "Electron 已经通过逐行 JSON 协议连接到独立 Java 核心。",
    },
    "pack-policy": {
      title: "可以创建本地原版实例",
      body: "先用本地 catalog 管理 Vanilla 实例，后续再接入真实整合包清单。",
    },
    "browser-mode": {
      title: "浏览器预览模式",
      body: "当前正在使用无 Electron IPC 的本地预览。",
    },
  },
  "zh-TW": {
    "core-roadmap": {
      title: "Java 核心橋接已啟用",
      body: "Electron 已經透過逐行 JSON 協議連接到獨立 Java 核心。",
    },
    "pack-policy": {
      title: "可以建立本機原版實例",
      body: "先用本機 catalog 管理 Vanilla 實例，後續再接入真實整合包清單。",
    },
    "browser-mode": {
      title: "瀏覽器預覽模式",
      body: "目前正在使用無 Electron IPC 的本機預覽。",
    },
  },
  en: {
    "core-roadmap": {
      title: "Java core bridge is active",
      body: "Electron talks to the standalone Java core through a line-delimited JSON protocol.",
    },
    "pack-policy": {
      title: "Local Vanilla instances are available",
      body: "Use the local catalog for Vanilla instances now, then connect real modpack manifests later.",
    },
    "browser-mode": {
      title: "Browser preview mode",
      body: "This preview is running without Electron IPC.",
    },
  },
};

const fallbackLocale: Locale = "zh-CN";
const savedLocale = typeof localStorage === "undefined" ? null : localStorage.getItem("canoe-locale");
const initialLocale = normalizeLocale(savedLocale ?? (typeof navigator === "undefined" ? fallbackLocale : navigator.language));

const currentLocale = ref<Locale>(initialLocale);

watch(currentLocale, (value) => {
  localStorage.setItem("canoe-locale", value);
});

export function useI18n() {
  const localeOptions = computed(() =>
    (Object.keys(localeNames) as Locale[]).map((value) => ({
      value,
      label: localeNames[value],
    })),
  );

  return {
    locale: currentLocale,
    localeOptions,
    t,
    localizePack,
    localizeNews,
    translateJobMessage,
  };
}

export function t(key: string, params: Record<string, string | number> = {}) {
  const messagesForLocale = messages[currentLocale.value] as Record<string, string>;
  const fallbackMessages = messages[fallbackLocale] as Record<string, string>;
  return interpolate(messagesForLocale[key] ?? fallbackMessages[key] ?? key, params);
}

export function localizePack(pack: Modpack): Modpack {
  const copy = packCopy[currentLocale.value][pack.id] ?? packCopy[fallbackLocale][pack.id];
  if (copy) {
    return { ...pack, ...copy };
  }
  if (pack.studio === "Local" && pack.loader === "Vanilla") {
    const localCopy = localVanillaCopy[currentLocale.value];
    return {
      ...pack,
      ...localCopy,
      tags: [...localCopy.tags, pack.minecraftVersion],
    };
  }
  return pack;
}

export function localizeNews(news: LauncherNews): LauncherNews {
  const copy = newsCopy[currentLocale.value][news.id] ?? newsCopy[fallbackLocale][news.id];
  return copy ? { ...news, ...copy } : news;
}

export function translateJobMessage(messageKey: string | undefined, fallback: string) {
  return messageKey ? t(messageKey) : fallback;
}

function normalizeLocale(value: string): Locale {
  const normalized = value.toLowerCase();
  if (normalized === "zh-tw" || normalized === "zh-hk" || normalized === "zh-hant") {
    return "zh-TW";
  }

  if (normalized.startsWith("en")) {
    return "en";
  }

  return "zh-CN";
}

function interpolate(message: string, params: Record<string, string | number>) {
  return message.replace(/\{(\w+)\}/g, (_match, key: string) => String(params[key] ?? ""));
}
