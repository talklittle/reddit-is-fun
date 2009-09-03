package com.andrewshu.android.reddit;

import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;

public class MoreChildrenActivity extends RedditCommentsListActivity {

	private static final String TAG = "MoreChildrenActivity";
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        
        // Pull current subreddit and thread info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
        	// Quit, because the Comments List requires subreddit and thread id from Intent.
        	Log.e(TAG, "Quitting because no subreddit and thread id data was passed into the Intent.");
        	finish();
        }
    	String thingFullname = extras.getString(CommentInfo.NAME);
            
        new LoadMoreCommentsTask(thingFullname, mSettings.subreddit).execute();
    }
	
    private class LoadMoreCommentsTask extends RedditCommentsListActivity.DownloadCommentsTask {
    	private CharSequence mThingName, mSubreddit;
    	public LoadMoreCommentsTask(CharSequence thingName, CharSequence subreddit) {
    		mThingName = thingName;
    		mSubreddit = subreddit;
    	}
    	
    	@Override
    	public Void doInBackground(Integer... maxComments) {
    		HttpEntity entity = null;
    		try {
    			// Hack to get the "more" id
    			CharSequence moreId = mThingName.subSequence(3, mThingName.length());
    			
    			HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
	        		.append(mSubreddit.toString().trim())
	        		.append("/comments/")
	        		.append(mSettings.threadId)
	        		.append("/z/").append(moreId).append(".json?")
	        		.append(mSortByUrl).append("&").toString());
	        	HttpResponse response = mClient.execute(request);
	        	entity = response.getEntity();
	        	
	        	InputStream in = entity.getContent();
	            
	            parseCommentsJSON(in);
	            
	            in.close();
	            entity.consumeContent();
	            
    		} catch (Exception e) {
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
    }


}
