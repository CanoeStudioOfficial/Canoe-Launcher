package studio.canoe.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CoreService {
    private final Path stateRoot = Path.of(System.getProperty("user.home"), ".canoe-launcher");
    private final Path settingsFile = stateRoot.resolve("settings.json");
    private final CanoeCoreFacade core;
    private final AccountStore accountStore = new AccountStore(stateRoot);
    private CoreEventEmitter emitter = (event, payload) -> {
    };
    private Map<String, Object> settings;

    public CoreService() throws IOException {
        Files.createDirectories(stateRoot);
        this.settings = loadSettings();
        this.core = new CanoeCoreFacade(Path.of(String.valueOf(settings.get("gameDirectory"))));
    }

    public void setEmitter(CoreEventEmitter emitter) {
        this.emitter = emitter;
    }

    public Object handle(String command, Map<String, Object> payload) throws Exception {
        return switch (command) {
            case "ping" -> ping();
            case "getLibrary" -> getLibrary();
            case "getSettings" -> settings;
            case "listAccounts" -> accountStore.listAccounts();
            case "listProcesses" -> core.listProcesses();
            case "updateSettings" -> updateSettings(payload);
            case "addOfflineAccount" -> addOfflineAccount(payload);
            case "installPack" -> installPack(requiredString(payload, "packId"));
            case "updatePack" -> updatePack(requiredString(payload, "packId"));
            case "launchInstance" -> launchInstance(requiredString(payload, "packId"));
            case "stopProcess" -> stopProcess(requiredString(payload, "processId"));
            case "openInstanceFolder" -> openInstanceFolder(requiredString(payload, "packId"));
            default -> throw new IllegalArgumentException("Unknown core command: " + command);
        };
    }

    private Map<String, Object> ping() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coreVersion", "0.1.0");
        payload.put("protocolVersion", 1);
        payload.put("javaVersion", System.getProperty("java.version"));
        payload.put("coreMode", "self-developed-java-core");
        payload.put("stateRoot", stateRoot.toString());
        return payload;
    }

    private Map<String, Object> getLibrary() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("featuredPackId", "");
        payload.put("packs", packs());
        payload.put("news", news());
        return payload;
    }

    private Map<String, Object> updateSettings(Map<String, Object> patch) throws IOException {
        Map<String, Object> next = new LinkedHashMap<>(settings);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (entry.getValue() != null) {
                next.put(entry.getKey(), entry.getValue());
            }
        }
        settings = next;
        core.setGameDirectory(Path.of(String.valueOf(settings.get("gameDirectory"))));
        saveSettings();
        return settings;
    }

    private Map<String, Object> addOfflineAccount(Map<String, Object> payload) throws IOException {
        String username = String.valueOf(payload.getOrDefault("username", settings.getOrDefault("playerName", "LocalPlayer")));
        Map<String, Object> account = accountStore.addOfflineAccount(username);
        settings = new LinkedHashMap<>(settings);
        settings.put("playerName", username);
        settings.put("profileId", account.get("id"));
        settings.put("accountType", "offline");
        saveSettings();
        return account;
    }

    private Map<String, Object> installPack(String packId) throws Exception {
        return runJob(packId, "install", List.of(
                step("job.install.readManifest", "Reading modpack manifest"),
                step("job.install.checkVersion", "Checking Minecraft and loader versions"),
                step("job.install.prepareLibraries", "Preparing runtime, assets, and libraries"),
                step("job.install.writeInstance", "Writing instance configuration"),
                step("job.install.finish", "Finishing installation")
        ), () -> core.installOrUpdate(packById(packId), settings, false));
    }

    private Map<String, Object> updatePack(String packId) throws Exception {
        return runJob(packId, "update", List.of(
                step("job.update.compare", "Comparing local and remote manifests"),
                step("job.update.downloadDelta", "Downloading changed files"),
                step("job.update.verify", "Verifying hashes"),
                step("job.update.migrate", "Migrating instance configuration"),
                step("job.update.finish", "Finishing update")
        ), () -> core.installOrUpdate(packById(packId), settings, true));
    }

    private Map<String, Object> launchInstance(String packId) throws Exception {
        return runJob(packId, "launch", List.of(
                step("job.launch.check", "Checking account, Java, and runtime"),
                step("job.launch.arguments", "Generating launch arguments"),
                step("job.launch.directory", "Preparing runtime directory"),
                step("job.launch.process", "Starting or staging the game process")
        ), () -> core.launchInstance(packById(packId), settings, accountStore.accountFromSettings(settings), emitter));
    }

    private Map<String, Object> stopProcess(String processId) throws Exception {
        return core.stopProcess(processId);
    }

    private Map<String, Object> openInstanceFolder(String packId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("path", core.instanceDirectory(packId).toString());
        return payload;
    }

    private Map<String, Object> runJob(String packId, String kind, List<JobStep> steps, ThrowingSupplier<Map<String, Object>> work) throws Exception {
        String jobId = kind + "-" + packId + "-" + System.currentTimeMillis();
        for (int index = 0; index < steps.size(); index++) {
            JobStep step = steps.get(index);
            emitJob(jobId, packId, kind, "running", Math.round(((index + 1) * 92f) / steps.size()), step.fallback(), step.key());
            Thread.sleep(220);
        }

        Map<String, Object> result = work.get();
        String completeKey = "launch".equals(kind) ? "job.complete.launch" : "job.complete.generic";
        String completeMessage = "launch".equals(kind) ? "Game runtime prepared by Canoe Core" : "Task complete";
        Map<String, Object> complete = emitJob(jobId, packId, kind, "complete", 100, completeMessage, completeKey);
        complete.put("result", result);
        return complete;
    }

    private Map<String, Object> emitJob(String jobId, String packId, String kind, String status, int progress, String message, String messageKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("packId", packId);
        payload.put("kind", kind);
        payload.put("status", status);
        payload.put("progress", progress);
        payload.put("message", message);
        payload.put("messageKey", messageKey);
        payload.put("timestamp", Instant.now().toString());
        emitter.emit("job", payload);
        return payload;
    }

    private List<Map<String, Object>> packs() {
        return List.of();
    }

    private List<Map<String, Object>> news() {
        Map<String, Object> core = new LinkedHashMap<>();
        core.put("id", "core-roadmap");
        core.put("title", "Java launcher core bridge is active");
        core.put("body", "Electron now talks to a standalone Canoe Java process through a line-delimited JSON protocol.");
        core.put("date", "2026-08-02");

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("id", "pack-policy");
        runtime.put("title", "Pack catalog is intentionally empty");
        runtime.put("body", "No built-in modpacks are bundled until real releases exist.");
        runtime.put("date", "2026-08-02");

        return List.of(core, runtime);
    }

    private Map<String, Object> loadSettings() throws IOException {
        if (Files.exists(settingsFile)) {
            Object parsed = Json.parse(Files.readString(settingsFile, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> loaded = new LinkedHashMap<>();
                map.forEach((key, value) -> loaded.put(String.valueOf(key), value));
                return withDefaultSettings(loaded);
            }
        }
        Map<String, Object> defaults = withDefaultSettings(new LinkedHashMap<>());
        Files.createDirectories(stateRoot);
        Files.writeString(settingsFile, Json.stringify(defaults), StandardCharsets.UTF_8);
        return defaults;
    }

    private Map<String, Object> withDefaultSettings(Map<String, Object> loaded) {
        loaded.putIfAbsent("gameDirectory", stateRoot.resolve("instances").toString());
        loaded.putIfAbsent("javaPath", "auto");
        loaded.putIfAbsent("memoryMb", 6144);
        loaded.putIfAbsent("concurrentDownloads", 6);
        loaded.putIfAbsent("downloadMirror", "BMCLAPI");
        loaded.putIfAbsent("closeAfterLaunch", false);
        loaded.putIfAbsent("playerName", "LocalPlayer");
        loaded.putIfAbsent("accountType", "offline");
        loaded.putIfAbsent("profileId", UUID.nameUUIDFromBytes("OfflinePlayer:LocalPlayer".getBytes(StandardCharsets.UTF_8)).toString());
        return loaded;
    }

    private void saveSettings() throws IOException {
        Files.createDirectories(stateRoot);
        Files.writeString(settingsFile, Json.stringify(settings), StandardCharsets.UTF_8);
    }

    private Map<String, Object> packById(String packId) throws IOException {
        return packs().stream()
                .filter(pack -> packId.equals(pack.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown modpack: " + packId));
    }

    private static JobStep step(String key, String fallback) {
        return new JobStep(key, fallback);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing payload field: " + key);
        }
        return String.valueOf(value);
    }

    private record JobStep(String key, String fallback) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
