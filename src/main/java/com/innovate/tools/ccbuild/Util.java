package com.innovate.tools.ccbuild;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Util {
    static String coalesce(String... ss) {
        for (String s : ss) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static Duration parseDuration(String s) {
        if (isBlank(s)) {
            return null;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.matches("\\d+[smhdw]")) {
                long n = Long.parseLong(t.replaceAll("[a-z]", ""));
                char u = t.charAt(t.length() - 1);
                return switch (u) {
                    case 's' -> Duration.ofSeconds(n);
                    case 'm' -> Duration.ofMinutes(n);
                    case 'h' -> Duration.ofHours(n);
                    case 'd' -> Duration.ofDays(n);
                    case 'w' -> Duration.ofDays(7 * n);
                    default -> null;
                };
            }
            if (t.endsWith("y")) {
                long n = Long.parseLong(t.substring(0, t.length() - 1));
                return Duration.ofDays(365 * n);
            }
            if (t.startsWith("pt")) {
                return Duration.parse(t); // ISO-8601 e.g. PT720H
            }
        } catch (Exception ignore) {
        }
        throw new IllegalArgumentException("Ungültige Duration: " + s);
    }

    static Instant tryParseInstantFromMap(Map<String, Object> raw) {
        for (String k : List.of("createdAt", "created", "timestamp", "time", "created_on")) {
            Object v = raw.get(k);
            if (v instanceof String str) {
                try {
                    return Instant.parse(str);
                } catch (DateTimeParseException ignored) {
                }
            }
        }
        return null;
    }

    static boolean isDeletableStatus(String status) {
        if (status == null) {
            return true; // defensiv
        }
        String s = status.toLowerCase(Locale.ROOT);
        // Hier ggf. an deine Statuswerte anpassen
        return !(s.contains("active") || s.contains("running") || s.contains("deploy") || s.contains("in_use"));
    }

    static List<Models.Build> applyFilters(List<Models.Build> builds, String env, Instant cutoff) {
        List<Models.Build> out = new ArrayList<>();
        for (Models.Build b : builds) {
            if (env != null && b.environment() != null && !b.environment().equalsIgnoreCase(env)) {
                continue;
            }
            if (cutoff != null) {
                Instant c = b.createdAt();
                if (c == null) {
                    c = tryParseInstantFromMap(b.raw());
                }
                if (c != null && c.isAfter(cutoff)) {
                    continue; // jünger als cutoff -> überspringen
                }
            }
            out.add(b);
        }
        return out;
    }

    static void printTable(List<Models.Build> builds) {
        System.out.printf("%-32s  %-24s  %-12s  %-25s  %-6s  %-8s%n", "CODE", "NAME", "STATUS", "CREATED_AT", "PINNED", "ENV");
        for (Models.Build b : builds) {
            String created = b.createdAt() != null ? b.createdAt().toString() : String.valueOf(b.raw().get("_createdAtFallback"));
            System.out.printf("%-32s  %-24s  %-12s  %-25s  %-6s  %-8s%n",
                    safe(b.code()), safe(b.name()), safe(b.status()), created, b.pinned(), safe(b.environment()));
        }
    }

    static String safe(String s) {
        return s == null ? "" : s;
    }
}
