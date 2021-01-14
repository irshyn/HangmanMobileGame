package ca.on.conestogac.caesarsalad;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class StatsActivity extends AppCompatActivity {

    private HangPersonApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Init choiceApplication instance
        this.application = ((HangPersonApplication) getApplication());
        // Init the action bar
        this.application.initActionBarWithBackButton(this);
        // Set the content view
        setContentView(R.layout.activity_stats);
        // Set the stats from db
        setStatsFromDB();
        /// Attach reset button listener
        attachResetButtonListener();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        boolean ret = true;

        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                break;
            default:
                ret = super.onOptionsItemSelected(item);
                break;
        }

        return ret;
    }

    private void setStatsFromDB() {
        ((TextView) findViewById(R.id.textViewTotalWon)).setText("" + this.application.getTotalWon());
        ((TextView) findViewById(R.id.textViewTotalLost)).setText("" + this.application.getTotalLost());
        ((TextView) findViewById(R.id.textViewAvgGoodGuesses)).setText("" + this.application.getAverageGoodGuesses());
        ((TextView) findViewById(R.id.textViewAvgBadGuesses)).setText("" + this.application.getAverageBadGuesses());
    }

    private void clearStats() {
        this.application.getSQLiteOpenHelper().getWritableDatabase().execSQL("DELETE FROM stats");
    }

    private void attachResetButtonListener() {
        (findViewById(R.id.buttonReset)).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clearStats();
                        setStatsFromDB();
                    }
                }
        );
    }
}