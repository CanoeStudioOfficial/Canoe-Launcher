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
    "home.canoePacks": "独木舟整合包",
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
    "loading": "正在载入",
    "job.install.readManifest": "读取整合包清单",
    "job.install.checkVersion": "检查 Minecraft 与加载器版本",
    "job.install.prepareLibraries": "准备资源和依赖库",
    "job.install.writeInstance": "写入实例配置",
    "job.install.finish": "完成安装",
    "job.update.compare": "比较本地与远程清单",
    "job.update.downloadDelta": "下载差异文件",
    "job.update.verify": "校验哈希",
    "job.update.migrate": "迁移实例配置",
    "job.update.finish": "完成更新",
    "job.launch.check": "检查账号与 Java",
    "job.launch.arguments": "生成启动参数",
    "job.launch.directory": "准备运行目录",
    "job.launch.process": "启动游戏进程",
    "job.complete.launch": "游戏进程已交给启动器核心",
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
    "home.canoePacks": "獨木舟整合包",
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
    "loading": "正在載入",
    "job.install.readManifest": "讀取整合包清單",
    "job.install.checkVersion": "檢查 Minecraft 與載入器版本",
    "job.install.prepareLibraries": "準備資源與依賴庫",
    "job.install.writeInstance": "寫入實例設定",
    "job.install.finish": "完成安裝",
    "job.update.compare": "比較本機與遠端清單",
    "job.update.downloadDelta": "下載差異檔案",
    "job.update.verify": "校驗雜湊",
    "job.update.migrate": "遷移實例設定",
    "job.update.finish": "完成更新",
    "job.launch.check": "檢查帳號與 Java",
    "job.launch.arguments": "產生啟動參數",
    "job.launch.directory": "準備執行目錄",
    "job.launch.process": "啟動遊戲程序",
    "job.complete.launch": "遊戲程序已交給啟動器核心",
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
    "home.canoePacks": "Canoe Packs",
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
    "loading": "Loading",
    "job.install.readManifest": "Reading modpack manifest",
    "job.install.checkVersion": "Checking Minecraft and loader versions",
    "job.install.prepareLibraries": "Preparing assets and libraries",
    "job.install.writeInstance": "Writing instance configuration",
    "job.install.finish": "Finishing installation",
    "job.update.compare": "Comparing local and remote manifests",
    "job.update.downloadDelta": "Downloading changed files",
    "job.update.verify": "Verifying hashes",
    "job.update.migrate": "Migrating instance configuration",
    "job.update.finish": "Finishing update",
    "job.launch.check": "Checking account and Java",
    "job.launch.arguments": "Generating launch arguments",
    "job.launch.directory": "Preparing runtime directory",
    "job.launch.process": "Starting game process",
    "job.complete.launch": "Game process handed to launcher core",
    "job.complete.generic": "Task complete",
    "job.preview.complete": "Preview task complete",
  },
} as const;

const packCopy: Record<
  Locale,
  Record<string, Pick<Modpack, "name" | "studio" | "summary" | "description" | "tags" | "changelog">>
