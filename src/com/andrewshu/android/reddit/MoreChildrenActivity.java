/*
 * Copyright 2009 Andrew Shu
 *
 * This file is part of "reddit is fun".
 *
 * "reddit is fun" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "reddit is fun" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "reddit is fun".  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrewshu.android.reddit;

import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

public class MoreChildrenActivity extends RedditCommentsListActivity {

	private static final String TAG = "MoreChildrenActivity";
	
	private String mMoreChildrenFullname;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        
        // Pull current subreddit and thread info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
        	// Quit, because the Comments List requires subreddit and thread id from Intent.
        	if (Constants.LOGGING) Log.e(TAG, "Quitting because no subreddit and thread id data was passed into the Intent.");
        	finish();
        }
    	if (savedInstanceState == null)
    		mMoreChildrenFullname = extras.getString(CommentInfo.NAME);
    	else
    		mMoreChildrenFullname = savedInstanceState.getString(CommentInfo.NAME);
            
        new LoadMoreCommentsTask(mMoreChildrenFullname, mSettings.subreddit).execute();
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mCanChord) {
            // The user has already fired a shortcut with this hold down of the
            // menu key.
            return false;
        }
        
        switch (item.getItemId()) {
        case R.id.login_logout_menu_id:
        	if (mSettings.loggedIn) {
        		Common.doLogout(mSettings, mClient);
        		Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new LoadMoreCommentsTask(mMoreChildrenFullname, mSettings.subreddit).execute();
        	} else {
        		showDialog(Constants.DIALOG_LOGIN);
        	}
    		break;
    	case R.id.refresh_menu_id:
    		new LoadMoreCommentsTask(mMoreChildrenFullname, mSettings.subreddit).execute();
    		break;
    	case R.id.open_browser_menu_id:
    		// XXX still using hack to get id from fullname (substring(3))
    		String url = new StringBuilder("http://www.reddit.com/r/")
				.append(mSettings.subreddit).append("/comments/").append(mSettings.threadId)
				.append("/z/").append(mMoreChildrenFullname.substring(3)).toString();
    		Common.launchBrowser(url, this);
    		break;
        default:
    		super.onOptionsItemSelected(item);
    	}
    	
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
    	super.onSaveInstanceState(icicle);
    	icicle.putString(CommentInfo.NAME, mMoreChildrenFullname);
    }


}
