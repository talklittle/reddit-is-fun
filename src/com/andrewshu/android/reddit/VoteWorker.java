package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;

/**
 * Worker thread that takes in a thingId, vote direction, and subreddit. Starts
 * a new HTTP Client, copying the main one's cookies, and votes.
 * @param username
 * @param password
 * @return
 */
class VoteWorker extends Thread{
	
	private static final String TAG = "VoteWorker";
	
	private CharSequence _mThingFullname, _mSubreddit;
	private int _mDirection;
	private RedditSettings _mSettings;
	
	public VoteWorker(CharSequence thingFullname, int direction, CharSequence subreddit, RedditSettings settings) {
		_mThingFullname = thingFullname;
		_mDirection = direction;
		_mSubreddit = subreddit;
		_mSettings = settings;
	}
	
	@Override
	public void run() {
    	String status = "";
    	if (!_mSettings.loggedIn) {
    		return;
    	}
    	if (_mDirection < -1 || _mDirection > 1) {
    		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
    	}
    	
    	// Update the modhash if necessary
    	if (_mSettings.modhash == null) {
    		if ((_mSettings.modhash = Common.doUpdateModhash(_mSettings)) == null) {
    			// doUpdateModhash should have given an error about credentials
    			throw new RuntimeException("Vote failed because doUpdateModhash() failed");
    		}
    	}
    	
    	try {
    		// Create a new HttpClient and copy cookies over from the main one
    		DefaultHttpClient client = new DefaultHttpClient();
    		client.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 30000);
    		List<Cookie> mainCookies = _mSettings.client.getCookieStore().getCookies();
    		for (Cookie c : mainCookies) {
    			client.getCookieStore().addCookie(c);
    		}
    		
    		// Construct data
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("id", _mThingFullname.toString()));
			nvps.add(new BasicNameValuePair("dir", String.valueOf(_mDirection)));
			nvps.add(new BasicNameValuePair("r", _mSubreddit.toString()));
			nvps.add(new BasicNameValuePair("uh", _mSettings.modhash.toString()));
			// Votehash is currently unused by reddit 
//				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
			
			HttpPost httppost = new HttpPost("http://www.reddit.com/api/vote");
	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
	        
	        Log.d(TAG, nvps.toString());
	        
            // Perform the HTTP POST request
	    	HttpResponse response = _mSettings.client.execute(httppost);
	    	status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	if (line == null) {
        		throw new HttpException("No content returned from vote POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		throw new Exception("Wrong password");
        	}
        	if (line.contains("USER_REQUIRED")) {
        		// The modhash probably expired
        		_mSettings.modhash = null;
        		// FIXME: mHandler.post
//        		mHandler.post(new ErrorToaster("Error voting. Please try again.", Toast.LENGTH_LONG));
        		return;
        	}
        	
        	Log.d(TAG, line);

//	        	// DEBUG
//	        	int c;
//	        	boolean done = false;
//	        	StringBuilder sb = new StringBuilder();
//	        	while ((c = in.read()) >= 0) {
//	        		sb.append((char) c);
//	        		for (int i = 0; i < 80; i++) {
//	        			c = in.read();
//	        			if (c < 0) {
//	        				done = true;
//	        				break;
//	        			}
//	        			sb.append((char) c);
//	        		}
//	        		Log.d(TAG, "doLogin response content: " + sb.toString());
//	        		sb = new StringBuilder();
//	        		if (done)
//	        			break;
//	        	}

        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
    	} catch (Exception e) {
            Log.e(TAG, e.getMessage());
    	}
    	Log.d(TAG, status);
    }
}
