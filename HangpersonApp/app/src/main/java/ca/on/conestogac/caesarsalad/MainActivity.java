package ca.on.conestogac.caesarsalad;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    private final AppCompatActivity SELF = this;
    private static final String TAG = "MainActivity";
    private final int NUMBER_CHARS = 28;

    final String WORD_WAS = "The word was '%s'.%n";
    final int SNACKBAR_FONT_SIZE = 19;

    private static final int FADE_DELAY = 250;

    private SharedPreferences sharedPref;
    private HangPersonApplication application;
    // variables related to the word to guess
    private final int WORD_LENGTH = 7;
    private final String BASE_GENERATOR_URL = "http://api.wordnik.com/v4/words.json/randomWords";
    private final String CONST_PARAMETERS = "hasDictionaryDef=true&minCorpusCount=0&limit=1";
    private final String API_KEY = "a2a73e7b926c924fad7001ca3111acd55af2ffabf50eb4ae5";
    private final String WORD_GENERATOR_URL = String.format("%s?%s&minLength=%d&maxLength=%d&api_key=%s",
            BASE_GENERATOR_URL, CONST_PARAMETERS, WORD_LENGTH, WORD_LENGTH, API_KEY);
    private String wordToGuess = "";

    final private int DICTIONARY_COUNT = 50;
    private String[] wordDictionary = new String[DICTIONARY_COUNT];

    // variables retrieved from the Settings
    private int maxAttemptsNumber;
    private boolean saveState;

    // variables used to determine whether the game should be restored or reset
    private boolean creatingActivity = false;
    private int screenOrientation;

    // variables used to check if the word is guessed and if there are more attempts left
    private int guessedLettersNumber = 0;
    private int unsuccessfulAttempts = 0;

    // variable to store letters entered by the user
    private ArrayList<Integer> buttonsPressed = new ArrayList<Integer>();

    // variable to block buttons while the animation is being displayed
    private boolean buttonsBlocked = false;

    // variables to connect to the widgets
    TextView textViewInvitation;
    ImageView imageViewResult;
    FloatingActionButton fab;
    TextView[] letterTextViews = new TextView[WORD_LENGTH];
    Button[] letterButtons = new Button[NUMBER_CHARS];

    // dictionary that connects letters to correspondent button ids
    HashMap<Integer, Character> idToChar = new HashMap<Integer, Character>();

    // dictionary that contains ids of all hangman drawings
    HashMap<Integer, Integer> imageToRender = new HashMap<Integer, Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        screenOrientation = getResources().getConfiguration().orientation;

        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        this.application = ((HangPersonApplication) getApplication());
        setContentView(R.layout.activity_main);

