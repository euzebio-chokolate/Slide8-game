package com.example.slide8;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Efeitos curtos locais. Nenhum áudio é enfileirado ou retomado após uma pausa. */
final class GameSounds {
    static final int MOVE = 0;
    static final int SHUFFLE = 1;
    static final int VICTORY = 2;
    private final int[] samples = new int[3];
    private final boolean[] loaded = new boolean[3];
    private SoundPool pool;
    private boolean enabled = true;
    private boolean resumed;
    private int stream;

    GameSounds(Context context) {
        pool = new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (pool != soundPool) return;
            for (int i = 0; i < samples.length; i++) {
                if (samples[i] == sampleId) loaded[i] = status == 0;
            }
        });
        samples[MOVE] = pool.load(context, R.raw.move, 1);
        samples[SHUFFLE] = pool.load(context, R.raw.shuffle, 1);
        samples[VICTORY] = pool.load(context, R.raw.victory, 1);
    }

    void play(int effect) {
        if (pool == null || !enabled || !resumed || !loaded[effect]) return;
        stop();
        stream = pool.play(samples[effect], 0.55f, 0.55f, 1, 0, 1f);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) stop();
    }

    void resume() {
        resumed = true;
    }

    void pause() {
        resumed = false;
        stop();
    }

    private void stop() {
        if (pool != null && stream != 0) pool.stop(stream);
        stream = 0;
    }

    void release() {
        pause();
        if (pool != null) {
            pool.setOnLoadCompleteListener(null);
            pool.release();
            pool = null;
        }
    }
}
