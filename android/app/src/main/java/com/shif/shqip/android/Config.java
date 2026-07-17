package com.shif.shqip.android;

import com.shif.shqip.android.utils.Constant;

public class Config {

    public static final String SERVER_KEY = "WVVoU01HTklUVFpNZVRsb1kwZHJkV015YUhCYWJrNXZZMWRzZDB4dFRuWmlWamxvWTBoQ2MyRlhUbWhrUjJ4MlltdHNhMWd5VG5aaVV6VjZZVWRzYlV4dVRtOWpWMngzVEcxR2RWcElTblpoVjFFOQ==";

    public static final String REST_API_KEY = "shifshqip";

    //bottom navigation menu configuration
    public static final boolean SET_CATEGORY_AS_MAIN_MENU = false;

    //dark theme configuration
    public static final boolean SET_DARK_MODE_AS_DEFAULT_THEME = true;

    //available channel view type : CHANNEL_LIST_DEFAULT, CHANNEL_GRID_2_COLUMN or CHANNEL_GRID_3_COLUMN
    public static final int CHANNEL_VIEW_TYPE = Constant.CHANNEL_LIST_DEFAULT;

    //available channel view type : CATEGORY_LIST_DEFAULT, CATEGORY_GRID_2_COLUMN or CATEGORY_GRID_3_COLUMN
    public static final int CATEGORY_VIEW_TYPE = Constant.CATEGORY_GRID_3_COLUMN;

    //available player screen orientation : PLAYER_MODE_PORTRAIT or PLAYER_MODE_LANDSCAPE
    public static final int DEFAULT_PLAYER_SCREEN_ORIENTATION = Constant.PLAYER_MODE_PORTRAIT;

    //show option to play streaming using external player
    // Kanalet duhet te luhen vetem brenda app-it tone
    public static final boolean ENABLE_PLAY_STREAMING_USING_EXTERNAL_PLAYER = false;

    //display category name in the channel list
    public static final boolean ENABLE_CHANNEL_LIST_CATEGORY_NAME = false;

    //set true to allow app usage over VPN/proxy networks
    public static final boolean ENABLE_VPN_ACCESS = true;

    //press back twice to exit from player screen
    public static final boolean PRESS_BACK_TWICE_TO_CLOSE_PLAYER = false;

    //if you use RTL Language e.g : Arabic Language or other, set true
    public static final boolean ENABLE_RTL_MODE = false;

    //load more pagination
    public static final int LOAD_MORE = 12;

    //looping mode
    public static final boolean ENABLE_LOOPING_MODE = false;

    //display dialog for exit confirmation
    public static final boolean ENABLE_EXIT_DIALOG = true;

    //GDPR
    public static final boolean ENABLE_GDPR_EU_CONSENT = true;

    //delay splash screen
    public static final int DELAY_SPLASH = 500;

    //Enable it with true value if want to the app will force to display open ads first before start the main menu
    //Longer duration to start the app may occur depending on internet connection or open ad response time itself
    // Hero live preview on Design 2 home — off by default (competes with playback / silent stalls)
    public static final boolean ENABLE_HERO_LIVE_PREVIEW = false;

}