//        SharedPreferences settings = getSharedPreferences("lastInputs", MODE_PRIVATE);
//        settings.edit().clear().commit();

        //retrieve shared preferences
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // determine whether we are creating activity (making sure that program doesn't come
        // from reorientation
        boolean temp = sharedPref.getBoolean("fromOrientation", false);
        if (!temp) {
            creatingActivity = true;
        }
        else {
            Editor ed = sharedPref.edit();
            ed.putBoolean("fromOrientation", false);
            ed.commit();
        }

        // initialize widget-connected variables
        initializeVariables();

        // get the word for the user to guess
        getWordToGuess();

        textViewInvitation.setVisibility(View.VISIBLE);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetGame();
            }
        });

        for (Button letterButton : letterButtons) {
            letterButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!buttonsBlocked) {
                        int buttonId = v.getId();

                        if (!buttonsPressed.contains(buttonId)) {
                            // change button color, check letter, display if is in the word, etc.
                            handlePressedButton(buttonId, true);

                            // check if the word is guessed
                            if (guessedLettersNumber == WORD_LENGTH || unsuccessfulAttempts >= maxAttemptsNumber) {
                                View view = findViewById(R.id.mainLayout);
                                if (view == null)
                                {
                                    view = findViewById(R.id.mainLayoutLand);
                                }
                                displaySnackbar(view, guessedLettersNumber == WORD_LENGTH);
                                persistValues(guessedLettersNumber == WORD_LENGTH);
                                resetGame();
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        int orientation = getResources().getConfiguration().orientation;
        Editor ed = sharedPref.edit();
        ed.putString("word", wordToGuess);
        ed.putInt("nbrAttempts", buttonsPressed.size());
        ed.putInt("nbrMaxAttempts", maxAttemptsNumber);
        if (orientation != screenOrientation)
        {
            ed.putBoolean("fromOrientation", true);
            screenOrientation = orientation;
        }
        // save ids of the buttons that were already pressed
        for (int i = 0; i < buttonsPressed.size(); i++) {
            String key = "id" + (i + 1);
            ed.putInt(key, buttonsPressed.get(i));
        }
        ed.commit();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        unsuccessfulAttempts = 0; // if coming from other activities, is not set back to zero by itself -> extras are added
        saveState = sharedPref.getBoolean("saveOnClose", false);
        int maxAttempts = 10 - Integer.parseInt(sharedPref.getString("difficultyLevel", "0"));
        int maxAttemptsSaved = sharedPref.getInt("nbrMaxAttempts", 0);
        boolean difficultyChanged = maxAttempts != maxAttemptsSaved;
        maxAttemptsNumber = maxAttempts;

        // save new maxAttempts value

        // if the difficulty level was changed mid-game, reset the game
        if (!creatingActivity && difficultyChanged) {
            resetGame();
        }
        // else, continue game
        else {
            if (saveState || !creatingActivity) {
                wordToGuess = sharedPref.getString("word", wordToGuess);
                int nbr_attempts = sharedPref.getInt("nbrAttempts", 0);

                if (wordToGuess.equals("")) {
                    wordToGuess = getWordFromDictionary();
                } else if (nbr_attempts != 0) {
                    for (int i = 0; i < nbr_attempts; i++) {
                        String key = "id" + (i + 1);
                        int id = sharedPref.getInt(key, 0);
                        if (id != 0 && !buttonsPressed.contains((id))) {
                            buttonsPressed.add(id);
                        }
                    }
                    restoreGame();
                }
            }
        }
        creatingActivity = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.hangperson_game_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        boolean ret = true;

        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                break;
            case R.id.menu_statistics:
                startActivity(new Intent(getApplicationContext(), StatsActivity.class));
                break;
            default:
                ret = super.onOptionsItemSelected(item);
                break;
        }
        return ret;
    }

    private void resetGame() {
        buttonsPressed.clear();
        imageViewResult.setVisibility(View.INVISIBLE);
        for (TextView letterView : letterTextViews) {
            letterView.setText(R.string.emptyLetter);
        }
        for (Button letterButton : letterButtons) {
            letterButton.setBackgroundResource(android.R.drawable.btn_default);
        }
        textViewInvitation.setText(R.string.invitation);
        textViewInvitation.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
        getWordToGuess();

        guessedLettersNumber = 0;
        unsuccessfulAttempts = 0;
    }

    private void restoreGame() {
        for (Integer buttonId : buttonsPressed) {
            handlePressedButton(buttonId, false);
        }
        if (unsuccessfulAttempts > 0) {
            int index = maxAttemptsNumber == 10 ? unsuccessfulAttempts :
                    maxAttemptsNumber == 9 ? unsuccessfulAttempts + 1 :
                            unsuccessfulAttempts + 2;
            imageViewResult.setImageResource(imageToRender.get(index));
            imageViewResult.setVisibility(View.VISIBLE);
        }
    }

    private void handlePressedButton(Integer buttonId, boolean fromOnClick) {
        View v = findViewById(buttonId);
        v.setBackgroundResource(R.color.colorButtonPressed);
        if (!buttonsPressed.contains(buttonId)) {
            buttonsPressed.add(buttonId);
        }

        char letter = idToChar.get(buttonId);

        // if the word contains this letter, display happy face and the letter(s) in the word
        if (wordToGuess.contains("" + letter)) {
            imageViewResult.setImageResource(R.drawable.ic_happy_face);
            textViewInvitation.setText(R.string.correct_guess);
            textViewInvitation.setTextColor(Color.GREEN);
            displayGuessedLetter(letter);
        }
        //display sad face and add the letter to the list of wrong letters
        else {
            imageViewResult.setImageResource(R.drawable.ic_sad_face);
            textViewInvitation.setText(R.string.incorrect_guess);
            textViewInvitation.setTextColor(Color.RED);
            unsuccessfulAttempts++;
//            if (creatingActivity || fromOnClick) {
//                unsuccessfulAttempts++;
//            }
        }
        if (fromOnClick) {
            buttonsBlocked = true; // block buttons to prevent entries while animation is being displayed
            imageViewResult.setAlpha(0f);
            imageViewResult.setVisibility(View.VISIBLE);
            imageViewResult.animate().alpha(1f).setDuration(FADE_DELAY).setListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finishAnimation();
                        }
                    }
            );
        }
    }

    private void finishAnimation() {
        imageViewResult.animate().alpha(0f).setDuration(FADE_DELAY).setListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (unsuccessfulAttempts > 0) {
                            int index = maxAttemptsNumber == 10 ? unsuccessfulAttempts :
                                    maxAttemptsNumber == 9 ? unsuccessfulAttempts + 1 :
                                            unsuccessfulAttempts + 2;
                            imageViewResult.setImageResource(imageToRender.get(index));
                            imageViewResult.setAlpha(1.0f);
                        }
                        buttonsBlocked = false;
                    }
                }
        );
    }

    private void displayGuessedLetter(char letter) {
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (wordToGuess.charAt(i) == letter) {
                letterTextViews[i].setText(Character.toString(letter));
                guessedLettersNumber++;
            }
        }
    }

    private void displaySnackbar(View v, boolean win) {
        String message = win ? String.format(WORD_WAS, wordToGuess) +
                getResources().getString(R.string.win_message) :
                String.format(WORD_WAS, wordToGuess) + getResources().getString(R.string.lost_message);

        try {
            // make snackbar
            Snackbar snackbar = Snackbar.make(v, message, Snackbar.LENGTH_SHORT);
            // get snackbar view
            View snackBarView = snackbar.getView();
            // set snackbar color
            if (win) {
                snackBarView.setBackgroundColor(getResources().getColor(R.color.snackbarWin));
            } else {
                snackBarView.setBackgroundColor(getResources().getColor(R.color.snackbarDefeat));
            }

            // get textview inside snackbar view
            TextView textView = (TextView) snackBarView.findViewById(R.id.snackbar_text);
            // set text to center
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            } else {
                textView.setGravity(Gravity.CENTER_HORIZONTAL);
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SNACKBAR_FONT_SIZE);
            textView.setLineSpacing(0.0f, 1.5f);
            // show the snackbar
            snackbar.show();
        } catch (Exception e) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

    }

    private void getWordToGuess() {
        wordToGuess = "";
        // check connectivity, and if it's here, retrieve the word from the internet
        final ConnectivityManager connManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            Thread getWord = new Thread(new Runnable() {
                @Override
                public void run() {
                    // some words that we pull from API contain characters that we don't have
                    // in our buttons -> need to make sure we don't use them
                    do {
                        wordToGuess = getWordFromAPI();
                    } while (!validateWord(wordToGuess));
                }
            });
            getWord.start();
            try {
                getWord.join();
            } catch (InterruptedException ex) {
                Log.d(TAG, ex.toString());
            }
        }
        // if the word was not retrieved form the api, use our inner library to select a word
        if (wordToGuess.equals("")) {
            wordToGuess = getWordFromDictionary();
        }
    }

    private String getWordFromAPI() {
        String word = "";
        try {
            URL url = new URL(WORD_GENERATOR_URL);
            InputStream in = url.openStream();

            Scanner s = new Scanner(in).useDelimiter("\\A");
            String result = s.hasNext()
                    ? s.next()
                    : "";
            word = new JSONArray(result)
                    .getJSONObject(0)
                    .getString("word");
        } catch (MalformedURLException ex) {
            Log.d(TAG, ex.toString());
        } catch (IOException ex) {
            Log.d(TAG, ex.toString());
        } catch (JSONException ex) {
            Log.d(TAG, ex.toString());
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        } finally {
            return word;
        }
    }

    private String getWordFromDictionary() {
        //int rnd = new Random().nextInt(DICTIONARY_COUNT);
        int rnd = (int) (System.currentTimeMillis() % DICTIONARY_COUNT);
        return wordDictionary[rnd];
    }

    private boolean validateWord(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!idToChar.values().contains(c)) {
                return false;
            }
        }
        return true;
    }

    private void initializeVariables() {
        imageViewResult = findViewById(R.id.imageViewResult);
        textViewInvitation = findViewById(R.id.textViewInvitation);
        fab = findViewById(R.id.fab);

        letterTextViews[0] = findViewById(R.id.textViewLetter1);
        letterTextViews[1] = findViewById(R.id.textViewLetter2);
        letterTextViews[2] = findViewById(R.id.textViewLetter3);
        letterTextViews[3] = findViewById(R.id.textViewLetter4);
        letterTextViews[4] = findViewById(R.id.textViewLetter5);
        letterTextViews[5] = findViewById(R.id.textViewLetter6);
        letterTextViews[6] = findViewById(R.id.textViewLetter7);

        letterButtons[0] = findViewById(R.id.buttonLetterA);
        letterButtons[1] = findViewById(R.id.buttonLetterB);
        letterButtons[2] = findViewById(R.id.buttonLetterC);
        letterButtons[3] = findViewById(R.id.buttonLetterD);
        letterButtons[4] = findViewById(R.id.buttonLetterE);
        letterButtons[5] = findViewById(R.id.buttonLetterF);
        letterButtons[6] = findViewById(R.id.buttonLetterG);
        letterButtons[7] = findViewById(R.id.buttonLetterH);
        letterButtons[8] = findViewById(R.id.buttonLetterI);
        letterButtons[9] = findViewById(R.id.buttonLetterJ);
        letterButtons[10] = findViewById(R.id.buttonLetterK);
        letterButtons[11] = findViewById(R.id.buttonLetterL);
        letterButtons[12] = findViewById(R.id.buttonLetterM);
        letterButtons[13] = findViewById(R.id.buttonLetterN);
        letterButtons[14] = findViewById(R.id.buttonLetterO);
        letterButtons[15] = findViewById(R.id.buttonLetterP);
        letterButtons[16] = findViewById(R.id.buttonLetterQ);
        letterButtons[17] = findViewById(R.id.buttonLetterR);
        letterButtons[18] = findViewById(R.id.buttonLetterS);
        letterButtons[19] = findViewById(R.id.buttonLetterT);
        letterButtons[20] = findViewById(R.id.buttonLetterU);
        letterButtons[21] = findViewById(R.id.buttonLetterV);
        letterButtons[22] = findViewById(R.id.buttonLetterW);
        letterButtons[23] = findViewById(R.id.buttonLetterX);
        letterButtons[24] = findViewById(R.id.buttonLetterY);
        letterButtons[25] = findViewById(R.id.buttonLetterZ);
        letterButtons[26] = findViewById(R.id.buttonLetterDash);
        letterButtons[27] = findViewById(R.id.buttonLetterApostrophe);

        idToChar.put(R.id.buttonLetterA, 'a');
        idToChar.put(R.id.buttonLetterB, 'b');
        idToChar.put(R.id.buttonLetterC, 'c');
        idToChar.put(R.id.buttonLetterD, 'd');
        idToChar.put(R.id.buttonLetterE, 'e');
        idToChar.put(R.id.buttonLetterF, 'f');
        idToChar.put(R.id.buttonLetterG, 'g');
        idToChar.put(R.id.buttonLetterH, 'h');
        idToChar.put(R.id.buttonLetterI, 'i');
        idToChar.put(R.id.buttonLetterJ, 'j');
        idToChar.put(R.id.buttonLetterK, 'k');
        idToChar.put(R.id.buttonLetterL, 'l');
        idToChar.put(R.id.buttonLetterM, 'm');
        idToChar.put(R.id.buttonLetterN, 'n');
        idToChar.put(R.id.buttonLetterO, 'o');
        idToChar.put(R.id.buttonLetterP, 'p');
        idToChar.put(R.id.buttonLetterQ, 'q');
        idToChar.put(R.id.buttonLetterR, 'r');
        idToChar.put(R.id.buttonLetterS, 's');
        idToChar.put(R.id.buttonLetterT, 't');
        idToChar.put(R.id.buttonLetterU, 'u');
        idToChar.put(R.id.buttonLetterV, 'v');
        idToChar.put(R.id.buttonLetterW, 'w');
        idToChar.put(R.id.buttonLetterX, 'x');
        idToChar.put(R.id.buttonLetterY, 'y');
        idToChar.put(R.id.buttonLetterZ, 'z');
        idToChar.put(R.id.buttonLetterDash, '-');
        idToChar.put(R.id.buttonLetterApostrophe, '\'');

        imageToRender.put(1, R.drawable.ic_01);
        imageToRender.put(2, R.drawable.ic_02);
        imageToRender.put(3, R.drawable.ic_03);
        imageToRender.put(4, R.drawable.ic_04);
        imageToRender.put(5, R.drawable.ic_05);
        imageToRender.put(6, R.drawable.ic_06);
        imageToRender.put(7, R.drawable.ic_07);
        imageToRender.put(8, R.drawable.ic_08);
        imageToRender.put(9, R.drawable.ic_09);
        imageToRender.put(10, R.drawable.ic_10);

        wordDictionary[0] = "miracle";
        wordDictionary[1] = "subject";
        wordDictionary[2] = "biology";
        wordDictionary[3] = "biscuit";
        wordDictionary[4] = "chapter";
        wordDictionary[5] = "college";
        wordDictionary[6] = "surgeon";
        wordDictionary[7] = "revival";
        wordDictionary[8] = "program";
        wordDictionary[9] = "formula";
        wordDictionary[10] = "athlete";
        wordDictionary[11] = "mystery";
        wordDictionary[12] = "trainer";
        wordDictionary[13] = "receipt";
        wordDictionary[14] = "comment";
        wordDictionary[15] = "gesture";
        wordDictionary[16] = "meeting";
        wordDictionary[17] = "gravity";
        wordDictionary[18] = "leaflet";
        wordDictionary[19] = "sunrise";
        wordDictionary[20] = "feature";
        wordDictionary[21] = "comfort";
        wordDictionary[22] = "episode";
        wordDictionary[23] = "context";
        wordDictionary[24] = "auditor";
        wordDictionary[25] = "product";
        wordDictionary[26] = "bedroom";
        wordDictionary[27] = "anxiety";
        wordDictionary[28] = "symptom";
        wordDictionary[29] = "venture";
        wordDictionary[30] = "courage";
        wordDictionary[31] = "mixture";
        wordDictionary[32] = "urgency";
        wordDictionary[33] = "council";
        wordDictionary[34] = "control";
        wordDictionary[35] = "license";
        wordDictionary[36] = "warning";
        wordDictionary[37] = "pyramid";
        wordDictionary[38] = "monster";
        wordDictionary[39] = "penalty";
        wordDictionary[40] = "feather";
        wordDictionary[41] = "volcano";
        wordDictionary[42] = "crevice";
        wordDictionary[43] = "variety";
        wordDictionary[44] = "pension";
        wordDictionary[45] = "vehicle";
        wordDictionary[46] = "emotion";
        wordDictionary[47] = "welfare";
        wordDictionary[48] = "haircut";
        wordDictionary[49] = "climate";
    }

    private void persistValues(Boolean hasWon) {
        int winner = hasWon ? 1 : -1;
        SQLiteDatabase db = this.application.getSQLiteOpenHelper().getWritableDatabase();
        db.execSQL("INSERT INTO stats (winner, nGoodGuess, nBadGuess) VALUES(" + winner + "," + this.guessedLettersNumber + "," + this.unsuccessfulAttempts + ")");
    }
}