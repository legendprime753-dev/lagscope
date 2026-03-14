package me.priyme.lagscope.module;

public interface Module {
    String id();
    String name();
    boolean enabledByDefault();
    void enable();
    void disable();
    boolean isEnabled();
}
