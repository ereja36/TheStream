package com.shif.shqip.android.utils;

import com.shif.shqip.android.models.Channel;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast playlist list paging — keeps native ad slots per page instead of loading thousands at once.
 */
public final class PlaylistListHelper {

    public static final int PLAYLIST_PAGE_SIZE = 48;

    private PlaylistListHelper() {
    }

    public static List<Channel> slicePage(List<Channel> source, int pageNo) {
        if (source == null || source.isEmpty() || pageNo < 1) {
            return new ArrayList<>();
        }
        int start = (pageNo - 1) * PLAYLIST_PAGE_SIZE;
        if (start >= source.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(source.size(), start + PLAYLIST_PAGE_SIZE);
        return new ArrayList<>(source.subList(start, end));
    }

    public static boolean hasMorePages(List<Channel> source, int pageNo) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        return pageNo * PLAYLIST_PAGE_SIZE < source.size();
    }
}
