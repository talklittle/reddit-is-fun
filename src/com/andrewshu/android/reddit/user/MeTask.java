package com.andrewshu.android.reddit.user;

import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.os.AsyncTask;
import android.util.Log;

import com.andrewshu.android.reddit.common.Constants;

public abstract class MeTask extends AsyncTask<Void, Void, Object> {
	
	private static final String TAG = "MeTask";
	
	private static final String REQUEST_URL = Constants.REDDIT_BASE_URL + "/api/me.json";
	
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
        	
        	UserInfo parseResult = UserInfoParser.parseJSON(in);

        	if (parseResult != null)
        		return onLoggedIn(parseResult);

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
        return null;
	}
	
	protected abstract Object onLoggedIn(UserInfo me);

}
