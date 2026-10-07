package com.example.slide8;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Resposta tátil nativa; funciona também em aparelhos sem vibrador. */
final class GameHaptics {
    private final Vibrator vibrator;

    /** Obtém o vibrador padrão disponível no dispositivo. */
    @SuppressWarnings("deprecation")
    GameHaptics(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = context.getSystemService(Vibrator.class);
        }
    }

    /** Reproduz um padrão curto de vibração ao concluir a partida. */
    @SuppressWarnings("deprecation")
    void victory() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 80, 60, 140};
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attributes);
        } else {
            vibrator.vibrate(pattern, -1, attributes);
        }
    }

    /** Interrompe a vibração em andamento. */
    void cancel() {
        if (vibrator != null) vibrator.cancel();
    }
}
