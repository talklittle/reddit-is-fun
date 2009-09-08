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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;

public class SubmitLinkActivity extends TabActivity {
	
	private static final String TAG = "SubmitLinkActivity";

	TabHost mTabHost;
	
	private RedditSettings mSettings = new RedditSettings();
	private final DefaultHttpClient mClient = Common.createGzipHttpClient();
	
	private String mSubmitUrl;
	private volatile String mCaptchaIden = null;
	private volatile String mCaptchaUrl = null;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		Common.loadRedditPreferences(this, mSettings, mClient);
		setTheme(mSettings.theme);
		
		setContentView(R.layout.submit_link_main);

		final FrameLayout fl = (FrameLayout) findViewById(android.R.id.tabcontent);
		if (mSettings.theme == R.style.Reddit_Light) {
			fl.setBackgroundResource(R.color.light_gray);
		} else {
			fl.setBackgroundResource(R.color.android_dark_background);
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
		
		if (mSettings.loggedIn) {
			start();
		} else {
			showDialog(Constants.DIALOG_LOGIN);
		}
	}
	
	/**
	 * Enable the UI after user is logged in.
	 */
	private void start() {
		// Intents can be external (browser share page) or from Reddit is fun.
        String intentAction = getIntent().getAction();
        if (Intent.ACTION_SEND.equals(intentAction)) {
        	// Share
	        Bundle extras = getIntent().getExtras();
	        if (extras != null) {
	        	String url = extras.getString(Intent.EXTRA_TEXT);
	        	final EditText submitLinkUrl = (EditText) findViewById(R.id.submit_link_url);
	        	final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
	        	submitLinkUrl.setText(url);
	        	submitLinkReddit.setText("reddit.com");
        		submitTextReddit.setText("reddit.com");
        		mSubmitUrl = "http://www.reddit.com/submit";
	        }
        } else {
	        Bundle extras = getIntent().getExtras();
	        if (extras != null) {
	            // Pull current subreddit and thread info from Intent
	        	String subreddit = extras.getString(ThreadInfo.SUBREDDIT);
	    		final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
	        	if (Constants.FRONTPAGE_STRING.equals(subreddit)) {
	        		submitLinkReddit.setText("reddit.com");
	        		submitTextReddit.setText("reddit.com");
	        		mSubmitUrl = "http://www.reddit.com/submit";
	        	} else {
		        	submitLinkReddit.setText(subreddit);
		        	submitTextReddit.setText(subreddit);
		        	mSubmitUrl = "http://www.reddit.com/r/"+subreddit+"/submit";
	        	}
	        } else {
	        	mSubmitUrl = "http://www.reddit.com/submit";
	        }
        }
        
        final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
        submitLinkButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (validateLinkForm()) {
	        		final EditText submitLinkTitle = (EditText) findViewById(R.id.submit_link_title);
	        		final EditText submitLinkUrl = (EditText) findViewById(R.id.submit_link_url);
	        		final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        		final EditText submitLinkCaptcha = (EditText) findViewById(R.id.submit_link_captcha);
	        		new SubmitLinkTask(submitLinkTitle.getText(), submitLinkUrl.getText(), submitLinkReddit.getText(),
	        				Constants.SUBMIT_KIND_LINK, submitLinkCaptcha.getText()).execute();
        		}
        	}
        });
        final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
        submitTextButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (validateTextForm()) {
	        		final EditText submitTextTitle = (EditText) findViewById(R.id.submit_text_title);
	        		final EditText submitTextText = (EditText) findViewById(R.id.submit_text_text);
	        		final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
	        		final EditText submitTextCaptcha = (EditText) findViewById(R.id.submit_text_captcha);
	        		new SubmitLinkTask(submitTextTitle.getText(), submitTextText.getText(), submitTextReddit.getText(),
	        				Constants.SUBMIT_KIND_SELF, submitTextCaptcha.getText()).execute();
        		}
        	}
        });
        
        // Check the CAPTCHA
        new CheckCaptchaRequiredTask().execute();
	}
	
	private void returnStatus(int status) {
		Intent i = new Intent();
		setResult(status, i);
		finish();
	}

	
	@Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
    }
    
    
	
	private class LoginTask extends AsyncTask<Void, Void, String> {
    	private CharSequence mUsername, mPassword;
    	
    	LoginTask(CharSequence username, CharSequence password) {
    		mUsername = username;
    		mPassword = password;
    	}
    	
    	@Override
    	public String doInBackground(Void... v) {
    		return Common.doLogin(mUsername, mPassword, mClient, mSettings);
        }
    	
    	@Override
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	@Override
    	protected void onPostExecute(String errorMessage) {
    		dismissDialog(Constants.DIALOG_LOGGING_IN);
			if (errorMessage == null) {
    			Toast.makeText(SubmitLinkActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
    			// Check mail
    			new Common.PeekEnvelopeTask(SubmitLinkActivity.this, mClient, mSettings.mailNotificationStyle).execute();
    			// Show the UI and allow user to proceed
    			start();
        	} else {
            	Common.showErrorToast(errorMessage, Toast.LENGTH_LONG, SubmitLinkActivity.this);
    			returnStatus(Constants.RESULT_LOGIN_REQUIRED);
        	}
    	}
    }
    
    

	private class SubmitLinkTask extends AsyncTask<Void, Void, ThreadInfo> {
    	CharSequence _mTitle, _mUrlOrText, _mSubreddit, _mKind, _mCaptcha;
		String _mUserError = "Error submitting reply. Please try again.";
    	
    	SubmitLinkTask(CharSequence title, CharSequence urlOrText, CharSequence subreddit, CharSequence kind, CharSequence captcha) {
    		_mTitle = title;
    		_mUrlOrText = urlOrText;
    		_mSubreddit = subreddit;
    		_mKind = kind;
    		_mCaptcha = captcha;
    	}
    	
    	@Override
        public ThreadInfo doInBackground(Void... voidz) {
        	ThreadInfo newlyCreatedThread = null;
        	String userError = "Error creating submission. Please try again.";
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, SubmitLinkActivity.this);
        		_mUserError = "Not logged in";
        		return null;
        	}
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient);
        			if (Constants.LOGGING) Log.e(TAG, "Reply failed because doUpdateModhash() failed");
        			return null;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("sr", _mSubreddit.toString()));
    			nvps.add(new BasicNameValuePair("r", _mSubreddit.toString()));
    			nvps.add(new BasicNameValuePair("title", _mTitle.toString()));
    			nvps.add(new BasicNameValuePair("kind", _mKind.toString()));
    			// Put a url or selftext based on the kind of submission
    			if (Constants.SUBMIT_KIND_LINK.equals(_mKind))
    				nvps.add(new BasicNameValuePair("url", _mUrlOrText.toString()));
    			else // if (Constants.SUBMIT_KIND_SELF.equals(_mKind))
    				nvps.add(new BasicNameValuePair("text", _mUrlOrText.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			if (mCaptchaIden != null) {
    				nvps.add(new BasicNameValuePair("iden", mCaptchaIden));
    				nvps.add(new BasicNameValuePair("captcha", _mCaptcha.toString()));
    			}
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/submit");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        // The progress dialog is non-cancelable, so set a shorter timeout than system's
    	        HttpParams params = httppost.getParams();
    	        HttpConnectionParams.setConnectionTimeout(params, 30000);
    	        HttpConnectionParams.setSoTimeout(params, 30000);
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	status = response.getStatusLine().toString();
            	if (!status.contains("OK"))
            		throw new HttpException(status);
            	
            	entity = response.getEntity();

            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();
            	if (line == null || Constants.EMPTY_STRING.equals(line)) {
            		throw new HttpException("No content returned from reply POST");
            	}
            	if (line.contains("WRONG_PASSWORD")) {
            		throw new Exception("Wrong password");
            	}
            	if (line.contains("USER_REQUIRED")) {
            		// The modhash probably expired
            		mSettings.setModhash(null);
            		throw new Exception("User required. Huh?");
            	}
            	if (line.contains("SUBREDDIT_NOEXIST")) {
            		_mUserError = "That subreddit does not exist.";
            		throw new Exception("SUBREDDIT_NOEXIST: " + _mSubreddit);
            	}
            	if (line.contains("SUBREDDIT_NOTALLOWED")) {
            		_mUserError = "You are not allowed to post to that subreddit.";
            		throw new Exception("SUBREDDIT_NOTALLOWED: " + _mSubreddit);
            	}
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);

            	String newId, newSubreddit;
            	Matcher idMatcher = Constants.NEW_THREAD_PATTERN.matcher(line);
            	if (idMatcher.find()) {
            		newSubreddit = idMatcher.group(1);
            		newId = idMatcher.group(2);
            	} else {
            		if (line.contains("RATELIMIT")) {
                		// Try to find the # of minutes using regex
                    	Matcher rateMatcher = Constants.RATELIMIT_RETRY_PATTERN.matcher(line);
                    	if (rateMatcher.find())
                    		userError = rateMatcher.group(1);
                    	else
                    		userError = "you are trying to submit too fast. try again in a few minutes.";
                		throw new Exception(userError);
                	}
            		if (line.contains("BAD_CAPTCHA")) {
            			userError = "Bad CAPTCHA. Try again.";
            			new DownloadCaptchaTask().execute();
            		}
                	throw new Exception("No id returned by reply POST.");
            	}
            	
            	entity.consumeContent();
            	
            	// Getting here means success. Create a new ThreadInfo.
            	newlyCreatedThread = new ThreadInfo();
            	// We only need to fill in a few fields.
            	newlyCreatedThread.put(ThreadInfo.ID, newId);
            	newlyCreatedThread.put(ThreadInfo.SUBREDDIT, newSubreddit);
            	newlyCreatedThread.put(ThreadInfo.TITLE, _mTitle.toString());
            	
            	return newlyCreatedThread;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        			}
        		}
        		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        	}
        	return null;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_SUBMITTING);
    	}
    	
    	
    	@Override
    	public void onPostExecute(ThreadInfo newlyCreatedThread) {
    		dismissDialog(Constants.DIALOG_SUBMITTING);
    		if (newlyCreatedThread == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, SubmitLinkActivity.this);
    		} else {
        		// Success. Return the subreddit and thread id
    			Intent i = new Intent();
    			i.putExtra(ThreadInfo.SUBREDDIT, newlyCreatedThread.getSubreddit());
    			i.putExtra(ThreadInfo.ID, newlyCreatedThread.getId());
    			i.putExtra(ThreadInfo.TITLE, newlyCreatedThread.getTitle());
    			setResult(Activity.RESULT_OK, i);
    			finish();
    		}
    	}
    }
	
	private class CheckCaptchaRequiredTask extends AsyncTask<Void, Void, Boolean> {
		@Override
		public Boolean doInBackground(Void... voidz) {
			HttpEntity entity = null;
			try {
				HttpGet request = new HttpGet(mSubmitUrl);
				HttpResponse response = mClient.execute(request);
				entity = response.getEntity(); 
	    		BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();

            	Matcher idenMatcher = Constants.CAPTCHA_IDEN_PATTERN.matcher(line);
            	Matcher urlMatcher = Constants.CAPTCHA_IMAGE_PATTERN.matcher(line);
            	if (idenMatcher.find() && urlMatcher.find()) {
            		mCaptchaIden = idenMatcher.group(1);
            		mCaptchaUrl = urlMatcher.group(2);
            		entity.consumeContent();
            		return true;
            	} else {
            		mCaptchaIden = null;
            		mCaptchaUrl = null;
            		entity.consumeContent();
            		return false;
            	}
			} catch (Exception e) {
				if (entity != null) {
					try {
						entity.consumeContent();
					} catch (Exception e2) {
						if (Constants.LOGGING) Log.e(TAG, e.getMessage());
					}
				}
				if (Constants.LOGGING) Log.e(TAG, "Error accessing "+mSubmitUrl+" to check for CAPTCHA");
			}
			return null;
		}
		
		@Override
		public void onPreExecute() {
			// Hide submit buttons so user can't submit until we know whether he needs captcha
			final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
			final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
			submitLinkButton.setVisibility(View.GONE);
			submitTextButton.setVisibility(View.GONE);
			// Show "loading captcha" label
			final TextView loadingLinkCaptcha = (TextView) findViewById(R.id.submit_link_captcha_loading);
			final TextView loadingTextCaptcha = (TextView) findViewById(R.id.submit_text_captcha_loading);
			loadingLinkCaptcha.setVisibility(View.VISIBLE);
			loadingTextCaptcha.setVisibility(View.VISIBLE);
		}
		
		@Override
		public void onPostExecute(Boolean required) {
			final TextView linkCaptchaLabel = (TextView) findViewById(R.id.submit_link_captcha_label);
			final ImageView linkCaptchaImage = (ImageView) findViewById(R.id.submit_link_captcha_image);
			final EditText linkCaptchaEdit = (EditText) findViewById(R.id.submit_link_captcha);
			final TextView textCaptchaLabel = (TextView) findViewById(R.id.submit_text_captcha_label);
			final ImageView textCaptchaImage = (ImageView) findViewById(R.id.submit_text_captcha_image);
			final EditText textCaptchaEdit = (EditText) findViewById(R.id.submit_text_captcha);
			final TextView loadingLinkCaptcha = (TextView) findViewById(R.id.submit_link_captcha_loading);
			final TextView loadingTextCaptcha = (TextView) findViewById(R.id.submit_text_captcha_loading);
			final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
			final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
			if (required == null) {
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, SubmitLinkActivity.this);
				return;
			}
			if (required) {
				linkCaptchaLabel.setVisibility(View.VISIBLE);
				linkCaptchaImage.setVisibility(View.VISIBLE);
				linkCaptchaEdit.setVisibility(View.VISIBLE);
				textCaptchaLabel.setVisibility(View.VISIBLE);
				textCaptchaImage.setVisibility(View.VISIBLE);
				textCaptchaEdit.setVisibility(View.VISIBLE);
				// Launch a task to download captcha and display it
				new DownloadCaptchaTask().execute();
			} else {
				linkCaptchaLabel.setVisibility(View.GONE);
				linkCaptchaImage.setVisibility(View.GONE);
				linkCaptchaEdit.setVisibility(View.GONE);
				textCaptchaLabel.setVisibility(View.GONE);
				textCaptchaImage.setVisibility(View.GONE);
				textCaptchaEdit.setVisibility(View.GONE);
			}
			loadingLinkCaptcha.setVisibility(View.GONE);
			loadingTextCaptcha.setVisibility(View.GONE);
			submitLinkButton.setVisibility(View.VISIBLE);
			submitTextButton.setVisibility(View.VISIBLE);
		}
	}
	
	private class DownloadCaptchaTask extends AsyncTask<Void, Void, Drawable> {
		@Override
		public Drawable doInBackground(Void... voidz) {
			try {
				HttpGet request = new HttpGet("http://www.reddit.com/" + mCaptchaUrl);
				HttpResponse response = mClient.execute(request);
	    	
				InputStream in = response.getEntity().getContent();
				
				return Drawable.createFromStream(in, "captcha");
			
			} catch (Exception e) {
				Common.showErrorToast("Error downloading captcha.", Toast.LENGTH_LONG, SubmitLinkActivity.this);
			}
			
			return null;
		}
		
		@Override
		public void onPostExecute(Drawable captcha) {
			if (captcha == null) {
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, SubmitLinkActivity.this);
				return;
			}
			final ImageView linkCaptchaView = (ImageView) findViewById(R.id.submit_link_captcha_image);
			final ImageView textCaptchaView = (ImageView) findViewById(R.id.submit_text_captcha_image);
			linkCaptchaView.setVisibility(View.VISIBLE);
			linkCaptchaView.setImageDrawable(captcha);
			textCaptchaView.setVisibility(View.VISIBLE);
			textCaptchaView.setImageDrawable(captcha);
		}
	}
    
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		ProgressDialog pdialog;
		switch (id) {
		case Constants.DIALOG_LOGIN:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.login_dialog);
    		dialog.setTitle("Login to reddit.com");
    		// If user presses "back" then quit.
    		dialog.setOnCancelListener(new OnCancelListener() {
    			public void onCancel(DialogInterface d) {
    				if (!mSettings.loggedIn) {
	        			returnStatus(Activity.RESULT_CANCELED);
    				}
    			}
    		});
    		final EditText loginUsernameInput = (EditText) dialog.findViewById(R.id.login_username_input);
    		final EditText loginPasswordInput = (EditText) dialog.findViewById(R.id.login_password_input);
    		loginUsernameInput.setOnKeyListener(new OnKeyListener() {
    			public boolean onKey(View v, int keyCode, KeyEvent event) {
    		        if ((event.getAction() == KeyEvent.ACTION_DOWN)
    		        		&& (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_TAB)) {
    		        	loginPasswordInput.requestFocus();
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		loginPasswordInput.setOnKeyListener(new OnKeyListener() {
    			public boolean onKey(View v, int keyCode, KeyEvent event) {
    		        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
        				CharSequence user = loginUsernameInput.getText();
        				CharSequence password = loginPasswordInput.getText();
        				dismissDialog(Constants.DIALOG_LOGIN);
    		        	new LoginTask(user, password).execute(); 
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		loginButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				CharSequence user = loginUsernameInput.getText();
    				CharSequence password = loginPasswordInput.getText();
    				dismissDialog(Constants.DIALOG_LOGIN);
    				new LoginTask(user, password).execute();
    		    }
    		});
    		break;

       	// "Please wait"
    	case Constants.DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Logging in...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
		case Constants.DIALOG_SUBMITTING:
			pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Submitting...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
		default:
    		break;
		}
		return dialog;
	}
	
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
    	super.onPrepareDialog(id, dialog);
    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		if (mSettings.username != null) {
	    		final TextView loginUsernameInput = (TextView) dialog.findViewById(R.id.login_username_input);
	    		loginUsernameInput.setText(mSettings.username);
    		}
    		final TextView loginPasswordInput = (TextView) dialog.findViewById(R.id.login_password_input);
    		loginPasswordInput.setText("");
    		break;
    		
		default:
			break;
    	}
    }
	
	private boolean validateLinkForm() {
		final EditText titleText = (EditText) findViewById(R.id.submit_link_title);
		final EditText urlText = (EditText) findViewById(R.id.submit_link_url);
		final EditText redditText = (EditText) findViewById(R.id.submit_link_reddit);
		if (Constants.EMPTY_STRING.equals(titleText.getText())) {
			Common.showErrorToast("Please provide a title.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Constants.EMPTY_STRING.equals(urlText.getText())) {
			Common.showErrorToast("Please provide a URL.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Constants.EMPTY_STRING.equals(redditText.getText())) {
			Common.showErrorToast("Please provide a subreddit.", Toast.LENGTH_LONG, this);
			return false;
		}
		return true;
	}
	private boolean validateTextForm() {
		final EditText titleText = (EditText) findViewById(R.id.submit_text_title);
		final EditText redditText = (EditText) findViewById(R.id.submit_text_reddit);
		if (Constants.EMPTY_STRING.equals(titleText.getText())) {
			Common.showErrorToast("Please provide a title.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Constants.EMPTY_STRING.equals(redditText.getText())) {
			Common.showErrorToast("Please provide a subreddit.", Toast.LENGTH_LONG, this);
			return false;
		}
		return true;
	}
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(0, R.id.pick_subreddit_menu_id, 0, "Pick subreddit")
            .setOnMenuItemClickListener(new SubmitLinkMenu(R.id.pick_subreddit_menu_id));

        menu.add(0, Constants.DIALOG_DOWNLOAD_CAPTCHA, 1, "Update CAPTCHA")
        	.setOnMenuItemClickListener(new SubmitLinkMenu(Constants.DIALOG_DOWNLOAD_CAPTCHA));

        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	super.onPrepareOptionsMenu(menu);
    	
    	if (mCaptchaUrl == null)
    		menu.findItem(Constants.DIALOG_DOWNLOAD_CAPTCHA).setVisible(false);
    	else
    		menu.findItem(Constants.DIALOG_DOWNLOAD_CAPTCHA).setVisible(true);
    	
    	return true;
    }
    
    private class SubmitLinkMenu implements MenuItem.OnMenuItemClickListener {
        private int mAction;

        SubmitLinkMenu(int action) {
            mAction = action;
        }

        public boolean onMenuItemClick(MenuItem item) {
        	switch (mAction) {
        	case R.id.pick_subreddit_menu_id:
        		Intent pickSubredditIntent = new Intent(SubmitLinkActivity.this, PickSubredditActivity.class);
        		pickSubredditIntent.putExtra(Constants.EXTRA_HIDE_FRONTPAGE_STRING, true);
        		startActivityForResult(pickSubredditIntent, Constants.ACTIVITY_PICK_SUBREDDIT);
        		break;
        	case Constants.DIALOG_DOWNLOAD_CAPTCHA:
        		new CheckCaptchaRequiredTask().execute();
        		break;
        	default:
        		throw new IllegalArgumentException("Unexpected action value "+mAction);
        	}
        	
        	return true;
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	
    	switch(requestCode) {
    	case Constants.ACTIVITY_PICK_SUBREDDIT:
    		if (resultCode == Activity.RESULT_OK) {
    			Bundle extras = intent.getExtras();
	    		String newSubreddit = extras.getString(ThreadInfo.SUBREDDIT);
	    		if (newSubreddit != null && !"".equals(newSubreddit)) {
	    			final EditText linkSubreddit = (EditText) findViewById(R.id.submit_link_reddit);
	    			final EditText textSubreddit = (EditText) findViewById(R.id.submit_text_reddit);
	    			linkSubreddit.setText(newSubreddit);
	    			textSubreddit.setText(newSubreddit);
	    		}
    		}
    		break;
    	default:
    		break;
    	}
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle state) {
    	super.onRestoreInstanceState(state);
    	try {
        	dismissDialog(Constants.DIALOG_LOGIN);
	    } catch (IllegalArgumentException e) {
	    	// Ignore.
	    }
	    try {
        	dismissDialog(Constants.DIALOG_LOGGING_IN);
	    } catch (IllegalArgumentException e) {
	    	// Ignore.
	    }
    }
}
