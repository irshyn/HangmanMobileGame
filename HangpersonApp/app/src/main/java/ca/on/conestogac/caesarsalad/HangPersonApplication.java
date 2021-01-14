package ca.on.conestogac.caesarsalad;

import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class HangPersonApplication extends Application {
    // Database variables
    SQLiteOpenHelper sqLiteOpenHelper;
    Integer DB_VERSION = 1;
    String DB_NAME = "HangPersonDB";

    @Override
    public void onCreate() {
        super.onCreate();
        createDatabase();
    }

    public SQLiteOpenHelper getSQLiteOpenHelper() {
        return this.sqLiteOpenHelper;
    }

    public int getTotalWon() {
        return getTotalFromDB("SELECT COUNT(winner) FROM stats WHERE winner = 1");
    }

    public int getTotalLost() {
        return getTotalFromDB("SELECT COUNT(winner) FROM stats WHERE winner = -1");
    }

    public int getAverageGoodGuesses() {
        return getTotalFromDB("SELECT AVG(nGoodGuess) FROM stats");
    }

    public int getAverageBadGuesses() {
        return getTotalFromDB("SELECT AVG(nBadGuess) FROM stats");
    }

    // Private functions
    private int getTotalFromDB(String query) {
        SQLiteDatabase db = this.sqLiteOpenHelper.getReadableDatabase();
        Cursor winningCursor = db.rawQuery(query, null);
        winningCursor.moveToFirst();
        int count = winningCursor.getInt(0);
        winningCursor.close();
        return count;
    }

    // Private functions
    private int getCountsFromDB(String query) {
        SQLiteDatabase db = this.sqLiteOpenHelper.getReadableDatabase();
        Cursor winningCursor = db.rawQuery(query, null);
        winningCursor.moveToFirst();
        int count = winningCursor.getInt(0);
        winningCursor.close();
        return count;
    }

    private void createDatabase() {
        this.sqLiteOpenHelper = new SQLiteOpenHelper(this, DB_NAME, null, DB_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase sqLiteDatabase) {
                sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS stats(winner INTEGER NOT NULL, nGoodGuess INTEGER NOT NULL, nBadGuess NOT NULL)");
            }

            @Override
            public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
                // No-op
            }
        };
    }

    public void initActionBarWithBackButton(AppCompatActivity appCompatActivity) {
        ActionBar actionBar = appCompatActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

}
