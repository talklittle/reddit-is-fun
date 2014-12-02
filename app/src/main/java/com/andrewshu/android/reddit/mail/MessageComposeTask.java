package com.andrewshu.android.reddit.mail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import com.andrewshu.android.reddit.captcha.CaptchaException;
import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.things.ThingInfo;

import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public abstract class MessageComposeTask extends AsyncTask<String, Void, Boolean> {
	
	private static final String TAG = "MessageComposeTask";
	
	protected Dialog _mDialog;  // needed to update CAPTCHA on failure
	ThingInfo _mTargetThingInfo;
	protected String _mUserError = "Error composing message. Please try again.";
	String _mCaptcha;
	String _mCaptchaIden;
	
	RedditSettings _mSettings;
	HttpClient _mClient;
	Context _mContext;
	
	protected MessageComposeTask(Dialog dialog, ThingInfo targetThingInfo, String captcha, String captchaIden,
			RedditSettings settings, HttpClient client, Context context) {
		_mDialog = dialog;
		_mTargetThingInfo = targetThingInfo;
		_mCaptcha = captcha;
		_mCaptchaIden = captchaIden;
		_mSettings = settings;
		_mClient = client;
		_mContext = context;
	}
	
	@Override
    public Boolean doInBackground(String... text) {
    	HttpEntity entity = null;
    	
    	if (!_mSettings.isLoggedIn()) {
    		Common.showErrorToast("You must be logged in to compose a message.", Toast.LENGTH_LONG, _mContext);
    		_mUserError = "Not logged in";
    		return false;
    	}
    	// Update the modhash if necessary
    	if (_mSettings.getModhash() == null) {
    		String modhash = Common.doUpdateModhash(_mClient);
    		if (modhash == null) {
    			// doUpdateModhash should have given an error about credentials
    			Common.doLogout(_mSettings, _mClient, _mContext);
    			if (Constants.LOGGING) Log.e(TAG, "Message compose failed because doUpdateModhash() failed");
    			return false;
    		}
    		_mSettings.setModhash(modhash);
    	}
    	
    	try {
    		// Construct data
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("text", text[0].toString()));
			nvps.add(new BasicNameValuePair("subject", _mTargetThingInfo.getSubject()));
			nvps.add(new BasicNameValuePair("to", _mTargetThingInfo.getDest()));
			nvps.add(new BasicNameValuePair("uh", _mSettings.getModhash().toString()));
			nvps.add(new BasicNameValuePair("thing_id", ""));
			if (_mCaptchaIden != null) {
				nvps.add(new BasicNameValuePair("iden", _mCaptchaIden));
				nvps.add(new BasicNameValuePair("captcha", _mCaptcha.toString()));
			}
			
			HttpPost httppost = new HttpPost(Constants.REDDIT_BASE_URL + "/api/compose");
	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
	        
	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
	        
            // Perform the HTTP POST request
	    	HttpResponse response = _mClient.execute(httppost);
        	entity = response.getEntity();

       		// Don't need the return value ID since reply isn't posted to inbox
        	Common.checkIDResponse(response, entity);

        	return true;
        	
    	} catch (CaptchaException e) {
    		if (Constants.LOGGING) Log.e(TAG, "CaptchaException", e);
    		_mUserError = e.getMessage();
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, "MessageComposeTask", e);
    		_mUserError = e.getMessage();
    	} finally {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (IOException e2) {
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
    			}
    		}
    	}
    	return false;
    }
	

}
