package com.example.slide8;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int SIZE = 3;
    private final int[] board = new int[SIZE * SIZE]; // Zero representa o espaço vazio.
    private final Button[] tiles = new Button[SIZE * SIZE];
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator easing = new DecelerateInterpolator(1.7f);
    private TextView timerView;
    private TextView movesView;
    private TextView hintView;
    private TextView progressLabel;
    private ProgressBar progressBar;
    private GridLayout grid;
    private View homeScreen;
    private View gameScreen;
    private View homeContent;
    private View gameContent;
    private Button startButton;
    private View newGameButton;
    private ObjectAnimator previewAnimator;
    private AlertDialog victoryDialog;
    private AlertDialog restartDialog;
    private int moves;
    private long elapsedMillis;
    private long resumedAt;
    private boolean timerRunning;
    private boolean won;
    private boolean victoryPending;
    private boolean hasGame;
    private boolean gameVisible;
    private boolean resumed;
    private boolean moving;
    private boolean suppressTouchClick;
    private int draggedTile = -1;
    private int animationVersion;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (timerRunning) {
                updateTimer();
                handler.postDelayed(this, 250);
            }
        }
    };
    private final Runnable victoryReveal = this::showVictoryDialog;

    // Button já implementa performClick; o listener o chama no toque e preserva ações assistivas.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerView = findViewById(R.id.timer);
        movesView = findViewById(R.id.moves);
        hintView = findViewById(R.id.game_hint);
        progressLabel = findViewById(R.id.progress_label);
        progressBar = findViewById(R.id.board_progress);
        grid = findViewById(R.id.board_grid);
        homeScreen = findViewById(R.id.home_screen);
        gameScreen = findViewById(R.id.game_screen);
        homeContent = findViewById(R.id.home_content);
        gameContent = findViewById(R.id.game_content);
        startButton = findViewById(R.id.start_game);
        newGameButton = findViewById(R.id.new_game);

        int[] ids = {R.id.tile0, R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4,
                R.id.tile5, R.id.tile6, R.id.tile7, R.id.tile8};
        for (int i = 0; i < tiles.length; i++) {
            final int position = i;
            tiles[i] = findViewById(ids[i]);
            tiles[i].setOnTouchListener(new TileTouchListener(position));
            // Clique sem toque físico permite jogar com TalkBack, teclado ou Switch Access.
            tiles[i].setOnClickListener(view -> {
                if (!suppressTouchClick) {
                    animateMove(position);
                }
            });
        }
        startButton.setOnClickListener(view -> {
            if (hasGame && !won) {
                showScreen(true, true);
            } else {
                newGame();
            }
        });
        newGameButton.setOnClickListener(view -> requestNewGame());
        findViewById(R.id.restart).setOnClickListener(view -> requestNewGame());
        findViewById(R.id.pause_game).setOnClickListener(view -> showScreen(false, true));
        findViewById(R.id.back_home).setOnClickListener(view -> showScreen(false, true));

        if (savedInstanceState != null) {
            int[] savedBoard = savedInstanceState.getIntArray("board");
            if (savedBoard != null && savedBoard.length == board.length) {
                System.arraycopy(savedBoard, 0, board, 0, board.length);
                moves = savedInstanceState.getInt("moves");
                elapsedMillis = savedInstanceState.getLong("elapsed");
                won = savedInstanceState.getBoolean("won");
                victoryPending = savedInstanceState.getBoolean("victoryPending");
                hasGame = savedInstanceState.getBoolean("hasGame");
                gameVisible = savedInstanceState.getBoolean("gameVisible");
            } else {
                shuffleBoard(board, random);
            }
        } else {
            shuffleBoard(board, random);
        }
        configureResponsiveLayout();
        updateBoard();
        updateTimer();
        showScreen(gameVisible, savedInstanceState == null);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

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
        grid.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) {
                // A primeira medição não deve cancelar a entrada nem a vitória restaurada.
                if (or - ol > 0) {
                    settleVisuals();
                }
                int side = (r - l - grid.getPaddingLeft() - grid.getPaddingRight()) / SIZE - dp(10);
                for (Button tile : tiles) {
                    GridLayout.LayoutParams params = (GridLayout.LayoutParams) tile.getLayoutParams();
                    params.height = Math.max(dp(48), side);
                    tile.setLayoutParams(params);
                }
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    private long duration(long millis) {
        return animationsEnabled() ? millis : 0;
    }

    private void showScreen(boolean showGame, boolean animate) {
        pauseTimer();
        stopPreviewAnimation();
        settleVisuals();
        gameVisible = showGame;
        homeScreen.setVisibility(showGame ? View.GONE : View.VISIBLE);
        gameScreen.setVisibility(showGame ? View.VISIBLE : View.GONE);
        startButton.setText(hasGame && !won ? R.string.home_continue : R.string.home_start);
        newGameButton.setVisibility(hasGame && !won ? View.VISIBLE : View.GONE);
        View content = showGame ? gameContent : homeContent;
        if (animate && animationsEnabled()) {
            content.setAlpha(0f);
            content.setTranslationY(dp(18));
            content.animate().alpha(1f).translationY(0f).setDuration(340).setInterpolator(easing).start();
        }
        if (showGame) {
            startTimer();
            if (victoryPending) {
                handler.postDelayed(victoryReveal, duration(360));
            }
        } else if (resumed) {
            startPreviewAnimation();
        }
    }

    private void startPreviewAnimation() {
        if (!animationsEnabled() || previewAnimator != null) {
            return;
        }
        previewAnimator = ObjectAnimator.ofFloat(findViewById(R.id.home_preview), View.ROTATION, -6f, -2f);
        previewAnimator.setDuration(2600);
        previewAnimator.setRepeatMode(ValueAnimator.REVERSE);
        // Um ciclo de flutuação na entrada evita movimento contínuo e consumo ocioso.
        previewAnimator.setRepeatCount(1);
        previewAnimator.start();
    }

    private void stopPreviewAnimation() {
        if (previewAnimator != null) {
            previewAnimator.cancel();
            previewAnimator = null;
        }
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

    private int adjacentEmpty(int position) {
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

    private final class TileTouchListener implements View.OnTouchListener {
        private final int position;
        private float startX;
        private float startY;
        private float targetX;
        private float targetY;
        private boolean active;
        private boolean adjacent;

        TileTouchListener(int position) {
            this.position = position;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (!gameVisible || won || moving) {
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (draggedTile != -1) {
                        return true;
                    }
                    view.animate().cancel();
                    view.setTranslationX(0f);
                    view.setTranslationY(0f);
                    draggedTile = position;
                    active = true;
                    startX = event.getRawX();
                    startY = event.getRawY();
                    int empty = adjacentEmpty(position);
                    adjacent = empty >= 0;
                    if (adjacent) {
                        targetX = tiles[empty].getLeft() - view.getLeft();
                        targetY = tiles[empty].getTop() - view.getTop();
                        view.setElevation(dp(12));
                        view.setScaleX(1.025f);
                        view.setScaleY(1.025f);
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        hintView.setText(R.string.game_hint);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (active && draggedTile == position && adjacent) {
                        float fraction = dragFraction(event.getRawX() - startX, event.getRawY() - startY, targetX, targetY);
                        view.setTranslationX(targetX * fraction);
                        view.setTranslationY(targetY * fraction);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!active || draggedTile != position) {
                        return true;
                    }
                    active = false;
                    draggedTile = -1;
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    if (adjacent && shouldCommitDrag(dx, dy, targetX, targetY)) {
                        animateMove(position);
                    } else {
                        // O toque simples mantém a semântica de clique, sem mover a peça.
                        if (Math.hypot(dx, dy) < ViewConfiguration.get(MainActivity.this).getScaledTouchSlop()) {
                            suppressTouchClick = true;
                            view.performClick();
                            suppressTouchClick = false;
                        }
                        hintView.setText(R.string.invalid_hint);
                        returnTile(view);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (active && draggedTile == position) {
                        active = false;
                        draggedTile = -1;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        returnTile(view);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private void returnTile(View tile) {
        moving = true;
        int version = animationVersion;
        tile.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(duration(180)).setInterpolator(easing).withEndAction(() -> {
                    if (version == animationVersion) {
                        moving = false;
                        tile.setElevation(dp(3));
                    }
                }).start();
    }

    private void animateMove(int position) {
        if (!gameVisible || won || moving) {
            return;
        }
        int empty = adjacentEmpty(position);
        if (empty < 0) {
            hintView.setText(R.string.invalid_hint);
            return;
        }
        Button tile = tiles[position];
        float targetX = tiles[empty].getLeft() - tile.getLeft();
        float targetY = tiles[empty].getTop() - tile.getTop();
        // Confirma o modelo antes de animar: rotação ou pausa não perdem a jogada.
        if (!tryMove(board, position)) {
            return;
        }
        moves++;
        won = isSolved(board);
        victoryPending = won;
        if (won) {
            pauseTimer();
        }
        moving = true;
        int version = animationVersion;
        tile.setElevation(dp(12));
        tile.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        tile.animate().translationX(targetX).translationY(targetY).scaleX(1f).scaleY(1f)
                .setDuration(duration(140)).setInterpolator(easing).withEndAction(() -> {
                    if (version != animationVersion) {
                        return;
                    }
                    moving = false;
                    updateBoard();
                    movesView.setScaleX(1.12f);
                    movesView.setScaleY(1.12f);
                    movesView.animate().scaleX(1f).scaleY(1f).setDuration(duration(180)).start();
                    if (won) {
                        celebrate();
                    }
                }).start();
    }

    private void celebrate() {
        hintView.setText(R.string.victory_title);
        grid.animate().scaleX(1.025f).scaleY(1.025f).setDuration(duration(160))
                .withEndAction(() -> grid.animate().scaleX(1f).scaleY(1f).setDuration(duration(180)).start()).start();
        handler.postDelayed(victoryReveal, duration(400));
    }

    private void settleVisuals() {
        animationVersion++;
        draggedTile = -1;
        moving = false;
        handler.removeCallbacks(victoryReveal);
        grid.getParent().requestDisallowInterceptTouchEvent(false);
        for (View view : new View[]{homeContent, gameContent, grid, movesView}) {
            view.animate().withEndAction(null).cancel();
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
        for (Button tile : tiles) {
            tile.animate().withEndAction(null).cancel();
        }
        updateBoard();
    }

    private void updateBoard() {
        int correct = 0;
        for (int i = 0; i < board.length; i++) {
            boolean empty = board[i] == 0;
            boolean inPlace = !empty && board[i] == i + 1;
            if (inPlace) {
                correct++;
            }
            Button tile = tiles[i];
            tile.setText(empty ? "" : String.valueOf(board[i]));
            tile.setEnabled(!empty && !won);
            tile.setAlpha(1f);
            tile.setTranslationX(0f);
            tile.setTranslationY(0f);
            tile.setScaleX(1f);
            tile.setScaleY(1f);
            tile.setElevation(empty ? 0f : dp(3));
            tile.setBackgroundResource(empty ? R.drawable.bg_empty
                    : inPlace ? R.drawable.bg_tile_correct : R.drawable.bg_tile);
            tile.setContentDescription(empty ? getString(R.string.empty_space)
                    : getString(R.string.tile_description, board[i], i / SIZE + 1, i % SIZE + 1));
            if (Build.VERSION.SDK_INT >= 26) {
                tile.setTooltipText(empty ? null : getString(R.string.tile_accessible_move));
            }
        }
        movesView.setText(getString(R.string.moves_value, moves));
        progressLabel.setText(getString(R.string.progress_value, correct));
        progressBar.setProgress(correct, animationsEnabled());
        hintView.setText(won ? R.string.victory_title : R.string.game_hint);
    }

    private void requestNewGame() {
        if (!hasGame || won || moves == 0) {
            newGame();
            return;
        }
        if (restartDialog != null && restartDialog.isShowing()) {
            return;
        }
        restartDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.restart_title)
                .setMessage(R.string.restart_message)
                .setPositiveButton(R.string.restart_confirm, (dialog, which) -> newGame())
                .setNegativeButton(R.string.cancel, null)
                .create();
        restartDialog.show();
    }

    private void newGame() {
        pauseTimer();
        settleVisuals();
        shuffleBoard(board, random);
        moves = 0;
        elapsedMillis = 0;
        won = false;
        victoryPending = false;
        hasGame = true;
        updateBoard();
        updateTimer();
        showScreen(true, true);
    }

    private long currentElapsedMillis() {
        return elapsedMillis + (timerRunning ? SystemClock.elapsedRealtime() - resumedAt : 0);
    }

    private String formattedTime() {
        long seconds = currentElapsedMillis() / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateTimer() {
        timerView.setText(formattedTime());
    }

    private void startTimer() {
        if (resumed && gameVisible && hasGame && !won && !timerRunning) {
            resumedAt = SystemClock.elapsedRealtime();
            timerRunning = true;
            handler.post(timerTick);
        }
    }

    private void pauseTimer() {
        if (timerRunning) {
            elapsedMillis = currentElapsedMillis();
            timerRunning = false;
        }
        handler.removeCallbacks(timerTick);
        updateTimer();
    }

    private void showVictoryDialog() {
        if (!resumed || !gameVisible || !victoryPending
                || (victoryDialog != null && victoryDialog.isShowing())) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_victory, null);
        ((TextView) content.findViewById(R.id.victory_stats)).setText(
                getResources().getQuantityString(R.plurals.victory_stats, moves, moves, formattedTime()));
        victoryDialog = new AlertDialog.Builder(this).setView(content).create();
        victoryDialog.setCanceledOnTouchOutside(false);
        victoryDialog.setOnCancelListener(dialog -> victoryPending = false);
        content.findViewById(R.id.victory_again).setOnClickListener(view -> {
            victoryDialog.dismiss();
            victoryPending = false;
            newGame();
        });
        content.findViewById(R.id.victory_home).setOnClickListener(view -> {
            victoryDialog.dismiss();
            victoryPending = false;
            showScreen(false, true);
        });
        victoryDialog.show();
        Window window = victoryDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(380)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        View emblem = content.findViewById(R.id.victory_emblem);
        emblem.setScaleX(0.6f);
        emblem.setScaleY(0.6f);
        emblem.setRotation(-20f);
        emblem.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(duration(420)).setInterpolator(easing).start();
    }

    private void handleBack() {
        if (gameVisible) {
            showScreen(false, true);
        } else {
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    // API 33+ usa OnBackInvokedDispatcher nativo; este fallback atende apenas APIs antigas.
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        startTimer();
        if (gameVisible && victoryPending) {
            handler.post(victoryReveal);
        } else if (!gameVisible) {
            startPreviewAnimation();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        pauseTimer();
        stopPreviewAnimation();
        settleVisuals();
        if (victoryDialog != null) {
            victoryDialog.dismiss();
        }
        if (restartDialog != null) {
            restartDialog.dismiss();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putIntArray("board", board.clone());
        outState.putInt("moves", moves);
        outState.putLong("elapsed", currentElapsedMillis());
        outState.putBoolean("won", won);
        outState.putBoolean("victoryPending", victoryPending);
        outState.putBoolean("hasGame", hasGame);
        outState.putBoolean("gameVisible", gameVisible);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopPreviewAnimation();
        super.onDestroy();
    }
}
