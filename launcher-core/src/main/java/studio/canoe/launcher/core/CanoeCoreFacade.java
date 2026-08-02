package studio.canoe.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CanoeCoreFacade {
    private final GameProcessManager processes = new GameProcessManager();
    private Path gameDirectory;

    public CanoeCoreFacade(Path gameDirectory) {
        setGameDirectory(gameDirectory);
    }

    public void setGameDirectory(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    public Path instanceDirectory(String packId) {
        return gameDirectory.resolve(packId);
    }

    public boolean isInstalled(String packId) {
        return Files.exists(instanceDirectory(packId).resolve("instance.json"));
    }

    public Optional<String> installedVersion(String packId) throws IOException {
        Path instanceFile = instanceDirectory(packId).resolve("instance.json");
        if (!Files.exists(instanceFile)) {
            return Optional.empty();
        }

        Object parsed = Json.parse(Files.readString(instanceFile, StandardCharsets.UTF_8));
        if (parsed instanceof Map<?, ?> map && map.get("version") != null) {
            return Optional.of(String.valueOf(map.get("version")));
        }
        return Optional.empty();
    }

    public Map<String, Object> installOrUpdate(Map<String, Object> pack, Map<String, Object> settings, boolean update) throws IOException, InterruptedException {
        Files.createDirectories(gameDirectory);
        Path instanceDir = instanceDirectory(String.valueOf(pack.get("id")));
        Path minecraftDir = instanceDir.resolve("minecraft");
        Files.createDirectories(minecraftDir);
        Files.createDirectories(instanceDir.resolve("logs"));

        MinecraftRuntime runtime = new MinecraftRuntime(minecraftDir);
        Map<String, Object> runtimePlan = runtime.install(pack, settings, update);

        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("id", pack.get("id"));
        instance.put("name", pack.get("name"));
        instance.put("version", pack.get("latestVersion"));
        instance.put("minecraftVersion", pack.get("minecraftVersion"));
        instance.put("loader", pack.get("loader"));
        instance.put("loaderVersion", pack.get("loaderVersion"));
        instance.put("installedAt", Instant.now().toString());
        instance.put("updated", update);
        instance.put("coreRuntime", "canoe-java-core");
        instance.put("coreDesign", "Self-developed Minecraft runtime inspired by HMCL architecture, without HMCL class dependencies.");
        instance.put("runtimePlan", runtimePlan);
        Files.writeString(instanceDir.resolve("instance.json"), Json.stringify(instance), StandardCharsets.UTF_8);
        Files.writeString(instanceDir.resolve("runtime-plan.json"), Json.stringify(runtimePlan), StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", update ? "update" : "install");
        result.put("gameDirectory", gameDirectory.toString());
        result.put("instanceDirectory", instanceDir.toString());
        result.put("minecraftDirectory", minecraftDir.toString());
        result.put("runtimePlan", runtimePlan);
        result.put("downloadMirror", settings.get("downloadMirror"));
        result.put("concurrentDownloads", settings.get("concurrentDownloads"));
        return result;
    }

    public Map<String, Object> launchInstance(Map<String, Object> pack, Map<String, Object> settings, Map<String, Object> account, CoreEventEmitter emitter) throws IOException, InterruptedException {
        String packId = String.valueOf(pack.get("id"));
        if (!isInstalled(packId)) {
            installOrUpdate(pack, settings, false);
        }

        Path instanceDir = instanceDirectory(packId);
        MinecraftRuntime runtime = new MinecraftRuntime(instanceDir.resolve("minecraft"));
        Map<String, Object> launchPlan = runtime.buildLaunchPlan(pack, settings, account);
        Files.writeString(instanceDir.resolve("last-launch-plan.json"), Json.stringify(launchPlan), StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>(launchPlan);
        if (Boolean.TRUE.equals(launchPlan.get("canStartProcess"))) {
            @SuppressWarnings("unchecked")
            List<String> command = (List<String>) launchPlan.get("command");
            Map<String, Object> process = processes.start(packId, command, Path.of(String.valueOf(launchPlan.get("gameDirectory"))), runtime.logFile(packId), emitter);
            result.put("processStarted", true);
            result.put("process", process);
        } else {
            result.put("processStarted", false);
            result.put("processBlockedReason", launchPlan.get("processLaunchSupport"));
        }
        return result;
    }

    public List<Map<String, Object>> listProcesses() {
        return processes.listProcesses();
    }

    public Map<String, Object> stopProcess(String processId) throws InterruptedException {
        return processes.stop(processId);
    }
}
