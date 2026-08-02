<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import {
  AlertCircle,
  Bell,
  CheckCircle2,
  Copy,
  Download,
  ExternalLink,
  FolderOpen,
  Gauge,
  Globe2,
  HardDriveDownload,
  Home,
  KeyRound,
  LibraryBig,
  PackageOpen,
  Play,
  Plus,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  TerminalSquare,
  UserRound,
} from "lucide-vue-next";
import { useI18n } from "@/i18n";
import type { Locale } from "@/i18n";
import { launcherClient } from "@/services/launcherClient";
import type {
  LaunchJobEvent,
  LauncherAccount,
  LauncherLibrary,
  LauncherSettings,
  MicrosoftLoginPollResult,
  MicrosoftLoginStart,
  Modpack,
} from "@/types/launcher";

const activeView = ref<"home" | "library" | "instances" | "settings">("home");
const library = ref<LauncherLibrary | null>(null);
const selectedPackId = ref("");
const settings = ref<LauncherSettings | null>(null);
const accounts = ref<LauncherAccount[]>([]);
const jobs = ref<LaunchJobEvent[]>([]);
const searchQuery = ref("");
const busyPackId = ref<string | null>(null);
const newInstanceName = ref("Minecraft 1.20.1");
const newMinecraftVersion = ref("1.20.1");
const isCreatingInstance = ref(false);
const microsoftLogin = ref<MicrosoftLoginStart | null>(null);
const microsoftLoginStatus = ref<MicrosoftLoginPollResult | null>(null);
const microsoftLoginError = ref("");
const isStartingMicrosoftLogin = ref(false);
const isPollingMicrosoftLogin = ref(false);
const { locale, localeOptions, t, localizePack, localizeNews, translateJobMessage } = useI18n();
let microsoftPollTimer: ReturnType<typeof setTimeout> | null = null;

const navItems = [
  { id: "home", labelKey: "nav.home", icon: Home },
  { id: "library", labelKey: "nav.library", icon: LibraryBig },
  { id: "instances", labelKey: "nav.instances", icon: PackageOpen },
  { id: "settings", labelKey: "nav.settings", icon: Settings },
] as const;

const localeShortLabels: Record<Locale, string> = {
  "zh-CN": "简",
  "zh-TW": "繁",
  en: "EN",
};

const localePreviewLabels: Record<Locale, string> = {
  "zh-CN": "简体界面",
  "zh-TW": "繁體介面",
  en: "English UI",
};

const packs = computed(() => library.value?.packs ?? []);
const localizedPacks = computed(() => packs.value.map(localizePack));
const featuredPack = computed(() => localizedPacks.value.find((pack) => pack.id === library.value?.featuredPackId) ?? localizedPacks.value[0] ?? null);
const selectedPack = computed(() => localizedPacks.value.find((pack) => pack.id === selectedPackId.value) ?? featuredPack.value);
const installedPacks = computed(() => localizedPacks.value.filter((pack) => pack.status !== "remote"));
const localizedNews = computed(() => library.value?.news.map(localizeNews) ?? []);
const latestJob = computed(() => jobs.value[0]);
const activeJob = computed(() => jobs.value.find((job) => job.status === "running"));
const hasPacks = computed(() => localizedPacks.value.length > 0);
const activeLocaleOption = computed(() => localeOptions.value.find((option) => option.value === locale.value) ?? localeOptions.value[0]);
const createInstanceDisabled = computed(() => isCreatingInstance.value || !newMinecraftVersion.value.trim());
const activeAccount = computed(() => {
  if (!settings.value) {
    return null;
  }

  return (
    accounts.value.find((account) => account.id === settings.value?.selectedAccountId) ??
    accounts.value.find((account) => account.id === settings.value?.profileId) ??
    null
  );
});
const activePlayerName = computed(() => activeAccount.value?.username ?? settings.value?.playerName ?? "LocalPlayer");
const accountProfileLabel = computed(() =>
  settings.value?.accountType === "microsoft" ? t("account.microsoftProfile") : t("account.offlineProfile"),
);
const microsoftLoginMessage = computed(() => {
  if (microsoftLoginError.value) {
    return microsoftLoginError.value;
  }

  if (microsoftLoginStatus.value?.status === "complete") {
    return t("microsoft.statusComplete");
  }

  if (microsoftLoginStatus.value?.status === "slow_down") {
    return t("microsoft.statusSlowDown");
  }

  if (microsoftLoginStatus.value?.status === "failed") {
    return microsoftLoginStatus.value.errorMessage ?? microsoftLoginStatus.value.message ?? t("microsoft.statusFailed");
  }

  if (microsoftLogin.value) {
    return t("microsoft.statusWaiting");
  }

  return t("microsoft.statusReady");
});
const microsoftStatusKind = computed(() => {
  if (microsoftLoginError.value || microsoftLoginStatus.value?.status === "failed") {
    return "failed";
  }
  return microsoftLoginStatus.value?.status ?? "ready";
});

