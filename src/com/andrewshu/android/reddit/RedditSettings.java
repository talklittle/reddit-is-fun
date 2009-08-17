package com.andrewshu.android.reddit;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.os.Handler;

/**
 * Common settings
 * @author Andrew
 *
 */
public class RedditSettings {
	// The Activity that these settings belong to.
	// The RedditSettings object is not transferred between Activities directly.
	Activity activity;
	
	boolean loggedIn = false;
	CharSequence username = null;
	Cookie redditSessionCookie = null;
	
	// --- Themes ---
	int theme = Constants.THEME_LIGHT;
	int themeResId = android.R.style.Theme_Light;
	
	// --- Ephemeral objects. Things that are reused within a session but not saved across sessions. ---
	// Handler is used to post stuff to the UI thread that created this RedditSettings object
	Handler handler = new Handler();
	DefaultHttpClient client = new DefaultHttpClient();
	String modhash = null;
	// Tell whether the Activity is alive
	boolean isAlive = true;
	
	// --- States that change frequently. ---
	CharSequence subreddit;
	CharSequence threadId;  // Does not change for a given commentslist (OP is static)
	
	//
	// --- Methods ---
	//
	
	RedditSettings(Activity activity) {
		this.activity = activity;
	}
	
	synchronized void setActivity(Activity activity) {
		this.activity = activity;
	}
	
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
	
	synchronized void setModhash(String modhash) {
		this.modhash = modhash;
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
