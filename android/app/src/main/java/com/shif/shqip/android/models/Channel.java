package com.shif.shqip.android.models;

import java.io.Serializable;

public class Channel implements Serializable {

    public String channel_id = "";
    public String channel_name = "";
    public String channel_image = "";
    public String channel_url = "";
    public String channel_description = "";
    public String channel_type = "";
    public String video_id = "";
    public String category_name = "";
    public String user_agent = "";
    public String total_views = "";
    public String epg_id = "";
    public String epg_url = "";
    /** Marker for native-ad placeholder rows — never treat real channels as ads. */
    public boolean is_native_ad = false;

    public static Channel nativeAdPlaceholder() {
        Channel ad = new Channel();
        ad.is_native_ad = true;
        ad.channel_name = "";
        return ad;
    }

}
