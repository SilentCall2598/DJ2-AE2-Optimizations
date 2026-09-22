package dj2.ae2opt.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemHandlerExtractionStats {

    public static final class HandlerStats {
        public final String handlerClassName;
        public String ownerClassName;
        public long requests;
        public long successfulRequests;
        public long unsuccessfulRequests;
        public long slotsExamined;
        public long requestedAmount;
        public long extractedAmount;

        HandlerStats(String handlerClassName) {
            this.handlerClassName = handlerClassName;
        }
    }

    private static final Map<Class<?>, HandlerStats> BY_CLASS = new LinkedHashMap<Class<?>, HandlerStats>();
    private static long overflowRequests;
    private static long overflowSlotsExamined;

    private ItemHandlerExtractionStats() {
    }

    public static void record(Class<?> handlerClass, Class<?> ownerClass, boolean successful,
                               long slotsExamined, long requestedAmount, long extractedAmount) {
        if (handlerClass == null) {
            return;
        }
        HandlerStats stats = BY_CLASS.get(handlerClass);
        if (stats == null) {
            if (BY_CLASS.size() >= OptimizationConfig.maxItemHandlerClassesTracked) {
                overflowRequests++;
                overflowSlotsExamined += slotsExamined;
                return;
            }
            stats = new HandlerStats(handlerClass.getName());
            BY_CLASS.put(handlerClass, stats);
        }
        if (stats.ownerClassName == null && ownerClass != null) {
            stats.ownerClassName = ownerClass.getName();
        }
        stats.requests++;
        if (successful) {
            stats.successfulRequests++;
        } else {
            stats.unsuccessfulRequests++;
        }
        stats.slotsExamined += slotsExamined;
        stats.requestedAmount += requestedAmount;
        stats.extractedAmount += extractedAmount;
    }

    public static List<HandlerStats> snapshot() {
        return new ArrayList<HandlerStats>(BY_CLASS.values());
    }

    public static long overflowRequests() {
        return overflowRequests;
    }

    public static long overflowSlotsExamined() {
        return overflowSlotsExamined;
    }

    public static int trackedClassCount() {
        return BY_CLASS.size();
    }

    public static boolean isOverflowing() {
        return overflowRequests > 0;
    }
}
