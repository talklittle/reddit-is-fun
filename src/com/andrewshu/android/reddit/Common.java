package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
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
	
	/**
     * Set the Drawable for the list selector etc. based on the current theme.
     */
	static void updateListDrawables(ListActivity la, int theme) {
		final ListView lv = la.getListView();
		final Resources res = la.getResources();
		if (theme == Constants.THEME_LIGHT) {
    		lv.setSelector(R.drawable.list_selector_solid_pale_blue);
    		lv.setCacheColorHint(res.getColor(R.color.white));
    	} else if (theme == Constants.THEME_DARK) {
    		lv.setSelector(android.R.drawable.list_selector_background);
    		lv.setCacheColorHint(res.getColor(R.color.android_dark_background));
    	}
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
    
    /**
     * Should be called from a background thread.
     * @return Error message, or null on success
     */
    static String doLogin(CharSequence username, CharSequence password, DefaultHttpClient client) {
		String status = "";
    	String userError = "Error logging in. Please try again.";
    	HttpEntity entity = null;
    	try {
    		// Construct data
    		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    		nvps.add(new BasicNameValuePair("user", username.toString()));
    		nvps.add(new BasicNameValuePair("passwd", password.toString()));
    		
            HttpPost httppost = new HttpPost("http://www.reddit.com/api/login/"+username);
            httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            
            // Perform the HTTP POST request
        	HttpResponse response = client.execute(httppost);
        	status = response.getStatusLine().toString();
        	if (!status.contains("OK")) {
        		throw new HttpException(status);
        	}
        	
        	entity = response.getEntity();
        	
        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	in.close();
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		throw new HttpException("No content returned from login POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		userError = "Bad password.";
        		throw new Exception("Wrong password");
        	}

//        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	for (int k = 0; k < line.length(); k += 80) {
//        		for (int i = 0; i < 80; i++) {
//        			if (k + i >= line.length()) {
//        				done = true;
//        				break;
//        			}
//        			c = line.charAt(k + i);
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doReply response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}
//	        	
        	
        	entity.consumeContent();
        	
        	if (client.getCookieStore().getCookies().isEmpty())
        		throw new HttpException("Failed to login: No cookies");
        	
        	// Getting here means you successfully logged in.
        	// Congratulations!
        	// You are a true reddit master!
        
        	return null;

    	} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (IOException e2) {
    				Log.e(TAG, e.getMessage());
    			}
    		}
        	Log.e(TAG, e.getMessage());
        }
        return userError;
    }
    
        
    static void doLogout(RedditSettings settings, DefaultHttpClient client) {
    	client.getCookieStore().clear();
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
    	HttpEntity entity = null;
    	try {
    		HttpGet httpget = new HttpGet(Constants.MODHASH_URL);
    		HttpResponse response = client.execute(httpget);
    		
    		// For modhash, we don't care about the status, since the 404 page has the info we want.
//    		status = response.getStatusLine().toString();
//        	if (!status.contains("OK"))
//        		throw new HttpException(status);
        	
        	entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	// modhash should appear within first 1200 chars
        	char[] buffer = new char[1200];
        	in.read(buffer, 0, 1200);
        	in.close();
        	String line = String.valueOf(buffer);
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
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
        			return null;
        		}
        	} else {
        		throw new Exception("No modhash found at URL "+Constants.MODHASH_URL);
        	}

//        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	for (int k = 0; k < line.length(); k += 80) {
//        		for (int i = 0; i < 80; i++) {
//        			if (k + i >= line.length()) {
//        				done = true;
//        				break;
//        			}
//        			c = line.charAt(k + i);
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doReply response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}
//	        	

        	entity.consumeContent();
        	
        	Log.d(TAG, "modhash: "+modhash);
        	return modhash;
        	
    	} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (IOException e2) {
    				Log.e(TAG, e.getMessage());
    			}
    		}
    		Log.e(TAG, e.getMessage());
    		return null;
    	}
    }
    
    
	/**
	 * http://hc.apache.org/httpcomponents-client/examples.html
	 * @return a Gzip-enabled DefaultHttpClient
	 */
	static DefaultHttpClient createGzipHttpClient() {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request, 
                    final HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }
        });
        httpclient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(
                                    new GzipDecompressingEntity(response.getEntity())); 
                            return;
                        }
                    }
                }
            }
        });
        return httpclient;
	}
    static class GzipDecompressingEntity extends HttpEntityWrapper {
        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }
        @Override
        public InputStream getContent()
            throws IOException, IllegalStateException {
            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();
            return new GZIPInputStream(wrappedin);
        }
        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }
    } 
}
