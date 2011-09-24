/*
 * Copyright 2011 Andrew Shu
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

package com.andrewshu.android.reddit.mail;

import org.apache.http.client.HttpClient;

import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.settings.RedditSettings;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

public class InboxActivity extends TabActivity {

	private final RedditSettings mSettings = new RedditSettings();
	private final HttpClient mClient = Common.getGzipHttpClient();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettings.loadRedditPreferences(this, mClient);
        setRequestedOrientation(mSettings.getRotation());
        setTheme(mSettings.getTheme());
        
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