const javaDisplayValue = computed(() => {
  if (!settings.value) {
    return "";
  }

  return settings.value.javaPath === "auto" ? t("settings.autoDetect") : settings.value.javaPath;
});

const filteredPacks = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) {
    return localizedPacks.value;
  }

  return localizedPacks.value.filter((pack) => {
    const haystack = [pack.name, pack.summary, pack.loader, pack.minecraftVersion, ...pack.tags].join(" ").toLowerCase();
    return haystack.includes(query);
  });
});

onMounted(async () => {
  const [libraryPayload, settingsPayload, accountsPayload] = await Promise.all([
    launcherClient.getLibrary(),
    launcherClient.getSettings(),
    launcherClient.listAccounts(),
  ]);

  library.value = libraryPayload;
  selectedPackId.value = libraryPayload.featuredPackId || libraryPayload.packs[0]?.id || "";
  settings.value = settingsPayload;
  accounts.value = accountsPayload;

  launcherClient.onJobEvent((event) => {
    jobs.value = [event, ...jobs.value.filter((job) => job.jobId !== event.jobId)].slice(0, 8);
    patchPackAfterJob(event);
  });
});

onBeforeUnmount(() => {
  clearMicrosoftPollTimer();
});

async function installPack(pack: Modpack) {
  busyPackId.value = pack.id;
  try {
    await launcherClient.installPack(pack.id);
  } finally {
    busyPackId.value = null;
  }
}

async function updatePack(pack: Modpack) {
  busyPackId.value = pack.id;
  try {
    await launcherClient.updatePack(pack.id);
  } finally {
    busyPackId.value = null;
  }
}

async function launchPack(pack: Modpack) {
  busyPackId.value = pack.id;
  try {
    await launcherClient.launchInstance(pack.id);
  } finally {
    busyPackId.value = null;
  }
}

async function openFolder(pack: Modpack) {
  await launcherClient.openInstanceFolder(pack.id);
}

async function createVanillaInstance() {
  if (!library.value || createInstanceDisabled.value) {
    return;
  }

  isCreatingInstance.value = true;
  try {
    const pack = await launcherClient.createVanillaInstance({
      name: newInstanceName.value,
      minecraftVersion: newMinecraftVersion.value,
    });

    library.value = {
      ...library.value,
      featuredPackId: library.value.featuredPackId || pack.id,
      packs: [...library.value.packs.filter((item) => item.id !== pack.id), pack],
    };
    selectedPackId.value = pack.id;
    activeView.value = "library";
  } finally {
    isCreatingInstance.value = false;
  }
}

async function startMicrosoftLogin() {
  clearMicrosoftPollTimer();
  microsoftLogin.value = null;
  microsoftLoginStatus.value = null;
  microsoftLoginError.value = "";
  isStartingMicrosoftLogin.value = true;

  try {
    microsoftLogin.value = await launcherClient.startMicrosoftLogin();
    microsoftLoginStatus.value = { status: "pending", message: t("microsoft.statusWaiting") };
    scheduleMicrosoftPoll(microsoftLogin.value.interval);
  } catch (error) {
    microsoftLoginError.value = errorMessage(error);
  } finally {
    isStartingMicrosoftLogin.value = false;
  }
}

