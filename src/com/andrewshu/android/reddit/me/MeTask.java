package com.andrewshu.android.reddit.me;

import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import android.os.AsyncTask;
import android.util.Log;

import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;

public abstract class MeTask extends AsyncTask<Void, Void, Object> {
	
	private static final String TAG = "MeTask";
	
	private static final String REQUEST_URL = Constants.REDDIT_BASE_URL + "/api/me.json";
	
	protected final ObjectMapper mObjectMapper = Common.getObjectMapper();
	
	protected HttpClient mClient;
	protected long mContentLength = 0;
	
	public MeTask(HttpClient client) {
		mClient = client;
	}

	@Override
	protected Object doInBackground(Void... arg0) {
		HttpEntity entity = null;
    	InputStream in = null;
        try {
	    	HttpGet request = new HttpGet(REQUEST_URL);
            HttpResponse response = mClient.execute(request);
            	
            // Read the header to get Content-Length since entity.getContentLength() returns -1
        	Header contentLengthHeader = response.getFirstHeader("Content-Length");
        	if (contentLengthHeader != null) {
        		mContentLength = Long.valueOf(contentLengthHeader.getValue());
            	if (Constants.LOGGING) Log.d(TAG, "Content length: "+mContentLength);
        	}
        	else {
        		mContentLength = -1; 
            	if (Constants.LOGGING) Log.d(TAG, "Content length: UNAVAILABLE");
        	}

        	entity = response.getEntity();
        	in = entity.getContent();
        	
        	return parseMeJSON(in);
            
        } catch (Exception e) {
        	if (Constants.LOGGING) Log.e(TAG, "MeTask", e);
        } finally {
        	try {
        		in.close();
        	} catch (Exception ignore) {}
			try {
				entity.consumeContent();
    		} catch (Exception ignore) {}
        }
        return false;
	}
	
	private Object parseMeJSON(InputStream in) {
		MeListing meListing = null;
		MeInfo meInfo = null;
		try {
			try {
				meListing = mObjectMapper.readValue(in, MeListing.class);
			} catch (JsonMappingException ex) {
				// it is not a Listing. user is not logged in.
				if (Constants.LOGGING) Log.i(TAG, "User is not logged in according to " + REQUEST_URL);
				return null;
			}
			
			meInfo = meListing.getData();
			return onLoggedIn(meInfo);
			
		} catch (Exception ex) {
			if (Constants.LOGGING) Log.e(TAG, "parseMeJSON", ex);
			return null;
		} finally {
			meListing = null;
			meInfo = null;
		}
	}
	
	protected abstract Object onLoggedIn(MeInfo me);

}
