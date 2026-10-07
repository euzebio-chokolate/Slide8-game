package com.example.slide8;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;

/** Conversões e política de movimento compartilhadas pelas Views e pelos diálogos. */
final class UiEffects {
    /** Impede a criação de instâncias desta classe utilitária. */
    private UiEffects() {}

    /** Converte uma medida em dp para pixels na tela atual. */
    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Informa se as animações do aplicativo podem ser executadas. */
    static boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    /** Remove a duração das animações quando o sistema as desativa. */
    static long duration(long millis) {
        return animationsEnabled() ? millis : 0;
    }
}
