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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

class SaveTask extends AsyncTask<Void, Void, Boolean> {
	private static final String TAG = "SaveWorker";
	
	private ThingInfo mTargetThreadInfo;
	private String mUserError = "Error voting.";
	private String mUrl;
	private boolean mSave;
	private RedditSettings mSettings;
	private Context mContext;
	private ArrayAdapter mThreadsListAdapter;
	
	private final DefaultHttpClient mClient = Common.getGzipHttpClient();
	
	public SaveTask(boolean mSave, ThingInfo mVoteTargetThreadInfo, 
								RedditSettings mSettings, Context mContext, 
								ArrayAdapter mThreadsListAdapter){
		if(mSave){
			this.mUrl = "http://www.reddit.com/api/save";
		} else {
			this.mUrl = "http://www.reddit.com/api/unsave";
		}
		
		this.mSave = mSave;
		this.mTargetThreadInfo = mVoteTargetThreadInfo;
		this.mSettings = mSettings;
		this.mContext = mContext;
		this.mThreadsListAdapter = mThreadsListAdapter;
	}
	
	@Override
	public void onPreExecute() {
		if (!mSettings.loggedIn) {
    		Common.showErrorToast("You must be logged in to save.", Toast.LENGTH_LONG, mContext);
    		cancel(true);
    		return;
    	}
	}
	
	@Override
	public Boolean doInBackground(Void... v) {
		
		String status = "";
    	HttpEntity entity = null;
    	
    	if (!mSettings.loggedIn) {
    		mUserError = "You must be logged in to save.";
    		return false;
    	}
    	
    	// Update the modhash if necessary
    	if (mSettings.modhash == null) {
    		CharSequence modhash = Common.doUpdateModhash(mClient);
    		if (modhash == null) {
    			// doUpdateModhash should have given an error about credentials
    			Common.doLogout(mSettings, mClient, mContext);
    			if (Constants.LOGGING) Log.e(TAG, "updating save status failed because doUpdateModhash() failed");
    			return false;
    		}
    		mSettings.setModhash(modhash);
    	}
    	
    	List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("id", mTargetThreadInfo.getName()));
		nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
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
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		mUserError = "Connection error when voting. Try again.";
        		throw new HttpException("No content returned from save POST");
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
    				if (Constants.LOGGING) Log.e(TAG, e2.getMessage());
    			}
    		}
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    	}
		
    	return false;
	}
	
	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			if(mSave){
				mTargetThreadInfo.setSaved(true);
				Toast.makeText(mContext, "Saved!", Toast.LENGTH_LONG).show();
			} else {
				mTargetThreadInfo.setSaved(false);
				Toast.makeText(mContext, "Unsaved!", Toast.LENGTH_LONG).show();
			}
			if(mThreadsListAdapter != null)
				mThreadsListAdapter.notifyDataSetChanged();
		} else {
			Common.showErrorToast(mUserError, Toast.LENGTH_LONG, mContext);
		}
	}
}