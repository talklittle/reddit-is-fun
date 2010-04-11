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

package com.andrewshu.android.reddit;

import android.app.Activity;

public class Constants {
	
	static final boolean LOGGING = false;
	
	// File containing the serialized variables of last subreddit viewed
	static final String FILENAME_SUBREDDIT_CACHE = "subreddit.dat";
	// File containing the serialized variables of last comments viewed
	static final String FILENAME_COMMENTS_CACHE = "comments.dat";
	// File containing a long integer System.currentTimeMillis(). Timestamp is shared among caches.
	static final String FILENAME_CACHE_TIME = "cachetime.dat";
	
	static final String COMMENT_KIND = "t1";
	static final String THREAD_KIND = "t3";
	static final String MESSAGE_KIND = "t4";
	static final String SUBREDDIT_KIND = "t5";
	
	// Requires a non-default font
	static final String LOOK_OF_DISAPPROVAL = "\u0ca0\u005f\u0ca0";

	static final int DEFAULT_THREAD_DOWNLOAD_LIMIT = 25;
    static final int DEFAULT_COMMENT_DOWNLOAD_LIMIT = 200;
    static final long DEFAULT_FRESH_DURATION = 1800000;  // 30 minutes

    // startActivityForResult request codes
    static final int ACTIVITY_PICK_SUBREDDIT = 0;
    static final int ACTIVITY_SUBMIT_LINK = 1;
    
    // notifications
    static final int NOTIFICATION_HAVE_MAIL = 0;
    
    // services
    static final int SERVICE_ENVELOPE = 0;
    
    // --- Intent extras ---
    // Tell PickSubredditActivity to hide the fronptage string
    static final String EXTRA_HIDE_FRONTPAGE_STRING = "hideFrontpage";
    // Tell CommentsListActivity to jump to a comment context (a URL. pattern match)
    static final String EXTRA_COMMENT_CONTEXT = "jumpToComment";
    // Tell CommentsListActivity to show "more children"
    static final String EXTRA_MORE_CHILDREN_ID = "moreChildrenId";
    
    // User-defined result codes
    static final int RESULT_LOGIN_REQUIRED = Activity.RESULT_FIRST_USER;
    
    // Menu and dialog actions
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_SUBMIT_LINK = 5;
    static final int DIALOG_THING_CLICK = 6;
    static final int DIALOG_OPEN_BROWSER = 11;
    static final int DIALOG_THEME = 12;
    static final int DIALOG_OP = 13;
    static final int DIALOG_REPLY = 14;
    static final int DIALOG_DOWNLOAD_CAPTCHA = 16;
    static final int DIALOG_HIDE_COMMENT = 17;
    static final int DIALOG_SHOW_COMMENT = 18;
    static final int DIALOG_SORT_BY = 20;
    static final int DIALOG_SORT_BY_NEW = 21;
    static final int DIALOG_SORT_BY_CONTROVERSIAL = 22;
    static final int DIALOG_SORT_BY_TOP = 23;
    static final int DIALOG_COMMENT_CLICK = 24;
    static final int DIALOG_MESSAGE_CLICK = 25;
    static final int DIALOG_GOTO_PARENT = 28;
    static final int DIALOG_EDIT = 29;
    static final int DIALOG_DELETE = 30;
    static final int DIALOG_COMPOSE = 31;
    // progress dialogs
    static final int DIALOG_LOGGING_IN = 1000;
    static final int DIALOG_LOADING_THREADS_LIST = 1001;
    static final int DIALOG_LOADING_COMMENTS_LIST = 1002;
    static final int DIALOG_LOADING_LOOK_OF_DISAPPROVAL = 1003;
    static final int DIALOG_SUBMITTING = 1004;
    static final int DIALOG_REPLYING = 1005;
    static final int DIALOG_LOADING_REDDITS_LIST = 1006;
    static final int DIALOG_LOADING_INBOX = 1007;
    static final int DIALOG_DELETING = 1008;
    static final int DIALOG_EDITING = 1009;
    static final int DIALOG_LOADING_THREADS_CACHE = 1010;
    static final int DIALOG_LOADING_COMMENTS_CACHE = 1011;
    static final int DIALOG_COMPOSING = 1012;
    
    // Special CSS for webviews to match themes
    static final String CSS_DARK = "<style>body{color:#c0c0c0;background-color:#191919}a:link{color:#ffffff}</style>";

    // Colors for markdown
    static final int MARKDOWN_LINK_COLOR = 0xff2288cc;
    
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
    
