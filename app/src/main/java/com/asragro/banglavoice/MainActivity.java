package com.asragro.banglavoice;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private static final int GREEN_DARK = Color.rgb(4, 66, 48);
    private static final int GREEN = Color.rgb(25, 190, 102);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int ORANGE = Color.rgb(245, 158, 11);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean wantListening = false;
    private boolean recognitionRunning = false;
    private String finalText = "";
    private String lastCommitted = "";
    private long lastCommitAt = 0L;

    private TextView statusText;
    private TextView finalView;
    private TextView partialView;
    private ScrollView boardScroll;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(GREEN_DARK);
        getWindow().setNavigationBarColor(Color.rgb(3, 45, 34));
        setContentView(buildUi());
        setupRecognizer();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(14));
        root.setBackgroundColor(GREEN_DARK);

        TextView title = new TextView(this);
        title.setText("🎙️ বাংলা ভয়েস বোর্ড AI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lpMatchWrap());

        TextView sub = new TextView(this);
        sub.setText("আপনি বাংলায় বলুন — বড় অক্ষরে লেখা উঠবে");
        sub.setTextColor(Color.rgb(201, 235, 220));
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = lpMatchWrap();
        subLp.setMargins(0, dp(5), 0, dp(10));
        root.addView(sub, subLp);

        statusText = new TextView(this);
        statusText.setText("● মাইক্রোফোন বন্ধ");
        statusText.setTextColor(Color.rgb(218, 245, 231));
        statusText.setTextSize(14);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statLp = lpMatchWrap();
        statLp.setMargins(0, 0, 0, dp(10));
        root.addView(statusText, statLp);

        boardScroll = new ScrollView(this);
        boardScroll.setFillViewport(true);
        boardScroll.setBackground(roundRect(Color.WHITE, 24));
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins(0, 0, 0, dp(12));

        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setGravity(Gravity.CENTER);
        board.setPadding(dp(18), dp(22), dp(18), dp(22));

        finalView = new TextView(this);
        finalView.setText("কথা বলুন…");
        finalView.setTextColor(Color.rgb(18, 31, 25));
        finalView.setTextSize(52);
        finalView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        finalView.setGravity(Gravity.CENTER);
        finalView.setTextIsSelectable(true);
        board.addView(finalView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        partialView = new TextView(this);
        partialView.setText("");
        partialView.setTextColor(Color.rgb(111, 127, 119));
        partialView.setTextSize(28);
        partialView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        partialView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams partialLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        partialLp.setMargins(0, dp(16), 0, 0);
        board.addView(partialView, partialLp);

        boardScroll.addView(board);
        root.addView(boardScroll, boardLp);

        LinearLayout row1 = buttonRow();
        startButton = actionButton("🎤 শুরু করুন", GREEN);
        Button stop = actionButton("⏹ বন্ধ করুন", RED);
        row1.addView(startButton, weightedButtonLp(true));
        row1.addView(stop, weightedButtonLp(false));
        root.addView(row1, lpMatchWrap());

        LinearLayout row2 = buttonRow();
        Button clear = actionButton("🧹 সব মুছুন", ORANGE);
        Button copy = actionButton("📋 কপি", Color.WHITE);
        copy.setTextColor(GREEN_DARK);
        row2.addView(clear, weightedButtonLp(true));
        row2.addView(copy, weightedButtonLp(false));
        LinearLayout.LayoutParams row2Lp = lpMatchWrap();
        row2Lp.setMargins(0, dp(9), 0, 0);
        root.addView(row2, row2Lp);

        LinearLayout row3 = buttonRow();
        Button newline = smallButton("↵ নতুন লাইন");
        Button fullscreen = smallButton("⛶ ফুল স্ক্রিন");
        row3.addView(newline, weightedButtonLp(true));
        row3.addView(fullscreen, weightedButtonLp(false));
        LinearLayout.LayoutParams row3Lp = lpMatchWrap();
        row3Lp.setMargins(0, dp(9), 0, 0);
        root.addView(row3, row3Lp);

        startButton.setOnClickListener(v -> startRequested());
        stop.setOnClickListener(v -> stopListening());
        clear.setOnClickListener(v -> clearBoard());
        copy.setOnClickListener(v -> copyText());
        newline.setOnClickListener(v -> addNewLine());
        fullscreen.setOnClickListener(v -> toggleFullscreen());

        return root;
    }

    private void setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("⚠️ এই ফোনে Speech Recognition service পাওয়া যায়নি");
            startButton.setEnabled(false);
            startButton.setAlpha(0.55f);
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                recognitionRunning = true;
                statusText.setText("🟢 শুনছি… পরিষ্কার করে বাংলায় বলুন");
            }
            @Override public void onBeginningOfSpeech() {
                statusText.setText("🎧 বাংলা কথা শনাক্ত করছি…");
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                statusText.setText("⏳ লেখা তৈরি হচ্ছে…");
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
                    statusText.setText("🟡 কথা বুঝিনি — আবার শুনছি");
                } else {
                    statusText.setText("🟡 আবার শুনছি…");
                }
                scheduleRestart(650);
            }
            @Override public void onResults(Bundle results) {
                recognitionRunning = false;
                String best = chooseBest(results);
                if (!best.isEmpty()) commit(best);
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

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "বাংলায় পরিষ্কার করে বলুন");
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
        return conf + Math.min(0.25, bnRatio * 0.25) + Math.min(0.06, text.length() / 300.0);
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").replaceAll("\\s+([।,!?])", "$1");
    }

    private void commit(String text) {
        text = normalize(text);
        if (text.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (text.equalsIgnoreCase(lastCommitted) && now - lastCommitAt < 5000) return;

        if (!text.matches(".*[।!?]$")) text = text + "।";
        finalText = finalText.isEmpty() ? text : finalText + " " + text;
        lastCommitted = normalize(text);
        lastCommitAt = now;
        finalView.setText(finalText);
        resizeFinalText();
        boardScroll.post(() -> boardScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void resizeFinalText() {
        int n = finalText.length();
        if (n < 55) finalView.setTextSize(52);
        else if (n < 130) finalView.setTextSize(43);
        else if (n < 260) finalView.setTextSize(34);
        else finalView.setTextSize(28);
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

    private void clearBoard() {
        finalText = "";
        lastCommitted = "";
        finalView.setText("কথা বলুন…");
        finalView.setTextSize(52);
        partialView.setText("");
    }

    private void addNewLine() {
        if (!finalText.isEmpty()) {
            finalText += "\n";
            finalView.setText(finalText);
        }
    }

    private void copyText() {
        if (finalText.isEmpty()) {
            Toast.makeText(this, "কপি করার মতো লেখা নেই", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Bangla Voice Text", finalText));
        Toast.makeText(this, "লেখা কপি হয়েছে", Toast.LENGTH_SHORT).show();
    }

    private void toggleFullscreen() {
        View decor = getWindow().getDecorView();
        int current = decor.getSystemUiVisibility();
        boolean full = (current & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
        if (full) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
    protected void onDestroy() {
        wantListening = false;
        handler.removeCallbacksAndMessages(null);
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
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundRect(color, 16));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        return b;
    }

    private Button smallButton(String text) {
        Button b = actionButton(text, Color.rgb(18, 96, 72));
        b.setTextSize(14);
        return b;
    }

    private LinearLayout.LayoutParams weightedButtonLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        if (first) lp.setMargins(0, 0, dp(5), 0);
        else lp.setMargins(dp(5), 0, 0, 0);
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
