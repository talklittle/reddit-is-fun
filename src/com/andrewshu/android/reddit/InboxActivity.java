package com.andrewshu.android.reddit;

import org.apache.http.impl.client.DefaultHttpClient;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

public class InboxActivity extends TabActivity {

	private final RedditSettings mSettings = new RedditSettings();
	private final DefaultHttpClient mClient = Common.getGzipHttpClient();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettings.loadRedditPreferences(this, mClient);
        setRequestedOrientation(mSettings.rotation);
        setTheme(mSettings.theme);
        
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        addInboxTab("inbox");
        addInboxTab("moderator");
        
        getTabHost().setCurrentTab(0);
	}
	
	private void addInboxTab(String whichInbox) {
        Intent inboxIntent = new Intent(getApplicationContext(), InboxListActivity.class);
        inboxIntent.putExtra(Constants.WHICH_INBOX_KEY, whichInbox);
        getTabHost().addTab(getTabHost().newTabSpec(whichInbox).setIndicator(whichInbox).setContent(inboxIntent));
	}
}
