package com.example.slide8;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import static com.example.slide8.UiEffects.animationsEnabled;
import static com.example.slide8.UiEffects.duration;

/** Renderização, gestos e animações do tabuleiro. Não decide navegação ou persistência. */
final class BoardController {
    interface Listener {
        /** Confirma a jogada no estado da partida. */
        boolean onMove(int position);
        /** Notifica a Activity quando a animação de vitória termina. */
        void onVictoryReady();
        /** Notifica a Activity quando muda o tamanho do tabuleiro. */
        void onBoardResized();
    }

    private final Activity activity;
    private final PuzzleGame game;
    private final Listener listener;
    private final Button[] tiles = new Button[PuzzleGame.CELL_COUNT];
    private final GridLayout grid;
    private final TextView movesView;
    private final TextView hintView;
    private final TextView progressLabel;
    private final ProgressBar progressBar;
    private final DecelerateInterpolator easing = new DecelerateInterpolator(1.7f);
    private boolean interactive;
    private boolean moving;
    private boolean suppressTouchClick;
    private int draggedTile = -1;
    private int animationVersion;

    // Button já implementa performClick; toque e ações assistivas seguem caminhos distintos.
    /** Conecta as peças e prepara os gestos e as animações do tabuleiro. */
    @SuppressLint("ClickableViewAccessibility")
    BoardController(Activity activity, PuzzleGame game, Listener listener) {
        this.activity = activity;
        this.game = game;
        this.listener = listener;
        grid = activity.findViewById(R.id.board_grid);
        movesView = activity.findViewById(R.id.moves);
        hintView = activity.findViewById(R.id.game_hint);
        progressLabel = activity.findViewById(R.id.progress_label);
        progressBar = activity.findViewById(R.id.board_progress);
        int[] ids = {R.id.tile0, R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4,
                R.id.tile5, R.id.tile6, R.id.tile7, R.id.tile8};
        for (int i = 0; i < tiles.length; i++) {
            final int position = i;
            tiles[i] = activity.findViewById(ids[i]);
            tiles[i].setOnTouchListener(new TileTouchListener(position));
            // Clique assistivo continua disponível para TalkBack, teclado e Switch Access.
            tiles[i].setOnClickListener(view -> {
                if (!suppressTouchClick) animateMove(position);
            });
        }
        grid.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) {
                if (or - ol > 0) listener.onBoardResized();
                int side = (r - l - grid.getPaddingLeft() - grid.getPaddingRight())
                        / PuzzleGame.SIZE - dp(10);
                for (Button tile : tiles) {
                    GridLayout.LayoutParams params = (GridLayout.LayoutParams) tile.getLayoutParams();
                    params.height = Math.max(dp(48), side);
                    tile.setLayoutParams(params);
                }
            }
        });
    }

    /** Ativa ou desativa a interação com as peças. */
    void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    /** Converte dp em pixels para dimensionar o tabuleiro. */
    private int dp(int value) { return UiEffects.dp(activity, value); }

    /** Cancela as animações pendentes e redesenha as peças. */
    void settle() {
        animationVersion++;
        draggedTile = -1;
        moving = false;
        grid.getParent().requestDisallowInterceptTouchEvent(false);
        for (View view : new View[]{grid, movesView}) {
            view.animate().withEndAction(null).cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
        for (Button tile : tiles) {
            tile.animate().withEndAction(null).cancel();
        }
        render();
    }

    /** Atualiza números, cores, descrições e progresso das peças. */
    void render() {
        int correct = 0;
        for (int i = 0; i < PuzzleGame.CELL_COUNT; i++) {
            boolean empty = game.tileAt(i) == 0;
            boolean inPlace = !empty && game.tileAt(i) == i + 1;
            if (inPlace) {
                correct++;
            }
            Button tile = tiles[i];
            tile.setText(empty ? "" : String.valueOf(game.tileAt(i)));
            tile.setEnabled(!empty && !game.isWon());
            tile.setAlpha(1f);
            tile.setTranslationX(0f);
            tile.setTranslationY(0f);
            tile.setScaleX(1f);
            tile.setScaleY(1f);
            tile.setElevation(empty ? 0f : dp(3));
            tile.setBackgroundResource(empty ? R.drawable.bg_empty
                    : inPlace ? R.drawable.bg_tile_correct : R.drawable.bg_tile);
            tile.setContentDescription(empty ? activity.getString(R.string.empty_space)
                    : activity.getString(R.string.tile_description, game.tileAt(i), i / PuzzleGame.SIZE + 1, i % PuzzleGame.SIZE + 1));
            if (Build.VERSION.SDK_INT >= 26) {
                tile.setTooltipText(empty ? null : activity.getString(R.string.tile_accessible_move));
            }
        }
        movesView.setText(activity.getString(R.string.moves_value, game.moves()));
        progressLabel.setText(activity.getString(R.string.progress_value, correct));
        progressBar.setProgress(correct, animationsEnabled());
        hintView.setText(game.isWon() ? R.string.victory_title : R.string.game_hint);
    }

    private final class TileTouchListener implements View.OnTouchListener {
        private final int position;
        private float startX;
        private float startY;
        private float targetX;
        private float targetY;
        private boolean active;
        private boolean adjacent;

        /** Cria o detector de gestos para uma peça do tabuleiro. */
        TileTouchListener(int position) {
            this.position = position;
        }

        /** Acompanha o arraste e confirma uma jogada válida. */
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (!interactive || game.isWon() || moving) {
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
                    int empty = game.adjacentEmpty(position);
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
                        float fraction = DragGesture.dragFraction(event.getRawX() - startX, event.getRawY() - startY, targetX, targetY);
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
                    if (adjacent && DragGesture.shouldCommitDrag(dx, dy, targetX, targetY)) {
                        animateMove(position);
                    } else {
                        // O toque simples mantém a semântica de clique, sem mover a peça.
                        if (Math.hypot(dx, dy) < ViewConfiguration.get(activity).getScaledTouchSlop()) {
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

    /** Devolve à posição inicial uma peça cujo gesto foi rejeitado. */
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

    /** Confirma a jogada e anima a peça até o espaço vazio. */
    private void animateMove(int position) {
        if (!interactive || game.isWon() || moving) {
            return;
        }
        int empty = game.adjacentEmpty(position);
        if (empty < 0) {
            hintView.setText(R.string.invalid_hint);
            return;
        }
        Button tile = tiles[position];
        float targetX = tiles[empty].getLeft() - tile.getLeft();
        float targetY = tiles[empty].getTop() - tile.getTop();
        // O estado é confirmado antes da animação; pausa e rotação preservam a jogada.
        if (!listener.onMove(position)) return;
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
                    render();
                    movesView.setScaleX(1.12f);
                    movesView.setScaleY(1.12f);
                    movesView.animate().scaleX(1f).scaleY(1f).setDuration(duration(180)).start();
                    if (game.isWon()) {
                        celebrate();
                    }
                }).start();
    }

    /** Apresenta o pulso visual e informa a Activity sobre a vitória. */
    private void celebrate() {
        hintView.setText(R.string.victory_title);
        grid.animate().scaleX(1.025f).scaleY(1.025f).setDuration(duration(160))
                .withEndAction(() -> grid.animate().scaleX(1f).scaleY(1f).setDuration(duration(180)).start()).start();
        listener.onVictoryReady();
    }

}
