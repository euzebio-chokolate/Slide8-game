package com.example.slide8;

import java.util.Locale;
import java.util.function.LongSupplier;

/** Tempo de jogo ativo; recebe um relógio monotônico e não conhece a interface. */
final class GameTimer {
    private final LongSupplier clock;
    private long elapsed;
    private long resumedAt;
    private boolean running;

    GameTimer(LongSupplier clock) {
        this.clock = clock;
    }

    void start() {
        if (running) return;
        resumedAt = clock.getAsLong();
        running = true;
    }

    void pause() {
        elapsed = elapsedMillis();
        running = false;
    }

    void restore(long elapsedMillis) {
        elapsed = Math.max(0, elapsedMillis);
        running = false;
    }

    long elapsedMillis() {
        return elapsed + (running ? clock.getAsLong() - resumedAt : 0);
    }

    boolean isRunning() { return running; }

    static String format(long elapsedMillis) {
        long seconds = elapsedMillis / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
}
