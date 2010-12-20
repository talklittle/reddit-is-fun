package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.AsyncTask;
import android.util.Log;

public abstract class CaptchaCheckRequiredTask extends AsyncTask<Void, Void, Boolean> {
	
	private static final String TAG = "CaptchaCheckRequiredTask";
	
	// Captcha "iden"
    private static final Pattern CAPTCHA_IDEN_PATTERN
    	= Pattern.compile("name=\"iden\" value=\"([^\"]+?)\"");
    // Group 2: Captcha image absolute path
    private static final Pattern CAPTCHA_IMAGE_PATTERN
    	= Pattern.compile("<img class=\"capimage\"( alt=\".*?\")? src=\"(/captcha/[^\"]+?)\"");

    
    String _mCaptchaIden;
    String _mCaptchaUrl;
    
    private DefaultHttpClient _mClient;
    
	public CaptchaCheckRequiredTask(DefaultHttpClient client) {
		_mClient = client;
	}
	
	@Override
	public Boolean doInBackground(Void... voidz) {
		HttpEntity entity = null;
		try {
			HttpGet request = new HttpGet("http://www.reddit.com/message/compose/");
			HttpResponse response = _mClient.execute(request);
			entity = response.getEntity(); 
    		BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	in.close();

        	Matcher idenMatcher = CAPTCHA_IDEN_PATTERN.matcher(line);
        	Matcher urlMatcher = CAPTCHA_IMAGE_PATTERN.matcher(line);
        	if (idenMatcher.find() && urlMatcher.find()) {
        		_mCaptchaIden = idenMatcher.group(1);
        		_mCaptchaUrl = urlMatcher.group(2);
        		saveState();
        		return true;
        	} else {
        		_mCaptchaIden = null;
        		_mCaptchaUrl = null;
        		saveState();
        		return false;
        	}
		} catch (Exception e) {
			if (Constants.LOGGING) Log.e(TAG, "Error accessing http://www.reddit.com/message/compose/ to check for CAPTCHA");
    	} finally {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
    			}
    		}
		}
		return null;
	}
	
	abstract protected void saveState();
}
