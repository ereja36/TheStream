package com.shif.shqip.android.utils;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class YouTubeVideoExistenceChecker {

    public interface VideoExistenceCallback {
        void onVideoExists(String videoId);

        void onVideoNotFound(String videoId);

        void onError(String videoId, String errorMessage);
    }

    private final Executor backgroundExecutor;
    private final Handler mainThreadHandler;

    public YouTubeVideoExistenceChecker() {
        // Create a fixed thread pool executor for background tasks.
        // Adjust the number of threads based on your application's needs.
        // For simple network calls, 2-4 threads might be sufficient.
        this.backgroundExecutor = Executors.newFixedThreadPool(2);
        // Handler to post results back to the main (UI) thread
        this.mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public void checkVideoExistence(String videoId, VideoExistenceCallback callback) {
        if (videoId == null) {
            callback.onError(videoId, "Invalid YouTube URL or video ID not found.");
            return;
        }

        backgroundExecutor.execute(() -> {
            String currentVideoId = videoId;
            String errorMessage = null;
            boolean exists = false;

            try {
                String oembedUrl = "https://www.youtube.com/oembed?url=http://www.youtube.com/watch?v=" + currentVideoId + "&format=json";
                URL url = new URL(oembedUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD"); // Use HEAD for efficiency
                connection.setConnectTimeout(5000); // 5 seconds
                connection.setReadTimeout(5000);    // 5 seconds

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    exists = true; // Video exists
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    exists = false; // Video does not exist
                } else {
                    errorMessage = "HTTP Error: " + responseCode;
                    exists = false; // Other error
                }
            } catch (IOException e) {
                errorMessage = "Network Error: " + e.getMessage();
                exists = false; // Network or other I/O error
            }

            final String finalErrorMessage = errorMessage;
            final boolean finalExists = exists;

            // Post the result back to the main thread
            mainThreadHandler.post(() -> {
                if (finalExists) {
                    callback.onVideoExists(currentVideoId);
                } else {
                    if (finalErrorMessage != null) {
                        callback.onError(currentVideoId, finalErrorMessage);
                    } else {
                        callback.onVideoNotFound(currentVideoId);
                    }
                }
            });
        });
    }
}