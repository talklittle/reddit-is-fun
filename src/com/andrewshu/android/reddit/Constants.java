package com.andrewshu.android.reddit;

import java.util.regex.Pattern;

import android.app.Activity;

public class Constants {

	static final String COMMENT_KIND = "t1";
	static final String THREAD_KIND = "t3";
	static final String MESSAGE_KIND = "t4";
	static final String SUBREDDIT_KIND = "t5";
	
	// Requires a non-default font
	static final String LOOK_OF_DISAPPROVAL = "\u0ca0\u005f\u0ca0";

	static final String PREFS_SESSION = "RedditSession";
	static final String PREFS_THEME = "RedditTheme";
	static final String PREFS_NOTIFICATIONS = "RedditNotifications";
	
    static final int DEFAULT_THREAD_DOWNLOAD_LIMIT = 25;
    static final int DEFAULT_COMMENT_DOWNLOAD_LIMIT = 200;
    
    // startActivityForResult request codes
    static final int ACTIVITY_PICK_SUBREDDIT = 0;
    static final int ACTIVITY_SUBMIT_LINK = 1;
    
    // notifications
    static final int NOTIFICATION_HAVE_MAIL = 0;
    
    // Tell PickSubredditActivity to hide the fronptage string
    static final String HIDE_FRONTPAGE_STRING = "hideFrontpage";
    
    // User-defined result codes
    static final int RESULT_LOGIN_REQUIRED = Activity.RESULT_FIRST_USER;
    
    // Menu and dialog actions
    static final int DIALOG_PICK_SUBREDDIT = 0;
    static final int DIALOG_REDDIT_COM = 1;
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_SUBMIT_LINK = 5;
    static final int DIALOG_THING_CLICK = 6;
    static final int DIALOG_LOGGING_IN = 7;
    static final int DIALOG_LOADING_THREADS_LIST = 8;
    static final int DIALOG_LOADING_COMMENTS_LIST = 9;
    static final int DIALOG_LOADING_LOOK_OF_DISAPPROVAL = 10;
    static final int DIALOG_OPEN_BROWSER = 11;
    static final int DIALOG_THEME = 12;
    static final int DIALOG_OP = 13;
    static final int DIALOG_REPLY = 14;
    static final int DIALOG_SUBMITTING = 15;
    static final int DIALOG_DOWNLOAD_CAPTCHA = 16;
    static final int DIALOG_HIDE_COMMENT = 17;
    static final int DIALOG_SHOW_COMMENT = 18;
    static final int DIALOG_LOADING_INBOX = 19;

    // Themes
    static final int THEME_LIGHT = 0;
    static final int THEME_DARK = 1;
    
    // Expanded status bar notification style
    static final int MAIL_NOTIFICATION_STYLE_DEFAULT = 0;
    static final int MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE = 1;
    static final int MAIL_NOTIFICATION_STYLE_OFF = 2;
    
    // Special CSS for webviews to match themes
    static final String CSS_DARK = "<style>body{color:#c0c0c0;background-color:#191919}a:link{color:#ffffff}</style>";
    
    // States for StateListDrawables
    static final int[] STATE_CHECKED = new int[]{android.R.attr.state_checked};
    static final int[] STATE_NONE = new int[0];
    
    // Strings
    static final String TRUE_STRING = "true";
    static final String FALSE_STRING = "false";
    static final String NULL_STRING = "null";
    static final String EMPTY_STRING = "";
    static final String NO_STRING = "no";
    
    static final String FRONTPAGE_STRING = "reddit front page";
    static final String HAVE_MAIL_TICKER = "reddit mail";
    static final String HAVE_MAIL_TITLE = "reddit is fun";
    static final String HAVE_MAIL_TEXT = "You have reddit mail.";
    
    static final String URL_TO_GET_HERE = "urlToGetHere";
    static final String THREAD_COUNT = "threadCount";
    
    static final String SUBMIT_KIND_LINK = "link";
    static final String SUBMIT_KIND_SELF = "self";
    static final String SUBMIT_KIND_POLL = "poll";
    
    // JSON values
    static final String JSON_AFTER = "after";
    static final String JSON_BEFORE = "before";
    static final String JSON_BODY = "body";
    static final String JSON_CHILDREN = "children";
    static final String JSON_DATA = "data";
    static final String JSON_KIND = "kind";
    static final String JSON_LISTING = "Listing";
    static final String JSON_MEDIA = "media";
    static final String JSON_MEDIA_EMBED = "media_embed";
    static final String JSON_MORE = "more";
	static final String JSON_REPLIES = "replies";
    
    // TabSpec tags
    static final String TAB_LINK = "tab_link";
    static final String TAB_TEXT = "tab_text";
    
    // Preference keys and values
    static final String PREF_THEME = "theme";
    static final String PREF_THEME_LIGHT = "THEME_LIGHT";
    static final String PREF_THEME_DARK	 = "THEME_DARK";
    static final String PREF_MAIL_NOTIFICATION_STYLE = "mail_notification_style";
    static final String PREF_MAIL_NOTIFICATION_STYLE_DEFAULT = "MAIL_NOTIFICATION_STYLE_DEFAULT";
    static final String PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE = "MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE";
    static final String PREF_MAIL_NOTIFICATION_STYLE_OFF = "MAIL_NOTIFICATION_STYLE_OFF";
    
    // A short HTML file returned by reddit, so we can get the modhash
    static final String MODHASH_URL = "http://www.reddit.com/r";
    
    // The pattern to find modhash from HTML javascript area
    static final Pattern MODHASH_PATTERN = Pattern.compile("modhash: '(.*?)'");
    // Group 1: fullname. Group 2: kind. Group 3: id36.
    static final Pattern NEW_ID_PATTERN = Pattern.compile("\"id\": \"((.+?)_(.+?))\"");
    // Group 1: Subreddit. Group 2: thread id (no t3_ prefix)
    static final Pattern NEW_THREAD_PATTERN = Pattern.compile("\"http://www.reddit.com/r/(.+?)/comments/(.+?)/.*?/\"");
    // Group 1: whole error. Group 2: the time part
    static final Pattern RATELIMIT_RETRY_PATTERN = Pattern.compile("(you are trying to submit too fast. try again in (.+?)\\.)");
    // Captcha "iden"
    static final Pattern CAPTCHA_IDEN_PATTERN = Pattern.compile("name=\"iden\" value=\"(.*?)\"");
    // Group 2: Captcha image absolute path
    static final Pattern CAPTCHA_IMAGE_PATTERN = Pattern.compile("<img class=\"capimage\"( alt=\".*?\")? src=\"(.+?)\"");
    // Group 1: "no" if no mail, "" if yes mail
    static final Pattern HAVE_MAIL_PATTERN = Pattern.compile("class=\"(no)?havemail\"");
}
