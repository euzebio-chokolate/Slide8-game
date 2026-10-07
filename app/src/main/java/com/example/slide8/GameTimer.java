package com.example.slide8;

import java.util.Locale;
import java.util.function.LongSupplier;

/** Tempo de jogo ativo; recebe um relógio monotônico e não conhece a interface. */
final class GameTimer {
    private final LongSupplier clock;
    private long elapsed;
    private long resumedAt;
    private boolean running;

    /** Cria o cronômetro com a fonte de tempo monotônico fornecida. */
    GameTimer(LongSupplier clock) {
        this.clock = clock;
    }

    /** Inicia ou retoma a medição do tempo de jogo. */
    void start() {
        if (running) return;
        resumedAt = clock.getAsLong();
        running = true;
    }

    /** Para a medição e conserva o tempo já decorrido. */
    void pause() {
        elapsed = elapsedMillis();
        running = false;
    }

    /** Restaura o tempo salvo sem iniciar o cronômetro. */
    void restore(long elapsedMillis) {
        elapsed = Math.max(0, elapsedMillis);
        running = false;
    }

    /** Retorna o tempo ativo acumulado em milissegundos. */
    long elapsedMillis() {
        return elapsed + (running ? clock.getAsLong() - resumedAt : 0);
    }

    /** Indica se o cronômetro está em execução. */
    boolean isRunning() { return running; }

    /** Formata o tempo decorrido em minutos e segundos. */
    static String format(long elapsedMillis) {
        long seconds = elapsedMillis / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
}
