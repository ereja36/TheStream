package com.shif.shqip.android.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.text.TextUtils;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Blocks modified/re-signed APK builds when enabled from the admin panel.
 */
public final class AppIntegrityHelper {

    public static final String BLOCK_REASON_MOD_APK = "modified_apk";
    public static final String BLOCK_REASON_DEBUG = "debug_build";
    public static final String BLOCK_REASON_ADS_TAMPER = "ads_tampered";

    private AppIntegrityHelper() {
    }

    public static boolean isAppAllowed(Context context, SharedPref sharedPref) {
        if (sharedPref == null || !sharedPref.isAppIntegrityEnabled()) {
            return true;
        }
        if (BuildConfig.DEBUG) {
            return true;
        }
        if (isDebuggable(context)) {
            return false;
        }
        return isSignatureAllowed(context, sharedPref.getAllowedReleaseCertSha256());
    }

    public static boolean isAdsConfigTrusted(Context context, SharedPref sharedPref) {
        if (sharedPref == null || !sharedPref.isAppIntegrityEnabled()) {
            return true;
        }
        if (BuildConfig.DEBUG) {
            return true;
        }
        if (!sharedPref.isServerAdsEnabled()) {
            return true;
        }
        AdsPref adsPref = new AdsPref(context.getApplicationContext());
        return adsPref.getServerAdsEnabled();
    }

    public static String currentSigningSha256(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo = pm.getPackageInfo(
                        context.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES
                );
                if (packageInfo.signingInfo == null) {
                    return "";
                }
                Signature[] signatures = packageInfo.signingInfo.hasMultipleSigners()
                        ? packageInfo.signingInfo.getApkContentsSigners()
                        : packageInfo.signingInfo.getSigningCertificateHistory();
                if (signatures == null || signatures.length == 0) {
                    return "";
                }
                return sha256Hex(signatures[0].toByteArray());
            }
            packageInfo = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            if (packageInfo.signatures == null || packageInfo.signatures.length == 0) {
                return "";
            }
            return sha256Hex(packageInfo.signatures[0].toByteArray());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isSignatureAllowed(Context context, String allowedCsv) {
        if (TextUtils.isEmpty(allowedCsv)) {
            return true;
        }
        String current = normalizeSha(currentSigningSha256(context));
        if (TextUtils.isEmpty(current)) {
            return false;
        }
        for (String allowed : allowedCsv.split(",")) {
            if (current.equals(normalizeSha(allowed))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDebuggable(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 3);
            for (byte value : hash) {
                builder.append(String.format(Locale.US, "%02X", value));
                builder.append(':');
            }
            if (builder.length() > 0) {
                builder.setLength(builder.length() - 1);
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeSha(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US).replace(" ", "");
    }

    public static List<String> parseAllowedCerts(String csv) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(csv)) {
            return result;
        }
        for (String part : csv.split(",")) {
            String normalized = normalizeSha(part);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }
}
