package me.priyme.lagscope.analysis;

import java.util.concurrent.atomic.AtomicInteger;

public final class SecondCounter {
    private volatile long second;
    private final AtomicInteger count = new AtomicInteger(0);

    public boolean tryIncrement(long nowSecond, int max) {
        if (max <= 0) return true;
        if (second != nowSecond) {
            synchronized (this) {
                if (second != nowSecond) {
                    second = nowSecond;
                    count.set(0);
                }
            }
        }
        int c = count.incrementAndGet();
        return c <= max;
    }
}
