package studio.canoe.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountStore {
    private final Path accountsFile;

    public AccountStore(Path stateRoot) {
        this.accountsFile = stateRoot.resolve("accounts.json");
    }

    public List<Map<String, Object>> listAccounts() throws IOException {
        return readAccounts().stream().map(this::publicAccount).toList();
    }

    public Map<String, Object> addOfflineAccount(String username) throws IOException {
        Map<String, Object> account = offlineAccount(username);
        List<Map<String, Object>> accounts = new ArrayList<>(readAccounts());
        accounts.removeIf(item -> String.valueOf(item.get("id")).equals(account.get("id")));
        accounts.add(account);
        save(accounts);
        return publicAccount(account);
    }

    public Map<String, Object> addMicrosoftAccount(Map<String, Object> account) throws IOException {
        Map<String, Object> normalized = new LinkedHashMap<>(account);
        normalized.put("type", "microsoft");
        normalized.putIfAbsent("createdAt", Instant.now().toString());

        if (isBlank(normalized.get("id")) || isBlank(normalized.get("username"))) {
            throw new IllegalArgumentException("Microsoft account profile is incomplete.");
        }

        List<Map<String, Object>> accounts = new ArrayList<>(readAccounts());
        accounts.removeIf(item -> String.valueOf(item.get("id")).equals(normalized.get("id")));
        accounts.add(normalized);
        save(accounts);
        return publicAccount(normalized);
    }

    public Map<String, Object> accountFromSettings(Map<String, Object> settings) throws IOException {
        String selectedId = String.valueOf(settings.getOrDefault("selectedAccountId", settings.getOrDefault("profileId", "")));
        String accountType = String.valueOf(settings.getOrDefault("accountType", "offline"));
        for (Map<String, Object> account : readAccounts()) {
            if (!selectedId.isBlank() && selectedId.equals(String.valueOf(account.get("id")))) {
                return account;
            }
        }

        if ("microsoft".equals(accountType)) {
            throw new IOException("Selected Microsoft account is not available. Please sign in again.");
        }

        String name = String.valueOf(settings.getOrDefault("playerName", "LocalPlayer"));
        return offlineAccount(name);
    }

    private List<Map<String, Object>> readAccounts() throws IOException {
        if (!Files.exists(accountsFile)) {
            Map<String, Object> account = offlineAccount("LocalPlayer");
            save(List.of(account));
            return List.of(account);
        }

        Object parsed = Json.parse(Files.readString(accountsFile, StandardCharsets.UTF_8));
        if (!(parsed instanceof List<?> list)) {
            throw new IOException("Invalid accounts file: " + accountsFile);
        }

        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> account = new LinkedHashMap<>();
                map.forEach((key, value) -> account.put(String.valueOf(key), value));
                accounts.add(account);
            }
        }
        return accounts;
    }

    private Map<String, Object> offlineAccount(String username) {
        String normalized = username == null || username.isBlank() ? "LocalPlayer" : username.trim();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", offlineUuid(normalized));
        account.put("type", "offline");
        account.put("username", normalized);
        account.put("accessToken", offlineUuid(normalized));
        account.put("createdAt", Instant.now().toString());
        return account;
    }

    private void save(List<Map<String, Object>> accounts) throws IOException {
        Files.createDirectories(accountsFile.getParent());
        Files.writeString(accountsFile, Json.stringify(accounts), StandardCharsets.UTF_8);
    }

    private Map<String, Object> publicAccount(Map<String, Object> account) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", account.get("id"));
        result.put("type", account.get("type"));
        result.put("username", account.get("username"));
        result.put("createdAt", account.get("createdAt"));
        result.put("expiresAt", account.get("expiresAt"));
        result.put("xuid", account.get("xuid"));
        return result;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
