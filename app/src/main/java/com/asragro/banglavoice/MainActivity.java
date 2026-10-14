package com.asragro.banglavoice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private static final int GREEN_DARK = Color.rgb(4, 66, 48);
    private static final int GREEN = Color.rgb(25, 190, 102);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int ORANGE = Color.rgb(245, 158, 11);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final String PREFS = "bangla_voice_board_v2";
    private static final String KEY_TEXT = "current_text";
    private static final String KEY_FONT = "font_size";
    private static final String KEY_HISTORY = "history_json";
    private static final String KEY_MODE = "language_mode";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> undoStack = new ArrayList<>();
    private final ArrayList<String> history = new ArrayList<>();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private SharedPreferences prefs;
    private boolean wantListening = false;
    private boolean recognitionRunning = false;
    private boolean fullscreen = false;
    private String finalText = "";
    private String lastCommitted = "";
    private long lastCommitAt = 0L;
    private float manualFontSize = 52f;
    private int languageMode = 0; // 0=Bangla, 1=Bangla+English, 2=English

    private TextView statusText;
    private TextView finalView;
    private TextView partialView;
    private ScrollView boardScroll;
    private Button startButton;
    private Button languageButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(GREEN_DARK);
        getWindow().setNavigationBarColor(Color.rgb(3, 45, 34));

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        finalText = prefs.getString(KEY_TEXT, "");
        manualFontSize = prefs.getFloat(KEY_FONT, 52f);
        languageMode = prefs.getInt(KEY_MODE, 0);
        loadHistory();

        setContentView(buildUi());
        setupRecognizer();
        refreshBoard();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(14), dp(12), dp(12));
        root.setBackgroundColor(GREEN_DARK);

        TextView title = new TextView(this);
        title.setText("🎙️ বাংলা ভয়েস বোর্ড AI V2");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lpMatchWrap());

        TextView sub = new TextView(this);
        sub.setText("বলুন — বড় অক্ষরে লেখা • Auto Save • Voice Command");
        sub.setTextColor(Color.rgb(201, 235, 220));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = lpMatchWrap();
        subLp.setMargins(0, dp(4), 0, dp(7));
        root.addView(sub, subLp);

        statusText = new TextView(this);
        statusText.setText("● মাইক্রোফোন বন্ধ");
        statusText.setTextColor(Color.rgb(218, 245, 231));
        statusText.setTextSize(13);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statLp = lpMatchWrap();
        statLp.setMargins(0, 0, 0, dp(8));
        root.addView(statusText, statLp);

        boardScroll = new ScrollView(this);
        boardScroll.setFillViewport(true);
        boardScroll.setBackground(roundRect(Color.WHITE, 24));
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins(0, 0, 0, dp(9));

        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setGravity(Gravity.CENTER);
        board.setPadding(dp(18), dp(20), dp(18), dp(20));

        finalView = new TextView(this);
        finalView.setTextColor(Color.rgb(18, 31, 25));
        finalView.setTextSize(manualFontSize);
        finalView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        finalView.setGravity(Gravity.CENTER);
        finalView.setTextIsSelectable(true);
        finalView.setLineSpacing(dp(4), 1.08f);
        board.addView(finalView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        partialView = new TextView(this);
        partialView.setText("");
        partialView.setTextColor(Color.rgb(111, 127, 119));
        partialView.setTextSize(25);
        partialView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        partialView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams partialLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        partialLp.setMargins(0, dp(14), 0, 0);
        board.addView(partialView, partialLp);

        boardScroll.addView(board);
        root.addView(boardScroll, boardLp);

        LinearLayout row1 = buttonRow();
        startButton = actionButton("🎤 শুরু করুন", GREEN);
        Button stop = actionButton("⏹ বন্ধ করুন", RED);
        row1.addView(startButton, weightedButtonLp(true, 55));
        row1.addView(stop, weightedButtonLp(false, 55));
        root.addView(row1, lpMatchWrap());

        LinearLayout row2 = buttonRow();
        Button clear = actionButton("🧹 সব মুছুন", ORANGE);
        Button copy = actionButton("📋 কপি", Color.WHITE);
        copy.setTextColor(GREEN_DARK);
        row2.addView(clear, weightedButtonLp(true, 50));
        row2.addView(copy, weightedButtonLp(false, 50));
        LinearLayout.LayoutParams row2Lp = lpMatchWrap();
        row2Lp.setMargins(0, dp(7), 0, 0);
        root.addView(row2, row2Lp);

        LinearLayout row3 = buttonRow();
        Button fontMinus = smallButton("A− ছোট");
        Button fontPlus = smallButton("A+ বড়");
        Button undo = smallButton("↶ Undo");
        row3.addView(fontMinus, weightedThirdLp(0));
        row3.addView(fontPlus, weightedThirdLp(1));
        row3.addView(undo, weightedThirdLp(2));
        LinearLayout.LayoutParams row3Lp = lpMatchWrap();
        row3Lp.setMargins(0, dp(7), 0, 0);
        root.addView(row3, row3Lp);

        LinearLayout row4 = buttonRow();
        Button share = smallButton("↗ Share");
        Button whatsapp = smallButton("🟢 WhatsApp");
        Button historyBtn = smallButton("🕘 History");
        row4.addView(share, weightedThirdLp(0));
        row4.addView(whatsapp, weightedThirdLp(1));
        row4.addView(historyBtn, weightedThirdLp(2));
        LinearLayout.LayoutParams row4Lp = lpMatchWrap();
        row4Lp.setMargins(0, dp(7), 0, 0);
        root.addView(row4, row4Lp);

        LinearLayout row5 = buttonRow();
        languageButton = smallButton(languageLabel());
        Button newline = smallButton("↵ নতুন লাইন");
        Button full = smallButton("⛶ Full");
        row5.addView(languageButton, weightedThirdLp(0));
        row5.addView(newline, weightedThirdLp(1));
        row5.addView(full, weightedThirdLp(2));
        LinearLayout.LayoutParams row5Lp = lpMatchWrap();
        row5Lp.setMargins(0, dp(7), 0, 0);
        root.addView(row5, row5Lp);

        startButton.setOnClickListener(v -> startRequested());
        stop.setOnClickListener(v -> stopListening());
        clear.setOnClickListener(v -> clearBoard(true));
        copy.setOnClickListener(v -> copyText());
        fontMinus.setOnClickListener(v -> changeFont(-4));
        fontPlus.setOnClickListener(v -> changeFont(4));
        undo.setOnClickListener(v -> undoLast());
        share.setOnClickListener(v -> shareText(false));
        whatsapp.setOnClickListener(v -> shareText(true));
        historyBtn.setOnClickListener(v -> showHistory());
        languageButton.setOnClickListener(v -> cycleLanguageMode());
        newline.setOnClickListener(v -> addNewLine());
        full.setOnClickListener(v -> toggleFullscreen());

        return root;
    }

    private void setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("⚠️ এই ফোনে Speech Recognition service পাওয়া যায়নি");
            startButton.setEnabled(false);
            startButton.setAlpha(0.55f);
            return;
        }

        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                recognitionRunning = true;
                statusText.setText(languageMode == 2 ? "🟢 Listening…" : "🟢 শুনছি… পরিষ্কার করে বলুন");
            }
            @Override public void onBeginningOfSpeech() {
                statusText.setText(languageMode == 2 ? "🎧 Recognizing speech…" : "🎧 কথা শনাক্ত করছি…");
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                statusText.setText(languageMode == 2 ? "⏳ Creating text…" : "⏳ লেখা তৈরি হচ্ছে…");
            }
            @Override public void onError(int error) {
                recognitionRunning = false;
                partialView.setText("");
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    wantListening = false;
                    statusText.setText("⚠️ Microphone permission দিন");
                    return;
                }
                if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    statusText.setText("⚠️ Internet/Speech service সমস্যা — আবার চেষ্টা করছি");
                } else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    statusText.setText("🟡 বুঝিনি — আবার শুনছি");
                } else {
                    statusText.setText("🟡 আবার শুনছি…");
                }
                scheduleRestart(650);
            }
            @Override public void onResults(Bundle results) {
                recognitionRunning = false;
                String best = chooseBest(results);
                if (!best.isEmpty()) {
                    if (!handleVoiceCommand(best)) commit(best);
                }
                partialView.setText("");
                scheduleRestart(450);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                String best = chooseBest(partialResults);
                partialView.setText(best);
                if (!best.isEmpty()) statusText.setText("🟢 শুনছি…");
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        rebuildRecognizerIntent();
    }

    private void rebuildRecognizerIntent() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String lang = languageMode == 2 ? "en-US" : "bn-BD";
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                languageMode == 2 ? "Speak clearly" : "বাংলায় পরিষ্কার করে বলুন");
        recognizerIntent.putExtra("android.speech.extra.DICTATION_MODE", true);
    }

    private void startRequested() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        wantListening = true;
        startListeningNow();
    }

    private void startListeningNow() {
        if (!wantListening || recognizer == null || recognitionRunning) return;
        try {
            recognitionRunning = true;
            statusText.setText("🟢 মাইক্রোফোন চালু হচ্ছে…");
            recognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            recognitionRunning = false;
            statusText.setText("⚠️ Mic চালু হয়নি — আবার চেষ্টা করুন");
            scheduleRestart(900);
        }
    }

    private void scheduleRestart(long delay) {
        if (!wantListening) {
            statusText.setText("● মাইক্রোফোন বন্ধ");
            return;
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startListeningNow, delay);
    }

    private String chooseBest(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return "";
        float[] confidence = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

        String best = list.get(0);
        double bestScore = scoreCandidate(best, confidence != null && confidence.length > 0 ? confidence[0] : -1f);
        for (int i = 1; i < list.size(); i++) {
            float conf = (confidence != null && i < confidence.length) ? confidence[i] : -1f;
            double score = scoreCandidate(list.get(i), conf);
            if (score > bestScore) {
                bestScore = score;
                best = list.get(i);
            }
        }
        return normalize(best);
    }

    private double scoreCandidate(String text, float confidence) {
        if (text == null || text.trim().isEmpty()) return -999;
        int bn = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0980' && c <= '\u09FF') bn++;
        }
        double bnRatio = (double) bn / Math.max(1, text.length());
        double conf = confidence >= 0 ? confidence : 0.50;
        double languageBonus = languageMode == 0 ? Math.min(0.25, bnRatio * 0.25)
                : languageMode == 1 ? Math.min(0.08, bnRatio * 0.08) : 0;
        return conf + languageBonus + Math.min(0.06, text.length() / 300.0);
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").replaceAll("\\s+([।,!?])", "$1");
    }

    private boolean handleVoiceCommand(String raw) {
        String t = normalize(raw).toLowerCase(Locale.ROOT);
        t = t.replace("।", "").replace("?", "").replace("!", "").trim();

        if (t.equals("সব মুছুন") || t.equals("সব মুছে দাও") || t.equals("লেখা মুছুন") ||
                t.equals("ক্লিয়ার") || t.equals("ক্লিয়ার") || t.equals("clear")) {
            clearBoard(true);
            toast("ভয়েস কমান্ড: সব মুছে দেওয়া হয়েছে");
            return true;
        }
        if (t.equals("নতুন লাইন") || t.equals("নিউ লাইন") || t.equals("new line")) {
            addNewLine();
            toast("ভয়েস কমান্ড: নতুন লাইন");
            return true;
        }
        if (t.equals("কপি করো") || t.equals("কপি করুন") || t.equals("copy")) {
            copyText();
            return true;
        }
        if (t.equals("শেয়ার করো") || t.equals("শেয়ার করো") || t.equals("share")) {
            shareText(false);
            return true;
        }
        return false;
    }

    private void commit(String text) {
        text = normalize(text);
        if (text.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (text.equalsIgnoreCase(lastCommitted) && now - lastCommitAt < 5000) return;

        pushUndo();
        if (languageMode != 2 && !text.matches(".*[।!?]$")) text = text + "।";
        else if (languageMode == 2 && !text.matches(".*[.!?]$")) text = text + ".";

        finalText = finalText.isEmpty() ? text : finalText + " " + text;
        lastCommitted = normalize(text);
        lastCommitAt = now;
        saveCurrent();
        addHistory(finalText);
        refreshBoard();
        boardScroll.post(() -> boardScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void refreshBoard() {
        if (finalView == null) return;
        if (finalText.trim().isEmpty()) {
            finalView.setText(languageMode == 2 ? "Speak now…" : "কথা বলুন…");
        } else {
            finalView.setText(finalText);
        }
        finalView.setTextSize(manualFontSize);
    }

    private void changeFont(float delta) {
        manualFontSize = Math.max(24f, Math.min(76f, manualFontSize + delta));
        finalView.setTextSize(manualFontSize);
        prefs.edit().putFloat(KEY_FONT, manualFontSize).apply();
        toast("Font: " + (int) manualFontSize);
    }

    private void pushUndo() {
        if (undoStack.size() >= 20) undoStack.remove(0);
        undoStack.add(finalText);
    }

    private void undoLast() {
        if (undoStack.isEmpty()) {
            toast("Undo করার মতো কিছু নেই");
            return;
        }
        finalText = undoStack.remove(undoStack.size() - 1);
        lastCommitted = "";
        saveCurrent();
        refreshBoard();
    }

    private void stopListening() {
        wantListening = false;
        handler.removeCallbacksAndMessages(null);
        partialView.setText("");
        recognitionRunning = false;
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) { }
        }
        statusText.setText("● মাইক্রোফোন বন্ধ");
    }

    private void clearBoard(boolean keepHistory) {
        if (!finalText.trim().isEmpty()) {
            pushUndo();
            if (keepHistory) addHistory(finalText);
        }
        finalText = "";
        lastCommitted = "";
        partialView.setText("");
        saveCurrent();
        refreshBoard();
    }

    private void addNewLine() {
        if (!finalText.isEmpty()) {
            pushUndo();
            finalText += "\n";
            saveCurrent();
            refreshBoard();
        }
    }

    private void copyText() {
        if (finalText.isEmpty()) {
            toast("কপি করার মতো লেখা নেই");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Bangla Voice Text", finalText));
        toast("লেখা কপি হয়েছে");
    }

    private void shareText(boolean whatsappOnly) {
        if (finalText.trim().isEmpty()) {
            toast("Share করার মতো লেখা নেই");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, finalText);
        if (whatsappOnly) send.setPackage("com.whatsapp");
        try {
            startActivity(whatsappOnly ? send : Intent.createChooser(send, "লেখা শেয়ার করুন"));
        } catch (Exception e) {
            if (whatsappOnly) {
                Intent fallback = new Intent(Intent.ACTION_SEND);
                fallback.setType("text/plain");
                fallback.putExtra(Intent.EXTRA_TEXT, finalText);
                startActivity(Intent.createChooser(fallback, "লেখা শেয়ার করুন"));
            }
        }
    }

    private void cycleLanguageMode() {
        boolean wasListening = wantListening;
        stopListening();
        languageMode = (languageMode + 1) % 3;
        prefs.edit().putInt(KEY_MODE, languageMode).apply();
        languageButton.setText(languageLabel());
        rebuildRecognizerIntent();
        refreshBoard();
        toast(languageMode == 0 ? "বাংলা মোড" : languageMode == 1 ? "বাংলা + English মোড" : "English mode");
        if (wasListening) {
            wantListening = true;
            handler.postDelayed(this::startListeningNow, 500);
        }
    }

    private String languageLabel() {
        if (languageMode == 0) return "🇧🇩 বাংলা";
        if (languageMode == 1) return "🌐 BN+EN";
        return "🇬🇧 English";
    }

    private void saveCurrent() {
        prefs.edit().putString(KEY_TEXT, finalText).apply();
    }

    private void loadHistory() {
        history.clear();
        String raw = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty()) history.add(s);
            }
        } catch (Exception ignored) { }
    }

    private void addHistory(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return;
        if (!history.isEmpty() && history.get(history.size() - 1).equals(t)) return;
        history.add(t);
        while (history.size() > 15) history.remove(0);
        JSONArray arr = new JSONArray();
        for (String s : history) arr.put(s);
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void showHistory() {
        if (history.isEmpty()) {
            toast("History এখনো খালি");
            return;
        }
        String[] items = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            String s = history.get(history.size() - 1 - i).replace("\n", " ");
            items[i] = s.length() > 70 ? s.substring(0, 70) + "…" : s;
        }
        new AlertDialog.Builder(this)
                .setTitle("🕘 Auto-saved History")
                .setItems(items, (dialog, which) -> {
                    pushUndo();
                    finalText = history.get(history.size() - 1 - which);
                    saveCurrent();
                    refreshBoard();
                    toast("History restore হয়েছে");
                })
                .setNegativeButton("বন্ধ", null)
                .show();
    }

    private void toggleFullscreen() {
        View decor = getWindow().getDecorView();
        fullscreen = !fullscreen;
        if (fullscreen) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                wantListening = true;
                startListeningNow();
            } else {
                statusText.setText("⚠️ Microphone permission ছাড়া কাজ করবে না");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrent();
        if (!finalText.trim().isEmpty()) addHistory(finalText);
    }

    @Override
    protected void onDestroy() {
        wantListening = false;
        handler.removeCallbacksAndMessages(null);
        saveCurrent();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button actionButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundRect(color, 16));
        b.setPadding(dp(7), dp(8), dp(7), dp(8));
        return b;
    }

    private Button smallButton(String text) {
        Button b = actionButton(text, Color.rgb(18, 96, 72));
        b.setTextSize(12);
        return b;
    }

    private LinearLayout.LayoutParams weightedButtonLp(boolean first, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(height), 1f);
        if (first) lp.setMargins(0, 0, dp(4), 0);
        else lp.setMargins(dp(4), 0, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightedThirdLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        if (index == 0) lp.setMargins(0, 0, dp(3), 0);
        else if (index == 1) lp.setMargins(dp(3), 0, dp(3), 0);
        else lp.setMargins(dp(3), 0, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
