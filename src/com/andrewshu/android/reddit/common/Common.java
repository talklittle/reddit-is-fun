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

package com.andrewshu.android.reddit.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
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
import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import android.app.Activity;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.CookieSyncManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.RedditIsFunApplication;
import com.andrewshu.android.reddit.browser.BrowserActivity;
import com.andrewshu.android.reddit.captcha.CaptchaException;
import com.andrewshu.android.reddit.comments.CommentsListActivity;
import com.andrewshu.android.reddit.common.util.StringUtils;
import com.andrewshu.android.reddit.common.util.Util;
import com.andrewshu.android.reddit.mail.InboxActivity;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.threads.ThreadsListActivity;
import com.andrewshu.android.reddit.user.ProfileActivity;

public class Common {
	
	private static final String TAG = "Common";
	
	private static final DefaultHttpClient mGzipHttpClient = createGzipHttpClient();
	private static final CookieStore mCookieStore = mGzipHttpClient.getCookieStore();
	// 1:subreddit 2:threadId 3:commentId
	private static final Pattern COMMENT_LINK = Pattern.compile(Constants.COMMENT_PATH_PATTERN_STRING);
	private static final Pattern REDDIT_LINK = Pattern.compile(Constants.REDDIT_PATH_PATTERN_STRING);
	private static final Pattern USER_LINK = Pattern.compile(Constants.USER_PATH_PATTERN_STRING);
	private static final ObjectMapper mObjectMapper = new ObjectMapper();
	
	// Default connection and socket timeout of 60 seconds.  Tweak to taste.
	private static final int SOCKET_OPERATION_TIMEOUT = 60 * 1000;

