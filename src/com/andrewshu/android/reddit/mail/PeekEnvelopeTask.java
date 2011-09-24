package com.andrewshu.android.reddit.mail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.util.Util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

public class PeekEnvelopeTask extends AsyncTask<Void, Void, Integer> {
	
	private static final String TAG = "PeekEnvelopeTask";
	
	protected Context mContext;
	protected HttpClient mClient;
	protected String mMailNotificationStyle;
	protected final JsonFactory jsonFactory = new JsonFactory();
	
	public PeekEnvelopeTask(Context context, HttpClient client, String mailNotificationStyle) {
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
			HttpGet request = new HttpGet(Constants.REDDIT_BASE_URL + "/message/inbox/.json?mark=false");
        	HttpResponse response = mClient.execute(request);
        	entity = response.getEntity();
        	
        	InputStream in = entity.getContent();
            
        	Integer count = processInboxJSON(in);
            
            in.close();
            
            return count;
            
        } catch (Exception e) {
        	if (Constants.LOGGING) Log.e(TAG, "failed", e);
        } finally {
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
		// reset the alarm
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		EnvelopeService.resetAlarm(mContext, Util.getMillisFromMailNotificationPref(
				prefs.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF)));

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
		if (jp.nextToken() != JsonToken.START_OBJECT) {
			Log.w(TAG, "Non-JSON-object in inbox (not logged in?)");
			return 0;
		}
		
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
				if ("true".equals(jp.getText()))
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
