package com.zishu.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ZishuWebBridge {

    private final Activity activity;
    private final WebView webView;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    private static final int REQUEST_RECORD_AUDIO = 1001;

    public ZishuWebBridge(
            Activity activity,
            WebView webView
    ) {

        this.activity = activity;
        this.webView = webView;

        initializeTTS();
        initializeSpeechRecognizer();
    }

    // =========================================================
    // TEXT TO SPEECH
    // =========================================================

    private void initializeTTS() {

        textToSpeech = new TextToSpeech(
                activity,
                new TextToSpeech.OnInitListener() {

                    @Override
                    public void onInit(int status) {

                        if (status == TextToSpeech.SUCCESS) {

                            int languageResult =
                                    textToSpeech.setLanguage(
                                            new Locale("hi", "IN")
                                    );

                            textToSpeech.setSpeechRate(0.95f);
                            textToSpeech.setPitch(1.05f);

                            textToSpeech.setOnUtteranceProgressListener(
                                    new UtteranceProgressListener() {

                                        @Override
                                        public void onStart(
                                                String utteranceId
                                        ) {

                                            sendToJavaScript(
                                                    "ZishuNativeSpeakingStarted()"
                                            );
                                        }

                                        @Override
                                        public void onDone(
                                                String utteranceId
                                        ) {

                                            sendToJavaScript(
                                                    "ZishuNativeSpeakingFinished()"
                                            );
                                        }

                                        @Override
                                        public void onError(
                                                String utteranceId
                                        ) {

                                            sendToJavaScript(
                                                    "ZishuNativeSpeakingError('tts_error')"
                                            );
                                        }
                                    }
                            );

                            if (languageResult ==
                                    TextToSpeech.LANG_MISSING_DATA ||
                                    languageResult ==
                                    TextToSpeech.LANG_NOT_SUPPORTED) {

                                sendToJavaScript(
                                        "ZishuNativeSpeakingError('hindi_tts_unavailable')"
                                );
                            }

                        } else {

                            sendToJavaScript(
                                    "ZishuNativeSpeakingError('tts_init_failed')"
                            );
                        }
                    }
                }
        );
    }

    @JavascriptInterface
    public void speak(String text) {

        if (text == null ||
                text.trim().length() == 0) {
            return;
        }

        if (textToSpeech == null) {

            sendToJavaScript(
                    "ZishuNativeSpeakingError('tts_unavailable')"
            );

            return;
        }

        final String speechText =
                text.trim();

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        try {

                            textToSpeech.stop();

                            int result =
                                    textToSpeech.speak(
                                            speechText,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "ZISHU_UTTERANCE"
                                    );

                            if (result ==
                                    TextToSpeech.ERROR) {

                                sendToJavaScript(
                                        "ZishuNativeSpeakingError('tts_speak_failed')"
                                );
                            }

                        } catch (Exception e) {

                            sendToJavaScript(
                                    "ZishuNativeSpeakingError('" +
                                            escapeJavaScript(
                                                    e.getMessage()
                                            ) +
                                            "')"
                            );
                        }
                    }
                }
        );
    }

    @JavascriptInterface
    public void stopSpeaking() {

        if (textToSpeech == null) {
            return;
        }

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        try {

                            textToSpeech.stop();

                            sendToJavaScript(
                                    "ZishuNativeSpeakingFinished()"
                            );

                        } catch (Exception ignored) {
                        }
                    }
                }
        );
    }

    @JavascriptInterface
    public boolean isSpeaking() {

        if (textToSpeech == null) {
            return false;
        }

        return textToSpeech.isSpeaking();
    }

    // =========================================================
    // SPEECH RECOGNITION
    // =========================================================

    private void initializeSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(
                activity
        )) {

            sendToJavaScript(
                    "ZishuNativeListeningError('recognizer_unavailable')"
            );

            return;
        }

        speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(
                        activity
                );

        speechRecognizer.setRecognitionListener(
                new RecognitionListener() {

                    @Override
                    public void onReadyForSpeech(
                            Bundle params
                    ) {

                        sendToJavaScript(
                                "ZishuNativeListeningStarted()"
                        );
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                    }

                    @Override
                    public void onRmsChanged(
                            float rmsdB
                    ) {
                    }

                    @Override
                    public void onBufferReceived(
                            byte[] buffer
                    ) {
                    }

                    @Override
                    public void onEndOfSpeech() {

                        sendToJavaScript(
                                "ZishuNativeListeningStopped()"
                        );
                    }

                    @Override
                    public void onError(
                            int error
                    ) {

                        sendToJavaScript(
                                "ZishuNativeListeningError('" +
                                        error +
                                        "')"
                        );
                    }

                    @Override
                    public void onResults(
                            Bundle results
                    ) {

                        ArrayList<String> matches =
                                results.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION
                                );

                        if (matches != null &&
                                !matches.isEmpty()) {

                            String transcript =
                                    matches.get(0);

                            if (transcript != null &&
                                    transcript.trim().length() > 0) {

                                sendToJavaScript(
                                        "ZishuNativeResult('" +
                                                escapeJavaScript(
                                                        transcript
                                                ) +
                                                "')"
                                );
                            }
                        }

                        sendToJavaScript(
                                "ZishuNativeListeningStopped()"
                        );
                    }

                    @Override
                    public void onPartialResults(
                            Bundle partialResults
                    ) {
                    }

                    @Override
                    public void onEvent(
                            int eventType,
                            Bundle params
                    ) {
                    }
                }
        );
    }

    @JavascriptInterface
    public void startListening() {

        if (activity.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            activity.requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQUEST_RECORD_AUDIO
            );

            return;
        }

        if (speechRecognizer == null) {

            initializeSpeechRecognizer();

            if (speechRecognizer == null) {

                sendToJavaScript(
                        "ZishuNativeListeningError('recognizer_unavailable')"
                );

                return;
            }
        }

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        try {

                            Intent intent =
                                    new Intent(
                                            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                                    );

                            intent.putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            );

                            intent.putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE,
                                    "hi-IN"
                            );

                            intent.putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                                    "hi-IN"
                            );

                            intent.putExtra(
                                    RecognizerIntent.EXTRA_MAX_RESULTS,
                                    1
                            );

                            intent.putExtra(
                                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                                    false
                            );

                            speechRecognizer.startListening(
                                    intent
                            );

                        } catch (Exception e) {

                            sendToJavaScript(
                                    "ZishuNativeListeningError('" +
                                            escapeJavaScript(
                                                    e.getMessage()
                                            ) +
                                            "')"
                            );
                        }
                    }
                }
        );
    }

    @JavascriptInterface
    public void stopListening() {

        if (speechRecognizer == null) {
            return;
        }

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        try {

                            speechRecognizer.stopListening();

                            sendToJavaScript(
                                    "ZishuNativeListeningStopped()"
                            );

                        } catch (Exception ignored) {
                        }
                    }
                }
        );
    }

    // =========================================================
    // WEB SEARCH
    // =========================================================

    @JavascriptInterface
    public void searchWeb(String query) {

        if (query == null ||
                query.trim().length() == 0) {

            sendActionResult(
                    "search_web",
                    "invalid_query"
            );

            return;
        }

        try {

            String encodedQuery =
                    URLEncoder.encode(
                            query.trim(),
                            "UTF-8"
                    );

            String url =
                    "https://www.google.com/search?q=" +
                            encodedQuery;

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    );

            activity.startActivity(intent);

            sendActionResult(
                    "search_web",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "search_web",
                    "error"
            );
        }
    }

    // =========================================================
    // OPEN URL
    // =========================================================

    @JavascriptInterface
    public void openUrl(String url) {

        if (url == null ||
                url.trim().length() == 0) {

            sendActionResult(
                    "open_url",
                    "invalid_url"
            );

            return;
        }

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url.trim())
                    );

            activity.startActivity(intent);

            sendActionResult(
                    "open_url",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "open_url",
                    "error"
            );
        }
    }

    // =========================================================
    // GOOGLE MAPS
    // =========================================================

    @JavascriptInterface
    public void openMaps(String query) {

        try {

            String mapQuery;

            if (query == null ||
                    query.trim().length() == 0) {

                mapQuery = "Google Maps";
            } else {

                mapQuery = query.trim();
            }

            String encoded =
                    URLEncoder.encode(
                            mapQuery,
                            "UTF-8"
                    );

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                    "https://www.google.com/maps/search/?api=1&query=" +
                                            encoded
                            )
                    );

            activity.startActivity(intent);

            sendActionResult(
                    "open_maps",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "open_maps",
                    "error"
            );
        }
    }

    // =========================================================
    // PHONE DIALER
    // =========================================================

    @JavascriptInterface
    public void makeCall(String phoneNumber) {

        if (phoneNumber == null ||
                phoneNumber.trim().length() == 0) {

            sendActionResult(
                    "make_call",
                    "invalid_number"
            );

            return;
        }

        try {

            String number =
                    phoneNumber.trim();

            Intent intent =
                    new Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse(
                                    "tel:" + Uri.encode(number)
                            )
                    );

            activity.startActivity(intent);

            sendActionResult(
                    "make_call",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "make_call",
                    "error"
            );
        }
    }

    // =========================================================
    // SMS COMPOSER
    // =========================================================

    @JavascriptInterface
    public void sendSms(
            String phoneNumber,
            String message
    ) {

        if (phoneNumber == null ||
                phoneNumber.trim().length() == 0) {

            sendActionResult(
                    "send_sms",
                    "invalid_number"
            );

            return;
        }

        try {

            String number =
                    phoneNumber.trim();

            Intent intent =
                    new Intent(
                            Intent.ACTION_SENDTO
                    );

            intent.setData(
                    Uri.parse(
                            "smsto:" +
                                    Uri.encode(number)
                    )
            );

            if (message != null) {

                intent.putExtra(
                        "sms_body",
                        message
                );
            }

            activity.startActivity(intent);

            sendActionResult(
                    "send_sms",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "send_sms",
                    "error"
            );
        }
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    @JavascriptInterface
    public void openSettings(String type) {

        String setting =
                type == null
                        ? ""
                        : type.toLowerCase(Locale.ROOT).trim();

        Intent intent = null;

        try {

            if (containsAny(
                    setting,
                    "wifi",
                    "wi-fi",
                    "वाईफाई",
                    "वाई-फाई"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_WIFI_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "bluetooth",
                    "ब्लूटूथ"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_BLUETOOTH_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "display",
                    "screen",
                    "डिस्प्ले",
                    "स्क्रीन"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_DISPLAY_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "sound",
                    "volume",
                    "audio",
                    "आवाज",
                    "वॉल्यूम"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_SOUND_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "apps",
                    "applications",
                    "app settings",
                    "ऐप",
                    "एप्लिकेशन"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_APPLICATION_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "notification",
                    "notifications",
                    "नोटिफिकेशन",
                    "सूचना"
            )) {

                if (Build.VERSION.SDK_INT >= 26) {

                    intent =
                            new Intent(
                                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            );

                    intent.putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            activity.getPackageName()
                    );

                } else {

                    intent =
                            new Intent(
                                    Settings.ACTION_SETTINGS
                            );
                }
            }

            else if (containsAny(
                    setting,
                    "location",
                    "gps",
                    "लोकेशन",
                    "जीपीएस"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_LOCATION_SOURCE_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "security",
                    "privacy",
                    "सिक्योरिटी",
                    "प्राइवेसी"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_SECURITY_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "battery",
                    "बैटरी"
            )) {

                if (Build.VERSION.SDK_INT >= 21) {

                    intent =
                            new Intent(
                                    Settings.ACTION_BATTERY_SAVER_SETTINGS
                            );

                } else {

                    intent =
                            new Intent(
                                    Settings.ACTION_SETTINGS
                            );
                }
            }

            else if (containsAny(
                    setting,
                    "accessibility",
                    "असिस्टिव",
                    "एक्सेसिबिलिटी"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_ACCESSIBILITY_SETTINGS
                        );
            }

            else if (containsAny(
                    setting,
                    "language",
                    "languages",
                    "भाषा",
                    "लैंग्वेज"
            )) {

                if (Build.VERSION.SDK_INT >= 24) {

                    intent =
                            new Intent(
                                    Settings.ACTION_LOCALE_SETTINGS
                            );

                } else {

                    intent =
                            new Intent(
                                    Settings.ACTION_SETTINGS
                            );
                }
            }

            else if (containsAny(
                    setting,
                    "developer",
                    "developer options",
                    "डेवलपर"
            )) {

                intent =
                        new Intent(
                                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                        );
            }

            else {

                intent =
                        new Intent(
                                Settings.ACTION_SETTINGS
                        );
            }

            activity.startActivity(intent);

            sendActionResult(
                    "open_settings",
                    "success"
            );

        } catch (Exception e) {

            try {

                activity.startActivity(
                        new Intent(
                                Settings.ACTION_SETTINGS
                        )
                );

                sendActionResult(
                        "open_settings",
                        "fallback"
                );

            } catch (Exception ignored) {

                sendActionResult(
                        "open_settings",
                        "error"
                );
            }
        }
    }

    // =========================================================
    // OPEN APP BY NAME
    // =========================================================

    @JavascriptInterface
    public void openAppByName(String appName) {

        if (appName == null ||
                appName.trim().length() == 0) {

            sendActionResult(
                    "open_app",
                    "unknown_app"
            );

            return;
        }

        String name =
                appName
                        .toLowerCase(Locale.ROOT)
                        .trim();

        String packageName = null;

        if (containsAny(
                name,
                "whatsapp",
                "व्हाट्सएप",
                "वाट्सएप",
                "वॉट्सऐप",
                "व्हाट्स अप"
        )) {

            packageName =
                    "com.whatsapp";
        }

        else if (containsAny(
                name,
                "youtube",
                "you tube",
                "यूट्यूब",
                "यूटुब"
        )) {

            packageName =
                    "com.google.android.youtube";
        }

        else if (containsAny(
                name,
                "instagram",
                "insta",
                "इंस्टाग्राम",
                "इंस्टा"
        )) {

            packageName =
                    "com.instagram.android";
        }

        else if (containsAny(
                name,
                "facebook",
                "fb",
                "फेसबुक"
        )) {

            packageName =
                    "com.facebook.katana";
        }

        else if (containsAny(
                name,
                "telegram",
                "टेलीग्राम"
        )) {

            packageName =
                    "org.telegram.messenger";
        }

        else if (containsAny(
                name,
                "gmail",
                "जीमेल",
                "जी मेल"
        )) {

            packageName =
                    "com.google.android.gm";
        }

        else if (containsAny(
                name,
                "chrome",
                "google chrome",
                "क्रोम",
                "गूगल क्रोम"
        )) {

            packageName =
                    "com.android.chrome";
        }

        else if (containsAny(
                name,
                "maps",
                "google maps",
                "गूगल मैप",
                "गूगल मैप्स",
                "मैप",
                "मैप्स"
        )) {

            packageName =
                    "com.google.android.apps.maps";
        }

        else if (containsAny(
                name,
                "settings",
                "setting",
                "phone settings",
                "सेटिंग",
                "सेटिंग्स",
                "फोन सेटिंग"
        )) {

            packageName =
                    "com.android.settings";
        }

        if (packageName == null) {

            sendActionResult(
                    "open_app",
                    "unknown_app"
            );

            return;
        }

        launchPackage(packageName);
    }

    // =========================================================
    // OPEN APP BY PACKAGE
    // =========================================================

    @JavascriptInterface
    public void openApp(String packageName) {

        if (packageName == null ||
                packageName.trim().length() == 0) {

            sendActionResult(
                    "open_app",
                    "unknown_app"
            );

            return;
        }

        launchPackage(
                packageName.trim()
        );
    }

    // =========================================================
    // LAUNCH PACKAGE
    // =========================================================

    private void launchPackage(
            String packageName
    ) {

        try {

            PackageManager packageManager =
                    activity.getPackageManager();

            Intent launchIntent =
                    packageManager.getLaunchIntentForPackage(
                            packageName
                    );

            if (launchIntent == null) {

                sendActionResult(
                        "open_app",
                        "not_installed"
                );

                return;
            }

            launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            activity.startActivity(
                    launchIntent
            );

            sendActionResult(
                    "open_app",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "open_app",
                    "error"
            );
        }
    }

    // =========================================================
    // INSTALLED APPLICATIONS
    // =========================================================

    @JavascriptInterface
    public void getInstalledApps() {

        try {

            PackageManager pm =
                    activity.getPackageManager();

            List<ApplicationInfo> apps =
                    pm.getInstalledApplications(
                            PackageManager.GET_META_DATA
                    );

            StringBuilder json =
                    new StringBuilder();

            json.append("[");

            boolean first = true;

            for (ApplicationInfo app : apps) {

                if ((app.flags &
                        ApplicationInfo.FLAG_SYSTEM) != 0) {

                    continue;
                }

                String label;

                try {

                    label =
                            pm.getApplicationLabel(app)
                                    .toString();

                } catch (Exception e) {

                    label = app.packageName;
                }

                if (!first) {
                    json.append(",");
                }

                first = false;

                json.append("{");

                json.append("\"name\":\"")
                        .append(
                                escapeJson(label)
                        )
                        .append("\",");

                json.append("\"packageName\":\"")
                        .append(
                                escapeJson(
                                        app.packageName
                                )
                        )
                        .append("\"");

                json.append("}");
            }

            json.append("]");

            sendToJavaScript(
                    "ZishuNativeInstalledAppsResult(" +
                            escapeJavaScript(
                                    json.toString()
                            ) +
                            ")"
            );

            sendActionResult(
                    "get_installed_apps",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "get_installed_apps",
                    "error"
            );
        }
    }

    // =========================================================
    // BATTERY INFORMATION
    // =========================================================

    @JavascriptInterface
    public void getBatteryInfo() {

        try {

            BatteryManager batteryManager =
                    (BatteryManager)
                            activity.getSystemService(
                                    Activity.BATTERY_SERVICE
                            );

            int level = -1;

            if (Build.VERSION.SDK_INT >= 21) {

                level =
                        batteryManager.getIntProperty(
                                BatteryManager.BATTERY_PROPERTY_CAPACITY
                        );
            }

            boolean charging = false;

            if (Build.VERSION.SDK_INT >= 23) {

                int status =
                        batteryManager.getIntProperty(
                                BatteryManager.BATTERY_PROPERTY_STATUS
                        );

                charging =
                        status ==
                                BatteryManager.BATTERY_STATUS_CHARGING ||
                        status ==
                                BatteryManager.BATTERY_STATUS_FULL;
            }

            String result =
                    "{"
                            + "\"level\":" + level + ","
                            + "\"charging\":" + charging
                            + "}";

            sendToJavaScript(
                    "ZishuNativeBatteryResult('" +
                            escapeJavaScript(result) +
                            "')"
            );

            sendActionResult(
                    "battery_info",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "battery_info",
                    "error"
            );
        }
    }

    // =========================================================
    // DEVICE INFORMATION
    // =========================================================

    @JavascriptInterface
    public void getDeviceInfo() {

        try {

            String result =
                    "{"
                            + "\"manufacturer\":\"" +
                            escapeJson(
                                    Build.MANUFACTURER
                            ) +
                            "\","
                            + "\"model\":\"" +
                            escapeJson(
                                    Build.MODEL
                            ) +
                            "\","
                            + "\"androidVersion\":\"" +
                            escapeJson(
                                    Build.VERSION.RELEASE
                            ) +
                            "\","
                            + "\"sdk\":" +
                            Build.VERSION.SDK_INT
                            + "}";

            sendToJavaScript(
                    "ZishuNativeDeviceInfoResult('" +
                            escapeJavaScript(result) +
                            "')"
            );

            sendActionResult(
                    "device_info",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "device_info",
                    "error"
            );
        }
    }

    // =========================================================
    // OPEN ALARM / CLOCK
    // =========================================================

    @JavascriptInterface
    public void setAlarm(
            String title,
            int hour,
            int minute
    ) {

        try {

            Intent intent =
                    new Intent(
                            android.provider.AlarmClock
                                    .ACTION_SET_ALARM
                    );

            intent.putExtra(
                    android.provider.AlarmClock
                            .EXTRA_HOUR,
                    hour
            );

            intent.putExtra(
                    android.provider.AlarmClock
                            .EXTRA_MINUTES,
                    minute
            );

            intent.putExtra(
                    android.provider.AlarmClock
                            .EXTRA_MESSAGE,
                    title == null
                            ? "Zishu Alarm"
                            : title
            );

            activity.startActivity(intent);

            sendActionResult(
                    "set_alarm",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "set_alarm",
                    "error"
            );
        }
    }

    // =========================================================
    // CALENDAR / REMINDER
    // =========================================================

    @JavascriptInterface
    public void createReminder(
            String title,
            String description
    ) {

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_INSERT
                    );

            intent.setData(
                    Uri.parse(
                            "content://com.android.calendar/events"
                    )
            );

            intent.putExtra(
                    "title",
                    title == null
                            ? "Zishu Reminder"
                            : title
            );

            intent.putExtra(
                    "description",
                    description == null
                            ? ""
                            : description
            );

            activity.startActivity(intent);

            sendActionResult(
                    "create_reminder",
                    "success"
            );

        } catch (Exception e) {

            sendActionResult(
                    "create_reminder",
                    "error"
            );
        }
    }

    // =========================================================
    // NATIVE NOTIFICATION
    // =========================================================

    @JavascriptInterface
    public void showNotification(
            String title,
            String message
    ) {

        /*
         * Notification creation is intentionally delegated
         * to the Android notification layer.
         *
         * This bridge safely reports that the native action
         * was received. A full NotificationChannel based
         * implementation can be added according to the
         * target Android SDK.
         */

        sendActionResult(
                "notification",
                "received"
        );
    }

    // =========================================================
    // ACTION RESULT
    // =========================================================

    private void sendActionResult(
            String action,
            String result
    ) {

        String safeAction =
                escapeJavaScript(action);

        String safeResult =
                escapeJavaScript(result);

        sendToJavaScript(
                "ZishuNativeActionResult('" +
                        safeAction +
                        "','" +
                        safeResult +
                        "')"
        );
    }

    // =========================================================
    // JAVASCRIPT COMMUNICATION
    // =========================================================

    private void sendToJavaScript(
            final String javascript
    ) {

        if (webView == null) {
            return;
        }

        webView.post(
                new Runnable() {

                    @Override
                    public void run() {

                        try {

                            webView.evaluateJavascript(
                                    javascript,
                                    null
                            );

                        } catch (Exception ignored) {
                        }
                    }
                }
        );
    }

    // =========================================================
    // PERMISSION RESULT
    // =========================================================

    public void onPermissionResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        if (requestCode !=
                REQUEST_RECORD_AUDIO) {

            return;
        }

        if (grantResults != null &&
                grantResults.length > 0 &&
                grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {

            sendToJavaScript(
                    "ZishuNativePermissionGranted('microphone')"
            );

            startListening();

        } else {

            sendToJavaScript(
                    "ZishuNativePermissionDenied('microphone')"
            );
        }
    }

    // =========================================================
    // TEXT MATCHING HELPER
    // =========================================================

    private boolean containsAny(
            String text,
            String... words
    ) {

        if (text == null) {
            return false;
        }

        for (String word : words) {

            if (word == null) {
                continue;
            }

            if (text.contains(
                    word.toLowerCase(Locale.ROOT)
            )) {

                return true;
            }
        }

        return false;
    }

    // =========================================================
    // JAVASCRIPT ESCAPE
    // =========================================================

    private String escapeJavaScript(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    // =========================================================
    // JSON ESCAPE
    // =========================================================

    private String escapeJson(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void destroy() {

        if (speechRecognizer != null) {

            try {

                speechRecognizer.destroy();

            } catch (Exception ignored) {
            }

            speechRecognizer = null;
        }

        if (textToSpeech != null) {

            try {

                textToSpeech.stop();
                textToSpeech.shutdown();

            } catch (Exception ignored) {
            }

            textToSpeech = null;
        }
    }
}
