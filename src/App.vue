<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  Bell,
  CheckCircle2,
  Download,
  FolderOpen,
  Gauge,
  Globe2,
  HardDriveDownload,
  Home,
  LibraryBig,
  PackageOpen,
  Play,
  RefreshCw,
  Search,
  Settings,
  SlidersHorizontal,
  TerminalSquare,
  UserRound,
} from "lucide-vue-next";
import { useI18n } from "@/i18n";
import { launcherClient } from "@/services/launcherClient";
import type { LaunchJobEvent, LauncherLibrary, LauncherSettings, Modpack } from "@/types/launcher";

const activeView = ref<"home" | "library" | "instances" | "settings">("home");
const library = ref<LauncherLibrary | null>(null);
const selectedPackId = ref("canoe-origins");
const settings = ref<LauncherSettings | null>(null);
const jobs = ref<LaunchJobEvent[]>([]);
const searchQuery = ref("");
const busyPackId = ref<string | null>(null);
const { locale, localeOptions, t, localizePack, localizeNews, translateJobMessage } = useI18n();

const navItems = [
  { id: "home", labelKey: "nav.home", icon: Home },
  { id: "library", labelKey: "nav.library", icon: LibraryBig },
  { id: "instances", labelKey: "nav.instances", icon: PackageOpen },
  { id: "settings", labelKey: "nav.settings", icon: Settings },
] as const;

const packs = computed(() => library.value?.packs ?? []);
const localizedPacks = computed(() => packs.value.map(localizePack));
const featuredPack = computed(() => localizedPacks.value.find((pack) => pack.id === library.value?.featuredPackId) ?? null);
const selectedPack = computed(() => localizedPacks.value.find((pack) => pack.id === selectedPackId.value) ?? featuredPack.value);
const installedPacks = computed(() => localizedPacks.value.filter((pack) => pack.status !== "remote"));
const localizedNews = computed(() => library.value?.news.map(localizeNews) ?? []);
const latestJob = computed(() => jobs.value[0]);
const activeJob = computed(() => jobs.value.find((job) => job.status === "running"));
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
  const [libraryPayload, settingsPayload] = await Promise.all([
    launcherClient.getLibrary(),
    launcherClient.getSettings(),
  ]);

  library.value = libraryPayload;
  selectedPackId.value = libraryPayload.featuredPackId;
  settings.value = settingsPayload;

  launcherClient.onJobEvent((event) => {
    jobs.value = [event, ...jobs.value.filter((job) => job.jobId !== event.jobId)].slice(0, 8);
    patchPackAfterJob(event);
  });
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

function jobMessage(job: LaunchJobEvent) {
  return translateJobMessage(job.messageKey, job.message);
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
          <strong>{{ t("account.player") }}</strong>
          <span>{{ t("account.profile") }}</span>
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
          <label class="locale-picker" :title="t('a11y.language')">
            <Globe2 :size="18" />
            <select v-model="locale" :aria-label="t('a11y.language')">
              <option v-for="option in localeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <button class="icon-button" :title="t('top.downloads')">
            <HardDriveDownload :size="19" />
          </button>
          <button class="icon-button" :title="t('top.notifications')">
            <Bell :size="19" />
          </button>
        </div>
      </header>

      <template v-if="library && selectedPack && featuredPack">
        <section v-if="activeView === 'home'" class="view-grid">
          <article class="feature-panel">
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
              <h2>{{ t("home.canoePacks") }}</h2>
              <button class="ghost-button" @click="activeView = 'library'">{{ t("home.viewAll") }}</button>
            </div>
            <div class="pack-grid">
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
                <small>{{ pack.minecraftVersion }} · {{ pack.loader }}</small>
              </button>
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
              <span class="eyebrow">Canoe Studio</span>
              <h1>{{ t("library.title") }}</h1>
            </div>
            <span>{{ t("library.count", { count: filteredPacks.length }) }}</span>
          </div>

          <div class="library-layout">
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

            <article class="detail-panel">
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
        </section>

        <section v-if="activeView === 'instances'" class="instances-view">
          <div class="section-title">
            <div>
              <span class="eyebrow">Library</span>
              <h1>{{ t("instances.title") }}</h1>
            </div>
            <span>{{ t("instances.count", { count: installedPacks.length }) }}</span>
          </div>

          <div class="instance-table">
            <article v-for="pack in installedPacks" :key="pack.id" class="instance-row">
              <img :src="pack.cover" alt="" />
              <div>
                <strong>{{ pack.name }}</strong>
                <span>{{ pack.minecraftVersion }} · {{ pack.loader }} · {{ pack.version }}</span>
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
