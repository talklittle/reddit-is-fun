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
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

public class Common {
	
	private static final String TAG = "Common";
	
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
    	}
    	
    	// Default subreddit
    	editor.putString(Constants.PREF_HOMEPAGE, rSettings.homepage.toString());
    	
    	// Theme
    	editor.putString(Constants.PREF_THEME, RedditSettings.Theme.toString(rSettings.theme));
    	
    	// Notifications
    	editor.putString(Constants.PREF_MAIL_NOTIFICATION_STYLE, rSettings.mailNotificationStyle);
    	editor.putString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, rSettings.mailNotificationService);
    
    	editor.commit();
    }
    
    static void loadRedditPreferences(Context context, RedditSettings rSettings, DefaultHttpClient client) {
        // Session
    	SharedPreferences sessionPrefs = PreferenceManager.getDefaultSharedPreferences(context);
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
        
        // Default subreddit
        String homepage = sessionPrefs.getString(Constants.PREF_HOMEPAGE, Constants.FRONTPAGE_STRING).trim();
        if (Constants.EMPTY_STRING.equals(homepage))
        	rSettings.setHomepage(Constants.FRONTPAGE_STRING);
        else
        	rSettings.setHomepage(homepage);
        
        // Theme
        rSettings.setTheme(RedditSettings.Theme.valueOf(
        		sessionPrefs.getString(Constants.PREF_THEME, Constants.PREF_THEME_LIGHT)));
        
        // Notifications
        rSettings.setMailNotificationStyle(sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_STYLE, Constants.PREF_MAIL_NOTIFICATION_STYLE_DEFAULT));
        rSettings.setMailNotificationService(sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF));
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
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		throw new HttpException("No content returned from login POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		userError = "Bad password.";
        		throw new Exception("Wrong password");
        	}

//        	// DEBUG
//        	Log.dLong(TAG, line);
        	
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
        	entity.consumeContent();
        	
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
//        	Log.dLong(TAG, line);
        	
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
    
    static class PeekEnvelopeTask extends AsyncTask<Void, Void, Boolean> {
    	private Context mContext;
    	private DefaultHttpClient mClient;
    	private String mMailNotificationStyle;
    	public PeekEnvelopeTask(Context context, DefaultHttpClient client, String mailNotificationStyle) {
    		mContext = context;
    		mClient = client;
    		mMailNotificationStyle = mailNotificationStyle;
    	}
    	@Override
    	public Boolean doInBackground(Void... voidz) {
    		try {
    			if (Constants.PREF_MAIL_NOTIFICATION_STYLE_OFF.equals(mMailNotificationStyle))
    	    		return false;
    	    	return Common.doPeekEnvelope(mClient, null);
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	@Override
    	public void onPostExecute(Boolean hasMail) {
    		// hasMail == null means error. Don't do anything.
    		if (hasMail == null)
    			return;
    		if (hasMail) {
    			Common.newMailNotification(mContext, mMailNotificationStyle);
    		} else {
    			Common.cancelMailNotification(mContext);
    		}
    	}
    }
    /**
     * Check mail. You should use PeekEnvelopeTask instead.
     * 
     * @param client
     * @param shortcutHtml The HTML for the page to bypass network
     * @return
     */
    static boolean doPeekEnvelope(DefaultHttpClient client, String shortcutHtml) throws Exception {
    	String no;
    	String line;
    	HttpEntity entity = null;
    	try {
    		if (shortcutHtml == null) {
	    		HttpGet httpget = new HttpGet(Constants.MODHASH_URL);
	    		HttpResponse response = client.execute(httpget);
	    		
	        	entity = response.getEntity();
	
	        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
	        	line = in.readLine();
	        	in.close();
	        	entity.consumeContent();
    		} else {
    			line = shortcutHtml;
    		}
        	
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		throw new HttpException("No content returned from doPeekEnvelope GET to "+Constants.MODHASH_URL);
        	}
        	if (line.contains("USER_REQUIRED")) {
        		throw new Exception("User session error: USER_REQUIRED");
        	}
        	
        	Matcher haveMailMatcher = Constants.HAVE_MAIL_PATTERN.matcher(line);
        	if (haveMailMatcher.find()) {
        		no = haveMailMatcher.group(1);
        		if (Constants.NO_STRING.equals(no)) {
        			// No mail.
        			return false;
        		}
        	} else {
        		throw new Exception("No envelope found at URL "+Constants.MODHASH_URL);
        	}

//        	// DEBUG
//        	Log.dLong(TAG, line);

        	return true;
        	
    	} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (IOException e2) {
    				Log.e(TAG, e.getMessage());
    			}
    		}
    		Log.e(TAG, e.getMessage());
    		throw e;
    	}
    }
    static void newMailNotification(Context context, String mailNotificationStyle) {
    	Intent nIntent = new Intent(context, InboxActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, nIntent, 0);
		Notification notification = new Notification(R.drawable.mail, Constants.HAVE_MAIL_TICKER, System.currentTimeMillis());
		if (Constants.PREF_MAIL_NOTIFICATION_STYLE_BIG_ENVELOPE.equals(mailNotificationStyle)) {
			RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.big_envelope_notification);
			notification.contentView = contentView;
		} else {
			notification.setLatestEventInfo(context, Constants.HAVE_MAIL_TITLE, Constants.HAVE_MAIL_TEXT, contentIntent);
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
		Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()));
		browser.putExtra(Browser.EXTRA_APPLICATION_ID, act.getPackageName());
		act.startActivity(browser);
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
