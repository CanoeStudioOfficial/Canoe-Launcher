package studio.canoe.launcher.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class GameProcessManager {
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();

    public Map<String, Object> start(String packId, List<String> command, Path runDirectory, Path logFile, CoreEventEmitter emitter) throws IOException {
        Files.createDirectories(runDirectory);
        Files.createDirectories(logFile.getParent());

        Process process = new ProcessBuilder(command)
                .directory(runDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        String processId = UUID.randomUUID().toString();
        ManagedProcess managed = new ManagedProcess(processId, packId, process, command, runDirectory, logFile);
        processes.put(processId, managed);
        pumpOutput(managed, emitter);
        watchExit(managed, emitter);
        return managed.snapshot();
    }

    public List<Map<String, Object>> listProcesses() {
        return processes.values().stream()
                .map(ManagedProcess::snapshot)
                .toList();
    }

    public Map<String, Object> stop(String processId) throws InterruptedException {
        ManagedProcess managed = processes.get(processId);
        if (managed == null) {
            throw new IllegalArgumentException("Unknown process: " + processId);
        }

        managed.process.destroy();
        if (!managed.process.waitFor(8, TimeUnit.SECONDS)) {
            managed.process.destroyForcibly();
        }
        managed.status = "stopped";
        managed.exitedAt = Instant.now().toString();
        managed.exitCode = managed.process.isAlive() ? null : managed.process.exitValue();
        return managed.snapshot();
    }

    private void pumpOutput(ManagedProcess managed, CoreEventEmitter emitter) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(managed.process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Files.writeString(managed.logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                            Files.exists(managed.logFile)
                                    ? java.nio.file.StandardOpenOption.APPEND
                                    : java.nio.file.StandardOpenOption.CREATE);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("processId", managed.id);
                    payload.put("packId", managed.packId);
                    payload.put("line", line);
                    payload.put("timestamp", Instant.now().toString());
                    emitter.emit("log", payload);
                }
            } catch (IOException error) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("processId", managed.id);
                payload.put("packId", managed.packId);
                payload.put("message", error.getMessage());
                payload.put("timestamp", Instant.now().toString());
                emitter.emit("process-error", payload);
            }
        }, "canoe-game-log-" + managed.id);
        thread.setDaemon(true);
        thread.start();
    }

    private void watchExit(ManagedProcess managed, CoreEventEmitter emitter) {
        Thread thread = new Thread(() -> {
            try {
                int code = managed.process.waitFor();
                managed.status = "exited";
                managed.exitCode = code;
                managed.exitedAt = Instant.now().toString();
                emitter.emit("process", managed.snapshot());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }, "canoe-game-watch-" + managed.id);
        thread.setDaemon(true);
        thread.start();
    }

    private static final class ManagedProcess {
        private final String id;
        private final String packId;
        private final Process process;
        private final List<String> command;
        private final Path runDirectory;
        private final Path logFile;
        private final String startedAt = Instant.now().toString();
        private String status = "running";
        private Integer exitCode;
        private String exitedAt;

        private ManagedProcess(String id, String packId, Process process, List<String> command, Path runDirectory, Path logFile) {
            this.id = id;
            this.packId = packId;
            this.process = process;
            this.command = new ArrayList<>(command);
            this.runDirectory = runDirectory;
            this.logFile = logFile;
        }

        private Map<String, Object> snapshot() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("processId", id);
            payload.put("packId", packId);
            payload.put("pid", process.pid());
            payload.put("status", status);
            payload.put("exitCode", exitCode);
            payload.put("startedAt", startedAt);
            payload.put("exitedAt", exitedAt);
            payload.put("runDirectory", runDirectory.toString());
            payload.put("logFile", logFile.toString());
            payload.put("command", command);
            return payload;
        }
    }
}