    // save instance state Bundle keys
    static final String URL_TO_GET_HERE_KEY = "url_to_get_here";
    static final String JUMP_TO_COMMENT_POSITION_KEY = "jump_to_comment_position";
    static final String JUMP_TO_COMMENT_ID_KEY = "jump_to_comment_id";
    static final String JUMP_TO_THREAD_ID_KEY = "jump_to_thread_id";
    static final String REPLY_TARGET_NAME_KEY = "reply_target_name";
    static final String EDIT_TARGET_BODY_KEY = "edit_target_body";
    static final String DELETE_TARGET_KIND_KEY = "delete_target_kind";
    
    static final String THREAD_COUNT = "threadCount";
    
    static final String SUBMIT_KIND_LINK = "link";
    static final String SUBMIT_KIND_SELF = "self";
    static final String SUBMIT_KIND_POLL = "poll";
    
    // Sorting things
    static final class ThreadsSort {
	    static final String SORT_BY_KEY = "threads_sort_by";
	    static final String SORT_BY_HOT = "hot";
	    static final String SORT_BY_NEW = "new";
	    static final String SORT_BY_CONTROVERSIAL = "controversial";
	    static final String SORT_BY_TOP = "top";
	    static final String SORT_BY_HOT_URL = "";
	    static final String SORT_BY_NEW_URL = "new/";
	    static final String SORT_BY_CONTROVERSIAL_URL = "controversial/";
	    static final String SORT_BY_TOP_URL = "top/";
	    static final CharSequence[] SORT_BY_CHOICES = {SORT_BY_HOT, SORT_BY_NEW, SORT_BY_CONTROVERSIAL, SORT_BY_TOP};
	    static final String SORT_BY_NEW_NEW = "new";
	    static final String SORT_BY_NEW_RISING = "rising";
	    static final String SORT_BY_NEW_NEW_URL = "sort=new";
	    static final String SORT_BY_NEW_RISING_URL = "sort=rising";
	    static final CharSequence[] SORT_BY_NEW_CHOICES = {SORT_BY_NEW_NEW, SORT_BY_NEW_RISING};
	    static final String SORT_BY_CONTROVERSIAL_HOUR = "this hour";
	    static final String SORT_BY_CONTROVERSIAL_DAY = "today";
	    static final String SORT_BY_CONTROVERSIAL_WEEK = "this week";
	    static final String SORT_BY_CONTROVERSIAL_MONTH = "this month";
	    static final String SORT_BY_CONTROVERSIAL_YEAR = "this year";
	    static final String SORT_BY_CONTROVERSIAL_ALL = "all time";
	    static final String SORT_BY_CONTROVERSIAL_HOUR_URL = "t=hour";
	    static final String SORT_BY_CONTROVERSIAL_DAY_URL = "t=day";
	    static final String SORT_BY_CONTROVERSIAL_WEEK_URL = "t=week";
	    static final String SORT_BY_CONTROVERSIAL_MONTH_URL = "t=month";
	    static final String SORT_BY_CONTROVERSIAL_YEAR_URL = "t=year";
	    static final String SORT_BY_CONTROVERSIAL_ALL_URL = "t=all";
	    static final CharSequence[] SORT_BY_CONTROVERSIAL_CHOICES = {SORT_BY_CONTROVERSIAL_HOUR, SORT_BY_CONTROVERSIAL_DAY,
	    	SORT_BY_CONTROVERSIAL_WEEK, SORT_BY_CONTROVERSIAL_MONTH, SORT_BY_CONTROVERSIAL_YEAR, SORT_BY_CONTROVERSIAL_ALL};
	    static final String SORT_BY_TOP_HOUR = "this hour";
	    static final String SORT_BY_TOP_DAY = "today";
	    static final String SORT_BY_TOP_WEEK = "this week";
	    static final String SORT_BY_TOP_MONTH = "this month";
	    static final String SORT_BY_TOP_YEAR = "this year";
	    static final String SORT_BY_TOP_ALL = "all time";
	    static final String SORT_BY_TOP_HOUR_URL = "t=hour";
	    static final String SORT_BY_TOP_DAY_URL = "t=day";
	    static final String SORT_BY_TOP_WEEK_URL = "t=week";
	    static final String SORT_BY_TOP_MONTH_URL = "t=month";
	    static final String SORT_BY_TOP_YEAR_URL = "t=year";
	    static final String SORT_BY_TOP_ALL_URL = "t=all";
	    static final CharSequence[] SORT_BY_TOP_CHOICES = {SORT_BY_TOP_HOUR, SORT_BY_TOP_DAY,
	    	SORT_BY_TOP_WEEK, SORT_BY_TOP_MONTH, SORT_BY_TOP_YEAR, SORT_BY_TOP_ALL};
    }
    static final class CommentsSort {
	    static final String SORT_BY_KEY = "comments_sort_by";
	    static final String SORT_BY_BEST = "best";
	    static final String SORT_BY_HOT = "hot";
	    static final String SORT_BY_NEW = "new";
	    static final String SORT_BY_CONTROVERSIAL = "controversial";
	    static final String SORT_BY_TOP = "top";
	    static final String SORT_BY_OLD = "old";
	    static final String SORT_BY_BEST_URL = "sort=confidence";
	    static final String SORT_BY_HOT_URL = "sort=hot";
	    static final String SORT_BY_NEW_URL = "sort=new";
	    static final String SORT_BY_CONTROVERSIAL_URL = "sort=controversial";
	    static final String SORT_BY_TOP_URL = "sort=top";
	    static final String SORT_BY_OLD_URL = "sort=old";
	    static final CharSequence[] SORT_BY_CHOICES =
	    	{SORT_BY_BEST, SORT_BY_HOT, SORT_BY_NEW,
	    	SORT_BY_CONTROVERSIAL, SORT_BY_TOP, SORT_BY_OLD};
    }
    
    
    // JSON values
    static final String JSON_AFTER = "after";
    static final String JSON_AUTHOR = "author";
    static final String JSON_BEFORE = "before";
    static final String JSON_BODY = "body";
    static final String JSON_CHILDREN = "children";
    static final String JSON_DATA = "data";
    static final String JSON_ERRORS = "errors";
    static final String JSON_JSON = "json";
    static final String JSON_KIND = "kind";
    static final String JSON_LISTING = "Listing";
    static final String JSON_MEDIA = "media";
    static final String JSON_MEDIA_EMBED = "media_embed";
    static final String JSON_MODHASH = "modhash";
    static final String JSON_MORE = "more";
    static final String JSON_NEW = "new";
    static final String JSON_NUM_COMMENTS = "num_comments";
    static final String JSON_TITLE = "title";
    static final String JSON_SUBREDDIT = "subreddit";
	static final String JSON_REPLIES = "replies";
	static final String JSON_SELFTEXT = "selftext";
	static final String JSON_SELFTEXT_HTML = "selftext_html";
	static final String JSON_SUBJECT = "subject";
    
