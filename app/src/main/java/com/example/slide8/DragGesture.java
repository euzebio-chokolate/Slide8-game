package com.example.slide8;

/** Geometria do arraste, testável sem Views ou eventos Android. */
final class DragGesture {
    private DragGesture() {}

    // Projeção do gesto no único eixo permitido: da peça até o espaço vazio.
    static float dragFraction(float dx, float dy, float targetX, float targetY) {
        float squaredDistance = targetX * targetX + targetY * targetY;
        if (squaredDistance == 0) {
            return 0;
        }
        return Math.max(0f, Math.min(1f, (dx * targetX + dy * targetY) / squaredDistance));
    }

    static boolean shouldCommitDrag(float dx, float dy, float targetX, float targetY) {
        float distance = (float) Math.hypot(targetX, targetY);
        if (distance == 0) {
            return false;
        }
        float forward = (dx * targetX + dy * targetY) / distance;
        float sideways = Math.abs(dx * targetY - dy * targetX) / distance;
        return forward >= distance * 0.24f && sideways <= forward;
    }

}
