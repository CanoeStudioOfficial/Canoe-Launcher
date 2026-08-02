package studio.canoe.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CatalogStore {
    private final Path catalogFile;

    public CatalogStore(Path stateRoot) {
        this.catalogFile = stateRoot.resolve("catalog.json");
    }

    public List<Map<String, Object>> listPacks(CanoeCoreFacade core) throws IOException {
        List<Map<String, Object>> packs = readRawPacks();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> pack : packs) {
            result.add(withRuntimeStatus(pack, core));
        }
        return result;
    }

    public Map<String, Object> createVanillaInstance(Map<String, Object> payload, CanoeCoreFacade core) throws IOException {
        String minecraftVersion = stringValue(payload.getOrDefault("minecraftVersion", "1.20.1")).trim();
        String requestedName = stringValue(payload.getOrDefault("name", "Minecraft " + minecraftVersion)).trim();
        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("minecraftVersion is required.");
        }
        if (requestedName.isBlank()) {
            requestedName = "Minecraft " + minecraftVersion;
        }

        List<Map<String, Object>> packs = readRawPacks();
        String id = uniqueId(slug(requestedName + "-" + minecraftVersion), packs);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("id", id);
        pack.put("name", requestedName);
        pack.put("studio", "Local");
        pack.put("summary", "A locally created Vanilla Minecraft instance.");
        pack.put("description", "Created by Canoe Launcher. Canoe Core can install Minecraft metadata, client, libraries, assets, and launch arguments for this instance.");
        pack.put("version", minecraftVersion);
        pack.put("latestVersion", minecraftVersion);
        pack.put("minecraftVersion", minecraftVersion);
        pack.put("loader", "Vanilla");
        pack.put("loaderVersion", "");
        pack.put("recommendedMemoryMb", 4096);
        pack.put("sizeGb", 0.0);
        pack.put("status", "remote");
        pack.put("tags", List.of("Local", "Vanilla", minecraftVersion));
        pack.put("cover", "/assets/canoe-mark.svg");
        pack.put("accent", "#2f7a5f");
        pack.put("changelog", List.of("Local Vanilla instance created", "Ready for runtime installation"));
        pack.put("createdAt", Instant.now().toString());

        packs.add(pack);
        saveRawPacks(packs);
        return withRuntimeStatus(pack, core);
    }

    private Map<String, Object> withRuntimeStatus(Map<String, Object> source, CanoeCoreFacade core) throws IOException {
        Map<String, Object> pack = withDefaults(new LinkedHashMap<>(source));
        String id = stringValue(pack.get("id"));
        String latestVersion = stringValue(pack.get("latestVersion"));
        if (!core.isInstalled(id)) {
            pack.put("status", "remote");
            return pack;
        }

        String installedVersion = core.installedVersion(id).orElse(stringValue(pack.get("version")));
        pack.put("version", installedVersion);
        pack.put("status", latestVersion.equals(installedVersion) ? "installed" : "updateAvailable");
        return pack;
    }

    private Map<String, Object> withDefaults(Map<String, Object> pack) {
        String minecraftVersion = stringValue(pack.getOrDefault("minecraftVersion", "1.20.1"));
        pack.putIfAbsent("studio", "Local");
        pack.putIfAbsent("summary", stringValue(pack.getOrDefault("name", "Minecraft")) + " instance.");
        pack.putIfAbsent("description", "Local Minecraft instance.");
        pack.putIfAbsent("version", minecraftVersion);
        pack.putIfAbsent("latestVersion", minecraftVersion);
        pack.putIfAbsent("loader", "Vanilla");
        pack.putIfAbsent("loaderVersion", "");
        pack.putIfAbsent("recommendedMemoryMb", 4096);
        pack.putIfAbsent("sizeGb", 0.0);
        pack.putIfAbsent("status", "remote");
        pack.putIfAbsent("tags", List.of("Local", stringValue(pack.get("loader")), minecraftVersion));
        pack.putIfAbsent("cover", "/assets/canoe-mark.svg");
        pack.putIfAbsent("accent", "#2f7a5f");
        pack.putIfAbsent("changelog", List.of());
        return pack;
    }

    private List<Map<String, Object>> readRawPacks() throws IOException {
        if (!Files.exists(catalogFile)) {
            saveRawPacks(List.of());
            return new ArrayList<>();
        }

        Object parsed = Json.parse(Files.readString(catalogFile, StandardCharsets.UTF_8));
        if (!(parsed instanceof List<?> list)) {
            throw new IOException("Invalid catalog file: " + catalogFile);
        }

        List<Map<String, Object>> packs = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> pack = new LinkedHashMap<>();
                map.forEach((key, value) -> pack.put(String.valueOf(key), value));
                packs.add(pack);
            }
        }
        return packs;
    }

    private void saveRawPacks(List<Map<String, Object>> packs) throws IOException {
        Files.createDirectories(catalogFile.getParent());
        Files.writeString(catalogFile, Json.stringify(packs), StandardCharsets.UTF_8);
    }

    private String uniqueId(String base, List<Map<String, Object>> packs) {
        String root = base.isBlank() ? "minecraft-instance" : base;
        String id = root;
        int suffix = 2;
        while (containsId(id, packs)) {
            id = root + "-" + suffix;
            suffix++;
        }
        return id;
    }

    private boolean containsId(String id, List<Map<String, Object>> packs) {
        return packs.stream().anyMatch(pack -> id.equals(pack.get("id")));
    }

    private String slug(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "minecraft-instance" : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
