package studio.canoe.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DownloadClient {
    private static final int MAX_ATTEMPTS = 3;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public String fetchString(String url) throws IOException, InterruptedException {
        HttpRequest request = request(url).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(url, response.statusCode());
        return response.body();
    }

    public Map<String, Object> fetchJsonObject(String url) throws IOException, InterruptedException {
        Object parsed = Json.parse(fetchString(url));
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        throw new IOException("Expected JSON object from " + url);
    }

    public Map<String, Object> download(String url, Path target, String expectedSha1) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        if (isValid(target, expectedSha1)) {
            return result(target, "cached", expectedSha1);
        }

        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.deleteIfExists(temp);
            try {
                HttpRequest request = request(url).build();
                HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp));
                requireSuccess(url, response.statusCode());
                if (!isValid(temp, expectedSha1)) {
                    throw new IOException("SHA-1 mismatch for " + target);
                }
                move(temp, target);
                return result(target, attempt == 1 ? "downloaded" : "downloaded-after-retry", expectedSha1);
            } catch (IOException error) {
                lastError = error;
                Files.deleteIfExists(temp);
            }
        }
        throw lastError == null ? new IOException("Failed to download " + url) : lastError;
    }

    public boolean isValid(Path file, String expectedSha1) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return Files.size(file) > 0;
        }
        return expectedSha1.equalsIgnoreCase(sha1(file));
    }

    public static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-1 is unavailable", error);
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "CanoeLauncherCore/0.1.0")
                .GET();
    }

    private void requireSuccess(String url, int statusCode) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " for " + url);
        }
    }

    private Map<String, Object> result(Path target, String status, String expectedSha1) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", target.toString());
        payload.put("status", status);
        payload.put("size", Files.size(target));
        payload.put("sha1", expectedSha1 == null || expectedSha1.isBlank() ? sha1(target) : expectedSha1);
        return payload;
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
