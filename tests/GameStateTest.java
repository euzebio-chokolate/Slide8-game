package com.example.slide8;

import java.util.Arrays;
import java.util.Locale;

/** Regressões de estado da partida e tempo ativo, sem Android, mocks ou esperas reais. */
public final class GameStateTest {
    /** Executa os testes do estado da partida e do cronômetro. */
    public static void main(String[] args) {
        testGame();
        testTimer();
        System.out.println("OK: restauração, identidade da partida, vitória única e tempo ativo com pausas.");
    }

    /** Confere criação, validação, salvamento e restauração de partidas. */
    private static void testGame() {
        PuzzleGame game = new PuzzleGame();
        check(!game.hasGame() && !game.move(0, 100), "Não jogar antes de começar");
        game.newGame();
        check(game.hasGame() && !game.isWon() && game.moves() == 0, "Iniciar uma partida");
        String firstId = game.matchId();
        int validMove = -1;
        for (int position = 0; position < PuzzleGame.CELL_COUNT; position++) {
            if (game.adjacentEmpty(position) >= 0) validMove = position;
        }
        check(game.move(validMove, 123), "Aceitar jogada válida");
        check(game.moves() == 1 && game.matchId().equals(firstId), "Contar a jogada e manter identidade");
        check(!game.move(-1, 124) && !game.move(9, 124) && game.moves() == 1,
                "Jogadas inválidas não alteram a contagem");

        int[] nearVictory = {1, 2, 3, 4, 5, 6, 7, 0, 8};
        check(game.restore(nearVictory, 12, true, "partida-teste", 0), "Restaurar partida");
        nearVictory[0] = 8;
        int[] copy = game.snapshot();
        copy[0] = 8;
        check(game.tileAt(0) == 1, "Modelo não compartilha o array interno");
        check(game.move(8, 5000) && game.isWon(), "Concluir a partida restaurada");
        check(game.moves() == 13 && game.completedAt() == 5000, "Guardar contagem e data da vitória");
        check(!game.move(7, 6000) && game.moves() == 13 && game.completedAt() == 5000,
                "Bloquear novas jogadas e preservar o resultado final");

        PuzzleGame restored = new PuzzleGame();
        check(restored.restore(game.snapshot(), game.moves(), game.hasGame(),
                game.matchId(), game.completedAt()), "Restaurar resultado final");
        check(restored.isWon() && restored.matchId().equals("partida-teste")
                        && restored.completedAt() == 5000 && restored.moves() == 13,
                "Recriação preserva identidade e resultado para o histórico");
        int[] previous = restored.snapshot();
        check(!restored.restore(null, 0, true, null, 0), "Rejeitar estado ausente");
        check(!restored.restore(new int[8], 0, true, null, 0), "Rejeitar tamanho inválido");
        check(!restored.restore(new int[9], 0, true, null, 0), "Rejeitar números repetidos");
        check(!restored.restore(new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0}, 0, true, null, 0),
                "Rejeitar tabuleiro sem solução");
        check(!restored.restore(previous, -1, true, null, 0), "Rejeitar contagem negativa");
        check(Arrays.equals(previous, restored.snapshot()), "Restauração inválida não altera a partida");
        restored.newGame();
        check(!restored.isWon() && restored.moves() == 0 && restored.completedAt() == 0
                        && !restored.matchId().equals(game.matchId()), "Nova partida recebe nova identidade");
    }

    /** Confere a medição do tempo ativo com um relógio controlado. */
    private static void testTimer() {
        long[] now = {100};
        GameTimer timer = new GameTimer(() -> now[0]);
        now[0] = 500;
        check(timer.elapsedMillis() == 0, "Tempo não começa sozinho");
        timer.start();
        now[0] += 1250;
        timer.start();
        check(timer.elapsedMillis() == 1250, "Início repetido não reinicia o relógio");
        timer.pause();
        now[0] += 60000;
        timer.pause();
        check(timer.elapsedMillis() == 1250 && !timer.isRunning(), "Pausa exclui tempo fora do jogo");
        timer.start();
        now[0] += 750;
        check(timer.elapsedMillis() == 2000, "Retomar acumula somente tempo ativo");
        GameTimer restored = new GameTimer(() -> now[0]);
        restored.restore(timer.elapsedMillis());
        now[0] += 3000;
        check(restored.elapsedMillis() == 2000 && !restored.isRunning(), "Restaurar mantém o tempo pausado");
        restored.start();
        now[0] += 1000;
        check(restored.elapsedMillis() == 3000, "Relógio restaurado continua a contagem");
        restored.restore(0);
        check(restored.elapsedMillis() == 0 && !restored.isRunning(), "Nova partida zera o relógio");

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            check(GameTimer.format(65000).equals("01:05"), "Formatar minutos e segundos");
            check(GameTimer.format(3600000).equals("60:00"), "Não zerar minutos após uma hora");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** Interrompe a suíte quando um resultado esperado não ocorre. */
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
