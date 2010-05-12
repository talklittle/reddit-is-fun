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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Main Activity class representing a Subreddit, i.e., a ThreadsList.
 * 
 * @author TalkLittle
 *
 */
public final class InboxActivity extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "InboxActivity";
	
	// Captcha "iden"
    private final Pattern CAPTCHA_IDEN_PATTERN = Pattern.compile("name=\"iden\" value=\"([^\"]+?)\"");
    // Group 2: Captcha image absolute path
    private final Pattern CAPTCHA_IMAGE_PATTERN = Pattern.compile("<img class=\"capimage\"( alt=\".*?\")? src=\"(/captcha/[^\"]+?)\"");

    private final ObjectMapper om = new ObjectMapper();
    private final Markdown markdown = new Markdown();
    
    /** Custom list adapter that fits our threads data into the list. */
    private MessagesListAdapter mMessagesAdapter;
    // Lock used when modifying the mMessagesAdapter
    private static final Object MESSAGE_ADAPTER_LOCK = new Object();
    
    
    private final DefaultHttpClient mClient = Common.getGzipHttpClient();
    
    
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
    // UI State
    private View mVoteTargetView = null;
    private ThingInfo mVoteTargetThingInfo = null;
    private String mReplyTargetName = null;
    private URLSpan[] mVoteTargetSpans = null;
    // TODO: String mVoteTargetId so when you rotate, you can find the TargetThingInfo again
    private DownloadMessagesTask mCurrentDownloadMessagesTask = null;
    private final Object mCurrentDownloadMessagesTaskLock = new Object();
    
    private String mAfter = null;
    private String mBefore = null;
    
    private volatile String mCaptchaIden = null;
	private volatile String mCaptchaUrl = null;
    
    // ProgressDialogs with percentage bars
