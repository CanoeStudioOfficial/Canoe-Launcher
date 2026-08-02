package studio.canoe.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MinecraftRuntime {
    private static final String OFFICIAL_VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String BMCL_VERSION_MANIFEST = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";
    private static final String FABRIC_PROFILE = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json";
    private static final String BMCL_FABRIC_PROFILE = "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/%s/%s/profile/json";

    private final DownloadClient downloads = new DownloadClient();
    private final Path minecraftRoot;

    public MinecraftRuntime(Path minecraftRoot) {
        this.minecraftRoot = minecraftRoot;
    }

    public Path root() {
        return minecraftRoot;
    }

    public Map<String, Object> install(Map<String, Object> pack, Map<String, Object> settings, boolean update) throws IOException, InterruptedException {
        Files.createDirectories(minecraftRoot);

        String minecraftVersion = string(pack, "minecraftVersion");
        String loader = string(pack, "loader");
        String loaderVersion = string(pack, "loaderVersion");
        String mirror = String.valueOf(settings.getOrDefault("downloadMirror", "Official"));

        Map<String, Object> base = installBaseVersion(minecraftVersion, mirror);
        Map<String, Object> loaderProfile = installLoaderProfile(loader, loaderVersion, minecraftVersion, mirror);
        String launchVersionId = String.valueOf(loaderProfile.getOrDefault("versionId", base.get("versionId")));
        Path versionJson = versionJson(launchVersionId);

        Map<String, Object> mergedVersion = loadMergedVersion(versionJson);
        Map<String, Object> libraries = installLibraries(mergedVersion, mirror);
        Map<String, Object> assets = installAssets(mergedVersion, mirror);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", update ? "update" : "install");
        result.put("runtime", "canoe-java-core");
        result.put("minecraftRoot", minecraftRoot.toString());
        result.put("minecraftVersion", minecraftVersion);
        result.put("launchVersionId", launchVersionId);
        result.put("baseVersion", base);
        result.put("loaderProfile", loaderProfile);
        result.put("libraries", libraries);
        result.put("assets", assets);
        result.put("supportsProcessLaunch", isLaunchSupported(loader));
        result.put("installedAt", Instant.now().toString());
        return result;
    }

    public Map<String, Object> buildLaunchPlan(Map<String, Object> pack, Map<String, Object> settings, Map<String, Object> account) throws IOException {
        String minecraftVersion = string(pack, "minecraftVersion");
        String loader = string(pack, "loader");
        String loaderVersion = string(pack, "loaderVersion");
        String launchVersionId = launchVersionId(loader, loaderVersion, minecraftVersion);
        Path versionJson = versionJson(launchVersionId);
        if (!Files.exists(versionJson)) {
            launchVersionId = minecraftVersion;
            versionJson = versionJson(launchVersionId);
        }
        if (!Files.exists(versionJson)) {
            throw new IOException("Minecraft runtime is not installed for " + minecraftVersion);
        }

        Map<String, Object> version = loadMergedVersion(versionJson);
        String javaPath = detectJava(String.valueOf(settings.get("javaPath")));
        int memoryMb = intValue(settings.getOrDefault("memoryMb", 4096));
        String playerName = String.valueOf(account.getOrDefault("username", "LocalPlayer"));
        String profileId = String.valueOf(account.getOrDefault("id", settings.getOrDefault("profileId", playerName)));
        Path gameDirectory = minecraftRoot.resolve("run");
        Path nativesDirectory = minecraftRoot.resolve("versions").resolve(launchVersionId).resolve("natives");
        Files.createDirectories(gameDirectory);
        Files.createDirectories(nativesDirectory);

        Map<String, String> variables = variables(version, launchVersionId, gameDirectory, nativesDirectory, playerName, profileId, account);
        List<String> classpath = classpath(version, launchVersionId);
        variables.put("classpath", String.join(FileSystems.getDefault().getSeparator().equals("\\") ? ";" : ":", classpath));
        String clientVersionId = String.valueOf(version.getOrDefault("inheritsFrom", version.getOrDefault("id", minecraftVersion)));
        Path launchClientJar = Files.exists(clientJar(clientVersionId)) ? clientJar(clientVersionId) : clientJar(launchVersionId);

        List<String> command = new ArrayList<>();
        command.add(javaPath);
        command.add("-Xmx" + memoryMb + "m");
        command.addAll(resolveJvmArguments(version, variables));
        command.add(String.valueOf(version.get("mainClass")));
        command.addAll(resolveGameArguments(version, variables));

        boolean supported = isLaunchSupported(loader);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("runtime", "canoe-java-core");
        plan.put("javaPath", javaPath);
        plan.put("memoryMb", memoryMb);
        plan.put("minecraftRoot", minecraftRoot.toString());
        plan.put("gameDirectory", gameDirectory.toString());
        plan.put("nativesDirectory", nativesDirectory.toString());
        plan.put("launchVersionId", launchVersionId);
        plan.put("mainClass", version.get("mainClass"));
        plan.put("classpathEntries", classpath.size());
        plan.put("account", publicAccount(account));
        plan.put("command", command);
        plan.put("commandPreview", redact(command));
        plan.put("clientJar", launchClientJar.toString());
        plan.put("canStartProcess", supported && Files.exists(launchClientJar));
        plan.put("processLaunchSupport", supported ? "supported" : "loader-runtime-not-yet-implemented");
        plan.put("createdAt", Instant.now().toString());
        return plan;
    }

    public Path logFile(String packId) throws IOException {
        Path logs = minecraftRoot.getParent().resolve("logs");
        Files.createDirectories(logs);
        return logs.resolve(packId + "-" + System.currentTimeMillis() + ".log");
    }

    private Map<String, Object> installBaseVersion(String minecraftVersion, String mirror) throws IOException, InterruptedException {
        Map<String, Object> manifest = downloads.fetchJsonObject(mirrorVersionManifestUrl(mirror));
        List<?> versions = list(manifest.get("versions"));
        Map<String, Object> versionEntry = null;
        for (Object item : versions) {
            Map<String, Object> entry = map(item);
            if (minecraftVersion.equals(entry.get("id"))) {
                versionEntry = entry;
                break;
            }
        }
        if (versionEntry == null) {
            throw new IOException("Minecraft version not found: " + minecraftVersion);
        }

        String versionUrl = mirrorUrl(string(versionEntry, "url"), mirror);
        Map<String, Object> version = downloads.fetchJsonObject(versionUrl);
        Path jsonFile = versionJson(minecraftVersion);
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, Json.stringify(version), StandardCharsets.UTF_8);

        Map<String, Object> downloadsMap = map(version.get("downloads"));
        Map<String, Object> client = map(downloadsMap.get("client"));
        Map<String, Object> clientDownload = downloads.download(
                mirrorUrl(string(client, "url"), mirror),
                clientJar(minecraftVersion),
                optionalString(client, "sha1")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionId", minecraftVersion);
        result.put("versionJson", jsonFile.toString());
        result.put("clientJar", clientDownload);
        return result;
    }

    private Map<String, Object> installLoaderProfile(String loader, String loaderVersion, String minecraftVersion, String mirror) throws IOException, InterruptedException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loader", loader);
        result.put("loaderVersion", loaderVersion);

        if ("Fabric".equalsIgnoreCase(loader)) {
            String url = String.format(Locale.ROOT, fabricProfileUrl(mirror), minecraftVersion, loaderVersion);
            Map<String, Object> profile = downloads.fetchJsonObject(url);
            String versionId = String.valueOf(profile.getOrDefault("id", launchVersionId(loader, loaderVersion, minecraftVersion)));
            Path json = versionJson(versionId);
            Files.createDirectories(json.getParent());
            Files.writeString(json, Json.stringify(profile), StandardCharsets.UTF_8);
            result.put("versionId", versionId);
            result.put("profileJson", json.toString());
            result.put("status", "installed");
            return result;
        }

        result.put("versionId", minecraftVersion);
        result.put("status", "deferred");
        result.put("reason", loader + " installer support will be implemented in Canoe Core without HMCL class dependencies.");
        return result;
    }

    private Map<String, Object> installLibraries(Map<String, Object> version, String mirror) throws IOException, InterruptedException {
        List<?> libraries = list(version.get("libraries"));
        int downloaded = 0;
        int cached = 0;
        int nativeArchives = 0;
        Set<String> seen = new HashSet<>();

        for (Object item : libraries) {
            Map<String, Object> library = map(item);
            if (!allowed(library.get("rules"))) {
                continue;
            }

            Map<String, Object> artifact = artifact(library);
            if (!artifact.isEmpty()) {
                String path = string(artifact, "path");
                if (seen.add(path)) {
                    Map<String, Object> result = downloads.download(
                            mirrorUrl(string(artifact, "url"), mirror),
                            minecraftRoot.resolve("libraries").resolve(path),
                            optionalString(artifact, "sha1")
                    );
                    if ("cached".equals(result.get("status"))) {
                        cached++;
                    } else {
                        downloaded++;
                    }
                }
            }

            Map<String, Object> nativeArtifact = nativeArtifact(library);
            if (!nativeArtifact.isEmpty()) {
                Path archive = minecraftRoot.resolve("libraries").resolve(string(nativeArtifact, "path"));
                downloads.download(mirrorUrl(string(nativeArtifact, "url"), mirror), archive, optionalString(nativeArtifact, "sha1"));
                extractNatives(archive, minecraftRoot.resolve("versions").resolve(String.valueOf(version.get("id"))).resolve("natives"), library);
                nativeArchives++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("downloaded", downloaded);
        result.put("cached", cached);
        result.put("nativeArchives", nativeArchives);
        result.put("totalDeclared", libraries.size());
        return result;
    }

    private Map<String, Object> installAssets(Map<String, Object> version, String mirror) throws IOException, InterruptedException {
        Map<String, Object> assetIndex = map(version.get("assetIndex"));
        if (assetIndex.isEmpty()) {
            return Map.of("status", "missing-asset-index");
        }

        String assetId = string(assetIndex, "id");
        Path indexFile = minecraftRoot.resolve("assets").resolve("indexes").resolve(assetId + ".json");
        downloads.download(mirrorUrl(string(assetIndex, "url"), mirror), indexFile, optionalString(assetIndex, "sha1"));

        Map<String, Object> index = map(Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8)));
        Map<String, Object> objects = map(index.get("objects"));
        int downloaded = 0;
        int cached = 0;
        for (Object value : objects.values()) {
            Map<String, Object> asset = map(value);
            String hash = string(asset, "hash");
            Path assetFile = minecraftRoot.resolve("assets").resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            Map<String, Object> result = downloads.download(assetUrl(hash, mirror), assetFile, hash);
            if ("cached".equals(result.get("status"))) {
                cached++;
            } else {
                downloaded++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetIndex", assetId);
        result.put("objects", objects.size());
        result.put("downloaded", downloaded);
        result.put("cached", cached);
        return result;
    }

    private Map<String, Object> loadMergedVersion(Path versionJson) throws IOException {
        Map<String, Object> version = map(Json.parse(Files.readString(versionJson, StandardCharsets.UTF_8)));
        Object parentId = version.get("inheritsFrom");
        if (parentId == null) {
            return version;
        }

        Path parentJson = versionJson(String.valueOf(parentId));
        if (!Files.exists(parentJson)) {
            throw new IOException("Missing parent version JSON: " + parentId);
        }
        Map<String, Object> parent = loadMergedVersion(parentJson);
        Map<String, Object> merged = new LinkedHashMap<>(parent);
        merged.putAll(version);
        merged.put("libraries", concat(list(parent.get("libraries")), list(version.get("libraries"))));
        merged.put("arguments", mergeArguments(map(parent.get("arguments")), map(version.get("arguments"))));
        if (!version.containsKey("assetIndex")) {
            merged.put("assetIndex", parent.get("assetIndex"));
        }
        if (!version.containsKey("downloads")) {
            merged.put("downloads", parent.get("downloads"));
        }
        return merged;
    }

    private List<String> classpath(Map<String, Object> version, String launchVersionId) {
        Set<String> entries = new LinkedHashSet<>();
        for (Object item : list(version.get("libraries"))) {
            Map<String, Object> library = map(item);
            if (!allowed(library.get("rules"))) {
                continue;
            }
            Map<String, Object> artifact = artifact(library);
            if (!artifact.isEmpty()) {
                entries.add(minecraftRoot.resolve("libraries").resolve(string(artifact, "path")).toString());
            }
        }
        String baseVersion = String.valueOf(version.getOrDefault("inheritsFrom", version.get("id")));
        Path jar = clientJar(baseVersion);
        if (!Files.exists(jar)) {
            jar = clientJar(launchVersionId);
        }
        entries.add(jar.toString());
        return new ArrayList<>(entries);
    }

    private List<String> resolveJvmArguments(Map<String, Object> version, Map<String, String> variables) {
        Map<String, Object> arguments = map(version.get("arguments"));
        if (!arguments.isEmpty() && arguments.containsKey("jvm")) {
            return resolveArgumentList(list(arguments.get("jvm")), variables);
        }

        List<String> fallback = new ArrayList<>();
        fallback.add("-Djava.library.path=${natives_directory}");
        fallback.add("-cp");
        fallback.add("${classpath}");
        return expand(fallback, variables);
    }

    private List<String> resolveGameArguments(Map<String, Object> version, Map<String, String> variables) {
        Map<String, Object> arguments = map(version.get("arguments"));
        if (!arguments.isEmpty() && arguments.containsKey("game")) {
            return resolveArgumentList(list(arguments.get("game")), variables);
        }

        String legacy = String.valueOf(version.getOrDefault("minecraftArguments", ""));
        List<String> split = new ArrayList<>();
        for (String item : legacy.split(" ")) {
            if (!item.isBlank()) {
                split.add(item);
            }
        }
        return expand(split, variables);
    }

    private List<String> resolveArgumentList(List<?> source, Map<String, String> variables) {
        List<String> result = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof String value) {
                result.add(replaceVariables(value, variables));
                continue;
            }
            Map<String, Object> object = map(item);
            if (!allowed(object.get("rules"))) {
                continue;
            }
            Object value = object.get("value");
            if (value instanceof List<?> list) {
                for (Object listItem : list) {
                    result.add(replaceVariables(String.valueOf(listItem), variables));
                }
            } else if (value != null) {
                result.add(replaceVariables(String.valueOf(value), variables));
            }
        }
        return result;
    }

    private Map<String, String> variables(Map<String, Object> version, String launchVersionId, Path gameDirectory, Path nativesDirectory, String playerName, String profileId, Map<String, Object> account) {
        Map<String, String> variables = new LinkedHashMap<>();
        String assetsId = String.valueOf(map(version.get("assetIndex")).getOrDefault("id", version.getOrDefault("assets", "")));
        variables.put("natives_directory", nativesDirectory.toString());
        variables.put("launcher_name", "CanoeLauncher");
        variables.put("launcher_version", "0.1.0");
        variables.put("version_name", launchVersionId);
        variables.put("game_directory", gameDirectory.toString());
        variables.put("assets_root", minecraftRoot.resolve("assets").toString());
        variables.put("assets_index_name", assetsId);
        variables.put("auth_player_name", playerName);
        variables.put("auth_uuid", profileId.replace("-", ""));
        variables.put("auth_access_token", String.valueOf(account.getOrDefault("accessToken", profileId)));
        variables.put("clientid", String.valueOf(account.getOrDefault("clientId", "")));
        variables.put("auth_xuid", String.valueOf(account.getOrDefault("xuid", "")));
        String accountType = String.valueOf(account.getOrDefault("type", "offline"));
        variables.put("user_type", "microsoft".equalsIgnoreCase(accountType) ? "msa" : accountType);
        variables.put("version_type", String.valueOf(version.getOrDefault("type", "release")));
        variables.put("resolution_width", "1280");
        variables.put("resolution_height", "720");
        variables.put("classpath_separator", FileSystems.getDefault().getSeparator().equals("\\") ? ";" : ":");
        variables.put("library_directory", minecraftRoot.resolve("libraries").toString());
        return variables;
    }

    private Map<String, Object> artifact(Map<String, Object> library) {
        Map<String, Object> downloadsMap = map(library.get("downloads"));
        Map<String, Object> artifact = map(downloadsMap.get("artifact"));
        if (!artifact.isEmpty()) {
            return artifact;
        }

        String name = optionalString(library, "name");
        String url = optionalString(library, "url");
        if (name == null || url == null) {
            return Map.of();
        }
        String path = mavenPath(name);
        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("path", path);
        generated.put("url", ensureSlash(url) + path);
        return generated;
    }

    private Map<String, Object> nativeArtifact(Map<String, Object> library) {
        Map<String, Object> natives = map(library.get("natives"));
        if (natives.isEmpty()) {
            return Map.of();
        }

        String classifier = optionalString(natives, osName());
        if (classifier == null) {
            return Map.of();
        }
        classifier = classifier.replace("${arch}", archBits());
        Map<String, Object> downloadsMap = map(library.get("downloads"));
        return map(map(downloadsMap.get("classifiers")).get(classifier));
    }

    private void extractNatives(Path archive, Path target, Map<String, Object> library) throws IOException {
        Files.createDirectories(target);
        Set<String> excludes = new HashSet<>();
        for (Object item : list(map(library.get("extract")).get("exclude"))) {
            excludes.add(String.valueOf(item));
        }

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || excluded(entry.getName(), excludes)) {
                    continue;
                }
                Path output = target.resolve(entry.getName()).normalize();
                if (!output.startsWith(target)) {
                    continue;
                }
                Files.createDirectories(output.getParent());
                Files.copy(zip, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean excluded(String name, Set<String> excludes) {
        for (String exclude : excludes) {
            if (name.startsWith(exclude)) {
                return true;
            }
        }
        return name.startsWith("META-INF/");
    }

    private boolean allowed(Object rulesValue) {
        List<?> rules = list(rulesValue);
        if (rules.isEmpty()) {
            return true;
        }

        boolean allowed = false;
        for (Object item : rules) {
            Map<String, Object> rule = map(item);
            if (ruleMatches(rule)) {
                allowed = "allow".equals(rule.get("action"));
            }
        }
        return allowed;
    }

    private boolean ruleMatches(Map<String, Object> rule) {
        Map<String, Object> os = map(rule.get("os"));
        if (!os.isEmpty()) {
            String name = optionalString(os, "name");
            if (name != null && !name.equals(osName())) {
                return false;
            }
            String arch = optionalString(os, "arch");
            if (arch != null && !System.getProperty("os.arch").contains(arch)) {
                return false;
            }
        }
        return map(rule.get("features")).isEmpty();
    }

    private String launchVersionId(String loader, String loaderVersion, String minecraftVersion) {
        if ("Fabric".equalsIgnoreCase(loader)) {
            return "fabric-loader-" + loaderVersion + "-" + minecraftVersion;
        }
        return minecraftVersion;
    }

    private boolean isLaunchSupported(String loader) {
        return loader == null || loader.isBlank() || "Vanilla".equalsIgnoreCase(loader) || "Fabric".equalsIgnoreCase(loader);
    }

    private Path versionJson(String versionId) {
        return minecraftRoot.resolve("versions").resolve(versionId).resolve(versionId + ".json");
    }

    private Path clientJar(String versionId) {
        return minecraftRoot.resolve("versions").resolve(versionId).resolve(versionId + ".jar");
    }

    private String mirrorVersionManifestUrl(String mirror) {
        return "BMCLAPI".equalsIgnoreCase(mirror) || "MCBBS".equalsIgnoreCase(mirror) ? BMCL_VERSION_MANIFEST : OFFICIAL_VERSION_MANIFEST;
    }

    private String fabricProfileUrl(String mirror) {
        return "BMCLAPI".equalsIgnoreCase(mirror) || "MCBBS".equalsIgnoreCase(mirror) ? BMCL_FABRIC_PROFILE : FABRIC_PROFILE;
    }

    private String mirrorUrl(String url, String mirror) {
        if (url == null || url.isBlank() || (!"BMCLAPI".equalsIgnoreCase(mirror) && !"MCBBS".equalsIgnoreCase(mirror))) {
            return url;
        }
        return url.replace("https://piston-meta.mojang.com", "https://bmclapi2.bangbang93.com")
                .replace("https://piston-data.mojang.com", "https://bmclapi2.bangbang93.com")
                .replace("https://libraries.minecraft.net", "https://bmclapi2.bangbang93.com/maven")
                .replace("https://resources.download.minecraft.net", "https://bmclapi2.bangbang93.com/assets");
    }

    private String assetUrl(String hash, String mirror) {
        String path = hash.substring(0, 2) + "/" + hash;
        if ("BMCLAPI".equalsIgnoreCase(mirror) || "MCBBS".equalsIgnoreCase(mirror)) {
            return "https://bmclapi2.bangbang93.com/assets/" + path;
        }
        return "https://resources.download.minecraft.net/" + path;
    }

    private String detectJava(String configuredJavaPath) {
        if (configuredJavaPath != null && !configuredJavaPath.isBlank() && !"auto".equals(configuredJavaPath)) {
            return configuredJavaPath;
        }
        Path javaHome = Path.of(System.getProperty("java.home"));
        return javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java").toString();
    }

    private List<String> redact(List<String> command) {
        List<String> redacted = new ArrayList<>();
        for (int index = 0; index < command.size(); index++) {
            String item = command.get(index);
            redacted.add("--accessToken".equals(item) && index + 1 < command.size() ? "<redacted>" : item);
        }
        return redacted;
    }

    private Map<String, Object> publicAccount(Map<String, Object> account) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", account.get("id"));
        result.put("type", account.get("type"));
        result.put("username", account.get("username"));
        return result;
    }

    private String replaceVariables(String value, Map<String, String> variables) {
        String result = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private List<String> expand(List<String> values, Map<String, String> variables) {
        return values.stream().map(value -> replaceVariables(value, variables)).toList();
    }

    private Map<String, Object> mergeArguments(Map<String, Object> parent, Map<String, Object> child) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("jvm", concat(list(parent.get("jvm")), list(child.get("jvm"))));
        merged.put("game", concat(list(parent.get("game")), list(child.get("game"))));
        return merged;
    }

    private List<Object> concat(List<?> first, List<?> second) {
        List<Object> result = new ArrayList<>();
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private String mavenPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate: " + name);
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private String ensureSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private String osName() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac")) {
            return "osx";
        }
        return "linux";
    }

    private String archBits() {
        return System.getProperty("os.arch").contains("64") ? "64" : "32";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing value: " + key);
        }
        return String.valueOf(value);
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> input) {
            Map<String, Object> result = new LinkedHashMap<>();
            input.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<?> list(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }
}
