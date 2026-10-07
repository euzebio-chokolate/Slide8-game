package com.example.slide8;

import java.util.Arrays;
import java.util.Random;

/** Testes da lógica real do jogo, executáveis sem JUnit ou emulador. */
public final class PuzzleLogicTest {
    /** Executa os testes de embaralhamento, movimentos e gestos. */
    public static void main(String[] args) {
        int[] solved = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        check(PuzzleGame.isSolved(solved), "Reconhecer vitória");
        check(PuzzleGame.isSolvable(solved), "Objetivo é solucionável");
        check(!PuzzleGame.isSolvable(new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0}),
                "Rejeitar duas peças invertidas");

        int[] lastMove = {1, 2, 3, 4, 5, 6, 7, 0, 8};
        check(!PuzzleGame.isSolved(lastMove), "Não antecipar vitória");
        check(PuzzleGame.tryMove(lastMove, 8), "Aceitar último movimento");
        check(PuzzleGame.isSolved(lastMove), "Detectar vitória após movimento");

        // Examina as 81 combinações de posição vazia e posição clicada.
        for (int empty = 0; empty < 9; empty++) {
            int[] original = solved.clone();
            original[8] = original[empty];
            original[empty] = 0;
            for (int clicked = 0; clicked < 9; clicked++) {
                int[] state = original.clone();
                boolean expected = (empty / 3 == clicked / 3 && Math.abs(empty - clicked) == 1)
                        || Math.abs(empty - clicked) == 3;
                check(PuzzleGame.tryMove(state, clicked) == expected,
                        "Adjacência: vazio=" + empty + ", clique=" + clicked);
                if (expected) {
                    check(state[clicked] == 0 && state[empty] == original[clicked], "Trocar peça");
                    check(PuzzleGame.tryMove(state, empty), "Reverter movimento");
                }
                check(Arrays.equals(state, original), "Não alterar outras posições");
            }
        }
        check(!PuzzleGame.tryMove(solved, -1), "Rejeitar índice negativo");
        check(!PuzzleGame.tryMove(solved, 9), "Rejeitar índice fora do tabuleiro");

        Random random = new Random(2026);
        int[] state = new int[9];
        for (int i = 0; i < 10000; i++) {
            PuzzleGame.shuffleBoard(state, random);
            check(PuzzleGame.isSolvable(state), "Embaralhamento solucionável");
            check(!PuzzleGame.isSolved(state), "Não iniciar com vitória");
            int[] sorted = state.clone();
            Arrays.sort(sorted);
            for (int j = 0; j < 9; j++) {
                check(sorted[j] == j, "Cada número deve aparecer exatamente uma vez");
            }
        }
        float[][] targets = {{100, 0}, {-100, 0}, {0, 100}, {0, -100}};
        for (float[] target : targets) {
            float x = target[0];
            float y = target[1];
            check(DragGesture.shouldCommitDrag(x * 0.5f, y * 0.5f, x, y), "Aceitar deslize na direção do vazio");
            check(!DragGesture.shouldCommitDrag(x * 0.1f, y * 0.1f, x, y), "Retornar gesto curto");
            check(!DragGesture.shouldCommitDrag(-x, -y, x, y), "Rejeitar sentido oposto");
            check(!DragGesture.shouldCommitDrag(y, -x, x, y), "Rejeitar eixo perpendicular");
            check(!DragGesture.shouldCommitDrag(x * 0.3f + y, y * 0.3f - x, x, y), "Rejeitar diagonal predominante");
            check(!DragGesture.shouldCommitDrag(0, 0, x, y), "Toque simples não move");
            check(DragGesture.dragFraction(x * 0.5f, y * 0.5f, x, y) == 0.5f, "Peça acompanha o dedo");
            check(DragGesture.dragFraction(x * 2, y * 2, x, y) == 1f, "Limitar ao espaço vazio");
            check(DragGesture.dragFraction(-x, -y, x, y) == 0f, "Não arrastar para trás");
        }
        check(!DragGesture.shouldCommitDrag(100, 100, 0, 0), "Sem destino não há movimento");
        check(DragGesture.dragFraction(100, 100, 0, 0) == 0, "Evitar divisão por zero");
        System.out.println("OK: 10.000 embaralhamentos, 81 combinações de movimento, vitória e gestos nas quatro direções.");
    }

    /** Interrompe a suíte se uma condição do jogo não for atendida. */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
