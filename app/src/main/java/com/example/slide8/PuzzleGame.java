package com.example.slide8;

import java.util.Random;
import java.util.UUID;

/** Estado e regras da partida, sem dependências de Android ou de interface. */
final class PuzzleGame {
    static final int SIZE = 3;
    static final int CELL_COUNT = SIZE * SIZE;
    private final int[] board = new int[CELL_COUNT];
    private final Random random = new Random();
    private int moves;
    private boolean hasGame;
    private boolean won;
    private String matchId = UUID.randomUUID().toString();
    private long completedAt;

    PuzzleGame() {
        shuffleBoard(board, random);
    }

    void newGame() {
        shuffleBoard(board, random);
        moves = 0;
        hasGame = true;
        won = false;
        matchId = UUID.randomUUID().toString();
        completedAt = 0;
    }

    boolean move(int position, long now) {
        if (!hasGame || won || !tryMove(board, position)) return false;
        moves++;
        won = isSolved(board);
        if (won) completedAt = now;
        return true;
    }

    boolean restore(int[] state, int savedMoves, boolean savedHasGame,
                    String savedId, long savedCompletedAt) {
        if (!isValidBoard(state) || savedMoves < 0) return false;
        System.arraycopy(state, 0, board, 0, board.length);
        moves = savedMoves;
        hasGame = savedHasGame;
        won = isSolved(board);
        matchId = savedId == null ? UUID.randomUUID().toString() : savedId;
        completedAt = savedCompletedAt;
        return true;
    }

    int tileAt(int position) { return board[position]; }
    int[] snapshot() { return board.clone(); }
    int moves() { return moves; }
    boolean hasGame() { return hasGame; }
    boolean isWon() { return won; }
    String matchId() { return matchId; }
    long completedAt() { return completedAt; }

    private static boolean isValidBoard(int[] state) {
        if (state == null || state.length != CELL_COUNT) return false;
        boolean[] seen = new boolean[CELL_COUNT];
        for (int tile : state) {
            if (tile < 0 || tile >= CELL_COUNT || seen[tile]) return false;
            seen[tile] = true;
        }
        return isSolvable(state);
    }

    int adjacentEmpty(int position) {
        if (board[position] == 0) {
            return -1;
        }
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0 && Math.abs(position / SIZE - i / SIZE)
                    + Math.abs(position % SIZE - i % SIZE) == 1) {
                return i;
            }
        }
        return -1;
    }

    // Em grades de largura ímpar, um número par de inversões garante solução.
    static boolean isSolvable(int[] state) {
        int inversions = 0;
        for (int i = 0; i < state.length; i++) {
            for (int j = i + 1; j < state.length; j++) {
                if (state[i] != 0 && state[j] != 0 && state[i] > state[j]) {
                    inversions++;
                }
            }
        }
        return inversions % 2 == 0;
    }

    static void shuffleBoard(int[] state, Random random) {
        do {
            for (int i = 0; i < state.length; i++) {
                state[i] = (i + 1) % state.length;
            }
            // Fisher–Yates: cada permutação tem a mesma probabilidade.
            for (int i = state.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int value = state[i];
                state[i] = state[j];
                state[j] = value;
            }
        } while (!isSolvable(state) || isSolved(state));
    }

    static boolean tryMove(int[] state, int position) {
        if (position < 0 || position >= state.length || state[position] == 0) {
            return false;
        }
        int empty = 0;
        while (state[empty] != 0) {
            empty++;
        }
        int distance = Math.abs(position / SIZE - empty / SIZE)
                + Math.abs(position % SIZE - empty % SIZE);
        if (distance != 1) {
            return false;
        }
        state[empty] = state[position];
        state[position] = 0;
        return true;
    }

    static boolean isSolved(int[] state) {
        for (int i = 0; i < state.length - 1; i++) {
            if (state[i] != i + 1) {
                return false;
            }
        }
        return state[state.length - 1] == 0;
    }


}
