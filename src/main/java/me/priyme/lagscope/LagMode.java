package me.priyme.lagscope;

import java.util.Locale;

public enum LagMode {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
    EXTREME;

    public static LagMode parse(String s) {
        if (s == null) return MEDIUM;
        try {
            return LagMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }
}