	public static void showErrorToast(String error, int duration, Context context) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		Toast t = new Toast(context);
		t.setDuration(duration);
		View v = inflater.inflate(R.layout.error_toast, null);
		TextView errorMessage = (TextView) v.findViewById(R.id.errorMessage);
		errorMessage.setText(error);
		t.setView(v);
		t.show();
	}
	
    public static boolean shouldLoadThumbnails(Activity activity, RedditSettings settings) {
    	//check for wifi connection and wifi thumbnail setting
    	boolean thumbOkay = true;
    	if (settings.isLoadThumbnailsOnlyWifi())
    	{
    		thumbOkay = false;
    		ConnectivityManager connMan = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
    		NetworkInfo netInfo = connMan.getActiveNetworkInfo();
    		if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI && netInfo.isConnected()) {
    			thumbOkay = true;
    		}
    	}
    	return settings.isLoadThumbnails() && thumbOkay;
    }
    
	/**
     * Set the Drawable for the list selector etc. based on the current theme.
     */
	public static void updateListDrawables(ListActivity la, int theme) {
		ListView lv = la.getListView();
		if (Util.isLightTheme(theme)) {
    		lv.setSelector(R.drawable.list_selector_blue);
        	// HACK: set background color directly for android 2.0
        	lv.setBackgroundResource(R.color.white);
    	} else /* if (Common.isDarkTheme(theme)) */ {
    		lv.setSelector(android.R.drawable.list_selector_background);
    	}
	}
	
    public static void updateNextPreviousButtons(ListActivity act, View nextPreviousView,
    		String after, String before, int count, RedditSettings settings,
    		OnClickListener downloadAfterOnClickListener, OnClickListener downloadBeforeOnClickListener) {
    	boolean shouldShow = after != null || before != null;
    	Button nextButton = null;
    	Button previousButton = null;
    	
    	// If alwaysShowNextPrevious, use the navbar
    	if (settings.isAlwaysShowNextPrevious()) {
        	nextPreviousView = act.findViewById(R.id.next_previous_layout);
        	if (nextPreviousView == null)
        		return;
        	View nextPreviousBorder = act.findViewById(R.id.next_previous_border_top);
        	
			if (shouldShow && nextPreviousView.getVisibility() != View.VISIBLE) {
		    	if (nextPreviousView != null && nextPreviousBorder != null) {
			    	if (Util.isLightTheme(settings.getTheme())) {
			    		nextPreviousView.setBackgroundResource(R.color.white);
			       		nextPreviousBorder.setBackgroundResource(R.color.black);
			    	} else {
			       		nextPreviousBorder.setBackgroundResource(R.color.white);
			    	}
			    	nextPreviousView.setVisibility(View.VISIBLE);
		    	}
				// update the "next 25" and "prev 25" buttons
		    	nextButton = (Button) act.findViewById(R.id.next_button);
		    	previousButton = (Button) act.findViewById(R.id.previous_button);
			} else if (!shouldShow && nextPreviousView.getVisibility() == View.VISIBLE) {
				nextPreviousView.setVisibility(View.GONE);
	    	}
    	}
    	// Otherwise we are using the ListView footer
    	else {
    		if (nextPreviousView == null)
    			return;
    		if (shouldShow && nextPreviousView.getVisibility() != View.VISIBLE) {
	    		nextPreviousView.setVisibility(View.VISIBLE);
    		} else if (!shouldShow && nextPreviousView.getVisibility() == View.VISIBLE) {
    			nextPreviousView.setVisibility(View.GONE);
    		}
			// update the "next 25" and "prev 25" buttons
	    	nextButton = (Button) nextPreviousView.findViewById(R.id.next_button);
	    	previousButton = (Button) nextPreviousView.findViewById(R.id.previous_button);
    	}
    	if (nextButton != null) {
	    	if (after != null) {
	    		nextButton.setVisibility(View.VISIBLE);
	    		nextButton.setOnClickListener(downloadAfterOnClickListener);
	    	} else {
	    		nextButton.setVisibility(View.INVISIBLE);
	    	}
    	}
    	if (previousButton != null) {
	    	if (before != null && count != Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT) {
	    		previousButton.setVisibility(View.VISIBLE);
	    		previousButton.setOnClickListener(downloadBeforeOnClickListener);
	    	} else {
	    		previousButton.setVisibility(View.INVISIBLE);
	    	}
    	}
    }
    
	
    static void clearCookies(RedditSettings settings, HttpClient client, Context context) {
        settings.setRedditSessionCookie(null);

        Common.getCookieStore().clear();
        CookieSyncManager.getInstance().sync();
        
        SharedPreferences sessionPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    	SharedPreferences.Editor editor = sessionPrefs.edit();
    	editor.remove("reddit_sessionValue");
    	editor.remove("reddit_sessionDomain");
    	editor.remove("reddit_sessionPath");
    	editor.remove("reddit_sessionExpiryDate");
        editor.commit();
    }
    
        
    public static void doLogout(RedditSettings settings, HttpClient client, Context context) {
    	clearCookies(settings, client, context);
    	CacheInfo.invalidateAllCaches(context);
    	settings.setUsername(null);
    }
    
    
    /**
     * Get a new modhash by scraping and return it
     * 
     * @param client
     * @return
     */
    public static String doUpdateModhash(HttpClient client) {
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
        	
        	if (StringUtils.isEmpty(line)) {
        		throw new HttpException("No content returned from doUpdateModhash GET to "+Constants.MODHASH_URL);
        	}
        	if (line.contains("USER_REQUIRED")) {
        		throw new Exception("User session error: USER_REQUIRED");
        	}
        	
        	Matcher modhashMatcher = MODHASH_PATTERN.matcher(line);
        	if (modhashMatcher.find()) {
        		modhash = modhashMatcher.group(1);
        		if (StringUtils.isEmpty(modhash)) {
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
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e);
    			}
    		}
    		if (Constants.LOGGING) Log.e(TAG, "doUpdateModhash()", e);
    		return null;
    	}
    }
    
    public static String checkResponseErrors(HttpResponse response, HttpEntity entity) {
    	String status = response.getStatusLine().toString();
    	String line;
    	
    	if (!status.contains("OK")) {
    		return "HTTP error. Status = "+status;
    	}
    	
    	try {
    		BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
    		line = in.readLine();
    		if (Constants.LOGGING) Common.logDLong(TAG, line);
        	in.close();
    	} catch (IOException e) {
    		if (Constants.LOGGING) Log.e(TAG, "IOException", e);
    		return "Error reading retrieved data.";
    	}
    	
    	if (StringUtils.isEmpty(line)) {
    		return "API returned empty data.";
    	}
    	if (line.contains("WRONG_PASSWORD")) {
    		return "Wrong password.";
    	}
    	if (line.contains("USER_REQUIRED")) {
    		// The modhash probably expired
    		return "Login expired.";
    	}
    	if (line.contains("SUBREDDIT_NOEXIST")) {
    		return "That subreddit does not exist.";
    	}
    	if (line.contains("SUBREDDIT_NOTALLOWED")) {
    		return "You are not allowed to post to that subreddit.";
    	}
    	
    	return null;
    }
    

	public static String checkIDResponse(HttpResponse response, HttpEntity entity) throws CaptchaException, Exception {
	    // Group 1: fullname. Group 2: kind. Group 3: id36.
	    final Pattern NEW_ID_PATTERN = Pattern.compile("\"id\": \"((.+?)_(.+?))\"");
	    // Group 1: whole error. Group 2: the time part
	    final Pattern RATELIMIT_RETRY_PATTERN = Pattern.compile("(you are trying to submit too fast. try again in (.+?)\\.)");

	    String status = response.getStatusLine().toString();
    	String line;
    	
    	if (!status.contains("OK")) {
    		throw new Exception("HTTP error. Status = "+status);
    	}
    	
    	try {
    		BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
    		line = in.readLine();
    		if (Constants.LOGGING) Common.logDLong(TAG, line);
        	in.close();
    	} catch (IOException e) {
    		if (Constants.LOGGING) Log.e(TAG, "IOException", e);
    		throw new Exception("Error reading retrieved data.");
    	}
    	
    	if (StringUtils.isEmpty(line)) {
    		throw new Exception("API returned empty data.");
    	}
    	if (line.contains("WRONG_PASSWORD")) {
    		throw new Exception("Wrong password.");
    	}
    	if (line.contains("USER_REQUIRED")) {
    		// The modhash probably expired
    		throw new Exception("Login expired.");
    	}
    	if (line.contains("SUBREDDIT_NOEXIST")) {
    		throw new Exception("That subreddit does not exist.");
    	}
    	if (line.contains("SUBREDDIT_NOTALLOWED")) {
    		throw new Exception("You are not allowed to post to that subreddit.");
    	}
    	
    	String newId;
    	Matcher idMatcher = NEW_ID_PATTERN.matcher(line);
    	if (idMatcher.find()) {
    		newId = idMatcher.group(3);
    	} else {
    		if (line.contains("RATELIMIT")) {
        		// Try to find the # of minutes using regex
            	Matcher rateMatcher = RATELIMIT_RETRY_PATTERN.matcher(line);
            	if (rateMatcher.find())
            		throw new Exception(rateMatcher.group(1));
            	else
            		throw new Exception("you are trying to submit too fast. try again in a few minutes.");
        	}
    		if (line.contains("DELETED_LINK")) {
    			throw new Exception("the link you are commenting on has been deleted");
    		}
    		if (line.contains("BAD_CAPTCHA")) {
    			throw new CaptchaException("Bad CAPTCHA. Try again.");
    		}
        	// No id returned by reply POST.
    		return null;
    	}
    	
    	// Getting here means success.
    	return newId;
	}
    
	
    public static void newMailNotification(Context context, String mailNotificationStyle, int count) {
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
    public static void cancelMailNotification(Context context) {
    	NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(Constants.NOTIFICATION_HAVE_MAIL);
    }
    
    /**
     * 
     * @param url
     * @param context
     * @param requireNewTask set this to true if context is not an Activity
     * @param bypassParser
     * @param useExternalBrowser
     */
    public static void launchBrowser(Context context, String url, String threadUrl,
    		boolean requireNewTask, boolean bypassParser, boolean useExternalBrowser) {
    	
    	Uri uri = Uri.parse(url);
    	
    	if (!bypassParser) {
    		if (Util.isRedditUri(uri)) {
	    		String path = uri.getPath();
	    		Matcher matcher = COMMENT_LINK.matcher(path);
		    	if (matcher.matches()) {
		    		if (matcher.group(3) != null || matcher.group(2) != null) {
		    			CacheInfo.invalidateCachedThread(context);
		    			Intent intent = new Intent(context, CommentsListActivity.class);
		    			intent.setData(uri);
		    			intent.putExtra(Constants.EXTRA_NUM_COMMENTS, Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
		    			if (requireNewTask)
		    				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    			context.startActivity(intent);
		    			return;
		    		}
		    	}
		    	matcher = REDDIT_LINK.matcher(path);
		    	if (matcher.matches()) {
	    			CacheInfo.invalidateCachedSubreddit(context);
	    			Intent intent = new Intent(context, ThreadsListActivity.class);
	    			intent.setData(uri);
	    			if (requireNewTask)
	    				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    			context.startActivity(intent);
	    			return;
		    	}
		    	matcher = USER_LINK.matcher(path);
		    	if (matcher.matches()) {
		    		Intent intent = new Intent(context, ProfileActivity.class);
		    		intent.setData(uri);
	    			if (requireNewTask)
	    				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    			context.startActivity(intent);
	    			return;
		    	}
	    	} else if (Util.isRedditShortenedUri(uri)) {
	    		String path = uri.getPath();
	    		if (path.equals("") || path.equals("/")) {
	    			CacheInfo.invalidateCachedSubreddit(context);
	    			Intent intent = new Intent(context, ThreadsListActivity.class);
	    			intent.setData(uri);
	    			if (requireNewTask)
	    				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    			context.startActivity(intent);
	    			return;
	    		} else {
		    		// Assume it points to a thread aka CommentsList
	    			CacheInfo.invalidateCachedThread(context);
	    			Intent intent = new Intent(context, CommentsListActivity.class);
	    			intent.setData(uri);
	    			intent.putExtra(Constants.EXTRA_NUM_COMMENTS, Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
	    			if (requireNewTask)
	    				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    			context.startActivity(intent);
	    		}
    			return;
	    	}
    	}
    	uri = Util.optimizeMobileUri(uri);
    	
    	// Some URLs should always be opened externally, if BrowserActivity doesn't support their content.
    	if (Util.isYoutubeUri(uri))
    		useExternalBrowser = true;
    	
    	if (useExternalBrowser) {
    		Intent browser = new Intent(Intent.ACTION_VIEW, uri);
    		browser.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
			if (requireNewTask)
				browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(browser);
    	} else {
	    	Intent browser = new Intent(context, BrowserActivity.class);
	    	browser.setData(uri);
	    	if (threadUrl != null)
	    		browser.putExtra(Constants.EXTRA_THREAD_URL, threadUrl);
			if (requireNewTask)
				browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(browser);
    	}
	}
    
    public static ObjectMapper getObjectMapper() {
    	return mObjectMapper;
    }
    
	/**
	 * http://hc.apache.org/httpcomponents-client/examples.html
	 * @return a Gzip-enabled DefaultHttpClient
	 */
	public static HttpClient getGzipHttpClient() {
		return mGzipHttpClient;
	}
	
	public static CookieStore getCookieStore() {
		return mCookieStore;
	}
	
	private static DefaultHttpClient createGzipHttpClient() {
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		
		DefaultHttpClient httpclient = new DefaultHttpClient(params) {
		    @Override
		    protected ClientConnectionManager createClientConnectionManager() {
		        SchemeRegistry registry = new SchemeRegistry();
		        registry.register(
		                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		        registry.register(
		        		new Scheme("https", getHttpsSocketFactory(), 443));
		        HttpParams params = getParams();
				HttpConnectionParams.setConnectionTimeout(params, SOCKET_OPERATION_TIMEOUT);
				HttpConnectionParams.setSoTimeout(params, SOCKET_OPERATION_TIMEOUT);
		        return new ThreadSafeClientConnManager(params, registry);
		    }
		    
		    /** Gets an HTTPS socket factory with SSL Session Caching if such support is available, otherwise falls back to a non-caching factory
		     * @return
		     */
		    protected SocketFactory getHttpsSocketFactory(){
				try {
					Class<?> sslSessionCacheClass = Class.forName("android.net.SSLSessionCache");
			    	Object sslSessionCache = sslSessionCacheClass.getConstructor(Context.class).newInstance(RedditIsFunApplication.getApplication());
			    	Method getHttpSocketFactory = Class.forName("android.net.SSLCertificateSocketFactory").getMethod("getHttpSocketFactory", new Class<?>[]{int.class, sslSessionCacheClass});
			    	return (SocketFactory) getHttpSocketFactory.invoke(null, SOCKET_OPERATION_TIMEOUT, sslSessionCache);
				}catch(Exception e){
					return SSLSocketFactory.getSocketFactory();
				}
		    }
		};
		
		
        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context
            ) throws HttpException, IOException {
                request.setHeader("User-Agent", Constants.USER_AGENT_STRING);
                if (!request.containsHeader("Accept-Encoding"))
                    request.addHeader("Accept-Encoding", "gzip");
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
	
    public static void logDLong(String tag, String msg) {
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
			if (Constants.LOGGING) Log.d(tag, "multipart log: " + sb.toString());
			sb = new StringBuilder();
			if (done)
				break;
		}
	} 
    
    public static String getSubredditId(String mSubreddit){
    	String subreddit_id = null;
    	JsonNode subredditInfo = 
    	RestJsonClient.connect(Constants.REDDIT_BASE_URL + "/r/" + mSubreddit + "/.json?count=1");
    	    	
    	if(subredditInfo != null){
    		ArrayNode children = (ArrayNode) subredditInfo.path("data").path("children");
    		subreddit_id = children.get(0).get("data").get("subreddit_id").getTextValue();
    	}
    	return subreddit_id;
    }
}
