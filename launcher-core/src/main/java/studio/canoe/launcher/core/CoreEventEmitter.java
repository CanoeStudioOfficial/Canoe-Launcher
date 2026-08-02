package studio.canoe.launcher.core;

import java.util.Map;

@FunctionalInterface
public interface CoreEventEmitter {
    void emit(String event, Map<String, Object> payload);
}
