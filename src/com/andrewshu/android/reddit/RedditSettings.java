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
	CharSequence homepage = Constants.FRONTPAGE_STRING;
	
	int threadDownloadLimit = Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
	
	// --- Themes ---
	int theme = R.style.Reddit_Light;
	
	String mailNotificationStyle = Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT;
	String mailNotificationService = Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF;
	
	// --- States that change frequently. ---
	CharSequence subreddit;
	boolean isFrontpage = false;
	CharSequence threadId;  // Does not change for a given commentslist (OP is static)
	
	
	//
	// --- Methods ---
	//
	
	// --- Preferences ---
	// Don't use a HashMap because that would need to be initialized many times
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
	
	synchronized void setHomepage(CharSequence homepage) {
		this.homepage = homepage;
	}
	
	synchronized void setLoggedIn(boolean loggedIn) {
		this.loggedIn = loggedIn;
	}
	
	synchronized void setMailNotificationService(String mailNotificationService) {
		this.mailNotificationService = mailNotificationService;
	}
	
	
	synchronized void setMailNotificationStyle(String mailNotificationStyle) {
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
