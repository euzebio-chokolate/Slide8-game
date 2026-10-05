package com.example.slide8;

import java.util.Arrays;
import java.util.Random;

/** Testes da lógica real da Activity, executáveis sem JUnit ou emulador. */
public final class PuzzleLogicTest {
    public static void main(String[] args) {
        int[] solved = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        check(MainActivity.isSolved(solved), "Reconhecer vitória");
        check(MainActivity.isSolvable(solved), "Objetivo é solucionável");
        check(!MainActivity.isSolvable(new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0}),
                "Rejeitar duas peças invertidas");

        int[] lastMove = {1, 2, 3, 4, 5, 6, 7, 0, 8};
        check(!MainActivity.isSolved(lastMove), "Não antecipar vitória");
        check(MainActivity.tryMove(lastMove, 8), "Aceitar último movimento");
        check(MainActivity.isSolved(lastMove), "Detectar vitória após movimento");

        // Examina as 81 combinações de posição vazia e posição clicada.
        for (int empty = 0; empty < 9; empty++) {
            int[] original = solved.clone();
            original[8] = original[empty];
            original[empty] = 0;
            for (int clicked = 0; clicked < 9; clicked++) {
                int[] state = original.clone();
                boolean expected = (empty / 3 == clicked / 3 && Math.abs(empty - clicked) == 1)
                        || Math.abs(empty - clicked) == 3;
                check(MainActivity.tryMove(state, clicked) == expected,
                        "Adjacência: vazio=" + empty + ", clique=" + clicked);
                if (expected) {
                    check(state[clicked] == 0 && state[empty] == original[clicked], "Trocar peça");
                    check(MainActivity.tryMove(state, empty), "Reverter movimento");
                }
                check(Arrays.equals(state, original), "Não alterar outras posições");
            }
        }
        check(!MainActivity.tryMove(solved, -1), "Rejeitar índice negativo");
        check(!MainActivity.tryMove(solved, 9), "Rejeitar índice fora do tabuleiro");

        Random random = new Random(2026);
        int[] state = new int[9];
        for (int i = 0; i < 10000; i++) {
            MainActivity.shuffleBoard(state, random);
            check(MainActivity.isSolvable(state), "Embaralhamento solucionável");
            check(!MainActivity.isSolved(state), "Não iniciar com vitória");
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
            check(MainActivity.shouldCommitDrag(x * 0.5f, y * 0.5f, x, y), "Aceitar deslize na direção do vazio");
            check(!MainActivity.shouldCommitDrag(x * 0.1f, y * 0.1f, x, y), "Retornar gesto curto");
            check(!MainActivity.shouldCommitDrag(-x, -y, x, y), "Rejeitar sentido oposto");
            check(!MainActivity.shouldCommitDrag(y, -x, x, y), "Rejeitar eixo perpendicular");
            check(!MainActivity.shouldCommitDrag(x * 0.3f + y, y * 0.3f - x, x, y), "Rejeitar diagonal predominante");
            check(!MainActivity.shouldCommitDrag(0, 0, x, y), "Toque simples não move");
            check(MainActivity.dragFraction(x * 0.5f, y * 0.5f, x, y) == 0.5f, "Peça acompanha o dedo");
            check(MainActivity.dragFraction(x * 2, y * 2, x, y) == 1f, "Limitar ao espaço vazio");
            check(MainActivity.dragFraction(-x, -y, x, y) == 0f, "Não arrastar para trás");
        }
        check(!MainActivity.shouldCommitDrag(100, 100, 0, 0), "Sem destino não há movimento");
        check(MainActivity.dragFraction(100, 100, 0, 0) == 0, "Evitar divisão por zero");
        System.out.println("OK: 10.000 embaralhamentos, 81 combinações de movimento, vitória e gestos nas quatro direções.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
