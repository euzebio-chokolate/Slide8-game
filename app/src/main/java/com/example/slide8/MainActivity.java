package com.example.slide8;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import static com.example.slide8.UiEffects.animationsEnabled;
import static com.example.slide8.UiEffects.duration;

/** Coordena navegação, ciclo de vida e os componentes do jogo. */
public class MainActivity extends Activity implements BoardController.Listener {
    private final PuzzleGame game = new PuzzleGame();
    private final GameTimer timer = new GameTimer(SystemClock::elapsedRealtime);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GameSounds sounds;
    private GameHistory history;
    private GameHaptics haptics;
    private GameDialogs dialogs;
    private BoardController board;
    private TextView timerView;
    private View homeScreen;
    private View gameScreen;
    private View homeContent;
    private View gameContent;
    private Button startButton;
    private View newGameButton;
    private ObjectAnimator previewAnimator;
    private boolean victoryPending;
    private boolean victoryVibrated;
    private boolean gameVisible;
    private boolean resumed;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (timer.isRunning()) {
                updateTimer();
                handler.postDelayed(this, 250);
            }
        }
    };
    private final Runnable victoryReveal = this::showVictoryDialog;

    @Override
    protected void attachBaseContext(Context base) {
        Configuration override = new Configuration();
        override.uiMode = GameSettings.isDarkTheme(base)
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        super.attachBaseContext(base);
        // Tamanho de fonte, idioma e orientação continuam seguindo o sistema.
        applyOverrideConfiguration(override);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sounds = new GameSounds(this);
        history = new GameHistory(this);
        haptics = new GameHaptics(this);
        dialogs = new GameDialogs(this, history);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_main);
        timerView = findViewById(R.id.timer);
        homeScreen = findViewById(R.id.home_screen);
        gameScreen = findViewById(R.id.game_screen);
        homeContent = findViewById(R.id.home_content);
        gameContent = findViewById(R.id.game_content);
        startButton = findViewById(R.id.start_game);
        newGameButton = findViewById(R.id.new_game);
        restoreState(savedInstanceState);
        board = new BoardController(this, game, this);
        new GameSettings(this, sounds, () -> {
            pauseTimer();
            settleVisuals();
            board.setInteractive(false);
            recreate();
        });
        bindNavigation();
        configureResponsiveLayout();
        updateTimer();
        showScreen(gameVisible, savedInstanceState == null);
        if (game.isWon() && game.completedAt() > 0) saveVictory();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void bindNavigation() {
        startButton.setOnClickListener(view -> {
            if (game.hasGame() && !game.isWon()) showScreen(true, true);
            else newGame();
        });
        newGameButton.setOnClickListener(view -> requestNewGame());
        findViewById(R.id.restart).setOnClickListener(view -> requestNewGame());
        findViewById(R.id.pause_game).setOnClickListener(view -> showScreen(false, true));
        findViewById(R.id.back_home).setOnClickListener(view -> showScreen(false, true));
        findViewById(R.id.about).setOnClickListener(view -> dialogs.showAbout());
        findViewById(R.id.history).setOnClickListener(view -> dialogs.showHistory());
    }

    private void restoreState(Bundle state) {
        if (state == null || !game.restore(state.getIntArray("board"), state.getInt("moves"),
                state.getBoolean("hasGame"), state.getString("matchId"), state.getLong("completedAt"))) return;
        timer.restore(state.getLong("elapsed"));
        victoryPending = state.getBoolean("victoryPending");
        victoryVibrated = state.getBoolean("victoryVibrated");
        gameVisible = state.getBoolean("gameVisible");
    }

    private int dp(int value) { return UiEffects.dp(this, value); }

    private void configureResponsiveLayout() {
        findViewById(R.id.main).addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int width = Math.min(r - l - view.getPaddingLeft() - view.getPaddingRight() - dp(48), dp(420));
            if (width > 0) {
                for (View content : new View[]{homeContent, gameContent}) {
                    if (content.getLayoutParams().width != width) {
                        ViewGroup.LayoutParams params = content.getLayoutParams();
                        params.width = width;
                        content.setLayoutParams(params);
                    }
                }
            }
        });
    }

    private void showScreen(boolean showGame, boolean animate) {
        pauseTimer();
        stopPreviewAnimation();
        settleVisuals();
        gameVisible = showGame;
        board.setInteractive(showGame && resumed);
        homeScreen.setVisibility(showGame ? View.GONE : View.VISIBLE);
        gameScreen.setVisibility(showGame ? View.VISIBLE : View.GONE);
        startButton.setText(game.hasGame() && !game.isWon() ? R.string.home_continue : R.string.home_start);
        newGameButton.setVisibility(game.hasGame() && !game.isWon() ? View.VISIBLE : View.GONE);
        View content = showGame ? gameContent : homeContent;
        if (animate && animationsEnabled()) {
            content.setAlpha(0f);
            content.setTranslationY(dp(18));
            content.animate().alpha(1f).translationY(0f).setDuration(340)
                    .setInterpolator(new DecelerateInterpolator(1.7f)).start();
        }
        if (showGame) {
            startTimer();
            if (victoryPending) handler.postDelayed(victoryReveal, duration(360));
        } else if (resumed) {
            startPreviewAnimation();
        }
    }

    private void startPreviewAnimation() {
        if (!animationsEnabled() || previewAnimator != null) return;
        previewAnimator = ObjectAnimator.ofFloat(findViewById(R.id.home_preview), View.ROTATION, -6f, -2f);
        previewAnimator.setDuration(2600);
        previewAnimator.setRepeatMode(ValueAnimator.REVERSE);
        previewAnimator.setRepeatCount(1);
        previewAnimator.start();
    }

    private void stopPreviewAnimation() {
        if (previewAnimator != null) {
            previewAnimator.cancel();
            previewAnimator = null;
        }
    }

    private void settleVisuals() {
        handler.removeCallbacks(victoryReveal);
        for (View view : new View[]{homeContent, gameContent}) {
            view.animate().withEndAction(null).cancel();
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
        board.settle();
    }

    @Override
    public boolean onMove(int position) {
        if (!resumed || !gameVisible || dialogs.isRestartShowing()
                || !game.move(position, System.currentTimeMillis())) return false;
        sounds.play(game.isWon() ? GameSounds.VICTORY : GameSounds.MOVE);
        victoryPending = game.isWon();
        if (game.isWon()) {
            pauseTimer();
            saveVictory();
        }
        return true;
    }

    @Override
    public void onVictoryReady() {
        handler.postDelayed(victoryReveal, duration(400));
    }

    @Override
    public void onBoardResized() {
        settleVisuals();
    }

    private void saveVictory() {
        history.save(new GameHistory.Match(game.matchId(), game.completedAt(), game.moves(),
                timer.elapsedMillis()), saved -> {
            if (!saved && !isDestroyed() && resumed) {
                Toast.makeText(this, R.string.history_save_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestNewGame() {
        if (!game.hasGame() || game.isWon() || game.moves() == 0) {
            newGame();
            return;
        }
        if (dialogs.isRestartShowing()) return;
        pauseTimer();
        settleVisuals();
        dialogs.showRestart(this::newGame, this::startTimer);
    }

    private void newGame() {
        pauseTimer();
        settleVisuals();
        game.newGame();
        timer.restore(0);
        victoryPending = false;
        victoryVibrated = false;
        updateTimer();
        showScreen(true, true);
        sounds.play(GameSounds.SHUFFLE);
    }

    private void updateTimer() {
        timerView.setText(GameTimer.format(timer.elapsedMillis()));
    }

    private void startTimer() {
        if (resumed && gameVisible && game.hasGame() && !game.isWon()
                && !dialogs.isRestartShowing() && !timer.isRunning()) {
            timer.start();
            handler.post(timerTick);
        }
    }

    private void pauseTimer() {
        timer.pause();
        handler.removeCallbacks(timerTick);
        updateTimer();
    }

    private void showVictoryDialog() {
        if (!resumed || !gameVisible || !victoryPending) return;
        boolean shown = dialogs.showVictory(game.moves(), GameTimer.format(timer.elapsedMillis()),
                this::newGame, () -> {
                    victoryPending = false;
                    showScreen(false, true);
                }, () -> victoryPending = false);
        if (shown && !victoryVibrated) {
            victoryVibrated = true;
            haptics.victory();
        }
    }

    private void handleBack() {
        if (gameVisible) showScreen(false, true);
        else finish();
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        sounds.resume();
        board.setInteractive(gameVisible);
        startTimer();
        if (gameVisible && victoryPending) handler.post(victoryReveal);
        else if (!gameVisible) startPreviewAnimation();
    }

    @Override
    protected void onPause() {
        resumed = false;
        board.setInteractive(false);
        sounds.pause();
        haptics.cancel();
        pauseTimer();
        stopPreviewAnimation();
        settleVisuals();
        dialogs.dismissAll();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putIntArray("board", game.snapshot());
        outState.putInt("moves", game.moves());
        outState.putLong("elapsed", timer.elapsedMillis());
        outState.putBoolean("won", game.isWon());
        outState.putBoolean("victoryPending", victoryPending);
        outState.putBoolean("hasGame", game.hasGame());
        outState.putBoolean("gameVisible", gameVisible);
        outState.putString("matchId", game.matchId());
        outState.putLong("completedAt", game.completedAt());
        outState.putBoolean("victoryVibrated", victoryVibrated);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopPreviewAnimation();
        sounds.release();
        history.close();
        super.onDestroy();
    }
}
