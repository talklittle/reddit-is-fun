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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import android.app.Activity;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

public class Common {
	
	private static final String TAG = "Common";
	
	private static final DefaultHttpClient mGzipHttpClient = createGzipHttpClient();
	private static final Pattern REDDIT_LINK = Pattern.compile(
      "https?://(?:[\\w-]+\\.)?reddit.com" +
      "(?:/r/([^/.]+))?" +
      "(?:/comments/([^/.]+)/[^/.]+" +
          "(?:/([^/.]+))?" +
      ")?/?");

	static void showErrorToast(CharSequence error, int duration, Context context) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		Toast t = new Toast(context);
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
		if (theme == R.style.Reddit_Light) {
    		lv.setSelector(R.drawable.list_selector_blue);
    	} else if (theme == R.style.Reddit_Dark) {
    		lv.setSelector(android.R.drawable.list_selector_background);
    	}
	}
	
    static void saveRedditPreferences(Context context, RedditSettings rSettings) {
    	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    	SharedPreferences.Editor editor = settings.edit();
    	editor.clear();
    	
    	// Session
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
	    	if (rSettings.modhash != null)
	    		editor.putString("modhash", rSettings.modhash.toString());
    	}
    	
    	// Default subreddit
    	editor.putString(Constants.PREF_HOMEPAGE, rSettings.homepage.toString());
    	
    	// Theme
    	editor.putString(Constants.PREF_THEME, RedditSettings.Theme.toString(rSettings.theme));
    	
    	// Rotation
    	editor.putString(Constants.PREF_ROTATION, RedditSettings.Rotation.toString(rSettings.rotation));
    	
    	// Notifications
    	editor.putString(Constants.PREF_MAIL_NOTIFICATION_STYLE, rSettings.mailNotificationStyle);
    	editor.putString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, rSettings.mailNotificationService);
    
    	editor.commit();
    }
    
    static void loadRedditPreferences(Context context, RedditSettings rSettings, DefaultHttpClient client) {
        // Session
    	SharedPreferences sessionPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        rSettings.setUsername(sessionPrefs.getString("username", null));
        rSettings.setModhash(sessionPrefs.getString("modhash", null));
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
        
        // Default subreddit
        String homepage = sessionPrefs.getString(Constants.PREF_HOMEPAGE, Constants.FRONTPAGE_STRING).trim();
        if (Constants.EMPTY_STRING.equals(homepage))
        	rSettings.setHomepage(Constants.FRONTPAGE_STRING);
        else
        	rSettings.setHomepage(homepage);
        
        // Theme
        rSettings.setTheme(RedditSettings.Theme.valueOf(
        		sessionPrefs.getString(Constants.PREF_THEME, Constants.PREF_THEME_LIGHT)));
        
        // Rotation
        rSettings.setRotation(RedditSettings.Rotation.valueOf(
        		sessionPrefs.getString(Constants.PREF_ROTATION, Constants.PREF_ROTATION_UNSPECIFIED)));
        
        // Notifications
        rSettings.setMailNotificationStyle(sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_STYLE, Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT));
        rSettings.setMailNotificationService(sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF));
    }
    
    /**
     * On success stores the session cookie and modhash in your RedditSettings.
     * On failure does not modify RedditSettings. 
     * Should be called from a background thread.
     * @return Error message, or null on success
     */
    static String doLogin(CharSequence username, CharSequence password, DefaultHttpClient client, RedditSettings settings) {
		String status = "";
    	String userError = "Error logging in. Please try again.";
    	HttpEntity entity = null;
    	try {
    		// Construct data
    		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    		nvps.add(new BasicNameValuePair("user", username.toString()));
    		nvps.add(new BasicNameValuePair("passwd", password.toString()));
    		nvps.add(new BasicNameValuePair("api_type", "json"));
    		
            HttpPost httppost = new HttpPost("http://www.reddit.com/api/login/"+username);
            httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            
            // Set timeout to 30 seconds for login
            HttpParams params = httppost.getParams();
	        HttpConnectionParams.setConnectionTimeout(params, 30000);
	        HttpConnectionParams.setSoTimeout(params, 30000);
	        
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
        	entity.consumeContent();
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		throw new HttpException("No content returned from login POST");
        	}
        	
        	if (Constants.LOGGING) Common.logDLong(TAG, line);
        	
        	if (client.getCookieStore().getCookies().isEmpty())
        		throw new HttpException("Failed to login: No cookies");
        	
        	final JsonFactory jsonFactory = new JsonFactory();
        	final JsonParser jp = jsonFactory.createJsonParser(line);
        	
        	// Go to the errors
        	while (jp.nextToken() != JsonToken.FIELD_NAME || !Constants.JSON_ERRORS.equals(jp.getCurrentName()))
        		;
        	if (jp.nextToken() != JsonToken.START_ARRAY)
        		throw new IllegalStateException("Login: expecting errors START_ARRAY");
        	if (jp.nextToken() != JsonToken.END_ARRAY) {
	        	if (line.contains("WRONG_PASSWORD")) {
	        		userError = "Bad password.";
	        		throw new Exception("Wrong password");
	        	} else {
	        		// Could parse for error code and error description but using whole line is easier.
	        		throw new Exception(line);
	        	}
        	}

        	// Getting here means you successfully logged in.
        	// Congratulations!
        	// You are a true reddit master!
        	
        	// Get modhash
        	while (jp.nextToken() != JsonToken.FIELD_NAME || !Constants.JSON_MODHASH.equals(jp.getCurrentName()))
        		;
        	jp.nextToken();
        	settings.setModhash(jp.getText());

        	// Could grab cookie from JSON too, but it lacks expiration date and stuff. So grab from HttpClient.
			List<Cookie> cookies = client.getCookieStore().getCookies();
        	for (Cookie c : cookies) {
        		if (c.getName().equals("reddit_session")) {
        			settings.setRedditSessionCookie(c);
        			break;
        		}
        	}
        	settings.setUsername(username);
        	settings.setLoggedIn(true);
        	
        	return null;

    	} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    			}
    		}
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        }
    	settings.setLoggedIn(false);
        return userError;
    }
    
        
    static void doLogout(RedditSettings settings, DefaultHttpClient client) {
    	client.getCookieStore().clear();
    	settings.setUsername(null);
        settings.setLoggedIn(false);
    }
    
    
    /**
     * Get a new modhash by scraping and return it
     * 
     * @param client
     * @return
     */
    static String doUpdateModhash(DefaultHttpClient client) {
        final Pattern MODHASH_PATTERN = Pattern.compile("modhash: '(.*?)'");
    	String modhash;
    	HttpEntity entity = null;
        // The pattern to find modhash from HTML javascript area
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
        	entity.consumeContent();
        	
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		throw new HttpException("No content returned from doUpdateModhash GET to "+Constants.MODHASH_URL);
        	}
        	if (line.contains("USER_REQUIRED")) {
        		throw new Exception("User session error: USER_REQUIRED");
        	}
        	
        	Matcher modhashMatcher = MODHASH_PATTERN.matcher(line);
        	if (modhashMatcher.find()) {
        		modhash = modhashMatcher.group(1);
        		if (Constants.EMPTY_STRING.equals(modhash)) {
        			// Means user is not actually logged in.
        			return null;
        		}
        	} else {
        		throw new Exception("No modhash found at URL "+Constants.MODHASH_URL);
        	}

        	if (Constants.LOGGING) Common.logDLong(TAG, line);
        	
        	if (Constants.LOGGING) Log.d(TAG, "modhash: "+modhash);
        	return modhash;
        	
    	} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    			}
    		}
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    		return null;
    	}
    }
    
    static class PeekEnvelopeTask extends AsyncTask<Void, Void, Integer> {
    	private Context mContext;
    	private DefaultHttpClient mClient;
    	private String mMailNotificationStyle;
    	private final JsonFactory jsonFactory = new JsonFactory();
    	
    	public PeekEnvelopeTask(Context context, DefaultHttpClient client, String mailNotificationStyle) {
    		mContext = context;
    		mClient = client;
    		mMailNotificationStyle = mailNotificationStyle;
    	}
    	@Override
    	public Integer doInBackground(Void... voidz) {
    		HttpEntity entity = null;
    		try {
    			if (Constants.PREF_MAIL_NOTIFICATION_STYLE_OFF.equals(mMailNotificationStyle))
    	    		return 0;
    			HttpGet request = new HttpGet("http://www.reddit.com/message/inbox/.json?mark=false");
	        	HttpResponse response = mClient.execute(request);
	        	entity = response.getEntity();
	        	
	        	InputStream in = entity.getContent();
	            
	        	Integer count = processInboxJSON(in);
	            
	            in.close();
	            entity.consumeContent();
	            
	            return count;
	            
	        } catch (Exception e) {
	        	if (Constants.LOGGING) Log.e(TAG, "failed:" + e.getMessage());
	            if (entity != null) {
	            	try {
	            		entity.consumeContent();
	            	} catch (Exception e2) {
	            		// Ignore.
	            	}
	            }
	        }
	        return null;
    	}
    	@Override
    	public void onPostExecute(Integer count) {
    		// null means error. Don't do anything.
    		if (count == null)
    			return;
    		if (count > 0) {
    			Common.newMailNotification(mContext, mMailNotificationStyle, count);
    		} else {
    			Common.cancelMailNotification(mContext);
    		}
    	}
    	
    	/**
    	 * Gets the author, title, body of the first inbox entry.
    	 */
    	private Integer processInboxJSON(InputStream in)
				throws IOException, JsonParseException, IllegalStateException {
			String genericListingError = "Not a valid listing";
			Integer count = 0;
			
			JsonParser jp = jsonFactory.createJsonParser(in);
			
			/* The comments JSON file is a JSON array with 2 elements. First element is a thread JSON object,
			 * equivalent to the thread object you get from a subreddit .json file.
			 * Second element is a similar JSON object, but the "children" array is an array of comments
			 * instead of threads. 
			 */
			if (jp.nextToken() != JsonToken.START_OBJECT)
				throw new IllegalStateException("Non-JSON-object in inbox (not logged in?)");
			
			while (!Constants.JSON_CHILDREN.equals(jp.getCurrentName())) {
				// Don't care
				jp.nextToken();
			}
			jp.nextToken();
			if (jp.getCurrentToken() != JsonToken.START_ARRAY)
				throw new IllegalStateException(genericListingError);
			
			while (jp.nextToken() != JsonToken.END_ARRAY) {
				if (JsonToken.FIELD_NAME != jp.getCurrentToken())
					continue;
				String namefield = jp.getCurrentName();
				// Should validate each field but I'm lazy
				jp.nextToken(); // move to value
				if (Constants.JSON_NEW.equals(namefield)) {
					if (Constants.TRUE_STRING.equals(jp.getText()))
						count++;
					else
						// Stop parsing on first old message
						break;
				}
			}
			// Finished parsing first child
			return count;
		}
    }
    static void newMailNotification(Context context, String mailNotificationStyle, int count) {
    	Intent nIntent = new Intent(context, InboxActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, nIntent, 0);
		Notification notification = new Notification(R.drawable.mail, Constants.HAVE_MAIL_TICKER, System.currentTimeMillis());
		if (Constants.PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE.equals(mailNotificationStyle)) {
			RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.big_envelope_notification);
			notification.contentView = contentView;
		} else {
			notification.setLatestEventInfo(context, Constants.HAVE_MAIL_TITLE,
					count + (count == 1 ? " unread message" : " unread messages"), contentIntent);
		}
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
		notification.contentIntent = contentIntent;
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(Constants.NOTIFICATION_HAVE_MAIL, notification);
    }
    static void cancelMailNotification(Context context) {
    	NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(Constants.NOTIFICATION_HAVE_MAIL);
    }
    
    static void launchBrowser(CharSequence url, Activity act) {
      Matcher matcher = REDDIT_LINK.matcher(url);
      if (matcher.matches()) {
        if (matcher.group(3) != null) {
          Intent intent = new Intent(act.getApplicationContext(), CommentsListActivity.class);
          intent.putExtra(Constants.EXTRA_COMMENT_CONTEXT, url);
          act.startActivity(intent);
          return;
        } else if (matcher.group(2) != null) {
          Intent intent = new Intent(act.getApplicationContext(), CommentsListActivity.class);
          intent.putExtra(ThreadInfo.SUBREDDIT, matcher.group(1));
          intent.putExtra(ThreadInfo.ID, matcher.group(2));
          intent.putExtra(ThreadInfo.NUM_COMMENTS, Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
          act.startActivity(intent);
          return;
        } else if (matcher.group(1) != null) {
          Intent intent = new Intent(act.getApplicationContext(), RedditIsFun.class);
          intent.putExtra(ThreadInfo.SUBREDDIT, matcher.group(1));
          act.startActivity(intent);
          return;
        }
      }
      Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()));
      browser.putExtra(Browser.EXTRA_APPLICATION_ID, act.getPackageName());
      act.startActivity(browser);
    }
    
    
    static boolean isFreshCache(long cacheTime) {
		long time = System.currentTimeMillis();
		return time - cacheTime <= Constants.DEFAULT_FRESH_DURATION;
	}
    
    static void deleteAllCaches(Context context) {
    	for (String fileName : context.fileList()) {
    		context.deleteFile(fileName);
    	}
    }
    
    static void deleteCachesOlderThan(Context context, long someTime) {
    	FileInputStream fis = null;
    	ObjectInputStream in = null;
    	
		try {
	    	fis = context.openFileInput(Constants.FILENAME_CACHE_TIME);
			in = new ObjectInputStream(fis);
			long cacheTime = in.readLong();
			
			// If at least one file is new enough, don't delete caches.
			if (cacheTime >= someTime)
				return;
    	} catch (Exception e) {
    		// Bad or missing time file. Delete cache.
    	} finally {
    		try {
    			in.close();
    		} catch (Exception ignore) {}
    		try {
    			fis.close();
    		} catch (Exception ignore) {}
    	}
    	deleteAllCaches(context);
    }
	
    
	/**
	 * http://hc.apache.org/httpcomponents-client/examples.html
	 * @return a Gzip-enabled DefaultHttpClient
	 */
	static DefaultHttpClient getGzipHttpClient() {
		return mGzipHttpClient;
	}
	
	private static DefaultHttpClient createGzipHttpClient() {
		BasicHttpParams params = new BasicHttpParams();
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
		DefaultHttpClient httpclient = new DefaultHttpClient(cm, params);
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
	
    static void logDLong(String tag, String msg) {
		int c;
		boolean done = false;
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < msg.length(); k += 80) {
			for (int i = 0; i < 80; i++) {
				if (k + i >= msg.length()) {
					done = true;
					break;
				}
				c = msg.charAt(k + i);
				sb.append((char) c);
			}
			if (Constants.LOGGING) Log.d(tag, "doReply response content: " + sb.toString());
			sb = new StringBuilder();
			if (done)
				break;
		}
	} 
}
