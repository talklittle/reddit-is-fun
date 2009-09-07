/*
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

import java.util.regex.Pattern;

import android.app.Activity;

public class Constants {
	
	static final boolean LOGGING = false;

	static final String COMMENT_KIND = "t1";
	static final String THREAD_KIND = "t3";
	static final String MESSAGE_KIND = "t4";
	static final String SUBREDDIT_KIND = "t5";
	
	// Requires a non-default font
	static final String LOOK_OF_DISAPPROVAL = "\u0ca0\u005f\u0ca0";

	static final int DEFAULT_THREAD_DOWNLOAD_LIMIT = 25;
    static final int DEFAULT_COMMENT_DOWNLOAD_LIMIT = 200;
    
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
    // Tell RedditCommentsListActivity to jump to a comment context
    static final String EXTRA_COMMENT_CONTEXT = "jumpToComment";
    
    // User-defined result codes
    static final int RESULT_LOGIN_REQUIRED = Activity.RESULT_FIRST_USER;
    
    // Menu and dialog actions
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
    static final int DIALOG_SORT_BY = 20;
    static final int DIALOG_SORT_BY_NEW = 21;
    static final int DIALOG_SORT_BY_CONTROVERSIAL = 22;
    static final int DIALOG_SORT_BY_TOP = 23;
    static final int DIALOG_COMMENT_CLICK = 24;
    static final int DIALOG_MESSAGE_CLICK = 25;
    static final int DIALOG_REPLYING = 26;
    static final int DIALOG_LOADING_REDDITS_LIST = 27;
    static final int DIALOG_GOTO_PARENT = 28;
    
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
    
    // save instance state Bundle keys
    static final String URL_TO_GET_HERE_KEY = "url_to_get_here";
    static final String JUMP_TO_COMMENT_POSITION_KEY = "jump_to_comment_position";
    static final String IS_SUBCLASS_KEY = "is_subclass";
    
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
	    static final String SORT_BY_HOT = "hot";
	    static final String SORT_BY_NEW = "new";
	    static final String SORT_BY_CONTROVERSIAL = "controversial";
	    static final String SORT_BY_TOP = "top";
	    static final String SORT_BY_OLD = "old";
	    static final String SORT_BY_HOT_URL = "sort=hot";
	    static final String SORT_BY_NEW_URL = "sort=new";
	    static final String SORT_BY_CONTROVERSIAL_URL = "sort=controversial";
	    static final String SORT_BY_TOP_URL = "sort=top";
	    static final String SORT_BY_OLD_URL = "sort=old";
	    static final CharSequence[] SORT_BY_CHOICES = {SORT_BY_HOT, SORT_BY_NEW, SORT_BY_CONTROVERSIAL, SORT_BY_TOP, SORT_BY_OLD};
    }
    
    
    // JSON values
    static final String JSON_AFTER = "after";
    static final String JSON_AUTHOR = "author";
    static final String JSON_BEFORE = "before";
    static final String JSON_BODY = "body";
    static final String JSON_CHILDREN = "children";
    static final String JSON_DATA = "data";
    static final String JSON_KIND = "kind";
    static final String JSON_LISTING = "Listing";
    static final String JSON_MEDIA = "media";
    static final String JSON_MEDIA_EMBED = "media_embed";
    static final String JSON_MORE = "more";
    static final String JSON_NEW = "new";
    static final String JSON_NUM_COMMENTS = "num_comments";
	static final String JSON_REPLIES = "replies";
	static final String JSON_SUBJECT = "subject";
    
    // TabSpec tags
    static final String TAB_LINK = "tab_link";
    static final String TAB_TEXT = "tab_text";
    
    // Preference keys and values
    static final String PREF_HOMEPAGE = "homepage";
    static final String PREF_THEME = "theme";
    static final String PREF_THEME_LIGHT = "THEME_LIGHT";
    static final String PREF_THEME_DARK	 = "THEME_DARK";
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
    
    // The pattern to find modhash from HTML javascript area
    static final Pattern MODHASH_PATTERN = Pattern.compile("modhash: '(.*?)'");
    // Group 1: fullname. Group 2: kind. Group 3: id36.
    static final Pattern NEW_ID_PATTERN = Pattern.compile("\"id\": \"((.+?)_(.+?))\"");
    // Group 1: Subreddit. Group 2: thread id (no t3_ prefix)
    static final Pattern NEW_THREAD_PATTERN = Pattern.compile("\"http://www.reddit.com/r/(.+?)/comments/(.+?)/.*?/\"");
    // Group 2: subreddit name. Group 3: thread id36. Group 4: Comment id36.
    static final Pattern COMMENT_CONTEXT_PATTERN = Pattern.compile("(http://www.reddit.com)?/r/(.+?)/comments/(.+?)/.+?/([a-zA-Z0-9]+)");
    // Group 1: whole error. Group 2: the time part
    static final Pattern RATELIMIT_RETRY_PATTERN = Pattern.compile("(you are trying to submit too fast. try again in (.+?)\\.)");
    // Captcha "iden"
    static final Pattern CAPTCHA_IDEN_PATTERN = Pattern.compile("name=\"iden\" value=\"(.*?)\"");
    // Group 2: Captcha image absolute path
    static final Pattern CAPTCHA_IMAGE_PATTERN = Pattern.compile("<img class=\"capimage\"( alt=\".*?\")? src=\"(.+?)\"");
    // Group 1: inner
    static final Pattern MY_SUBREDDITS_OUTER = Pattern.compile("your front page reddits.*?<ul>(.*?)</ul>");
    // Group 3: subreddit name. Repeat the matcher.find() until it fails.
    static final Pattern MY_SUBREDDITS_INNER = Pattern.compile("<a(.*?)/r/(.*?)>(.+?)</a>");
}
