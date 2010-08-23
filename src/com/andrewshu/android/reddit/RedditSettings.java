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

import org.apache.http.cookie.Cookie;

/**
 * Common settings
 * @author Andrew
 *
 */
public class RedditSettings {
	boolean loggedIn = false;
	String username = null;
	Cookie redditSessionCookie = null;
	String modhash = null;
	String homepage = Constants.FRONTPAGE_STRING;
	boolean useExternalBrowser = false;
	
	int threadDownloadLimit = Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
	String commentsSortByUrl = Constants.CommentsSort.SORT_BY_BEST_URL;
	
    
	// --- Themes ---
	int theme = R.style.Reddit_Light_Medium;
	int rotation = -1;  // -1 means unspecified
	boolean loadThumbnails = true;
	boolean loadThumbnailsOnlyWifi = false;
	
	String mailNotificationStyle = Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT;
	String mailNotificationService = Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF;
	
	
	
	//
	// --- Methods ---
	//
	
	// --- Preferences ---
	public static class Rotation {
		/* From http://developer.android.com/reference/android/R.attr.html#screenOrientation
		 * unspecified -1
		 * landscape 0
		 * portrait 1
		 * user 2
		 * behind 3
		 * sensor 4
		 * nosensor 5
		 */
		public static int valueOf(String valueString) {
			if (Constants.PREF_ROTATION_UNSPECIFIED.equals(valueString))
				return -1;
			if (Constants.PREF_ROTATION_PORTRAIT.equals(valueString))
				return 1;
			if (Constants.PREF_ROTATION_LANDSCAPE.equals(valueString))
				return 0;
			return -1;
		}
		public static String toString(int value) {
			switch (value) {
			case -1:
				return Constants.PREF_ROTATION_UNSPECIFIED;
			case 1:
				return Constants.PREF_ROTATION_PORTRAIT;
			case 0:
				return Constants.PREF_ROTATION_LANDSCAPE;
			default:
				return Constants.PREF_ROTATION_UNSPECIFIED;
			}
		}
	}
	
	// --- Setters ---
	
	void setCommentsSortByUrl(String commentsSortByUrl) {
		this.commentsSortByUrl = commentsSortByUrl;
	}
	
	void setHomepage(String homepage) {
		this.homepage = homepage;
	}
	
	void setLoadThumbnails(boolean loadThumbnails) {
		this.loadThumbnails = loadThumbnails;
	}
	
	void setLoadThumbnailsOnlyWifi(boolean loadThumbnails) {
		this.loadThumbnailsOnlyWifi = loadThumbnails;
	}
	
	void setLoggedIn(boolean loggedIn) {
		this.loggedIn = loggedIn;
	}
	
	void setMailNotificationService(String mailNotificationService) {
		this.mailNotificationService = mailNotificationService;
	}
	
	
	void setMailNotificationStyle(String mailNotificationStyle) {
		this.mailNotificationStyle = mailNotificationStyle;
	}
	
	void setModhash(String modhash) {
		this.modhash = modhash;
	}
	
	void setRedditSessionCookie(Cookie redditSessionCookie) {
		this.redditSessionCookie = redditSessionCookie;
	}
	
	void setRotation(int rotation) {
		this.rotation = rotation;
	}
	
	void setTheme(int theme) {
		this.theme = theme;
	}
	
	void setThreadDownloadLimit(int threadDownloadLimit) {
		this.threadDownloadLimit = threadDownloadLimit;
	}

	void setUseExternalBrowser(boolean useExternalBrowser) {
		this.useExternalBrowser = useExternalBrowser;
	}

	void setUsername(String username) {
		this.username = username;
	}
	
}
