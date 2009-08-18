package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.regex.Matcher;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class Common {
	
	private static final String TAG = "Common";
	
	static void showErrorToast(CharSequence error, int duration, Activity activity) {
		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		Toast t = new Toast(activity);
		t.setDuration(duration);
		View v = inflater.inflate(R.layout.error_toast, null);
		TextView errorMessage = (TextView) v.findViewById(R.id.errorMessage);
		errorMessage.setText(error);
		t.setView(v);
		t.show();
	}
	
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
    
    static void loadRedditPreferences(Activity act, RedditSettings rSettings, DefaultHttpClient client) {
        // Retrieve the stored session info
        SharedPreferences sessionPrefs = act.getSharedPreferences(Constants.PREFS_SESSION, 0);
        rSettings.setUsername(sessionPrefs.getString("username", null));
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
        	rSettings.setRedditSessionCookie(redditSessionCookie);
        	if (client != null)
        		client.getCookieStore().addCookie(redditSessionCookie);
        	rSettings.setLoggedIn(true);
        } else {
        	rSettings.setLoggedIn(false);
        }
        
        sessionPrefs = act.getSharedPreferences(Constants.PREFS_THEME, 0);
        rSettings.setTheme(sessionPrefs.getInt("theme", Constants.THEME_LIGHT));
        rSettings.setThemeResId(sessionPrefs.getInt("theme_resid", android.R.style.Theme_Light));
    }
    
        
    static void doLogout(RedditSettings settings, DefaultHttpClient client) {
    	settings.setUsername(null);
        settings.setLoggedIn(false);
    }
    
    
    
    /**
     * Get a new modhash and return it
     * 
     * @param client
     * @return
     */
    static String doUpdateModhash(DefaultHttpClient client) {
    	String modhash;
    	try {
    		String status;
    		
    		HttpGet httpget = new HttpGet(Constants.MODHASH_URL);
    		HttpResponse response = client.execute(httpget);
    		
    		status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	// modhash should appear within first 1200 chars
        	char[] buffer = new char[2048];
        	in.read(buffer, 0, 2048);
        	String line = String.valueOf(buffer);
        	if (line == null) {
        		throw new HttpException("No content returned from doUpdateModhash GET to "+Constants.MODHASH_URL);
        	}
        	if (line.contains("USER_REQUIRED")) {
        		throw new Exception("User session error: USER_REQUIRED");
        	}
        	
        	Matcher modhashMatcher = Constants.MODHASH_PATTERN.matcher(line);
        	if (modhashMatcher.find()) {
        		modhash = modhashMatcher.group(1);
        		if (Constants.EMPTY_STRING.equals(modhash)) {
        			// Means user is not actually logged in.
        			// FIXME: ErrorToaster
//        			settings.handler.post(new ErrorToaster("You have been logged out. Please login again.", Toast.LENGTH_LONG, settings));
        			return null;
        		}
        	} else {
        		throw new Exception("No modhash found at URL "+Constants.MODHASH_URL);
        	}

//        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	while ((c = in.read()) >= 0) {
//        		sb.append((char) c);
//        		for (int i = 0; i < 80; i++) {
//        			c = in.read();
//        			if (c < 0) {
//        				done = true;
//        				break;
//        			}
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doLogin response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}

        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
    	} catch (Exception e) {
    		Log.e(TAG, e.getMessage());
			// FIXME: ErrorToaster
//    		settings.handler.post(new ErrorToaster("Error performing action. Please try again.", Toast.LENGTH_LONG, settings));
    		return null;
    	}
    	Log.d(TAG, "modhash: "+modhash);
    	return modhash;
    }


}
