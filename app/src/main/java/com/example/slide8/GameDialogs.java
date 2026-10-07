package com.example.slide8;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import static com.example.slide8.UiEffects.duration;

/** Diálogos nativos com callbacks de ações; o fluxo da partida fica na Activity. */
final class GameDialogs {
    private final Activity activity;
    private final GameHistory history;
    private AlertDialog aboutDialog;
    private AlertDialog historyDialog;
    private AlertDialog restartDialog;
    private AlertDialog victoryDialog;

    GameDialogs(Activity activity, GameHistory history) {
        this.activity = activity;
        this.history = history;
    }

    private int dp(int value) { return UiEffects.dp(activity, value); }

    void showAbout() {
        if (aboutDialog != null && aboutDialog.isShowing()) return;
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_about, null);
        aboutDialog = new AlertDialog.Builder(activity).setView(content).create();
        content.findViewById(R.id.about_close).setOnClickListener(view -> aboutDialog.dismiss());
        showStyledDialog(aboutDialog);
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(40), dp(380)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    void showHistory() {
        if (historyDialog != null && historyDialog.isShowing()) return;
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_history, null);
        int height = Math.min(activity.getResources().getDisplayMetrics().heightPixels - dp(96), dp(560));
        // Dimensiona o conteúdo, não só a janela, para a lista ocupar o espaço disponível.
        content.setMinimumHeight(height);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(content).create();
        historyDialog = dialog;
        content.findViewById(R.id.history_close).setOnClickListener(view -> dialog.dismiss());
        showStyledDialog(dialog);
        TextView status = content.findViewById(R.id.history_status);
        ListView list = content.findViewById(R.id.history_list);
        history.load(matches -> {
            if (activity.isDestroyed() || !dialog.isShowing()) return;
            if (matches != null) {
                TextView count = content.findViewById(R.id.history_count);
                count.setText(activity.getResources().getQuantityString(R.plurals.history_count, matches.size(), matches.size()));
                count.setVisibility(View.VISIBLE);
            }
            if (matches == null || matches.isEmpty()) {
                status.setText(matches == null ? R.string.history_error : R.string.history_empty);
                return;
            }
            status.setVisibility(View.GONE);
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            list.setAdapter(new ArrayAdapter<GameHistory.Match>(activity, R.layout.item_history, matches) {
                @Override
                public boolean isEnabled(int position) { return false; }

                @Override
                public boolean areAllItemsEnabled() { return false; }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View row = convertView != null ? convertView
                            : activity.getLayoutInflater().inflate(R.layout.item_history, parent, false);
                    GameHistory.Match match = getItem(position);
                    ((TextView) row.findViewById(R.id.history_date)).setText(activity.getString(
                            R.string.history_result, dateFormat.format(new Date(match.completedAt))));
                    ((TextView) row.findViewById(R.id.history_stats)).setText(activity.getResources()
                            .getQuantityString(R.plurals.victory_stats, match.moves,
                                    match.moves, GameTimer.format(match.elapsedMillis)));
                    return row;
                }
            });
        });
    }

    boolean isRestartShowing() {
        return restartDialog != null && restartDialog.isShowing();
    }

    void showRestart(Runnable onConfirm, Runnable onDismiss) {
        if (isRestartShowing()) return;
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_restart, null);
        restartDialog = new AlertDialog.Builder(activity).setView(content).create();
        restartDialog.setOnDismissListener(dialog -> onDismiss.run());
        content.findViewById(R.id.restart_confirm).setOnClickListener(view -> {
            restartDialog.dismiss();
            onConfirm.run();
        });
        content.findViewById(R.id.restart_cancel).setOnClickListener(view -> restartDialog.dismiss());
        showStyledDialog(restartDialog);
    }

    boolean showVictory(int moves, String elapsed, Runnable onAgain, Runnable onHome, Runnable onCancel) {
        if (victoryDialog != null && victoryDialog.isShowing()) return false;
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_victory, null);
        ((TextView) content.findViewById(R.id.victory_stats)).setText(
                activity.getResources().getQuantityString(R.plurals.victory_stats, moves, moves, elapsed));
        victoryDialog = new AlertDialog.Builder(activity).setView(content).create();
        victoryDialog.setCanceledOnTouchOutside(false);
        victoryDialog.setOnCancelListener(dialog -> onCancel.run());
        content.findViewById(R.id.victory_again).setOnClickListener(view -> {
            victoryDialog.dismiss();
            onAgain.run();
        });
        content.findViewById(R.id.victory_home).setOnClickListener(view -> {
            victoryDialog.dismiss();
            onHome.run();
        });
        showStyledDialog(victoryDialog);
        View emblem = content.findViewById(R.id.victory_emblem);
        emblem.setScaleX(0.6f);
        emblem.setScaleY(0.6f);
        emblem.setRotation(-20f);
        emblem.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(duration(420)).setInterpolator(new DecelerateInterpolator(1.7f)).start();
        return true;
    }

    void dismissAll() {
        for (AlertDialog dialog : new AlertDialog[]{aboutDialog, historyDialog, restartDialog, victoryDialog}) {
            if (dialog != null) dialog.dismiss();
        }
    }
}
