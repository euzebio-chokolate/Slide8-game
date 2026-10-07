package com.example.slide8;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.Button;

/** Preferências persistentes e controles de tema/som, compartilhados pelas duas telas. */
final class GameSettings {
    private static final String PREFERENCES = "slide8_settings";
    private static final String DARK_THEME = "dark_theme";
    private static final String SOUND_ENABLED = "sound_enabled";
    private final Activity activity;
    private final SharedPreferences preferences;
    private final GameSounds sounds;
    private final boolean darkTheme;
    private boolean soundEnabled;
    private boolean changingTheme;

    GameSettings(Activity activity, GameSounds sounds, Runnable onThemeChanged) {
        this.activity = activity;
        this.sounds = sounds;
        preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        darkTheme = preferences.getBoolean(DARK_THEME, true);
        soundEnabled = preferences.getBoolean(SOUND_ENABLED, true);
        bind(onThemeChanged);
    }

    static boolean isDarkTheme(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DARK_THEME, true);
    }

    @SuppressWarnings("deprecation")
    private void bind(Runnable onThemeChanged) {
        if (Build.VERSION.SDK_INT >= 26) {
            View decor = activity.getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(darkTheme ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        for (int id : new int[]{R.id.home_theme, R.id.game_theme}) {
            Button button = activity.findViewById(id);
            button.setText(darkTheme ? R.string.theme_dark : R.string.theme_light);
            button.setContentDescription(activity.getString(darkTheme
                    ? R.string.switch_to_light : R.string.switch_to_dark));
            button.setOnClickListener(view -> {
                if (changingTheme) return;
                changingTheme = true;
                preferences.edit().putBoolean(DARK_THEME, !darkTheme).apply();
                onThemeChanged.run();
            });
        }
        for (int id : new int[]{R.id.home_sound, R.id.game_sound}) {
            activity.findViewById(id).setOnClickListener(view -> {
                soundEnabled = !soundEnabled;
                preferences.edit().putBoolean(SOUND_ENABLED, soundEnabled).apply();
                updateSoundButtons();
                if (soundEnabled) sounds.play(GameSounds.MOVE);
            });
        }
        updateSoundButtons();
    }

    private void updateSoundButtons() {
        sounds.setEnabled(soundEnabled);
        for (int id : new int[]{R.id.home_sound, R.id.game_sound}) {
            Button button = activity.findViewById(id);
            button.setText(soundEnabled ? R.string.sound_on : R.string.sound_off);
            button.setContentDescription(activity.getString(soundEnabled
                    ? R.string.disable_sound : R.string.enable_sound));
        }
    }

}
