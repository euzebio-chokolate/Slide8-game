package com.example.slide8;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** SQLite nativo. Todas as operações e o fechamento usam a mesma fila fora da UI. */
final class GameHistory {
    private final Database database;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Cria o acesso assíncrono ao histórico privado do aplicativo. */
    GameHistory(Context context) {
        database = new Database(context.getApplicationContext());
    }

    static final class Match {
        final String id;
        final long completedAt;
        final int moves;
        final long elapsedMillis;

        /** Guarda os dados de uma partida concluída. */
        Match(String id, long completedAt, int moves, long elapsedMillis) {
            this.id = id;
            this.completedAt = completedAt;
            this.moves = moves;
            this.elapsedMillis = elapsedMillis;
        }
    }

    /** Insere uma vitória na fila de escrita e retorna o resultado na thread principal. */
    void save(Match match, Consumer<Boolean> callback) {
        worker.execute(() -> {
            boolean saved;
            try {
                // O mesmo ID pode ser reenviado após recriação sem duplicar a vitória.
                database.getWritableDatabase().execSQL(
                        "INSERT OR IGNORE INTO matches (id, completed_at, moves, elapsed_millis) VALUES (?, ?, ?, ?)",
                        new Object[]{match.id, match.completedAt, match.moves, match.elapsedMillis});
                saved = true;
            } catch (SQLiteException error) {
                Log.e("GameHistory", "Não foi possível salvar a partida", error);
                saved = false;
            }
            boolean result = saved;
            main.post(() -> callback.accept(result));
        });
    }

    // null sinaliza falha; lista vazia representa um histórico sem vitórias.
    /** Lê as vitórias em ordem da mais recente para a mais antiga. */
    void load(Consumer<List<Match>> callback) {
        worker.execute(() -> {
            List<Match> matches = new ArrayList<>();
            try (Cursor cursor = database.getReadableDatabase().query("matches",
                    new String[]{"id", "completed_at", "moves", "elapsed_millis"},
                    null, null, null, null, "completed_at DESC, rowid DESC")) {
                while (cursor.moveToNext()) {
                    matches.add(new Match(cursor.getString(0), cursor.getLong(1),
                            cursor.getInt(2), cursor.getLong(3)));
                }
            } catch (SQLiteException error) {
                Log.e("GameHistory", "Não foi possível ler o histórico", error);
                main.post(() -> callback.accept(null));
                return;
            }
            main.post(() -> callback.accept(matches));
        });
    }

    /** Fecha o banco depois das tarefas já enfileiradas. */
    void close() {
        // Não interrompe uma gravação quando a Activity é recriada ou encerrada.
        worker.execute(database::close);
        worker.shutdown();
    }

    private static final class Database extends SQLiteOpenHelper {
        /** Inicializa o banco e sua tabela de partidas. */
        Database(Context context) {
            super(context, "slide8_history.db", null, 1);
        }

        /** Cria a tabela para registrar as partidas concluídas. */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE matches (id TEXT PRIMARY KEY NOT NULL, "
                    + "completed_at INTEGER NOT NULL, moves INTEGER NOT NULL CHECK (moves >= 0), "
                    + "elapsed_millis INTEGER NOT NULL CHECK (elapsed_millis >= 0))");
        }

        /** Preserva o histórico se a versão do esquema precisar ser atualizada. */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Novas versões deverão migrar os registros, nunca apagar o histórico.
            throw new SQLiteException("Migração não implementada: " + oldVersion + " -> " + newVersion);
        }
    }
}
