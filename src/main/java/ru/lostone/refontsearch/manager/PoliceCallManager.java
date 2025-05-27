package ru.lostone.refontsearch.manager;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.stream.Collectors;

public class PoliceCallManager {

    public static class CallInfo {
        private final Player caller;
        private final String message;
        private final Location location;

        public CallInfo(Player caller, String message, Location location) {
            this.caller = caller;
            this.message = message;
            this.location = location;
        }

        public Player getCaller() {
            return caller;
        }

        public String getMessage() {
            return message;
        }

        public Location getLocation() {
            return location;
        }
    }

    // Храним заявки по UUID игрока
    private static final Map<UUID, CallInfo> pendingCalls = new HashMap<>();

    public static void addCall(Player caller, String message) {
        pendingCalls.put(caller.getUniqueId(), new CallInfo(caller, message, caller.getLocation()));
    }

    public static boolean hasCall(Player caller) {
        return pendingCalls.containsKey(caller.getUniqueId());
    }

    // Ищем заявку по имени (без учёта регистра)
    public static CallInfo getCallByName(String name) {
        for (CallInfo info : pendingCalls.values()) {
            if (info.getCaller().getName().equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }

    public static void removeCall(Player caller) {
        pendingCalls.remove(caller.getUniqueId());
    }

    // Получаем список ников, подавших заявки
    public static Collection<String> getCallersNames() {
        return pendingCalls.values().stream()
                .map(info -> info.getCaller().getName())
                .collect(Collectors.toList());
    }
}