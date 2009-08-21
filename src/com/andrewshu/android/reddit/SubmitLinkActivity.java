package com.andrewshu.android.reddit;

import org.apache.http.impl.client.DefaultHttpClient;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;

public class SubmitLinkActivity extends TabActivity {

	TabHost mTabHost;
	
	private RedditSettings mSettings = new RedditSettings();
	private DefaultHttpClient mClient = new DefaultHttpClient();
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		Common.loadRedditPreferences(this, mSettings, mClient);
		setTheme(mSettings.themeResId);
		
		setContentView(R.layout.submit_link_main);

		final FrameLayout fl = (FrameLayout) findViewById(android.R.id.tabcontent);
		if (mSettings.theme == Constants.THEME_LIGHT) {
			fl.setBackgroundResource(R.color.light_gray);
		} else {
			fl.setBackgroundResource(R.color.android_dark_background);
		}
		
		if (!mSettings.loggedIn) {
			Intent loginRequired = new Intent();
			setResult(Constants.RESULT_LOGIN_REQUIRED, loginRequired);
			finish();
		}
		
		mTabHost = getTabHost();
		mTabHost.addTab(mTabHost.newTabSpec(Constants.TAB_LINK).setIndicator("link").setContent(R.id.submit_link_view));
		mTabHost.addTab(mTabHost.newTabSpec(Constants.TAB_TEXT).setIndicator("text").setContent(R.id.submit_text_view));
		mTabHost.setOnTabChangedListener(new OnTabChangeListener() {
			public void onTabChanged(String tabId) {
				// Copy everything (except url and text) from old tab to new tab
				final EditText submitLinkTitle = (EditText) findViewById(R.id.submit_link_title);
				final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        	final EditText submitTextTitle = (EditText) findViewById(R.id.submit_text_title);
	        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
				if (Constants.TAB_LINK.equals(tabId)) {
					submitLinkTitle.setText(submitTextTitle.getText());
					submitLinkReddit.setText(submitTextReddit.getText());
				} else {
					submitTextTitle.setText(submitLinkTitle.getText());
					submitTextReddit.setText(submitLinkReddit.getText());
				}
			}
		});
		mTabHost.setCurrentTab(0);
		
        // Pull current subreddit and thread info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	String subreddit = extras.getString(ThreadInfo.SUBREDDIT);
        	if (!Constants.FRONTPAGE_STRING.equals(subreddit)) {
        		mSettings.setSubreddit(subreddit);
	        	final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
	        	submitLinkReddit.setText(subreddit);
	        	submitTextReddit.setText(subreddit);
        	}
        }
	}
	
	
}