> = {
  "zh-CN": {
    "canoe-origins": {
      name: "独木舟：起源",
      studio: "独木舟工作室",
      summary: "探索、轻魔法与轻量科技并行的长期生存整合包。",
      description: "围绕基地建设、维度探索和轻量自动化设计，适合多人服务器长期游玩。",
      tags: ["生存", "探索", "魔法", "多人"],
      changelog: ["新增暮色系任务线", "优化服务端同步配置", "修复部分 Shader 兼容问题"],
    },
    "canoe-machina": {
      name: "独木舟：机械纪元",
      studio: "独木舟工作室",
      summary: "面向自动化玩家的科技线整合包。",
      description: "从早期机械动力到后期能源网络，强调清晰的阶段目标和稳定的性能表现。",
      tags: ["科技", "自动化", "任务", "性能"],
      changelog: ["重平衡矿物处理收益", "加入启动前配置校验"],
    },
    "canoe-wilderness": {
      name: "独木舟：荒野札记",
      studio: "独木舟工作室",
      summary: "偏向沉浸、建筑与地形探索的轻整合。",
      description: "为休闲探索和建筑玩家准备，保留原版节奏，同时加强世界生成和生活化内容。",
      tags: ["建筑", "地形", "轻量", "休闲"],
      changelog: ["第一版公开测试", "内置光影配置模板"],
    },
  },
  "zh-TW": {
    "canoe-origins": {
      name: "獨木舟：起源",
      studio: "獨木舟工作室",
      summary: "探索、輕魔法與輕量科技並行的長期生存整合包。",
      description: "圍繞基地建設、維度探索和輕量自動化設計，適合多人伺服器長期遊玩。",
      tags: ["生存", "探索", "魔法", "多人"],
      changelog: ["新增暮色系任務線", "最佳化伺服器同步設定", "修復部分 Shader 相容問題"],
    },
    "canoe-machina": {
      name: "獨木舟：機械紀元",
      studio: "獨木舟工作室",
      summary: "面向自動化玩家的科技線整合包。",
      description: "從早期機械動力到後期能源網路，強調清晰的階段目標和穩定的效能表現。",
      tags: ["科技", "自動化", "任務", "效能"],
      changelog: ["重新平衡礦物處理收益", "加入啟動前設定校驗"],
    },
    "canoe-wilderness": {
      name: "獨木舟：荒野札記",
      studio: "獨木舟工作室",
      summary: "偏向沉浸、建築與地形探索的輕整合。",
      description: "為休閒探索和建築玩家準備，保留原版節奏，同時加強世界生成和生活化內容。",
      tags: ["建築", "地形", "輕量", "休閒"],
      changelog: ["第一版公開測試", "內建光影設定模板"],
    },
  },
  en: {
    "canoe-origins": {
      name: "Canoe: Origins",
      studio: "Canoe Studio",
      summary: "A long-term survival pack that blends exploration, light magic, and compact tech.",
      description: "Built around base building, dimension exploration, and lightweight automation for long-running multiplayer worlds.",
      tags: ["Survival", "Exploration", "Magic", "Multiplayer"],
      changelog: ["Added a Twilight questline", "Improved server sync config", "Fixed several shader compatibility issues"],
    },
    "canoe-machina": {
      name: "Canoe: Machina Age",
      studio: "Canoe Studio",
      summary: "A technology-focused pack for automation players.",
      description: "From early kinetic systems to late-game energy networks, with clear progression goals and stable performance.",
      tags: ["Tech", "Automation", "Quests", "Performance"],
      changelog: ["Rebalanced ore processing rewards", "Added pre-launch configuration validation"],
    },
    "canoe-wilderness": {
      name: "Canoe: Wilderness Notes",
      studio: "Canoe Studio",
      summary: "A lighter pack focused on immersion, building, and terrain exploration.",
      description: "Designed for relaxed exploration and builders while keeping the vanilla rhythm and enriching world generation.",
      tags: ["Building", "Terrain", "Lightweight", "Casual"],
      changelog: ["First public test release", "Bundled shader configuration templates"],
    },
  },
};

const newsCopy: Record<Locale, Record<string, Pick<LauncherNews, "title" | "body">>> = {
  "zh-CN": {
    "core-roadmap": {
      title: "HMCLCore 适配层已预留",
      body: "安装、更新、启动、修复和日志接口已经通过 IPC 收口。",
    },
    "pack-policy": {
      title: "整合包清单采用可签名格式",
      body: "后续可以接入独木舟工作室自己的 CDN 和版本索引。",
    },
    "browser-mode": {
      title: "浏览器预览模式",
      body: "当前正在使用无 Electron IPC 的本地预览。",
    },
  },
  "zh-TW": {
    "core-roadmap": {
      title: "HMCLCore 適配層已預留",
      body: "安裝、更新、啟動、修復和日誌介面已經透過 IPC 收口。",
    },
    "pack-policy": {
      title: "整合包清單採用可簽章格式",
      body: "後續可以接入獨木舟工作室自己的 CDN 和版本索引。",
    },
    "browser-mode": {
      title: "瀏覽器預覽模式",
      body: "目前正在使用無 Electron IPC 的本機預覽。",
    },
  },
  en: {
    "core-roadmap": {
      title: "HMCLCore adapter boundary is ready",
      body: "Install, update, launch, repair, and log flows are already routed through IPC.",
    },
    "pack-policy": {
      title: "Pack manifests use a signable format",
      body: "The launcher can later connect to Canoe Studio's own CDN and version index.",
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
  const value = messages[currentLocale.value][key as keyof (typeof messages)[Locale]] ?? messages[fallbackLocale][key as keyof (typeof messages)[Locale]] ?? key;
  return interpolate(value, params);
}

export function localizePack(pack: Modpack): Modpack {
  const copy = packCopy[currentLocale.value][pack.id] ?? packCopy[fallbackLocale][pack.id];
  return copy ? { ...pack, ...copy } : pack;
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
