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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.captcha.CaptchaCheckRequiredTask;
import com.andrewshu.android.reddit.captcha.CaptchaDownloadTask;
import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.FormValidation;
import com.andrewshu.android.reddit.common.RedditIsFunHttpClientFactory;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.things.ThingInfo;

public class InboxActivity extends TabActivity {
	
	private final RedditSettings mSettings = new RedditSettings();
	private final HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();
	
	private static final String[] whichInboxes = {"inbox", "moderator"};
	
    private volatile String mCaptchaIden = null;
	private volatile String mCaptchaUrl = null;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettings.loadRedditPreferences(this, mClient);
        setRequestedOrientation(mSettings.getRotation());
        setTheme(mSettings.getTheme());
        
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        for (String whichInbox : whichInboxes)
        	addInboxTab(whichInbox);
        
        getTabHost().setCurrentTab(0);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		int previousTheme = mSettings.getTheme();

    	mSettings.loadRedditPreferences(this, mClient);

    	if (mSettings.getTheme() != previousTheme) {
    		relaunchActivity();
    	}
	}
	
	private void relaunchActivity() {
		finish();
		startActivity(getIntent());
	}
	
	private void addInboxTab(String whichInbox) {
        Intent inboxIntent = createInboxIntent(whichInbox);
        getTabHost().addTab(getTabHost().newTabSpec(whichInbox).setIndicator(whichInbox).setContent(inboxIntent));
	}

	private Intent createInboxIntent(String whichInbox) {
		return new Intent(this, InboxListActivity.class)
			.putExtra(Constants.WHICH_INBOX_KEY, whichInbox);
	}
	
    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inbox, menu);
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	switch (item.getItemId()) {
    	case R.id.compose_message_menu_id:
    		showDialog(Constants.DIALOG_COMPOSE);
    		break;
    	case R.id.refresh_menu_id:
    		String whichInbox = whichInboxes[getTabHost().getCurrentTab()];
    		InboxListActivity inboxListActivity = (InboxListActivity) getLocalActivityManager().getActivity(whichInbox);
    		if (inboxListActivity != null)
    			inboxListActivity.refresh();
			break;
    	case android.R.id.home:
    		Common.goHome(this);
    		break;
    	}
    	
    	return true;
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
    	AlertDialog.Builder builder;
    	LayoutInflater inflater;
    	View layout; // used for inflated views for AlertDialog.Builder.setView()

    	switch (id) {
    	case Constants.DIALOG_COMPOSE:
    		inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		layout = inflater.inflate(R.layout.compose_dialog, null);
    		dialog = builder.setView(layout).create();
    		final Dialog composeDialog = dialog;
    		
    		Common.setTextColorFromTheme(
    				mSettings.getTheme(),
    				getResources(),
    				(TextView) layout.findViewById(R.id.compose_destination_textview),
    				(TextView) layout.findViewById(R.id.compose_subject_textview),
    				(TextView) layout.findViewById(R.id.compose_message_textview),
    				(TextView) layout.findViewById(R.id.compose_captcha_textview),
    				(TextView) layout.findViewById(R.id.compose_captcha_loading)
			);
    		
    		final EditText composeDestination = (EditText) layout.findViewById(R.id.compose_destination_input);
    		final EditText composeSubject = (EditText) layout.findViewById(R.id.compose_subject_input);
    		final EditText composeText = (EditText) layout.findViewById(R.id.compose_text_input);
    		final Button composeSendButton = (Button) layout.findViewById(R.id.compose_send_button);
    		final Button composeCancelButton = (Button) layout.findViewById(R.id.compose_cancel_button);
    		final EditText composeCaptcha = (EditText) layout.findViewById(R.id.compose_captcha_input);
    		composeSendButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
		    		ThingInfo thingInfo = new ThingInfo();
		    		
		    		if (!FormValidation.validateComposeMessageInputFields(InboxActivity.this, composeDestination, composeSubject, composeText, composeCaptcha))
		    			return;
		    		
		    		thingInfo.setDest(composeDestination.getText().toString().trim());
		    		thingInfo.setSubject(composeSubject.getText().toString().trim());
		    		new MyMessageComposeTask(
		    				composeDialog,
		    				thingInfo,
		    				composeCaptcha.getText().toString().trim(),
		    				mCaptchaIden,
		    				mSettings,
		    				mClient,
		    				InboxActivity.this
    				).execute(composeText.getText().toString().trim());
		    		removeDialog(Constants.DIALOG_COMPOSE);
				}
    		});
    		composeCancelButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					removeDialog(Constants.DIALOG_COMPOSE);
				}
    		});
    		break;

    	case Constants.DIALOG_COMPOSING:
    		pdialog = new ProgressDialog(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		pdialog.setMessage("Composing message...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	default:
    		throw new IllegalArgumentException("Unexpected dialog id "+id);
    	}
    	return dialog;
    }
    
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
    	switch (id) {
		case Constants.DIALOG_COMPOSE:
			new MyCaptchaCheckRequiredTask(dialog).execute();
			break;
    	}
    }
    
	private class MyMessageComposeTask extends MessageComposeTask {
    	
    	public MyMessageComposeTask(Dialog dialog,
				ThingInfo targetThingInfo, String captcha, String captchaIden,
				RedditSettings settings, HttpClient client, Context context) {
			super(dialog, targetThingInfo, captcha, captchaIden, settings, client, context);
		}

		@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_COMPOSING);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		removeDialog(Constants.DIALOG_COMPOSING);
    		if (success) {
    			Toast.makeText(InboxActivity.this, "Message sent.", Toast.LENGTH_SHORT).show();
    			// TODO: add the reply beneath the original, OR redirect to sent messages page
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    		}
    	}
    }
    
    private class MyCaptchaCheckRequiredTask extends CaptchaCheckRequiredTask {
    	
    	Dialog _mDialog;
    	
		public MyCaptchaCheckRequiredTask(Dialog dialog) {
			super(Constants.REDDIT_BASE_URL + "/message/compose/", mClient);
			_mDialog = dialog;
		}
		
		@Override
		protected void saveState() {
			InboxActivity.this.mCaptchaIden = _mCaptchaIden;
			InboxActivity.this.mCaptchaUrl = _mCaptchaUrl;
		}

		@Override
		public void onPreExecute() {
			// Hide send button so user can't send until we know whether he needs captcha
			final Button sendButton = (Button) _mDialog.findViewById(R.id.compose_send_button);
			sendButton.setVisibility(View.INVISIBLE);
			// Show "loading captcha" label
			final TextView loadingCaptcha = (TextView) _mDialog.findViewById(R.id.compose_captcha_loading);
			loadingCaptcha.setVisibility(View.VISIBLE);
		}
		
		@Override
		public void onPostExecute(Boolean required) {
			final TextView captchaLabel = (TextView) _mDialog.findViewById(R.id.compose_captcha_textview);
			final ImageView captchaImage = (ImageView) _mDialog.findViewById(R.id.compose_captcha_image);
			final EditText captchaEdit = (EditText) _mDialog.findViewById(R.id.compose_captcha_input);
			final TextView loadingCaptcha = (TextView) _mDialog.findViewById(R.id.compose_captcha_loading);
			final Button sendButton = (Button) _mDialog.findViewById(R.id.compose_send_button);
			if (required == null) {
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, InboxActivity.this);
				return;
			}
			if (required) {
				captchaLabel.setVisibility(View.VISIBLE);
				captchaImage.setVisibility(View.VISIBLE);
				captchaEdit.setVisibility(View.VISIBLE);
				// Launch a task to download captcha and display it
				new MyCaptchaDownloadTask(_mDialog).execute();
			} else {
				captchaLabel.setVisibility(View.GONE);
				captchaImage.setVisibility(View.GONE);
				captchaEdit.setVisibility(View.GONE);
			}
			loadingCaptcha.setVisibility(View.GONE);
			sendButton.setVisibility(View.VISIBLE);
		}
	}
	
    private class MyCaptchaDownloadTask extends CaptchaDownloadTask {
    	
    	Dialog _mDialog;
    	
    	public MyCaptchaDownloadTask(Dialog dialog) {
    		super(mCaptchaUrl, mClient);
    		_mDialog = dialog;
    	}
    	
		@Override
		public void onPostExecute(Drawable captcha) {
			if (captcha == null) {
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, InboxActivity.this);
				return;
			}
			final ImageView composeCaptchaView = (ImageView) _mDialog.findViewById(R.id.compose_captcha_image);
			composeCaptchaView.setVisibility(View.VISIBLE);
			composeCaptchaView.setImageDrawable(captcha);
		}
	}
    
}
