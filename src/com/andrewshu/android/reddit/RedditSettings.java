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
	
	// Tell whether the Activity is alive
	boolean isAlive = true;
	
	// --- States that change frequently. ---
	CharSequence subreddit;
	CharSequence threadId;  // Does not change for a given commentslist (OP is static)
	
	//
	// --- Methods ---
	//
	
	synchronized void setLoggedIn(boolean loggedIn) {
		this.loggedIn = loggedIn;
	}
	
	synchronized void setUsername(CharSequence username) {
		this.username = username;
	}
	
	synchronized void setRedditSessionCookie(Cookie redditSessionCookie) {
		this.redditSessionCookie = redditSessionCookie;
	}
	
	synchronized void setTheme(int theme) {
		this.theme = theme;
	}
	
	synchronized void setThemeResId(int themeResId) {
		this.themeResId = themeResId;
	}
	
	synchronized void setIsAlive(boolean isAlive) {
		this.isAlive = isAlive;
	}
	
	synchronized void setSubreddit(CharSequence subreddit) {
		this.subreddit = subreddit;
	}
	
	synchronized void setThreadId(CharSequence threadId) {
		this.threadId = threadId;
	}
}
