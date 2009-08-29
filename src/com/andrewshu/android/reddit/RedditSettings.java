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
	int theme = R.style.Reddit_Light;
	
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
			return Constants.MAIL_NOTIFICATION_STYLE_DEFAULT;
		}
		public static String toString(int value) {
			switch (value) {
			case Constants.MAIL_NOTIFICATION_STYLE_DEFAULT:
				return Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT;
			case Constants.MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE:
				return Constants.PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE;
			case Constants.MAIL_NOTIFICATION_STYLE_OFF:
				return Constants.PREF_MAIL_NOTIFICATION_STYLE_OFF;
			default:
				return Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT;
			}
		}
	}
	public static class Theme {
		public static int valueOf(String valueString) {
			if (Constants.PREF_THEME_LIGHT.equals(valueString))
				return R.style.Reddit_Light;
			if (Constants.PREF_THEME_DARK.equals(valueString))
				return R.style.Reddit_Dark;
			return R.style.Reddit_Light;
		}
		public static String toString(int value) {
			switch (value) {
			case R.style.Reddit_Light:
				return Constants.PREF_THEME_LIGHT;
			case R.style.Reddit_Dark:
				return Constants.PREF_THEME_DARK;
			default:
				return Constants.PREF_THEME_LIGHT;
			}
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
	
	synchronized void setSubreddit(CharSequence subreddit) {
		this.subreddit = subreddit;
		isFrontpage = Constants.FRONTPAGE_STRING.equals(subreddit);
	}
	
	synchronized void setThreadId(CharSequence threadId) {
		this.threadId = threadId;
	}
}
