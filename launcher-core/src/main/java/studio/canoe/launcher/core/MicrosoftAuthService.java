package studio.canoe.launcher.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MicrosoftAuthService {
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String MICROSOFT_SCOPE = "XboxLive.signin offline_access";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public Map<String, Object> startDeviceCode(String clientId) throws IOException, InterruptedException {
        requireClientId(clientId);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", MICROSOFT_SCOPE);

        Map<String, Object> body = postForm(DEVICE_CODE_URL, form);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceCode", requiredString(body, "device_code"));
        result.put("userCode", requiredString(body, "user_code"));
        result.put("verificationUri", String.valueOf(body.getOrDefault("verification_uri", body.get("verification_url"))));
        result.put("expiresIn", longValue(body.getOrDefault("expires_in", 900)));
        result.put("interval", Math.max(1, longValue(body.getOrDefault("interval", 5))));
        result.put("message", optionalString(body, "message"));
        result.put("expiresAt", Instant.now().plusSeconds(longValue(result.get("expiresIn"))).toString());
        return result;
    }

    public Map<String, Object> pollDeviceCode(String clientId, String deviceCode) throws IOException, InterruptedException {
        requireClientId(clientId);
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("Microsoft device code is missing.");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("device_code", deviceCode);

        HttpResponse<String> response = postFormRaw(TOKEN_URL, form);
        Map<String, Object> body = parseObject(response.body(), TOKEN_URL);
        if (isSuccess(response.statusCode()) && body.containsKey("access_token")) {
            Map<String, Object> account = authenticateMinecraft(clientId, body, null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "complete");
            result.put("account", account);
            result.put("message", "Microsoft account connected.");
            return result;
        }

        String error = optionalString(body, "error");
        String description = optionalString(body, "error_description");
        if ("authorization_pending".equals(error)) {
            return status("pending", "Waiting for Microsoft authorization.", null, 0);
        }
        if ("slow_down".equals(error)) {
            return status("slow_down", "Microsoft asked the launcher to poll more slowly.", null, 5);
        }
        if ("authorization_declined".equals(error) || "expired_token".equals(error) || "bad_verification_code".equals(error)) {
            return status("failed", description == null ? "Microsoft authorization was not completed." : description, error, 0);
        }

        throw new IOException(serviceError(TOKEN_URL, response.statusCode(), body));
    }

    public Map<String, Object> refreshMinecraftSession(String clientId, Map<String, Object> existingAccount) throws IOException, InterruptedException {
        requireClientId(clientId);
        String refreshToken = optionalString(existingAccount, "refreshToken");
        if (refreshToken == null) {
            throw new IOException("Microsoft refresh token is missing. Please sign in again.");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", MICROSOFT_SCOPE);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);

        Map<String, Object> token = postForm(TOKEN_URL, form);
        Map<String, Object> account = authenticateMinecraft(clientId, token, refreshToken);
        Object createdAt = existingAccount.get("createdAt");
        if (createdAt != null) {
            account.put("createdAt", createdAt);
        }
        return account;
    }

    private Map<String, Object> authenticateMinecraft(String clientId, Map<String, Object> microsoftToken, String fallbackRefreshToken) throws IOException, InterruptedException {
        String microsoftAccessToken = requiredString(microsoftToken, "access_token");
        String refreshToken = optionalString(microsoftToken, "refresh_token");
        if (refreshToken == null) {
            refreshToken = fallbackRefreshToken;
        }

        Map<String, Object> xboxProperties = new LinkedHashMap<>();
        xboxProperties.put("AuthMethod", "RPS");
        xboxProperties.put("SiteName", "user.auth.xboxlive.com");
        xboxProperties.put("RpsTicket", "d=" + microsoftAccessToken);

        Map<String, Object> xboxRequest = new LinkedHashMap<>();
        xboxRequest.put("Properties", xboxProperties);
        xboxRequest.put("RelyingParty", "http://auth.xboxlive.com");
        xboxRequest.put("TokenType", "JWT");
        Map<String, Object> xbox = postJson(XBOX_AUTH_URL, xboxRequest);

        String xboxToken = requiredString(xbox, "Token");
        Map<String, Object> xstsProperties = new LinkedHashMap<>();
        xstsProperties.put("SandboxId", "RETAIL");
        xstsProperties.put("UserTokens", List.of(xboxToken));

        Map<String, Object> xstsRequest = new LinkedHashMap<>();
        xstsRequest.put("Properties", xstsProperties);
        xstsRequest.put("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.put("TokenType", "JWT");
        Map<String, Object> xsts = postJson(XSTS_AUTH_URL, xstsRequest);

        String userHash = userHash(xsts);
        if (userHash == null) {
            userHash = userHash(xbox);
        }
        if (userHash == null) {
            throw new IOException("Xbox Live authentication did not return a user hash.");
        }

        Map<String, Object> minecraftLoginRequest = new LinkedHashMap<>();
        minecraftLoginRequest.put("identityToken", "XBL3.0 x=" + userHash + ";" + requiredString(xsts, "Token"));
        Map<String, Object> minecraftToken = postJson(MINECRAFT_LOGIN_URL, minecraftLoginRequest);

        String minecraftAccessToken = requiredString(minecraftToken, "access_token");
        Map<String, Object> profile = getJson(MINECRAFT_PROFILE_URL, minecraftAccessToken);
        String profileId = requiredString(profile, "id");
        String profileName = requiredString(profile, "name");

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", profileId);
        account.put("type", "microsoft");
        account.put("username", profileName);
        account.put("accessToken", minecraftAccessToken);
        account.put("refreshToken", refreshToken);
        account.put("expiresAt", Instant.now().plusSeconds(longValue(minecraftToken.getOrDefault("expires_in", 86400))).toString());
        account.put("microsoftExpiresAt", Instant.now().plusSeconds(longValue(microsoftToken.getOrDefault("expires_in", 3600))).toString());
        account.put("xuid", xuid(xsts));
        account.put("clientId", clientId);
        account.put("createdAt", Instant.now().toString());
        return account;
    }

    private Map<String, Object> postForm(String url, Map<String, String> form) throws IOException, InterruptedException {
        HttpResponse<String> response = postFormRaw(url, form);
        Map<String, Object> body = parseObject(response.body(), url);
        if (!isSuccess(response.statusCode())) {
            throw new IOException(serviceError(url, response.statusCode(), body));
        }
        return body;
    }

    private HttpResponse<String> postFormRaw(String url, Map<String, String> form) throws IOException, InterruptedException {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "CanoeLauncherCore/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofString(builder.toString(), StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> postJson(String url, Map<String, Object> payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "CanoeLauncherCore/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> body = parseObject(response.body(), url);
        if (!isSuccess(response.statusCode())) {
            throw new IOException(serviceError(url, response.statusCode(), body));
        }
        return body;
    }

    private Map<String, Object> getJson(String url, String bearerToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .header("User-Agent", "CanoeLauncherCore/0.1.0")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> body = parseObject(response.body(), url);
        if (response.statusCode() == 404 && MINECRAFT_PROFILE_URL.equals(url)) {
            throw new IOException("This Microsoft account does not own a Minecraft: Java Edition profile.");
        }
        if (!isSuccess(response.statusCode())) {
            throw new IOException(serviceError(url, response.statusCode(), body));
        }
        return body;
    }

    private Map<String, Object> status(String status, String message, String errorCode, long intervalDelta) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("message", message);
        result.put("intervalDelta", intervalDelta);
        if (errorCode != null) {
            result.put("errorCode", errorCode);
            result.put("errorMessage", message);
        }
        return result;
    }

    private String serviceError(String url, int statusCode, Map<String, Object> body) {
        String xsts = xstsMessage(body);
        if (xsts != null) {
            return xsts;
        }

        String description = optionalString(body, "error_description");
        String errorMessage = optionalString(body, "errorMessage");
        String message = optionalString(body, "message");
        String microsoftError = optionalString(body, "error");
        String detail = firstNonBlank(description, errorMessage, message, microsoftError, Json.stringify(body));
        return "HTTP " + statusCode + " from " + url + ": " + detail;
    }

    private String xstsMessage(Map<String, Object> body) {
        Object xerr = body.get("XErr");
        if (xerr == null) {
            return null;
        }

        long code = longValue(xerr);
        return switch (String.valueOf(code)) {
            case "2148916233" -> "This Microsoft account does not have an Xbox account. Please create an Xbox profile first.";
            case "2148916235" -> "Xbox Live is not available for this Microsoft account region.";
            case "2148916236", "2148916237" -> "This Microsoft account needs adult verification before Xbox Live sign-in.";
            case "2148916238" -> "This Microsoft account is a child account and must be added to a family by an adult.";
            default -> "Xbox Live authentication failed with XErr " + code + ".";
        };
    }

    private Map<String, Object> parseObject(String json, String url) throws IOException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed = Json.parse(json);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        throw new IOException("Expected JSON object from " + url);
    }

    private String userHash(Map<String, Object> response) {
        return optionalString(firstXui(response), "uhs");
    }

    private String xuid(Map<String, Object> response) {
        return optionalString(firstXui(response), "xid");
    }

    private Map<String, Object> firstXui(Map<String, Object> response) {
        Map<String, Object> displayClaims = map(response.get("DisplayClaims"));
        List<?> xui = list(displayClaims.get("xui"));
        if (xui.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return map(xui.get(0));
    }

    private void requireClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Microsoft OAuth client ID is not configured. Set microsoftClientId in launcher settings or CANOE_MICROSOFT_CLIENT_ID.");
        }
    }

    private String requiredString(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing Microsoft auth field: " + key);
        }
        return value;
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
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
