package com.andrewshu.android.reddit;

import java.util.Date;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

import android.app.Activity;
import android.content.SharedPreferences;

public class Common {
    static void saveRedditPreferences(Activity act, RedditSettings rSettings) {
    	SharedPreferences settings = act.getSharedPreferences(Constants.PREFS_SESSION, 0);
    	SharedPreferences.Editor editor = settings.edit();
    	editor.clear();
    	if (rSettings.loggedIn) {
	    	if (rSettings.username != null)
	    		editor.putString("username", rSettings.username.toString());
	    	if (rSettings.redditSessionCookie != null) {
	    		editor.putString("reddit_sessionValue",  rSettings.redditSessionCookie.getValue());
	    		editor.putString("reddit_sessionDomain", rSettings.redditSessionCookie.getDomain());
	    		editor.putString("reddit_sessionPath",   rSettings.redditSessionCookie.getPath());
	    		if (rSettings.redditSessionCookie.getExpiryDate() != null)
	    			editor.putLong("reddit_sessionExpiryDate", rSettings.redditSessionCookie.getExpiryDate().getTime());
	    	}
    	}
    	editor.commit();
    	
    	settings = act.getSharedPreferences(Constants.PREFS_THEME, 0);
    	editor = settings.edit();
    	editor.clear();
    	switch (rSettings.theme) {
    	case Constants.THEME_DARK:
    		editor.putInt("theme", Constants.THEME_DARK);
    		editor.putInt("theme_resid", android.R.style.Theme);
    		break;
    	default:
    		editor.putInt("theme", Constants.THEME_LIGHT);
    		editor.putInt("theme_resid", android.R.style.Theme_Light);
    	}
    	editor.commit();
    }
    
    static void loadRedditPreferences(Activity act, RedditSettings rSettings, DefaultHttpClient httpclient) {
        // Retrieve the stored session info
        SharedPreferences sessionPrefs = act.getSharedPreferences(Constants.PREFS_SESSION, 0);
        rSettings.username = sessionPrefs.getString("username", null);
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
        	rSettings.redditSessionCookie = redditSessionCookie;
        	if (httpclient != null)
        		httpclient.getCookieStore().addCookie(redditSessionCookie);
        	rSettings.loggedIn = true;
        }
        
        sessionPrefs = act.getSharedPreferences(Constants.PREFS_THEME, 0);
        rSettings.theme = sessionPrefs.getInt("theme", Constants.THEME_LIGHT);
        rSettings.themeResId = sessionPrefs.getInt("theme_resid", android.R.style.Theme_Light);
    }

}