//    private AutoResetProgressDialog mLoadingCommentsProgress;
//    private int mNumVisibleMessages;
    
    private boolean mCanChord = false;
    
    /**
     * Called when the activity starts up. Do activity initialization
     * here, not in a constructor.
     * 
     * @see Activity#onCreate
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Common.loadRedditPreferences(this, mSettings, mClient);
        setRequestedOrientation(mSettings.rotation);
        setTheme(mSettings.theme);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(R.layout.inbox_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().
        
		if (mSettings.loggedIn) {
			new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
		} else {
			showDialog(Constants.DIALOG_LOGIN);
		}
		
		if (savedInstanceState != null) {
        	mReplyTargetName = savedInstanceState.getString(Constants.REPLY_TARGET_NAME_KEY);
		}
    }
    
    /**
     * Hack to explicitly set background color whenever changing ListView.
     */
    public void setContentView(int layoutResID) {
    	super.setContentView(layoutResID);
    	// HACK: set background color directly for android 2.0
        if (mSettings.theme == R.style.Reddit_Light)
        	getListView().setBackgroundResource(R.color.white);
        registerForContextMenu(getListView());
    }
    
	private void returnStatus(int status) {
		Intent i = new Intent();
		setResult(status, i);
		finish();
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
    	int previousTheme = mSettings.theme;
    	boolean previousLoggedIn = mSettings.loggedIn;
    	Common.loadRedditPreferences(this, mSettings, mClient);
    	setRequestedOrientation(mSettings.rotation);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.theme);
    		setContentView(R.layout.threads_list_content);
    		setListAdapter(mMessagesAdapter);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
    	if (mSettings.loggedIn != previousLoggedIn) {
    		new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    	}
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
    }
    
    
    /**
     * Return the ThingInfo based on linear search over the names
     */
    private ThingInfo findThingInfoByName(String name) {
    	if (name == null)
    		return null;
    	synchronized(MESSAGE_ADAPTER_LOCK) {
    		for (int i = 0; i < mMessagesAdapter.getCount(); i++) {
    			if (mMessagesAdapter.getItem(i).getName().equals(name))
    				return mMessagesAdapter.getItem(i);
    		}
    	}
    	return null;
    }
    
    
    private final class MessagesListAdapter extends ArrayAdapter<ThingInfo> {
    	public boolean mIsLoading = true;
    	
    	private LayoutInflater mInflater;
        
    	public boolean isEmpty() {
    		if (mIsLoading)
    			return false;
    		return super.isEmpty();
    	}
    	
        public MessagesListAdapter(Context context, List<ThingInfo> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            
            ThingInfo item = this.getItem(position);
            
            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(R.layout.inbox_list_item, null);
            } else {
                view = convertView;
            }
            
            // Set the values of the Views for the CommentsListItem
            
            TextView fromInfoView = (TextView) view.findViewById(R.id.from_info);
            TextView subjectView = (TextView) view.findViewById(R.id.subject);
            TextView bodyView = (TextView) view.findViewById(R.id.body);
            
            // Highlight new messages in red
            if (item.isNew())
            	fromInfoView.setTextColor(getResources().getColor(R.color.red));
            else
            	fromInfoView.setTextColor(getResources().getColor(R.color.dark_gray));
            // Build fromInfoView using Spans. Example (** means bold & different color):
            // from *talklittle_test* sent 20 hours ago
            SpannableStringBuilder builder = new SpannableStringBuilder();
            SpannableString authorSS = new SpannableString(item.getAuthor());
            builder.append("from ");
            // Make the author bold and a different color
            int authorLen = item.getAuthor().length();
            StyleSpan authorStyleSpan = new StyleSpan(Typeface.BOLD);
            authorSS.setSpan(authorStyleSpan, 0, authorLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ForegroundColorSpan fcs;
            if (mSettings.theme == R.style.Reddit_Light)
            	fcs = new ForegroundColorSpan(getResources().getColor(R.color.dark_blue));
            else
            	fcs = new ForegroundColorSpan(getResources().getColor(R.color.white));
            authorSS.setSpan(fcs, 0, authorLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(authorSS);
            // When it was sent
            builder.append(" sent ");
            builder.append(Util.getTimeAgo(Double.valueOf(item.getCreated_utc())));
            fromInfoView.setText(builder);
            
            subjectView.setText(item.getSubject());
            bodyView.setText(item.getSSBBody());
    
	        return view;
        }
    } // End of MessagesListAdapter

    
    /**
     * Called when user clicks an item in the list. Mark message read.
     * If item was already focused, open a dialog.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ThingInfo item = mMessagesAdapter.getItem(position);
        
        // Mark the message/comment as selected
        mVoteTargetThingInfo = item;
        mVoteTargetView = v;
        mReplyTargetName = item.getName();
        
        // If new, mark the message read. Otherwise handle it.
        if (item.isNew()) {
        	new ReadMessageTask().execute();
        } else {
            openContextMenu(v);
        }
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    	int rowId = (int) info.id;
    	ThingInfo item = mMessagesAdapter.getItem(rowId);
    	
        // Mark the message/comment as selected
        mVoteTargetThingInfo = item;
        mVoteTargetView = v;
        mReplyTargetName = item.getName();

        if (item.isWas_comment()) {
        	// TODO: include the context!
        	menu.add(0, Constants.DIALOG_COMMENT_CLICK, Menu.NONE, "Go to comment");
    	} else {
            menu.add(0, Constants.DIALOG_MESSAGE_CLICK, Menu.NONE, "Reply");
    	}
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case Constants.DIALOG_COMMENT_CLICK:
			Intent i = new Intent(getApplicationContext(), CommentsListActivity.class);
			i.putExtra(Constants.EXTRA_COMMENT_CONTEXT, mVoteTargetThingInfo.getContext());
			startActivity(i);
			return true;
    	case Constants.DIALOG_MESSAGE_CLICK:
    		showDialog(Constants.DIALOG_REPLY);
    		return true;
		default:
    		return super.onContextItemSelected(item);	
    	}
    }
    	
    

    /**
     * Resets the output UI list contents, retains session state.
     * @param messagesAdapter A MessagesListAdapter to use. Pass in null if you want a new empty one created.
     */
    void resetUI(MessagesListAdapter messagesAdapter) {
    	setContentView(R.layout.inbox_list_content);
    	synchronized (MESSAGE_ADAPTER_LOCK) {
	    	if (messagesAdapter == null) {
	            // Reset the list to be empty.
	    		mMessagesAdapter = new MessagesListAdapter(this, new ArrayList<ThingInfo>());
	    	} else {
	    		mMessagesAdapter = messagesAdapter;
	    	}
		    setListAdapter(mMessagesAdapter);
		    mMessagesAdapter.mIsLoading = false;
		    mMessagesAdapter.notifyDataSetChanged();  // Just in case
		}
    	getListView().setDivider(null);
        Common.updateListDrawables(this, mSettings.theme);
    }
    
    private void enableLoadingScreen() {
    	if (mSettings.theme == R.style.Reddit_Light) {
    		setContentView(R.layout.loading_light);
    	} else {
    		setContentView(R.layout.loading_dark);
    	}
    	synchronized (MESSAGE_ADAPTER_LOCK) {
	    	if (mMessagesAdapter != null)
	    		mMessagesAdapter.mIsLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mMessagesAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
    }

        
    
    /**
     * Task takes in a subreddit name string and thread id, downloads its data, parses
     * out the comments, and communicates them back to the UI as they are read.
     */
    private class DownloadMessagesTask extends AsyncTask<Integer, Long, Void>
    		implements PropertyChangeListener {
    	
    	private ArrayList<ThingInfo> _mThingInfos = new ArrayList<ThingInfo>();
    	private long _mContentLength;
    	
    	// XXX: maxComments is unused for now
    	public Void doInBackground(Integer... maxComments) {
    		HttpEntity entity = null;
            try {
            	HttpGet request = new HttpGet("http://www.reddit.com/message/inbox/.json");
            	HttpResponse response = mClient.execute(request);
            	
            	// Read the header to get Content-Length since entity.getContentLength() returns -1
            	Header contentLengthHeader = response.getFirstHeader("Content-Length");
            	_mContentLength = Long.valueOf(contentLengthHeader.getValue());
            	if (Constants.LOGGING) Log.d(TAG, "Content length: "+_mContentLength);

            	entity = response.getEntity();
            	InputStream in = entity.getContent();
            	
            	// setup a special InputStream to report progress
            	ProgressInputStream pin = new ProgressInputStream(in, _mContentLength);
            	pin.addPropertyChangeListener(this);
            	
                parseInboxJSON(pin);
                
                pin.close();
                in.close();
                
                // XXX: HACK: http://code.reddit.com/ticket/709
                // Marking messages as read is currently broken (even with mark=
                // For now, just send an extra request to the regular non-JSON i
                mClient.execute(new HttpGet("http://www.reddit.com/message/inbox"));

            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "failed:" + e.getMessage());
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent():" + e2.getMessage());
        			}
        		}
            }
            return null;
	    }
    	
    	private void parseInboxJSON(InputStream in) throws IOException,
		    	JsonParseException, IllegalStateException {
		
    		String genericListingError = "Not an inbox listing";
    		try {
    			Listing listing = om.readValue(in, Listing.class);
    			
    			if (!Constants.JSON_LISTING.equals(listing.getKind()))
    				throw new IllegalStateException(genericListingError);
    			// Save the modhash, after, and before
    			ListingData data = listing.getData();
    			if (Constants.EMPTY_STRING.equals(data.getModhash()))
    				mSettings.setModhash(null);
    			else
    				mSettings.setModhash(data.getModhash());
    			mAfter = data.getAfter();
    			mBefore = data.getBefore();
    			
    			// Go through the children and get the ThingInfos
    			for (ThingListing tiContainer : data.getChildren()) {
   					ThingInfo ti = tiContainer.getData();
   					// do markdown
   					ti.setBody(StringEscapeUtils.unescapeHtml(ti.getBody().trim().replaceAll("\r", "")));
   					ti.setSSBBody(markdown.markdown(ti.getBody(), new SpannableStringBuilder(), ti.getUrls()));
   					_mThingInfos.add(ti);
    			}
    		} catch (Exception ex) {
    			if (Constants.LOGGING) Log.e(TAG, "parseInboxJSON:" + ex.getMessage());
    		}
    	}

		@Override
    	public void onPreExecute() {
			synchronized (mCurrentDownloadMessagesTaskLock) {
				if (mCurrentDownloadMessagesTask != null)
					mCurrentDownloadMessagesTask.cancel(true);
				mCurrentDownloadMessagesTask = this;
			}
    		resetUI(null);
    		enableLoadingScreen();
    	}
    	
		@Override
    	public void onPostExecute(Void v) {
			synchronized (mCurrentDownloadMessagesTaskLock) {
				mCurrentDownloadMessagesTask = null;
			}
    		synchronized(MESSAGE_ADAPTER_LOCK) {
    			for (ThingInfo mi : _mThingInfos)
    				mMessagesAdapter.add(mi);
    		}
			disableLoadingScreen();
			Common.cancelMailNotification(InboxActivity.this.getApplicationContext());
    	}
		
    	@Override
    	public void onProgressUpdate(Long... progress) {
    		// 0-9999 is ok, 10000 means it's finished
    		getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * 9999 / (int) _mContentLength);
    	}
    	
    	public void propertyChange(PropertyChangeEvent event) {
    		publishProgress((Long) event.getNewValue());
    	}
    }
    
    
    private class LoginTask extends AsyncTask<Void, Void, String> {
    	private CharSequence mUsername, mPassword, mUserError;
    	
    	LoginTask(CharSequence username, CharSequence password) {
    		mUsername = username;
    		mPassword = password;
    	}
    	
    	@Override
    	public String doInBackground(Void... v) {
    		return Common.doLogin(mUsername, mPassword, mSettings, mClient, getApplicationContext());
        }
    	
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	protected void onPostExecute(String errorMessage) {
    		dismissDialog(Constants.DIALOG_LOGGING_IN);
    		if (errorMessage == null) {
    			Toast.makeText(InboxActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
	    		// Refresh the threads list
    			new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		} else {
            	Common.showErrorToast(mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    			returnStatus(Constants.RESULT_LOGIN_REQUIRED);
    		}
    	}
    }
    
    
    private class ReadMessageTask extends AsyncTask<Void, Void, Boolean> {
    	
    	private static final String TAG = "ReadMessageTask";
    	
    	private String _mUserError = "Error marking messag read.";
    	private ThingInfo _mTargetThingInfo;
    	
    	ReadMessageTask() {
    		_mTargetThingInfo = mVoteTargetThingInfo;
    	}
    	
    	@Override
    	public Boolean doInBackground(Void... v) {
        	String status = "";
        	HttpEntity entity = null;
        	
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to read the message.";
        		return false;
        	}
        	
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient, getApplicationContext());
        			if (Constants.LOGGING) Log.e(TAG, "Read message failed because doUpdateModhash() failed");
        			return false;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("id", _mTargetThingInfo.getName()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/read_message");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	status = response.getStatusLine().toString();
            	if (!status.contains("OK")) {
            		_mUserError = "HTTP error when marking message read. Try again.";
            		throw new HttpException(status);
            	}
            	
            	entity = response.getEntity();

            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();
            	if (line == null || Constants.EMPTY_STRING.equals(line)) {
            		_mUserError = "Connection error when marking message read. Try again.";
            		throw new HttpException("No content returned from read_message POST");
            	}
            	if (line.contains("WRONG_PASSWORD")) {
            		_mUserError = "Wrong password.";
            		throw new Exception("Wrong password.");
            	}
            	if (line.contains("USER_REQUIRED")) {
            		// The modhash probably expired
            		throw new Exception("User required. Huh?");
            	}
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);
            	
            	return true;
            	
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "ReadMessageTask:" + e.getMessage());
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent():" + e2.getMessage());
        			}
        		}
        	}
        	return false;
        }
    	
    	@Override
    	public void onPreExecute() {
    		if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to read message.", Toast.LENGTH_LONG, InboxActivity.this);
        		cancel(true);
        		return;
        	}
        	_mTargetThingInfo.setNew(false);
    		mMessagesAdapter.notifyDataSetChanged();
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		if (!success) {
    			// Read message failed. Mark new again...
            	_mTargetThingInfo.setLikes(true);
        		mMessagesAdapter.notifyDataSetChanged();
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    		}
    	}
    }
    
    
    private class MessageReplyTask extends AsyncTask<CharSequence, Void, Boolean> {
    	private CharSequence _mParentThingId;
    	String _mUserError = "Error submitting reply. Please try again.";
    	
    	MessageReplyTask(CharSequence parentThingId) {
    		_mParentThingId = parentThingId;
    	}
    	
    	@Override
        public Boolean doInBackground(CharSequence... text) {
        	String userError = "Error replying. Please try again.";
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, InboxActivity.this);
        		_mUserError = "Not logged in";
        		return false;
        	}
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient, getApplicationContext());
        			if (Constants.LOGGING) Log.e(TAG, "Reply failed because doUpdateModhash() failed");
        			return false;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("thing_id", _mParentThingId.toString()));
    			nvps.add(new BasicNameValuePair("text", text[0].toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/comment");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	entity = response.getEntity();

            	// Don't need return value id since reply isn't posted to inbox
            	Common.checkIDResponse(response, entity);
            	
            	return true;
            	
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "MessageReplyTask:" + e.getMessage());
        		_mUserError = e.getMessage();
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (IOException e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent():" + e2.getMessage());
        			}
        		}
        	}
        	return false;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_REPLYING);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_REPLYING);
    		if (success) {
    			Toast.makeText(InboxActivity.this, "Reply sent.", Toast.LENGTH_SHORT).show();
    			// TODO: add the reply beneath the original, OR redirect to sent messages page
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    		}
    	}
    }
    
    private class MessageComposeTask extends AsyncTask<CharSequence, Void, Boolean> {
    	Dialog _mDialog;  // needed to update CAPTCHA on failure
    	ThingInfo _mTargetThingInfo;
    	String _mUserError = "Error composing message. Please try again.";
    	CharSequence _mCaptcha;
    	
    	MessageComposeTask(Dialog dialog, ThingInfo targetThingInfo, CharSequence captcha) {
    		_mDialog = dialog;
    		_mTargetThingInfo = targetThingInfo;
    		_mCaptcha = captcha;
    	}
    	
    	@Override
        public Boolean doInBackground(CharSequence... text) {
        	HttpEntity entity = null;
        	
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to compose a message.", Toast.LENGTH_LONG, InboxActivity.this);
        		_mUserError = "Not logged in";
        		return false;
        	}
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient, getApplicationContext());
        			if (Constants.LOGGING) Log.e(TAG, "Message compose failed because doUpdateModhash() failed");
        			return false;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("text", text[0].toString()));
    			nvps.add(new BasicNameValuePair("subject", _mTargetThingInfo.getSubject()));
    			nvps.add(new BasicNameValuePair("to", _mTargetThingInfo.getDest()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			nvps.add(new BasicNameValuePair("thing_id", ""));
    			if (mCaptchaIden != null) {
    				nvps.add(new BasicNameValuePair("iden", mCaptchaIden));
    				nvps.add(new BasicNameValuePair("captcha", _mCaptcha.toString()));
    			}
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/compose");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
            	entity = response.getEntity();

           		// Don't need the return value ID since reply isn't posted to inbox
            	Common.checkIDResponse(response, entity);

            	return true;
            	
        	} catch (CaptchaException e) {
        		if (Constants.LOGGING) Log.e(TAG, "CaptchaException:" + e.getMessage());
        		_mUserError = e.getMessage();
    			new DownloadCaptchaTask(_mDialog).execute();
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "MessageComposeTask:" + e.getMessage());
        		_mUserError = e.getMessage();
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (IOException e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent():" + e2.getMessage());
        			}
        		}
        	}
        	return false;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_COMPOSING);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_COMPOSING);
    		if (success) {
    			Toast.makeText(InboxActivity.this, "Message sent.", Toast.LENGTH_SHORT).show();
    			// TODO: add the reply beneath the original, OR redirect to sent messages page
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    		}
    	}
    }
    
    private class CheckCaptchaRequiredTask extends AsyncTask<Void, Void, Boolean> {
    	private Dialog _mDialog;
    	public CheckCaptchaRequiredTask(Dialog dialog) {
    		_mDialog = dialog;
    	}
    	
		@Override
		public Boolean doInBackground(Void... voidz) {
			HttpEntity entity = null;
			try {
				HttpGet request = new HttpGet("http://www.reddit.com/message/compose/");
				HttpResponse response = mClient.execute(request);
				entity = response.getEntity(); 
	    		BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();

            	Matcher idenMatcher = CAPTCHA_IDEN_PATTERN.matcher(line);
            	Matcher urlMatcher = CAPTCHA_IMAGE_PATTERN.matcher(line);
            	if (idenMatcher.find() && urlMatcher.find()) {
            		mCaptchaIden = idenMatcher.group(1);
            		mCaptchaUrl = urlMatcher.group(2);
            		return true;
            	} else {
            		mCaptchaIden = null;
            		mCaptchaUrl = null;
            		return false;
            	}
			} catch (Exception e) {
				if (Constants.LOGGING) Log.e(TAG, "Error accessing http://www.reddit.com/message/compose/ to check for CAPTCHA");
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent():" + e2.getMessage());
        			}
        		}
			}
			return null;
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
				new DownloadCaptchaTask(_mDialog).execute();
			} else {
				captchaLabel.setVisibility(View.GONE);
				captchaImage.setVisibility(View.GONE);
				captchaEdit.setVisibility(View.GONE);
			}
			loadingCaptcha.setVisibility(View.GONE);
			sendButton.setVisibility(View.VISIBLE);
		}
	}
	
    private class DownloadCaptchaTask extends AsyncTask<Void, Void, Drawable> {
    	private Dialog _mDialog;
    	public DownloadCaptchaTask(Dialog dialog) {
    		_mDialog = dialog;
    	}
		@Override
		public Drawable doInBackground(Void... voidz) {
			try {
				HttpGet request = new HttpGet("http://www.reddit.com/" + mCaptchaUrl);
				HttpResponse response = mClient.execute(request);
	    	
				InputStream in = response.getEntity().getContent();
				
				return Drawable.createFromStream(in, "captcha");
			
			} catch (Exception e) {
				Common.showErrorToast("Error downloading captcha.", Toast.LENGTH_LONG, InboxActivity.this);
			}
			
			return null;
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
    
    
    
    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(0, Constants.DIALOG_COMPOSE, 0, "Compose Message");
        menu.add(0, Constants.DIALOG_REFRESH, 0, "Refresh");
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	switch (item.getItemId()) {
    	case Constants.DIALOG_COMPOSE:
    		showDialog(Constants.DIALOG_COMPOSE);
    		break;
    	case Constants.DIALOG_REFRESH:
			new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			break;
    	}
    	
    	return true;
    }
//
//    private class CommentsListMenu implements MenuItem.OnMenuItemClickListener {
//        private int mAction;
//
//        CommentsListMenu(int action) {
//            mAction = action;
//        }
//
//        public boolean onMenuItemClick(MenuItem item) {
//        	switch (mAction) {
//        	case Constants.DIALOG_OP:
//        		mVoteTargetThingInfo = mMessagesAdapter.getItem(0);
//        		showDialog(Constants.DIALOG_THING_CLICK);
//        		break;
//        	case Constants.DIALOG_REPLY:
//        		// From the menu, only used for the OP, which is a thread.
//            	mVoteTargetThingInfo = mMessagesAdapter.getItem(0);
//                showDialog(mAction);
//                break;
//        	case Constants.DIALOG_LOGIN:
//        		showDialog(mAction);
//        		break;
//        	case Constants.DIALOG_LOGOUT:
//        		Common.doLogout(mSettings, mClient);
//        		Toast.makeText(InboxActivity.this, "You have been logged out.", Toast.LENGTH_SHORT).show();
//        		new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
//        		break;
//        	case Constants.DIALOG_REFRESH:
//        		new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
//        		break;
//        	case Constants.DIALOG_OPEN_BROWSER:
//        		String url = "http://www.reddit.com/message/inbox";
//        		Common.launchBrowser(url, InboxActivity.this);
//        		break;
//        	case Constants.DIALOG_THEME:
//        		if (mSettings.theme == R.style.Reddit_Light) {
//        			mSettings.setTheme(R.style.Reddit_Dark);
//        		} else {
//        			mSettings.setTheme(R.style.Reddit_Light);
//        		}
//        		InboxActivity.this.setTheme(mSettings.theme);
//        		InboxActivity.this.setContentView(R.layout.comments_list_content);
//        		registerForContextMenu(getListView());
//                InboxActivity.this.setListAdapter(mMessagesAdapter);
//                Common.updateListDrawables(InboxActivity.this, mSettings.theme);
//        		break;
//        	default:
//        		throw new IllegalArgumentException("Unexpected action value "+mAction);
//        	}
//        	
//        	return true;
//        }
//    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
    	AlertDialog.Builder builder;
    	LayoutInflater inflater;
    	View layout; // used for inflated views for AlertDialog.Builder.setView()
    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		dialog = new LoginDialog(this, mSettings, true) {
				@Override
				public void onLoginChosen(CharSequence user, CharSequence password) {
					dismissDialog(Constants.DIALOG_LOGIN);
		        	new LoginTask(user, password).execute();
				}
			};
    		break;
    		
    	case Constants.DIALOG_REPLY:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.compose_reply_dialog);
    		final EditText replyBody = (EditText) dialog.findViewById(R.id.body);
    		final Button replySaveButton = (Button) dialog.findViewById(R.id.reply_save_button);
    		final Button replyCancelButton = (Button) dialog.findViewById(R.id.reply_cancel_button);
    		replySaveButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				if(mReplyTargetName != null){
        				new MessageReplyTask(mReplyTargetName).execute(replyBody.getText());
        				dismissDialog(Constants.DIALOG_REPLY);
    				}
    				else{
    					Common.showErrorToast("Error replying. Please try again.", Toast.LENGTH_SHORT, InboxActivity.this);
    				}
    			}
    		});
    		replyCancelButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				dismissDialog(Constants.DIALOG_REPLY);
    			}
    		});
    		break;
    	case Constants.DIALOG_COMPOSE:
    		inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(this);
    		layout = inflater.inflate(R.layout.compose_dialog, null);
    		dialog = builder.setView(layout).create();
    		final Dialog composeDialog = dialog;
    		final EditText composeDestination = (EditText) layout.findViewById(R.id.compose_destination_input);
    		final EditText composeSubject = (EditText) layout.findViewById(R.id.compose_subject_input);
    		final EditText composeText = (EditText) layout.findViewById(R.id.compose_text_input);
    		final Button composeSendButton = (Button) layout.findViewById(R.id.compose_send_button);
    		final Button composeCancelButton = (Button) layout.findViewById(R.id.compose_cancel_button);
    		final EditText composeCaptcha = (EditText) layout.findViewById(R.id.compose_captcha_input);
    		composeSendButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
		    		ThingInfo hi = new ThingInfo();
		    		// reddit.com performs these sanity checks too.
		    		if ("".equals(composeDestination.getText().toString().trim())) {
		    			Toast.makeText(InboxActivity.this, "please enter a username", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		if ("".equals(composeSubject.getText().toString().trim())) {
		    			Toast.makeText(InboxActivity.this, "please enter a subject", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		if ("".equals(composeText.getText().toString().trim())) {
		    			Toast.makeText(InboxActivity.this, "you need to enter a message", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		if (composeCaptcha.getVisibility() == View.VISIBLE && "".equals(composeCaptcha.getText().toString().trim())) {
		    			Toast.makeText(InboxActivity.this, "", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		hi.setDest(composeDestination.getText().toString().trim());
		    		hi.setSubject(composeSubject.getText().toString().trim());
		    		new MessageComposeTask(composeDialog, hi, composeCaptcha.getText().toString().trim())
		    			.execute(composeText.getText().toString().trim());
		    		dismissDialog(Constants.DIALOG_COMPOSE);
				}
    		});
    		composeCancelButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dismissDialog(Constants.DIALOG_COMPOSE);
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
    	case Constants.DIALOG_REPLYING:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Sending reply...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;   		
    	case Constants.DIALOG_COMPOSING:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Composing message...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
    		
    	default:
    		throw new IllegalArgumentException("Unexpected dialog id "+id);
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
    		
    	case Constants.DIALOG_REPLY:
    		if (mVoteTargetThingInfo != null && mVoteTargetThingInfo.getReplyDraft() != null) {
    			EditText replyBodyView = (EditText) dialog.findViewById(R.id.body); 
    			replyBodyView.setText(mVoteTargetThingInfo.getReplyDraft());
    		}
    		break;
    		
    	case Constants.DIALOG_COMPOSE:
    		new CheckCaptchaRequiredTask(dialog).execute();
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putCharSequence(Constants.REPLY_TARGET_NAME_KEY, mReplyTargetName);
    }
    
    /**
     * Called to "thaw" re-animate the app from a previous onSaveInstanceState().
     * 
     * @see android.app.Activity#onRestoreInstanceState
     */
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        final int[] myDialogs = {
        	Constants.DIALOG_COMMENT_CLICK,
        	Constants.DIALOG_LOGGING_IN,
        	Constants.DIALOG_LOGIN,
        	Constants.DIALOG_MESSAGE_CLICK,
        	Constants.DIALOG_REPLY,
        	Constants.DIALOG_REPLYING,
        };
        for (int dialog : myDialogs) {
	        try {
	        	dismissDialog(dialog);
		    } catch (IllegalArgumentException e) {
		    	// Ignore.
		    }
        }
    }
}
