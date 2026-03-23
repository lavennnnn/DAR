package cn.hush.dar.scheduler.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public enum ScheduleStrategy {
    DRF("DRF"),
    PRIORITY("PRIORITY"),
    FCFS("FCFS");

    private final String code;

    ScheduleStrategy(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ScheduleStrategy from(String value) {
        if (value == null) return DRF;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ScheduleStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(normalized)) {
                return strategy;
            }
        }
        return DRF;
    }

    public static List<String> codes() {
        return Arrays.stream(values())
                .map(ScheduleStrategy::getCode)
                .collect(Collectors.toList());
    }
}
