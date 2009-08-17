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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;

import android.widget.Toast;

public class LoginWorker extends Thread {
	
	private static String TAG = "LoginWorker";

	private CharSequence _mUsername;
	private CharSequence _mPassword;
	private RedditSettings _mSettings;
	private Runnable _mCallback;
	
	LoginWorker(CharSequence username, CharSequence password, RedditSettings settings, Runnable callback) {
		_mUsername = username;
		_mPassword = password;
		_mSettings = settings;
		_mCallback = callback;
	}
	
	@Override
	public void run() {
		String status = "";
    	String userError = "Error logging in. Please try again.";
    	try {
    		// Construct data
    		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    		nvps.add(new BasicNameValuePair("user", _mUsername.toString()));
    		nvps.add(new BasicNameValuePair("passwd", _mPassword.toString()));
    		
            _mSettings.client.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 20000);
            HttpPost httppost = new HttpPost("http://www.reddit.com/api/login/"+_mUsername);
            httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            
            // Perform the HTTP POST request
        	HttpResponse response = _mSettings.client.execute(httppost);
        	status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();
        	
        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	if (line == null) {
        		throw new HttpException("No content returned from login POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		userError = "Bad password.";
        		throw new Exception("Wrong password");
        	}

        	// DEBUG
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
        	
        	List<Cookie> cookies = _mSettings.client.getCookieStore().getCookies();
        	if (cookies.isEmpty()) {
        		throw new HttpException("Failed to login: No cookies");
        	}
        	for (Cookie c : cookies) {
        		if (c.getName().equals("reddit_session")) {
        			_mSettings.setRedditSessionCookie(c);
        			break;
        		}
        	}
        	
        	// Getting here means you successfully logged in.
        	// Congratulations!
        	// You are a true reddit master!
        
        	_mSettings.setUsername(_mUsername);
        	_mSettings.setLoggedIn(true);
        	Toast.makeText(_mSettings.activity, "Logged in as "+_mUsername, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
        	_mSettings.handler.post(new ErrorToaster(userError, Toast.LENGTH_LONG, _mSettings));
        	_mSettings.setLoggedIn(false);
        }
        Log.d(TAG, status);

        // Post a response using handler
        if (_mSettings.isAlive) {
        	_mSettings.handler.post(_mCallback);
        }
    }
}