async function pollMicrosoftLogin(auto = false) {
  if (!microsoftLogin.value || isPollingMicrosoftLogin.value) {
    return;
  }

  if (!auto) {
    clearMicrosoftPollTimer();
  }

  isPollingMicrosoftLogin.value = true;
  microsoftLoginError.value = "";
  try {
    const result = await launcherClient.pollMicrosoftLogin(microsoftLogin.value.deviceCode);
    microsoftLoginStatus.value = result;

    if (result.status === "complete") {
      clearMicrosoftPollTimer();
      const [settingsPayload, accountsPayload] = await Promise.all([
        launcherClient.getSettings(),
        launcherClient.listAccounts(),
      ]);
      settings.value = settingsPayload;
      accounts.value = accountsPayload;
      return;
    }

    if (result.status === "pending" || result.status === "slow_down") {
      const interval = Math.max(1, microsoftLogin.value.interval + (result.intervalDelta ?? 0));
      scheduleMicrosoftPoll(interval);
      return;
    }

    clearMicrosoftPollTimer();
  } catch (error) {
    microsoftLoginError.value = errorMessage(error);
    clearMicrosoftPollTimer();
  } finally {
    isPollingMicrosoftLogin.value = false;
  }
}

async function openMicrosoftVerification() {
  if (microsoftLogin.value?.verificationUri) {
    await launcherClient.openExternal(microsoftLogin.value.verificationUri);
  }
}

async function copyMicrosoftCode() {
  if (microsoftLogin.value?.userCode && navigator.clipboard) {
    await navigator.clipboard.writeText(microsoftLogin.value.userCode);
  }
}

function scheduleMicrosoftPoll(seconds: number) {
  clearMicrosoftPollTimer();
  microsoftPollTimer = setTimeout(() => {
    void pollMicrosoftLogin(true);
  }, Math.max(1, seconds) * 1000);
}

function clearMicrosoftPollTimer() {
  if (microsoftPollTimer) {
    clearTimeout(microsoftPollTimer);
    microsoftPollTimer = null;
  }
}

function patchPackAfterJob(event: LaunchJobEvent) {
  if (!library.value || event.status !== "complete") {
    return;
  }

  library.value = {
    ...library.value,
    packs: library.value.packs.map((pack) => {
      if (pack.id !== event.packId) {
        return pack;
      }

      if (event.kind === "update") {
        return { ...pack, version: pack.latestVersion, status: "installed" };
      }

      if (event.kind === "install") {
        return { ...pack, status: "installed" };
      }

      if (event.kind === "launch") {
        return { ...pack, status: "running" };
      }

      return pack;
    }),
  };
}

function statusLabel(pack: Modpack) {
  return t(`status.${pack.status}`);
}

function primaryAction(pack: Modpack) {
  if (pack.status === "remote") {
    return { label: t("action.install"), icon: Download, action: () => installPack(pack) };
  }

  if (pack.status === "updateAvailable") {
    return { label: t("action.update"), icon: RefreshCw, action: () => updatePack(pack) };
  }

  return { label: t("action.launch"), icon: Play, action: () => launchPack(pack) };
}

async function saveSetting<Key extends keyof LauncherSettings>(key: Key, value: LauncherSettings[Key]) {
  if (!settings.value) {
    return;
  }

  settings.value = await launcherClient.updateSettings({ [key]: value });
}

function setLocale(value: Locale) {
  locale.value = value;
}

