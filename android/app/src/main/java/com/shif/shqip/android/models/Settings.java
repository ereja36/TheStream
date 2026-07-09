package com.shif.shqip.android.models;

import java.io.Serializable;

public class Settings implements Serializable {

    public String privacy_policy;
    public String youtube_api_key = "";
    public String fcm_notification_topic = "";
    public String onesignal_app_id = "";
    public String more_apps_url = "";
    public String providers = "";
    public int enable_player_epg = 1;
    public String player_engine = "exoplayer";
    public String player_brand_text = "";
    public String player_brand_logo_url = "";
    public int player_brand_enabled = 1;
    public int app_integrity_enabled = 1;
    public String app_release_cert_sha256 = "";
    public String ads_antiblock_script = "";
    public String privacy_public_url = "https://shifshqip.com/privacy";

}
