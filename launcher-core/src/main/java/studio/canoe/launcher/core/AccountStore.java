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

    public Map<String, Object> addOfflineAccount(String username) throws IOException {
        Map<String, Object> account = offlineAccount(username);
        List<Map<String, Object>> accounts = new ArrayList<>(listAccounts());
        accounts.removeIf(item -> String.valueOf(item.get("id")).equals(account.get("id")));
        accounts.add(account);
        save(accounts);
        return account;
    }

    public Map<String, Object> accountFromSettings(Map<String, Object> settings) {
        String name = String.valueOf(settings.getOrDefault("playerName", "LocalPlayer"));
        String id = String.valueOf(settings.getOrDefault("profileId", offlineUuid(name)));
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", id);
        account.put("type", settings.getOrDefault("accountType", "offline"));
        account.put("username", name);
        account.put("accessToken", id);
        account.put("createdAt", Instant.now().toString());
        return account;
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

    private String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
