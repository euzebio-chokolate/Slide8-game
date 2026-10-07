package com.example.slide8;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;

/** Conversões e política de movimento compartilhadas pelas Views e pelos diálogos. */
final class UiEffects {
    private UiEffects() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    static long duration(long millis) {
        return animationsEnabled() ? millis : 0;
    }
}
