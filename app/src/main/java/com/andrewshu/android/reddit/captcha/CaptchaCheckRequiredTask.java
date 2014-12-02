package com.andrewshu.android.reddit.captcha;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.util.Util;

import android.os.AsyncTask;
import android.util.Log;

public abstract class CaptchaCheckRequiredTask extends AsyncTask<Void, Void, Boolean> {
	
	private static final String TAG = "CaptchaCheckRequiredTask";
	
	// Captcha "iden"
    private static final Pattern CAPTCHA_IDEN_PATTERN
    	= Pattern.compile("name=\"iden\" value=\"([^\"]+)\"");
    // Group 2: Captcha image absolute path
    private static final Pattern CAPTCHA_IMAGE_PATTERN
    	= Pattern.compile("<img class=\"capimage\"( alt=\"[^\"]*\")? src=\"(/captcha/[^\"]+)\"");

    
    protected String _mCaptchaIden;
    protected String _mCaptchaUrl;
    
    private String _mCheckUrl;
    private HttpClient _mClient;
    
	public CaptchaCheckRequiredTask(String checkUrl, HttpClient client) {
		_mCheckUrl = checkUrl;
		_mClient = client;
	}
	
	@Override
	public Boolean doInBackground(Void... voidz) {
		HttpEntity entity = null;
		BufferedReader in = null;
		try {
			HttpGet request = new HttpGet(_mCheckUrl);
			HttpResponse response = _mClient.execute(request);
			if (!Util.isHttpStatusOK(response)) {
				throw new HttpException("bad HTTP response: "+response);
			}
			entity = response.getEntity();
    		in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line;
        	
        	while ((line = in.readLine()) != null) {
	        	Matcher idenMatcher = CAPTCHA_IDEN_PATTERN.matcher(line);
	        	Matcher urlMatcher = CAPTCHA_IMAGE_PATTERN.matcher(line);
	        	if (idenMatcher.find() && urlMatcher.find()) {
	        		_mCaptchaIden = idenMatcher.group(1);
	        		_mCaptchaUrl = urlMatcher.group(2);
	        		saveState();
	        		return true;
	        	}
        	}
        	
    		_mCaptchaIden = null;
    		_mCaptchaUrl = null;
    		saveState();
    		return false;
    		
		} catch (Exception e) {
			if (Constants.LOGGING) Log.e(TAG, "Error accessing "+_mCheckUrl+" to check for CAPTCHA", e);
    	} finally {
    		if (in != null) {
    			try {
    				in.close();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, "in.Close()", e2);
    			}
    		}
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
    			}
    		}
		}
    	// mCaptchaIden and mCaptchaUrl are null if not required
    	// so on error, set them to some non-null dummy value
    	_mCaptchaIden = "";
    	_mCaptchaUrl = "";
    	saveState();
		return null;
	}
	
	abstract protected void saveState();
}