    // TabSpec tags
    static final String TAB_LINK = "tab_link";
    static final String TAB_TEXT = "tab_text";
    
    // Preference keys and values
    static final String PREF_HOMEPAGE = "homepage";
    static final String PREF_THEME = "theme";
    static final String PREF_THEME_LIGHT = "THEME_LIGHT";
    static final String PREF_THEME_DARK	 = "THEME_DARK";
    static final String PREF_ROTATION = "rotation";
    static final String PREF_ROTATION_UNSPECIFIED = "ROTATION_UNSPECIFIED";
    static final String PREF_ROTATION_PORTRAIT = "ROTATION_PORTRAIT";
    static final String PREF_ROTATION_LANDSCAPE = "ROTATION_LANDSCAPE";
    static final String PREF_MAIL_NOTIFICATION_STYLE = "mail_notification_style";
    static final String PREF_MAIL_NOTIFICATION_STYLE_DEFAULT = "MAIL_NOTIFICATION_STYLE_DEFAULT";
    static final String PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE = "MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE";
    static final String PREF_MAIL_NOTIFICATION_STYLE_OFF = "MAIL_NOTIFICATION_STYLE_OFF";
    static final String PREF_MAIL_NOTIFICATION_SERVICE = "mail_notification_service";
    static final String PREF_MAIL_NOTIFICATION_SERVICE_OFF = "MAIL_NOTIFICATION_SERVICE_OFF";
    static final String PREF_MAIL_NOTIFICATION_SERVICE_5MIN = "MAIL_NOTIFICATION_SERVICE_5MIN";
    static final String PREF_MAIL_NOTIFICATION_SERVICE_30MIN = "MAIL_NOTIFICATION_SERVICE_30MIN";
    static final String PREF_MAIL_NOTIFICATION_SERVICE_1HOUR = "MAIL_NOTIFICATION_SERVICE_1HOUR";
    static final String PREF_MAIL_NOTIFICATION_SERVICE_6HOURS = "MAIL_NOTIFICATION_SERVICE_6HOURS";
    static final String PREF_MAIL_NOTIFICATION_SERVICE_1DAY = "MAIL_NOTIFICATION_SERVICE_1DAY";
    
    // A short HTML file returned by reddit, so we can get the modhash
    static final String MODHASH_URL = "http://www.reddit.com/r";
}
