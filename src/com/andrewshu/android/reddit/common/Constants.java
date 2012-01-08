/*
 * Copyright 2009 Andrew Shu
 *
 * This file is part of "reddit is fun".
 *
 * "reddit is fun" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "reddit is fun" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "reddit is fun".  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrewshu.android.reddit.common;

import android.app.Activity;

public class Constants {
	
	public static final boolean LOGGING = true;
	
	public static final boolean USE_COMMENTS_CACHE = false;
	public static final boolean USE_THREADS_CACHE = false;
	public static final boolean USE_SUBREDDITS_CACHE = true;
	
	// File containing the serialized variables of last subreddit viewed
	public static final String FILENAME_SUBREDDIT_CACHE = "subreddit.dat";
	// File containing the serialized variables of last comments viewed
	public static final String FILENAME_THREAD_CACHE = "thread.dat";
	// File containing a long integer System.currentTimeMillis(). Timestamp is shared among caches.
	public static final String FILENAME_CACHE_INFO = "cacheinfo.dat";
	public static final String[] FILENAMES_CACHE = {
		FILENAME_SUBREDDIT_CACHE, FILENAME_THREAD_CACHE, FILENAME_CACHE_INFO
	};
	
    public static final long MESSAGE_CHECK_MINIMUM_INTERVAL_MILLIS = 5 * 60 * 1000;  // 5 minutes
    public static final String LAST_MAIL_CHECK_TIME_MILLIS_KEY = "LAST_MAIL_CHECK_TIME_MILLIS_KEY";
	
	// 1:subreddit 2:threadId 3:commentId
	// The following commented-out one is good, but tough to get right, e.g.,
	// http://www.reddit.com/eorhm vs. http://www.reddit.com/prefs, mobile, store, etc.
	// So, for now require the captured URLs to have /comments or /tb prefix.
//	public static final String COMMENT_PATH_PATTERN_STRING
//		= "(?:/r/([^/]+)/comments|/comments|/tb)?/([^/]+)(?:/?$|/[^/]+/([a-zA-Z0-9]+)?)?";
	public static final String COMMENT_PATH_PATTERN_STRING
		= "(?:/r/([^/]+)/comments|/comments|/tb)/([^/]+)(?:/?$|/[^/]+/([a-zA-Z0-9]+)?)?";
	public static final String REDDIT_PATH_PATTERN_STRING = "(?:/r/([^/]+))?/?$";
	public static final String USER_PATH_PATTERN_STRING = "/user/([^/]+)/?$";
	
	public static final String COMMENT_KIND = "t1";
	public static final String THREAD_KIND = "t3";
	public static final String MESSAGE_KIND = "t4";
	public static final String SUBREDDIT_KIND = "t5";
	public static final String MORE_KIND = "more";
    
	public static final int DEFAULT_THREAD_DOWNLOAD_LIMIT = 25;
    public static final int DEFAULT_COMMENT_DOWNLOAD_LIMIT = 200;
    public static final long DEFAULT_FRESH_DURATION = 1800000;  // 30 minutes
    public static final long DEFAULT_FRESH_SUBREDDIT_LIST_DURATION = 86400000;  // 24 hours

    // startActivityForResult request codes
    public static final int ACTIVITY_PICK_SUBREDDIT = 0;
    public static final int ACTIVITY_SUBMIT_LINK = 1;
    
    // notifications
    public static final int NOTIFICATION_HAVE_MAIL = 0;
    
    // services
    public static final int SERVICE_ENVELOPE = 0;
    
    // --- Intent extras ---
    // Tell PickSubredditActivity to hide the fake subreddits string
    public static final String EXTRA_HIDE_FAKE_SUBREDDITS_STRING = "hideFakeSubreddits";
    public static final String EXTRA_ID = "id";
    // Tell CommentsListActivity to jump to a comment context (a URL. pattern match)
    public static final String EXTRA_COMMENT_CONTEXT = "jumpToComment";
    // Tell CommentsListActivity to show "more children"
    public static final String EXTRA_MORE_CHILDREN_ID = "moreChildrenId";
    public static final String EXTRA_NUM_COMMENTS = "num_comments";
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_THREAD_URL = "thread_url";
    public static final String EXTRA_TITLE = "title";
    
    // User-defined result codes
    public static final int RESULT_LOGIN_REQUIRED = Activity.RESULT_FIRST_USER;
    
    // Menu and dialog actions
    public static final int DIALOG_LOGIN = 2;
    public static final int DIALOG_LOGOUT = 3;
    public static final int DIALOG_THEME = 12;
    public static final int DIALOG_REPLY = 14;
    public static final int DIALOG_HIDE_COMMENT = 17;
    public static final int DIALOG_SHOW_COMMENT = 18;
    public static final int DIALOG_SORT_BY = 20;
    public static final int DIALOG_SORT_BY_NEW = 21;
    public static final int DIALOG_SORT_BY_CONTROVERSIAL = 22;
    public static final int DIALOG_SORT_BY_TOP = 23;
    public static final int DIALOG_COMMENT_CLICK = 24;
    public static final int DIALOG_MESSAGE_CLICK = 25;
    public static final int DIALOG_GOTO_PARENT = 28;
    public static final int DIALOG_EDIT = 29;
    public static final int DIALOG_DELETE = 30;
    public static final int DIALOG_COMPOSE = 31;
    public static final int DIALOG_FIND = 32;
    public static final int DIALOG_REPORT = 33;
    public static final int DIALOG_THREAD_CLICK = 34;
    public static final int DIALOG_VIEW_PROFILE = 35;

    // progress dialogs
    public static final int DIALOG_LOGGING_IN = 1000;
    public static final int DIALOG_SUBMITTING = 1004;
    public static final int DIALOG_REPLYING = 1005;
    public static final int DIALOG_LOADING_REDDITS_LIST = 1006;
    public static final int DIALOG_DELETING = 1008;
    public static final int DIALOG_EDITING = 1009;
    public static final int DIALOG_COMPOSING = 1012;
    
	public static final int SHARE_CONTEXT_ITEM = 1013;
	public static final int OPEN_IN_BROWSER_CONTEXT_ITEM = 1014;
	public static final int OPEN_COMMENTS_CONTEXT_ITEM = 1015;
	public static final int SAVE_CONTEXT_ITEM = 1016;
	public static final int UNSAVE_CONTEXT_ITEM = 1017;
	public static final int HIDE_CONTEXT_ITEM = 1018;
	public static final int UNHIDE_CONTEXT_ITEM = 1019;
	public static final int VIEW_SUBREDDIT_CONTEXT_ITEM = 1020;

    
    // Special CSS for webviews to match themes
    public static final String CSS_DARK = "<style>body{color:#c0c0c0;background-color:#000000}a:link{color:#ffffff}</style>";

    // Colors for markdown
    public static final int MARKDOWN_LINK_COLOR = 0xff2288cc;
    
    // States for StateListDrawables
    public static final int[] STATE_CHECKED = new int[]{android.R.attr.state_checked};
    public static final int[] STATE_NONE = new int[0];
    
    // Strings
    public static final String NO_STRING = "no";
    
    public static final String FRONTPAGE_STRING = "reddit front page";
    
    public static final String HAVE_MAIL_TICKER = "reddit mail";
    public static final String HAVE_MAIL_TITLE = "reddit is fun";
    public static final String HAVE_MAIL_TEXT = "You have reddit mail.";
    
    // save instance state Bundle keys
    public static final String AFTER_KEY = "after";
    public static final String BEFORE_KEY = "before";
    public static final String DELETE_TARGET_KIND_KEY = "delete_target_kind";
    public static final String EDIT_TARGET_BODY_KEY = "edit_target_body";
    public static final String ID_KEY = "id";
    public static final String JUMP_TO_THREAD_ID_KEY = "jump_to_thread_id";
    public static final String KARMA_KEY = "karma";
    public static final String LAST_AFTER_KEY = "last_after";
    public static final String LAST_BEFORE_KEY = "last_before";
    public static final String REPORT_TARGET_NAME_KEY = "report_target_name";
    public static final String REPLY_TARGET_NAME_KEY = "reply_target_name";
    public static final String SUBREDDIT_KEY = "subreddit";
    public static final String THREAD_COUNT_KEY = "thread_count";
    public static final String THREAD_ID_KEY = "thread_id";
    public static final String THREAD_LAST_COUNT_KEY = "last_thread_count";
    public static final String THREAD_TITLE_KEY = "thread_title";
    public static final String USERNAME_KEY = "username";
    public static final String VOTE_TARGET_THING_INFO_KEY = "vote_target_thing_info";
    public static final String WHICH_INBOX_KEY = "which_inbox";
    
    public static final String SUBMIT_KIND_LINK = "link";
    public static final String SUBMIT_KIND_SELF = "self";
    public static final String SUBMIT_KIND_POLL = "poll";
    
    // Sorting things
    public static final class ThreadsSort {
	    public static final String SORT_BY_KEY = "threads_sort_by";
	    public static final String SORT_BY_HOT = "hot";
	    public static final String SORT_BY_NEW = "new";
	    public static final String SORT_BY_CONTROVERSIAL = "controversial";
	    public static final String SORT_BY_TOP = "top";
	    public static final String SORT_BY_HOT_URL = "";
	    public static final String SORT_BY_NEW_URL = "new/";
	    public static final String SORT_BY_CONTROVERSIAL_URL = "controversial/";
	    public static final String SORT_BY_TOP_URL = "top/";
	    public static final String[] SORT_BY_CHOICES = {SORT_BY_HOT, SORT_BY_NEW, SORT_BY_CONTROVERSIAL, SORT_BY_TOP};
	    public static final String[] SORT_BY_URL_CHOICES = {SORT_BY_HOT_URL, SORT_BY_NEW_URL, SORT_BY_CONTROVERSIAL_URL, SORT_BY_TOP_URL};
	    public static final String SORT_BY_NEW_NEW = "new";
	    public static final String SORT_BY_NEW_RISING = "rising";
	    public static final String SORT_BY_NEW_NEW_URL = "sort=new";
	    public static final String SORT_BY_NEW_RISING_URL = "sort=rising";
	    public static final String[] SORT_BY_NEW_CHOICES = {SORT_BY_NEW_NEW, SORT_BY_NEW_RISING};
	    public static final String[] SORT_BY_NEW_URL_CHOICES = {SORT_BY_NEW_NEW_URL, SORT_BY_NEW_RISING_URL};
	    public static final String SORT_BY_CONTROVERSIAL_HOUR = "this hour";
	    public static final String SORT_BY_CONTROVERSIAL_DAY = "today";
	    public static final String SORT_BY_CONTROVERSIAL_WEEK = "this week";
	    public static final String SORT_BY_CONTROVERSIAL_MONTH = "this month";
	    public static final String SORT_BY_CONTROVERSIAL_YEAR = "this year";
	    public static final String SORT_BY_CONTROVERSIAL_ALL = "all time";
	    public static final String SORT_BY_CONTROVERSIAL_HOUR_URL = "t=hour";
	    public static final String SORT_BY_CONTROVERSIAL_DAY_URL = "t=day";
	    public static final String SORT_BY_CONTROVERSIAL_WEEK_URL = "t=week";
	    public static final String SORT_BY_CONTROVERSIAL_MONTH_URL = "t=month";
	    public static final String SORT_BY_CONTROVERSIAL_YEAR_URL = "t=year";
	    public static final String SORT_BY_CONTROVERSIAL_ALL_URL = "t=all";
	    public static final String[] SORT_BY_CONTROVERSIAL_CHOICES = {SORT_BY_CONTROVERSIAL_HOUR, SORT_BY_CONTROVERSIAL_DAY,
	    	SORT_BY_CONTROVERSIAL_WEEK, SORT_BY_CONTROVERSIAL_MONTH, SORT_BY_CONTROVERSIAL_YEAR, SORT_BY_CONTROVERSIAL_ALL};
	    public static final String[] SORT_BY_CONTROVERSIAL_URL_CHOICES = {SORT_BY_CONTROVERSIAL_HOUR_URL, SORT_BY_CONTROVERSIAL_DAY_URL,
	    	SORT_BY_CONTROVERSIAL_WEEK_URL, SORT_BY_CONTROVERSIAL_MONTH_URL, SORT_BY_CONTROVERSIAL_YEAR_URL, SORT_BY_CONTROVERSIAL_ALL_URL};
	    public static final String SORT_BY_TOP_HOUR = "this hour";
	    public static final String SORT_BY_TOP_DAY = "today";
	    public static final String SORT_BY_TOP_WEEK = "this week";
	    public static final String SORT_BY_TOP_MONTH = "this month";
	    public static final String SORT_BY_TOP_YEAR = "this year";
	    public static final String SORT_BY_TOP_ALL = "all time";
	    public static final String SORT_BY_TOP_HOUR_URL = "t=hour";
	    public static final String SORT_BY_TOP_DAY_URL = "t=day";
	    public static final String SORT_BY_TOP_WEEK_URL = "t=week";
	    public static final String SORT_BY_TOP_MONTH_URL = "t=month";
	    public static final String SORT_BY_TOP_YEAR_URL = "t=year";
	    public static final String SORT_BY_TOP_ALL_URL = "t=all";
	    public static final String[] SORT_BY_TOP_CHOICES = {SORT_BY_TOP_HOUR, SORT_BY_TOP_DAY,
	    	SORT_BY_TOP_WEEK, SORT_BY_TOP_MONTH, SORT_BY_TOP_YEAR, SORT_BY_TOP_ALL};
	    public static final String[] SORT_BY_TOP_URL_CHOICES = {SORT_BY_TOP_HOUR_URL, SORT_BY_TOP_DAY_URL,
	    	SORT_BY_TOP_WEEK_URL, SORT_BY_TOP_MONTH_URL, SORT_BY_TOP_YEAR_URL, SORT_BY_TOP_ALL_URL};
    }
    public static final class CommentsSort {
	    public static final String SORT_BY_KEY = "comments_sort_by";
	    public static final String SORT_BY_BEST = "best";
	    public static final String SORT_BY_HOT = "hot";
	    public static final String SORT_BY_NEW = "new";
	    public static final String SORT_BY_CONTROVERSIAL = "controversial";
	    public static final String SORT_BY_TOP = "top";
	    public static final String SORT_BY_OLD = "old";
	    public static final String SORT_BY_BEST_URL = "sort=confidence";
	    public static final String SORT_BY_HOT_URL = "sort=hot";
	    public static final String SORT_BY_NEW_URL = "sort=new";
	    public static final String SORT_BY_CONTROVERSIAL_URL = "sort=controversial";
	    public static final String SORT_BY_TOP_URL = "sort=top";
	    public static final String SORT_BY_OLD_URL = "sort=old";
	    public static final String[] SORT_BY_CHOICES =
	    	{SORT_BY_BEST, SORT_BY_HOT, SORT_BY_NEW,
	    	SORT_BY_CONTROVERSIAL, SORT_BY_TOP, SORT_BY_OLD};
	    public static final String[] SORT_BY_URL_CHOICES =
	    	{SORT_BY_BEST_URL, SORT_BY_HOT_URL, SORT_BY_NEW_URL,
	    	SORT_BY_CONTROVERSIAL_URL, SORT_BY_TOP_URL, SORT_BY_OLD_URL};
    }
    
    
    // JSON values
    public static final String JSON_AFTER = "after";
    public static final String JSON_AUTHOR = "author";
    public static final String JSON_BEFORE = "before";
    public static final String JSON_BODY = "body";
    public static final String JSON_CHILDREN = "children";
    public static final String JSON_DATA = "data";
    public static final String JSON_ERRORS = "errors";
    public static final String JSON_JSON = "json";
    public static final String JSON_KIND = "kind";
    public static final String JSON_LISTING = "Listing";
    public static final String JSON_MEDIA = "media";
    public static final String JSON_MEDIA_EMBED = "media_embed";
    public static final String JSON_MODHASH = "modhash";
    public static final String JSON_NEW = "new";
    public static final String JSON_NUM_COMMENTS = "num_comments";
    public static final String JSON_TITLE = "title";
    public static final String JSON_SUBREDDIT = "subreddit";
	public static final String JSON_REPLIES = "replies";
	public static final String JSON_SELFTEXT = "selftext";
	public static final String JSON_SELFTEXT_HTML = "selftext_html";
	public static final String JSON_SUBJECT = "subject";
    
    // TabSpec tags
    public static final String TAB_LINK = "tab_link";
    public static final String TAB_TEXT = "tab_text";
    
    // Preference keys and values
    public static final String PREF_HOMEPAGE = "homepage";
    public static final String PREF_USE_EXTERNAL_BROWSER = "use_external_browser";
    public static final String PREF_CONFIRM_QUIT = "confirm_quit";
    public static final String PREF_SAVE_HISTORY = "save_history";
    public static final String PREF_ALWAYS_SHOW_NEXT_PREVIOUS = "always_show_next_previous";
    public static final String PREF_COMMENTS_SORT_BY_URL = "sort_by_url";
    public static final String PREF_THEME = "theme";
    public static final String PREF_THEME_LIGHT = "THEME_LIGHT";
    public static final String PREF_THEME_DARK	 = "THEME_DARK";
    public static final String PREF_TEXT_SIZE = "text_size";
    public static final String PREF_TEXT_SIZE_MEDIUM = "TEXT_SIZE_MEDIUM";
    public static final String PREF_TEXT_SIZE_LARGE = "TEXT_SIZE_LARGE";
    public static final String PREF_TEXT_SIZE_LARGER = "TEXT_SIZE_LARGER";
    public static final String PREF_TEXT_SIZE_HUGE = "TEXT_SIZE_HUGE";
    public static final String PREF_SHOW_COMMENT_GUIDE_LINES = "show_comment_guide_lines";
    public static final String PREF_ROTATION = "rotation";
    public static final String PREF_ROTATION_UNSPECIFIED = "ROTATION_UNSPECIFIED";
    public static final String PREF_ROTATION_PORTRAIT = "ROTATION_PORTRAIT";
    public static final String PREF_ROTATION_LANDSCAPE = "ROTATION_LANDSCAPE";
    public static final String PREF_LOAD_THUMBNAILS = "load_thumbnails";
    public static final String PREF_LOAD_THUMBNAILS_ONLY_WIFI = "load_thumbnails_only_wifi";
    public static final String PREF_MAIL_NOTIFICATION_STYLE = "mail_notification_style";
    public static final String PREF_MAIL_NOTIFICATION_STYLE_DEFAULT = "MAIL_NOTIFICATION_STYLE_DEFAULT";
    public static final String PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE = "MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE";
    public static final String PREF_MAIL_NOTIFICATION_STYLE_OFF = "MAIL_NOTIFICATION_STYLE_OFF";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE = "mail_notification_service";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE_OFF = "MAIL_NOTIFICATION_SERVICE_OFF";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE_5MIN = "MAIL_NOTIFICATION_SERVICE_5MIN";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE_30MIN = "MAIL_NOTIFICATION_SERVICE_30MIN";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE_1HOUR = "MAIL_NOTIFICATION_SERVICE_1HOUR";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE_6HOURS = "MAIL_NOTIFICATION_SERVICE_6HOURS";
    public static final String PREF_MAIL_NOTIFICATION_SERVICE_1DAY = "MAIL_NOTIFICATION_SERVICE_1DAY";
    
    // Reddit's base URL, without trailing slash
    public static final String REDDIT_BASE_URL = "http://www.reddit.com";
    public static final String REDDIT_SSL_BASE_URL = "https://pay.reddit.com";
	public static final String REDDIT_LOGIN_URL = "https://ssl.reddit.com/api/login";
	
    // A short HTML file returned by reddit, so we can get the modhash
    public static final String MODHASH_URL = REDDIT_BASE_URL + "/r";
}
