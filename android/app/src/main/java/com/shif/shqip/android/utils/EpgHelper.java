package com.shif.shqip.android.utils;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class EpgHelper {

    public static class EpgProgram {
        public String title = "";
        public long startTimeMs;
        public long stopTimeMs;
    }

    public static class EpgPrograms {
        public EpgProgram now;
        public EpgProgram next;
    }

    private EpgHelper() {
    }

    public static EpgPrograms fetchPrograms(String epgXmlUrl, String epgId, String nameFallback) {
        if (TextUtils.isEmpty(epgXmlUrl)) {
            return null;
        }
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(epgXmlUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "ShifShqip/6.6 (Android)");
            connection.setRequestProperty("Accept", "*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            inputStream = connection.getInputStream();
            return parseEpgXml(inputStream, epgId, nameFallback);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
                // no-op
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String formatProgram(EpgProgram program, String unknownTitle) {
        if (program == null) {
            return "";
        }
        String title = TextUtils.isEmpty(program.title) ? unknownTitle : program.title;
        if (program.startTimeMs <= 0 || program.stopTimeMs <= 0) {
            return title;
        }
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.US);
        String range = hourFormat.format(new Date(program.startTimeMs))
                + " - " + hourFormat.format(new Date(program.stopTimeMs));
        return title + " (" + range + ")";
    }

    private static EpgPrograms parseEpgXml(InputStream inputStream, String epgId, String nameFallback)
            throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(inputStream, "UTF-8");
        long now = System.currentTimeMillis();
        EpgProgram currentProgram = null;
        EpgProgram nextProgram = null;
        EpgProgram pendingProgram = null;
        boolean matchChannel = false;
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                if ("programme".equals(tag)) {
                    String channelAttr = parser.getAttributeValue(null, "channel");
                    matchChannel = isEpgChannelMatch(channelAttr, epgId, nameFallback);
                    if (matchChannel) {
                        pendingProgram = new EpgProgram();
                        pendingProgram.startTimeMs = parseEpgTime(parser.getAttributeValue(null, "start"));
                        pendingProgram.stopTimeMs = parseEpgTime(parser.getAttributeValue(null, "stop"));
                    }
                } else if (matchChannel && pendingProgram != null && "title".equals(tag)) {
                    pendingProgram.title = safeText(parser.nextText());
                }
            } else if (eventType == XmlPullParser.END_TAG && "programme".equals(tag)) {
                if (matchChannel && pendingProgram != null) {
                    if (pendingProgram.startTimeMs <= now && now < pendingProgram.stopTimeMs) {
                        if (currentProgram == null || pendingProgram.startTimeMs > currentProgram.startTimeMs) {
                            currentProgram = pendingProgram;
                        }
                    } else if (pendingProgram.startTimeMs > now) {
                        if (nextProgram == null || pendingProgram.startTimeMs < nextProgram.startTimeMs) {
                            nextProgram = pendingProgram;
                        }
                    }
                }
                pendingProgram = null;
                matchChannel = false;
            }
            eventType = parser.next();
        }

        EpgPrograms programs = new EpgPrograms();
        programs.now = currentProgram;
        programs.next = nextProgram;
        return programs;
    }

    private static boolean isEpgChannelMatch(String xmlChannelValue, String epgId, String nameFallback) {
        if (TextUtils.isEmpty(xmlChannelValue)) {
            return false;
        }
        String channelNorm = xmlChannelValue.trim().toLowerCase(Locale.US);
        if (!TextUtils.isEmpty(epgId) && channelNorm.equals(epgId.trim().toLowerCase(Locale.US))) {
            return true;
        }
        if (!TextUtils.isEmpty(nameFallback)) {
            return channelNorm.equals(nameFallback.trim().toLowerCase(Locale.US));
        }
        return false;
    }

    private static long parseEpgTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0L;
        }
        String normalized = value.trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length >= 2) {
            normalized = parts[0] + " " + parts[1];
        } else if (parts.length == 1) {
            normalized = parts[0] + " +0000";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        try {
            Date date = format.parse(normalized);
            return date == null ? 0L : date.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