function jobMessage(job: LaunchJobEvent) {
  return translateJobMessage(job.messageKey, job.message);
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error);
}
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/assets/canoe-mark.svg" alt="" class="brand-mark" />
        <div>
          <strong>Canoe</strong>
          <span>Launcher</span>
        </div>
      </div>

      <nav class="nav-list" :aria-label="t('a11y.mainNav')">
        <button
          v-for="item in navItems"
          :key="item.id"
          class="nav-item"
          :class="{ active: activeView === item.id }"
          :title="t(item.labelKey)"
          @click="activeView = item.id"
        >
          <component :is="item.icon" :size="19" />
          <span>{{ t(item.labelKey) }}</span>
        </button>
      </nav>

      <section class="account-strip">
        <UserRound :size="18" />
        <div>
          <strong>{{ activePlayerName }}</strong>
          <span>{{ accountProfileLabel }}</span>
        </div>
      </section>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="search-box">
          <Search :size="18" />
          <input v-model="searchQuery" type="search" :placeholder="t('search.placeholder')" />
        </div>

        <div class="top-actions">
          <div class="locale-switcher" :title="t('a11y.language')" :aria-label="t('a11y.language')">
            <Globe2 :size="18" />
            <div class="locale-track">
              <button
                v-for="option in localeOptions"
                :key="option.value"
                class="locale-chip"
                :class="{ active: locale === option.value }"
                type="button"
                :aria-pressed="locale === option.value"
                :title="option.label"
                @click="setLocale(option.value)"
              >
                <span class="locale-code">{{ localeShortLabels[option.value] }}</span>
                <span class="locale-name">{{ option.label }}</span>
              </button>
            </div>
            <strong class="locale-current">{{ localeShortLabels[locale] }}</strong>
          </div>
          <button class="icon-button" :title="t('top.downloads')">
            <HardDriveDownload :size="19" />
          </button>
          <button class="icon-button" :title="t('top.notifications')">
            <Bell :size="19" />
          </button>
        </div>
      </header>

      <template v-if="library">
        <section v-if="activeView === 'home'" class="view-grid">
          <article v-if="featuredPack" class="feature-panel">
            <img :src="featuredPack.cover" alt="" />
            <div class="feature-copy">
              <span class="eyebrow">{{ featuredPack.studio }}</span>
              <h1>{{ featuredPack.name }}</h1>
              <p>{{ featuredPack.summary }}</p>
              <div class="meta-row">
                <span>{{ featuredPack.minecraftVersion }}</span>
                <span>{{ featuredPack.loader }} {{ featuredPack.loaderVersion }}</span>
                <span>{{ featuredPack.sizeGb }} GB</span>
              </div>
              <button class="primary-button" :disabled="busyPackId === featuredPack.id" @click="primaryAction(featuredPack).action">
                <component :is="primaryAction(featuredPack).icon" :size="19" />
                <span>{{ busyPackId === featuredPack.id ? t("action.processing") : primaryAction(featuredPack).label }}</span>
              </button>
            </div>
          </article>

          <article v-else class="feature-panel empty-feature">
            <div class="empty-feature-mark">
              <PackageOpen :size="42" />
            </div>
            <div class="feature-copy">
              <span class="eyebrow">{{ t("empty.catalogEyebrow") }}</span>
              <h1>{{ t("empty.noPacksTitle") }}</h1>
              <p>{{ t("empty.noPacksBody") }}</p>
              <form class="quick-create-panel" @submit.prevent="createVanillaInstance">
                <label>
                  <span>{{ t("create.instanceName") }}</span>
                  <input v-model="newInstanceName" type="text" autocomplete="off" />
                </label>
                <label>
                  <span>{{ t("create.minecraftVersion") }}</span>
                  <input v-model="newMinecraftVersion" type="text" autocomplete="off" />
                </label>
                <button class="primary-button" type="submit" :disabled="createInstanceDisabled">
                  <Plus :size="18" />
                  <span>{{ isCreatingInstance ? t("action.processing") : t("create.vanilla") }}</span>
                </button>
              </form>
            </div>
          </article>

          <section class="side-panel">
            <div class="panel-heading">
              <h2>{{ t("home.runtime") }}</h2>
              <Gauge :size="20" />
            </div>
            <div class="stat-grid">
              <div>
                <strong>{{ installedPacks.length }}</strong>
                <span>{{ t("home.instances") }}</span>
              </div>
              <div>
                <strong>{{ packs.length }}</strong>
                <span>{{ t("home.modpacks") }}</span>
              </div>
              <div>
                <strong>{{ settings?.memoryMb ?? 0 }}</strong>
                <span>{{ t("home.memoryUnit") }}</span>
              </div>
            </div>
            <div v-if="latestJob" class="job-card compact">
              <span>{{ jobMessage(latestJob) }}</span>
              <progress :value="latestJob.progress" max="100" />
            </div>
            <div v-else class="empty-note">{{ t("home.noTasks") }}</div>
          </section>

          <section class="content-band">
            <div class="panel-heading">
              <h2>{{ t("home.modpackCatalog") }}</h2>
              <button class="ghost-button" @click="activeView = 'library'">{{ t("home.viewAll") }}</button>
            </div>
            <div v-if="hasPacks" class="pack-grid">
              <button
                v-for="pack in localizedPacks"
                :key="pack.id"
                class="pack-card"
                :class="{ selected: selectedPackId === pack.id }"
                @click="selectedPackId = pack.id"
              >
                <img :src="pack.cover" alt="" />
                <span class="status-pill">{{ statusLabel(pack) }}</span>
                <strong>{{ pack.name }}</strong>
                <small>{{ pack.minecraftVersion }} / {{ pack.loader }}</small>
              </button>
            </div>
            <div v-else class="empty-inline">
              <PackageOpen :size="22" />
              <span>{{ t("empty.noCatalogInline") }}</span>
            </div>
          </section>

          <section class="side-panel">
            <div class="panel-heading">
              <h2>{{ t("home.news") }}</h2>
              <TerminalSquare :size="20" />
            </div>
            <article v-for="item in localizedNews" :key="item.id" class="news-item">
              <span>{{ item.date }}</span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.body }}</p>
            </article>
          </section>
        </section>

        <section v-if="activeView === 'library'" class="library-view">
          <div class="section-title">
            <div>
              <span class="eyebrow">Catalog</span>
              <h1>{{ t("library.title") }}</h1>
            </div>
            <span>{{ t("library.count", { count: filteredPacks.length }) }}</span>
          </div>

          <div v-if="hasPacks" class="library-layout">
            <div class="pack-list">
              <button
                v-for="pack in filteredPacks"
                :key="pack.id"
                class="library-pack-row"
                :class="{ selected: selectedPackId === pack.id }"
                @click="selectedPackId = pack.id"
              >
                <img :src="pack.cover" alt="" />
                <div>
                  <strong>{{ pack.name }}</strong>
                  <span>{{ pack.summary }}</span>
                </div>
                <small>{{ statusLabel(pack) }}</small>
              </button>
            </div>

            <article v-if="selectedPack" class="detail-panel">
              <img :src="selectedPack.cover" alt="" class="detail-cover" />
              <div class="detail-content">
                <span class="eyebrow">{{ selectedPack.studio }}</span>
                <h2>{{ selectedPack.name }}</h2>
                <p>{{ selectedPack.description }}</p>
                <div class="tag-row">
                  <span v-for="tag in selectedPack.tags" :key="tag">{{ tag }}</span>
                </div>
                <dl class="spec-grid">
                  <div>
                    <dt>Minecraft</dt>
                    <dd>{{ selectedPack.minecraftVersion }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("spec.loader") }}</dt>
                    <dd>{{ selectedPack.loader }} {{ selectedPack.loaderVersion }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("spec.recommendedMemory") }}</dt>
                    <dd>{{ selectedPack.recommendedMemoryMb }} MB</dd>
                  </div>
                  <div>
                    <dt>{{ t("spec.size") }}</dt>
                    <dd>{{ selectedPack.sizeGb }} GB</dd>
                  </div>
                </dl>
                <div class="action-row">
                  <button class="primary-button" :disabled="busyPackId === selectedPack.id" @click="primaryAction(selectedPack).action">
                    <component :is="primaryAction(selectedPack).icon" :size="19" />
                    <span>{{ busyPackId === selectedPack.id ? t("action.processing") : primaryAction(selectedPack).label }}</span>
                  </button>
                  <button class="icon-button bordered" :title="t('action.openFolder')" @click="openFolder(selectedPack)">
                    <FolderOpen :size="19" />
                  </button>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="empty-state">
            <PackageOpen :size="44" />
            <strong>{{ t("empty.noPacksTitle") }}</strong>
            <p>{{ t("empty.noPacksBody") }}</p>
            <form class="quick-create-panel compact" @submit.prevent="createVanillaInstance">
              <label>
                <span>{{ t("create.instanceName") }}</span>
                <input v-model="newInstanceName" type="text" autocomplete="off" />
              </label>
              <label>
                <span>{{ t("create.minecraftVersion") }}</span>
                <input v-model="newMinecraftVersion" type="text" autocomplete="off" />
              </label>
              <button class="primary-button" type="submit" :disabled="createInstanceDisabled">
                <Plus :size="18" />
                <span>{{ isCreatingInstance ? t("action.processing") : t("create.vanilla") }}</span>
              </button>
            </form>
          </div>
        </section>

        <section v-if="activeView === 'instances'" class="instances-view">
          <div class="section-title">
            <div>
              <span class="eyebrow">Library</span>
              <h1>{{ t("instances.title") }}</h1>
            </div>
            <span>{{ t("instances.count", { count: installedPacks.length }) }}</span>
          </div>

          <div v-if="installedPacks.length > 0" class="instance-table">
            <article v-for="pack in installedPacks" :key="pack.id" class="instance-row">
              <img :src="pack.cover" alt="" />
              <div>
                <strong>{{ pack.name }}</strong>
                <span>{{ pack.minecraftVersion }} / {{ pack.loader }} / {{ pack.version }}</span>
              </div>
              <span class="status-pill">{{ statusLabel(pack) }}</span>
              <button class="icon-button bordered" :title="t('action.updateInstance')" :disabled="pack.version === pack.latestVersion" @click="updatePack(pack)">
                <RefreshCw :size="18" />
              </button>
              <button class="primary-button small" @click="launchPack(pack)">
                <Play :size="17" />
                <span>{{ t("action.launch") }}</span>
              </button>
            </article>
          </div>

          <div v-else class="empty-state">
            <PackageOpen :size="44" />
            <strong>{{ t("empty.noInstancesTitle") }}</strong>
            <p>{{ t("empty.noInstancesBody") }}</p>
          </div>
        </section>

        <section v-if="activeView === 'settings' && settings" class="settings-view">
          <div class="section-title">
            <div>
              <span class="eyebrow">Launcher</span>
              <h1>{{ t("settings.title") }}</h1>
            </div>
            <SlidersHorizontal :size="22" />
          </div>

          <div class="settings-grid">
            <div class="language-setting">
              <span>{{ t("a11y.language") }}</span>
              <div class="language-card-row">
                <button
                  v-for="option in localeOptions"
                  :key="option.value"
                  class="language-card"
                  :class="{ active: locale === option.value }"
                  type="button"
                  :aria-pressed="locale === option.value"
                  @click="setLocale(option.value)"
                >
                  <span>{{ localeShortLabels[option.value] }}</span>
                  <strong>{{ option.label }}</strong>
                  <small>{{ localePreviewLabels[option.value] }}</small>
                </button>
              </div>
              <strong>{{ activeLocaleOption?.label }}</strong>
            </div>
            <label class="setting-row">
              <span>{{ t("settings.gameDirectory") }}</span>
              <input :value="settings.gameDirectory" @change="saveSetting('gameDirectory', ($event.target as HTMLInputElement).value)" />
            </label>
            <label class="setting-row">
              <span>{{ t("settings.java") }}</span>
              <input :value="javaDisplayValue" @change="saveSetting('javaPath', ($event.target as HTMLInputElement).value)" />
            </label>
            <label class="setting-row">
              <span>{{ t("settings.memory") }}</span>
              <input
                type="range"
                min="2048"
                max="16384"
                step="512"
                :value="settings.memoryMb"
                @input="saveSetting('memoryMb', Number(($event.target as HTMLInputElement).value))"
              />
              <strong>{{ settings.memoryMb }} MB</strong>
            </label>
            <label class="setting-row">
              <span>{{ t("settings.concurrentDownloads") }}</span>
              <input
                type="number"
                min="1"
                max="16"
                :value="settings.concurrentDownloads"
                @change="saveSetting('concurrentDownloads', Number(($event.target as HTMLInputElement).value))"
              />
            </label>
            <label class="setting-row">
              <span>{{ t("settings.downloadMirror") }}</span>
              <select
                :value="settings.downloadMirror"
                @change="saveSetting('downloadMirror', ($event.target as HTMLSelectElement).value as LauncherSettings['downloadMirror'])"
              >
                <option value="BMCLAPI">BMCLAPI</option>
                <option value="Official">Official</option>
                <option value="MCBBS">MCBBS</option>
              </select>
            </label>
            <label class="setting-row">
              <span>{{ t("settings.playerName") }}</span>
              <input :value="settings.playerName" @change="saveSetting('playerName', ($event.target as HTMLInputElement).value)" />
            </label>
            <label class="setting-row">
              <span>{{ t("settings.accountType") }}</span>
              <select
                :value="settings.accountType"
                @change="saveSetting('accountType', ($event.target as HTMLSelectElement).value as LauncherSettings['accountType'])"
              >
                <option value="offline">{{ t("settings.offlineAccount") }}</option>
                <option value="microsoft">{{ t("settings.microsoftAccount") }}</option>
              </select>
            </label>
            <div class="microsoft-login-panel">
              <div class="microsoft-login-heading">
                <span class="microsoft-badge">
                  <ShieldCheck :size="20" />
                </span>
                <div>
                  <strong>{{ t("microsoft.title") }}</strong>
                  <p>{{ t("microsoft.body") }}</p>
                </div>
              </div>

              <label class="microsoft-client-row">
                <span>{{ t("settings.microsoftClientId") }}</span>
                <input
                  :value="settings.microsoftClientId"
                  :placeholder="t('microsoft.clientIdPlaceholder')"
                  autocomplete="off"
                  @change="saveSetting('microsoftClientId', ($event.target as HTMLInputElement).value)"
                />
              </label>

              <div class="microsoft-action-row">
                <button class="primary-button" type="button" :disabled="isStartingMicrosoftLogin" @click="startMicrosoftLogin">
                  <KeyRound :size="18" />
                  <span>{{ isStartingMicrosoftLogin ? t("action.processing") : t("microsoft.start") }}</span>
                </button>
                <button class="ghost-button" type="button" :disabled="!microsoftLogin || isPollingMicrosoftLogin" @click="pollMicrosoftLogin(false)">
                  <RefreshCw :size="17" />
                  <span>{{ isPollingMicrosoftLogin ? t("action.processing") : t("microsoft.check") }}</span>
                </button>
              </div>

              <div v-if="microsoftLogin" class="device-code-panel">
                <span>{{ t("microsoft.userCode") }}</span>
                <strong>{{ microsoftLogin.userCode }}</strong>
                <button class="icon-button bordered" type="button" :title="t('microsoft.copyCode')" @click="copyMicrosoftCode">
                  <Copy :size="18" />
                </button>
                <button class="ghost-button" type="button" @click="openMicrosoftVerification">
                  <ExternalLink :size="17" />
                  <span>{{ t("microsoft.openPage") }}</span>
                </button>
              </div>

              <div class="auth-status" :class="microsoftStatusKind">
                <CheckCircle2 v-if="microsoftStatusKind === 'complete'" :size="18" />
                <AlertCircle v-else-if="microsoftStatusKind === 'failed'" :size="18" />
                <KeyRound v-else :size="18" />
                <span>{{ microsoftLoginMessage }}</span>
              </div>
            </div>
            <label class="toggle-row">
              <span>{{ t("settings.closeAfterLaunch") }}</span>
              <input
                type="checkbox"
                :checked="settings.closeAfterLaunch"
                @change="saveSetting('closeAfterLaunch', ($event.target as HTMLInputElement).checked)"
              />
            </label>
          </div>
        </section>
      </template>

      <section v-else class="loading-view">
        <img src="/assets/canoe-mark.svg" alt="" />
        <span>{{ t("loading") }}</span>
      </section>
    </section>

    <aside v-if="activeJob" class="job-dock">
      <div>
        <CheckCircle2 v-if="activeJob.status === 'complete'" :size="18" />
        <Download v-else :size="18" />
        <strong>{{ jobMessage(activeJob) }}</strong>
      </div>
      <progress :value="activeJob.progress" max="100" />
    </aside>
  </main>
</template>
