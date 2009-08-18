package com.andrewshu.android.reddit;

import java.util.regex.Pattern;

public class Constants {

	static final String COMMENT_KIND = "t1";
	static final String THREAD_KIND = "t3";
	
	static final String LOOK_OF_DISAPPROVAL = "\u0ca0\u005f\u0ca0";

	static final String PREFS_SESSION = "RedditSession";
	static final String PREFS_THEME= "RedditTheme";
	

    static final int DEFAULT_THREAD_DOWNLOAD_LIMIT = 25;
    static final int DEFAULT_COMMENT_DOWNLOAD_LIMIT = 200;
    
    // startActivityForResult request codes
    static final int ACTIVITY_PICK_SUBREDDIT = 0;
    
    // Menu and dialog actions
    static final int DIALOG_PICK_SUBREDDIT = 0;
    static final int DIALOG_REDDIT_COM = 1;
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_POST_THREAD = 5;
    static final int DIALOG_THING_CLICK = 6;
    static final int DIALOG_LOGGING_IN = 7;
    static final int DIALOG_LOADING_THREADS_LIST = 8;
    static final int DIALOG_LOADING_COMMENTS_LIST = 9;
    static final int DIALOG_LOADING_LOOK_OF_DISAPPROVAL = 10;
    static final int DIALOG_OPEN_BROWSER = 11;
    static final int DIALOG_THEME = 12;
    static final int DIALOG_OP = 13;
    static final int DIALOG_REPLY = 14;

    
    // Themes
    static final int THEME_LIGHT = 0;
    static final int THEME_DARK = 1;
    
    // Special CSS for webviews to match themes
    static final String CSS_DARK = "<body style=\"color:#c0c0c0;background-color:#191919\">";
    
    // States for StateListDrawables
    static final int[] STATE_CHECKED = new int[]{android.R.attr.state_checked};
    static final int[] STATE_NONE = new int[0];
    
    // Strings
    static final String TRUE_STRING = "true";
    static final String FALSE_STRING = "false";
    static final String NULL_STRING = "null";
    static final String EMPTY_STRING = "";
    
    // JSON keys
    static final String JSON_BODY = "body";
    static final String JSON_CHILDREN = "children";
    static final String JSON_DATA = "data";
    static final String JSON_KIND = "kind";
    static final String JSON_LISTING = "Listing";
    static final String JSON_MEDIA = "media";
    static final String JSON_MEDIA_EMBED = "media_embed";
    static final String JSON_REPLIES = "replies";
    
    // Keys used for data in the onSaveInstanceState() Map.
    static final String STRINGS_KEY = "strings";
    static final String SELECTION_KEY = "selection";
    static final String URL_KEY = "url";
    static final String STATUS_KEY = "status";
    
    // A short HTML file returned by reddit, so we can get the modhash
    static final String MODHASH_URL = "http://www.reddit.com/stats";
    
    // The pattern to find modhash from HTML javascript area
    static final Pattern MODHASH_PATTERN = Pattern.compile("modhash: '(.*?)'");
    // Group 1: fullname. Group 2: kind. Group 3: id36.
    static final Pattern NEW_ID_PATTERN = Pattern.compile("\"id\": \"((.+?)_(.+?))\"");
    // Group 1: whole error. Group 2: the time part
    static final Pattern RATELIMIT_RETRY_PATTERN = Pattern.compile("(you are trying to submit too fast. try again in (.+?)\\.)");


}
