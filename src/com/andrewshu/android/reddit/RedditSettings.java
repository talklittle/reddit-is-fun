package com.andrewshu.android.reddit;

import org.apache.http.cookie.Cookie;

/**
 * Common settings
 * @author Andrew
 *
 */
public class RedditSettings {
	boolean loggedIn = false;
	CharSequence username = null;
	Cookie redditSessionCookie = null;
	
	int threadDownloadLimit = Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
	
	// --- Themes ---
	int theme = Constants.THEME_LIGHT;
	int themeResId = android.R.style.Theme_Light;
	
	int mailNotificationStyle = Constants.MAIL_NOTIFICATION_STYLE_DEFAULT;
	
	// --- States that change frequently. ---
	CharSequence subreddit;
	boolean isFrontpage = false;
	CharSequence threadId;  // Does not change for a given commentslist (OP is static)
	
	
	//
	// --- Methods ---
	//
	
	// --- Preferences ---
	// Don't use a HashMap because that would need to be initialized many times
	public static class MailNotificationStyle {
		public static int valueOf(String valueString) {
			if (Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT.equals(valueString))
				return Constants.MAIL_NOTIFICATION_STYLE_DEFAULT;
			if (Constants.PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE.equals(valueString))
				return Constants.MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE;
			if (Constants.PREF_MAIL_NOTIFICATION_STYLE_OFF.equals(valueString))
				return Constants.MAIL_NOTIFICATION_STYLE_OFF;
			return -1;	
		}
	}
	public static class Theme {
		public static int valueOf(String valueString) {
			if (Constants.PREF_THEME_LIGHT.equals(valueString))
				return Constants.THEME_LIGHT;
			if (Constants.PREF_THEME_DARK.equals(valueString))
				return Constants.THEME_DARK;
			return -1;
		}
	}
	
	// --- Synchronized Setters ---
	
	synchronized void setLoggedIn(boolean loggedIn) {
		this.loggedIn = loggedIn;
	}
	
	synchronized void setMailNotificationStyle(int mailNotificationStyle) {
		this.mailNotificationStyle = mailNotificationStyle;
	}
	
	synchronized void setUsername(CharSequence username) {
		this.username = username;
	}
	
	synchronized void setRedditSessionCookie(Cookie redditSessionCookie) {
		this.redditSessionCookie = redditSessionCookie;
	}
	
	synchronized void setThreadDownloadLimit(int threadDownloadLimit) {
		this.threadDownloadLimit = threadDownloadLimit;
	}

	synchronized void setTheme(int theme) {
		this.theme = theme;
	}
	
	synchronized void setThemeResId(int themeResId) {
		this.themeResId = themeResId;
	}
	
	synchronized void setSubreddit(CharSequence subreddit) {
		this.subreddit = subreddit;
		isFrontpage = Constants.FRONTPAGE_STRING.equals(subreddit);
	}
	
	synchronized void setThreadId(CharSequence threadId) {
		this.threadId = threadId;
	}
}
