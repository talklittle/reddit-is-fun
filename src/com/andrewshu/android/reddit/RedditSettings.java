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

import java.util.Date;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.CookieSyncManager;

/**
 * Common settings
 * @author Andrew
 *
 */
public class RedditSettings {
	
	private static final String TAG = "RedditSettings";
	
	String username = null;
	Cookie redditSessionCookie = null;
	String modhash = null;
	String homepage = Constants.FRONTPAGE_STRING;
	boolean useExternalBrowser = false;
	boolean showCommentGuideLines = true;
	boolean confirmQuit = true;
	boolean alwaysShowNextPrevious = true;
	
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
	
	// --- Query ---
	
	boolean isLoggedIn() {
		return username != null;
	}
	
	// --- Setters ---

	void setAlwaysShowNextPrevious(boolean alwaysShowNextPrevious) {
		this.alwaysShowNextPrevious = alwaysShowNextPrevious;
	}
	
	void setCommentsSortByUrl(String commentsSortByUrl) {
		this.commentsSortByUrl = commentsSortByUrl;
	}
	
	void setConfirmQuit(boolean confirmQuit) {
		this.confirmQuit = confirmQuit;
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
	
	void setShowCommentGuideLines(boolean showCommentGuideLines) {
		this.showCommentGuideLines = showCommentGuideLines;
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
	
    public void saveRedditPreferences(Context context) {
    	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    	SharedPreferences.Editor editor = settings.edit();
    	
    	// Session
    	if (this.username != null)
    		editor.putString("username", this.username);
    	else
    		editor.remove("username");
    	if (this.redditSessionCookie != null) {
    		editor.putString("reddit_sessionValue",  this.redditSessionCookie.getValue());
    		editor.putString("reddit_sessionDomain", this.redditSessionCookie.getDomain());
    		editor.putString("reddit_sessionPath",   this.redditSessionCookie.getPath());
    		if (this.redditSessionCookie.getExpiryDate() != null)
    			editor.putLong("reddit_sessionExpiryDate", this.redditSessionCookie.getExpiryDate().getTime());
    	}
    	if (this.modhash != null)
    		editor.putString("modhash", this.modhash.toString());
    	
    	// Default subreddit
    	editor.putString(Constants.PREF_HOMEPAGE, this.homepage.toString());
    	
    	// Use external browser instead of BrowserActivity
    	editor.putBoolean(Constants.PREF_USE_EXTERNAL_BROWSER, this.useExternalBrowser);
    	
    	// Show confirmation dialog when backing out of root Activity
    	editor.putBoolean(Constants.PREF_CONFIRM_QUIT, this.confirmQuit);
    	
    	// Whether to always show the next/previous buttons, or only at bottom of list
    	editor.putBoolean(Constants.PREF_ALWAYS_SHOW_NEXT_PREVIOUS, this.alwaysShowNextPrevious);
    	
    	// Comments sort order
    	editor.putString(Constants.PREF_COMMENTS_SORT_BY_URL, this.commentsSortByUrl);
    	
    	// Theme and text size
    	String[] themeTextSize = Util.getPrefsFromThemeResource(this.theme);
    	editor.putString(Constants.PREF_THEME, themeTextSize[0]);
    	editor.putString(Constants.PREF_TEXT_SIZE, themeTextSize[1]);
    	
    	// Comment guide lines
    	editor.putBoolean(Constants.PREF_SHOW_COMMENT_GUIDE_LINES, this.showCommentGuideLines);
    	
    	// Rotation
    	editor.putString(Constants.PREF_ROTATION, RedditSettings.Rotation.toString(this.rotation));
    	
    	// Thumbnails
    	editor.putBoolean(Constants.PREF_LOAD_THUMBNAILS, this.loadThumbnails);
    	editor.putBoolean(Constants.PREF_LOAD_THUMBNAILS_ONLY_WIFI, this.loadThumbnailsOnlyWifi);
    	
    	// Notifications
    	editor.putString(Constants.PREF_MAIL_NOTIFICATION_STYLE, this.mailNotificationStyle);
    	editor.putString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, this.mailNotificationService);

    	editor.commit();
    }
    
    public void loadRedditPreferences(Context context, DefaultHttpClient client) {
        // Session
    	SharedPreferences sessionPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    	this.setUsername(sessionPrefs.getString("username", null));
    	this.setModhash(sessionPrefs.getString("modhash", null));
        String cookieValue = sessionPrefs.getString("reddit_sessionValue", null);
        String cookieDomain = sessionPrefs.getString("reddit_sessionDomain", null);
        String cookiePath = sessionPrefs.getString("reddit_sessionPath", null);
        long cookieExpiryDate = sessionPrefs.getLong("reddit_sessionExpiryDate", -1);
        if (cookieValue != null) {
        	BasicClientCookie redditSessionCookie = new BasicClientCookie("reddit_session", cookieValue);
        	redditSessionCookie.setDomain(cookieDomain);
        	redditSessionCookie.setPath(cookiePath);
        	if (cookieExpiryDate != -1)
        		redditSessionCookie.setExpiryDate(new Date(cookieExpiryDate));
        	else
        		redditSessionCookie.setExpiryDate(null);
        	this.setRedditSessionCookie(redditSessionCookie);
        	if (client != null) {
        		client.getCookieStore().addCookie(redditSessionCookie);
        		try {
        			CookieSyncManager.getInstance().sync();
        		} catch (IllegalStateException ex) {
        			if (Constants.LOGGING) Log.e(TAG, "CookieSyncManager.getInstance().sync()", ex);
        		}
        	}
        }
        
        // Default subreddit
        String homepage = sessionPrefs.getString(Constants.PREF_HOMEPAGE, Constants.FRONTPAGE_STRING).trim();
        if (Util.isEmpty(homepage))
        	this.setHomepage(Constants.FRONTPAGE_STRING);
        else
        	this.setHomepage(homepage);
        
    	// Use external browser instead of BrowserActivity
        this.setUseExternalBrowser(sessionPrefs.getBoolean(Constants.PREF_USE_EXTERNAL_BROWSER, false));
        
    	// Show confirmation dialog when backing out of root Activity
        this.setConfirmQuit(sessionPrefs.getBoolean(Constants.PREF_CONFIRM_QUIT, true));
        
    	// Whether to always show the next/previous buttons, or only at bottom of list
        this.setAlwaysShowNextPrevious(sessionPrefs.getBoolean(Constants.PREF_ALWAYS_SHOW_NEXT_PREVIOUS, true));
        
    	// Comments sort order
        this.setCommentsSortByUrl(sessionPrefs.getString(Constants.PREF_COMMENTS_SORT_BY_URL, Constants.CommentsSort.SORT_BY_BEST_URL));
        
        // Theme and text size
        this.setTheme(Util.getThemeResourceFromPrefs(
        		sessionPrefs.getString(Constants.PREF_THEME, Constants.PREF_THEME_LIGHT),
        		sessionPrefs.getString(Constants.PREF_TEXT_SIZE, Constants.PREF_TEXT_SIZE_MEDIUM)));
        
        // Comment guide lines
        this.setShowCommentGuideLines(sessionPrefs.getBoolean(Constants.PREF_SHOW_COMMENT_GUIDE_LINES, true));
        
        // Rotation
        this.setRotation(RedditSettings.Rotation.valueOf(
        		sessionPrefs.getString(Constants.PREF_ROTATION, Constants.PREF_ROTATION_UNSPECIFIED)));
        
        // Thumbnails
        this.setLoadThumbnails(sessionPrefs.getBoolean(Constants.PREF_LOAD_THUMBNAILS, true));
        // Thumbnails on Wifi
        this.setLoadThumbnailsOnlyWifi(sessionPrefs.getBoolean(Constants.PREF_LOAD_THUMBNAILS_ONLY_WIFI, false));
        
        // Notifications
        this.setMailNotificationStyle(sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_STYLE, Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT));
        this.setMailNotificationService(sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF));
    }
    
}
