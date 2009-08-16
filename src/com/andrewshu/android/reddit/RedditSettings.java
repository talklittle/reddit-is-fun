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
	
	// Themes
	int theme = Constants.THEME_LIGHT;
	int themeResId = android.R.style.Theme_Light;
}
