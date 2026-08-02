package studio.canoe.launcher.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Main {
    public static void main(String[] args) throws Exception {
        CoreService service = new CoreService();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        service.setEmitter((event, payload) -> {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "event");
            message.put("event", event);
            message.put("payload", payload);
            out.println(Json.stringify(message));
        });

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                out.println(handle(service, line));
            }
        }
    }

    private static String handle(CoreService service, String line) {
        String id = null;
        try {
            Object parsed = Json.parse(line);
            if (!(parsed instanceof Map<?, ?> request)) {
                throw new IllegalArgumentException("Request must be a JSON object.");
            }

            id = stringValue(request.get("id"));
            String command = stringValue(request.get("command"));
            Map<String, Object> payload = objectValue(request.get("payload"));
            Object result = service.handle(command, payload);
            return Json.stringify(response(id, true, result, null));
        } catch (Exception error) {
            return Json.stringify(response(id, false, null, error.getMessage()));
        }
    }

    private static Map<String, Object> response(String id, boolean ok, Object payload, String error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "response");
        response.put("id", id);
        response.put("ok", ok);
        response.put("payload", payload);
        response.put("error", error);
        return response;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }
}
