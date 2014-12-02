package com.andrewshu.android.reddit.common.tasks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.RedditIsFunHttpClientFactory;
import com.andrewshu.android.reddit.common.util.StringUtils;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.things.ThingInfo;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class HideTask extends AsyncTask<Void, Void, Boolean> {
	private static final String TAG = "HideTask";
	
	protected ThingInfo mTargetThreadInfo;
	protected String mUserError = "Error hiding thread.";
	protected String mUrl;
	private RedditSettings mSettings;
	private Context mContext;
	protected boolean mHide;
	
	private final HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();
	
	public HideTask(boolean hide, ThingInfo mVoteTargetThreadInfo, RedditSettings mSettings, Context mContext){
		this.mTargetThreadInfo = mVoteTargetThreadInfo;
		this.mSettings = mSettings;
		this.mContext = mContext;
		this.mHide = hide;
		if (hide) {
			mUrl = Constants.REDDIT_BASE_URL + "/api/hide";
		} else {
			mUrl = Constants.REDDIT_BASE_URL + "/api/unhide";
		}
	}
	
	@Override
	public void onPreExecute() {
		if (!mSettings.isLoggedIn()) {
    		Common.showErrorToast("You must be logged in to hide/unhide a thread.", Toast.LENGTH_LONG, mContext);
    		cancel(true);
    		return;
    	}
		if (mHide) {
			Toast.makeText(mContext, "Hidden.", Toast.LENGTH_SHORT).show();
			mTargetThreadInfo.setHidden(true);
		} else {
			Toast.makeText(mContext, "Unhidden.", Toast.LENGTH_SHORT).show();
			mTargetThreadInfo.setHidden(false);
		}
	}
	
	@Override
	public Boolean doInBackground(Void... v) {
		
		String status = "";
    	HttpEntity entity = null;
    	
    	if (!mSettings.isLoggedIn()) {
    		mUserError = "You must be logged in to hide/unhide a thread.";
    		return false;
    	}
    	
    	// Update the modhash if necessary
    	if (mSettings.getModhash() == null) {
    		String modhash = Common.doUpdateModhash(mClient);
    		if (modhash == null) {
    			// doUpdateModhash should have given an error about credentials
    			Common.doLogout(mSettings, mClient, mContext);
    			if (Constants.LOGGING) Log.e(TAG, "updating hide status failed because doUpdateModhash() failed");
    			return false;
    		}
    		mSettings.setModhash(modhash);
    	}
    	
    	List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("id", mTargetThreadInfo.getName()));
		nvps.add(new BasicNameValuePair("uh", mSettings.getModhash().toString()));
		//nvps.add(new BasicNameValuePair("executed", _mExecuted));
		
		try {
			HttpPost request = new HttpPost(mUrl);
			request.setHeader("Content-Type", "application/x-www-form-urlencoded");
			request.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			
			HttpResponse response = mClient.execute(request);
	    	status = response.getStatusLine().toString();
	    	
        	if (!status.contains("OK")) {
        		mUserError = mUrl;
        		throw new HttpException(mUrl);
        	}
        	
        	entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	in.close();
        	if (StringUtils.isEmpty(line)) {
        		mUserError = "Connection error when hiding thread. Try again.";
        		throw new HttpException("No content returned from hide POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		mUserError = "Wrong password.";
        		throw new Exception("Wrong password.");
        	}
        	if (line.contains("USER_REQUIRED")) {
        		// The modhash probably expired
        		throw new Exception("User required. Huh?");
        	}
        	
        	Common.logDLong(TAG, line);
        	
        	entity.consumeContent();
        	return true;
        	
		} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
    			}
    		}
    		if (Constants.LOGGING) Log.e(TAG, "HideTask", e);
    	}
		
    	return false;
	}
	
	@Override
	public void onPostExecute(Boolean success) {
		if (!success) {
			Common.showErrorToast(mUserError, Toast.LENGTH_LONG, mContext);
		}
	}
}