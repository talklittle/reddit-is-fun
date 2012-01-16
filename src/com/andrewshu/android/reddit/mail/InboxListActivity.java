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

package com.andrewshu.android.reddit.mail;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieSyncManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.comments.CommentsListActivity;
import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.ProgressInputStream;
import com.andrewshu.android.reddit.common.RedditIsFunHttpClientFactory;
import com.andrewshu.android.reddit.common.util.Assert;
import com.andrewshu.android.reddit.common.util.StringUtils;
import com.andrewshu.android.reddit.common.util.Util;
import com.andrewshu.android.reddit.login.LoginDialog;
import com.andrewshu.android.reddit.login.LoginTask;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.things.Listing;
import com.andrewshu.android.reddit.things.ListingData;
import com.andrewshu.android.reddit.things.ThingInfo;
import com.andrewshu.android.reddit.things.ThingListing;

/**
 * Main Activity class representing a Subreddit, i.e., a ThreadsList.
 * 
 * @author TalkLittle
 *
 */
public final class InboxListActivity extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "InboxListActivity";
	
    private final ObjectMapper mObjectMapper = Common.getObjectMapper();
    
    /** Custom list adapter that fits our threads data into the list. */
    private MessagesListAdapter mMessagesAdapter;
    private ArrayList<ThingInfo> mMessagesList;
    // Lock used when modifying the mMessagesAdapter
    private static final Object MESSAGE_ADAPTER_LOCK = new Object();
    
    
    private final HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();
    
    
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
    private View mNextPreviousView = null;
    
    private String mWhichInbox = "inbox";
    
    private String mAfter = null;
    private String mBefore = null;
    private int mCount = 0;
    private String mLastAfter = null;
    private String mLastBefore = null;
    private int mLastCount = 0;
    
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
        
		CookieSyncManager.createInstance(getApplicationContext());
		
        mSettings.loadRedditPreferences(this, mClient);
        setRequestedOrientation(mSettings.getRotation());
        setTheme(mSettings.getTheme());
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(R.layout.inbox_list_content);
        registerForContextMenu(getListView());
        
		if (mSettings.isLoggedIn()) {
			if (savedInstanceState != null) {
	        	mReplyTargetName = savedInstanceState.getString(Constants.REPLY_TARGET_NAME_KEY);
	        	mAfter = savedInstanceState.getString(Constants.AFTER_KEY);
		        mBefore = savedInstanceState.getString(Constants.BEFORE_KEY);
		        mCount = savedInstanceState.getInt(Constants.THREAD_COUNT_KEY);
		        mLastAfter = savedInstanceState.getString(Constants.LAST_AFTER_KEY);
		        mLastBefore = savedInstanceState.getString(Constants.LAST_BEFORE_KEY);
		        mLastCount = savedInstanceState.getInt(Constants.THREAD_LAST_COUNT_KEY);
			    mVoteTargetThingInfo = savedInstanceState.getParcelable(Constants.VOTE_TARGET_THING_INFO_KEY);
			    mWhichInbox = savedInstanceState.getString(Constants.WHICH_INBOX_KEY);

			    restoreLastNonConfigurationInstance();
	        	if (mMessagesList == null) {
	        		// Load previous view of threads
			        if (mLastAfter != null) {
			        	new DownloadMessagesTask(mWhichInbox, mLastAfter, null, mLastCount).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			        } else if (mLastBefore != null) {
			        	new DownloadMessagesTask(mWhichInbox, null, mLastBefore, mLastCount).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			        } else {
			        	new DownloadMessagesTask(mWhichInbox).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			        }
		        } else {
			    	// Orientation change. Use prior instance.
	        		resetUI(new MessagesListAdapter(this, mMessagesList));
	        	}
			} else {
				Bundle extras = getIntent().getExtras();
				if (extras != null) {
					if (extras.containsKey(Constants.WHICH_INBOX_KEY))
						mWhichInbox = extras.getString(Constants.WHICH_INBOX_KEY);
				}
				new DownloadMessagesTask(mWhichInbox).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			}
		} else {
			showDialog(Constants.DIALOG_LOGIN);
		}
		setTitle(String.format(getResources().getString(R.string.inbox_title), mSettings.getUsername()));
    }
    
    
	private void returnStatus(int status) {
		Intent i = new Intent();
		setResult(status, i);
		finish();
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
		CookieSyncManager.getInstance().startSync();
    	int previousTheme = mSettings.getTheme();
    	boolean previousLoggedIn = mSettings.isLoggedIn();
    	mSettings.loadRedditPreferences(this, mClient);
    	setRequestedOrientation(mSettings.getRotation());
    	if (mSettings.getTheme() != previousTheme) {
    		resetUI(mMessagesAdapter);
    	}
    	updateNextPreviousButtons();
    	if (mSettings.isLoggedIn() != previousLoggedIn) {
    		new DownloadMessagesTask(mWhichInbox).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    	}
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
		CookieSyncManager.getInstance().stopSync();
		mSettings.saveRedditPreferences(this);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        // Avoid having to re-download and re-parse the messages list
    	// when rotating or opening keyboard.
    	return mMessagesList;
    }
    
    @SuppressWarnings("unchecked")
	private void restoreLastNonConfigurationInstance() {
    	mMessagesList = (ArrayList<ThingInfo>) getLastNonConfigurationInstance();
    }
    
    public void refresh() {
    	new DownloadMessagesTask(mWhichInbox).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
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
            	fromInfoView.setTextColor(getResources().getColor(R.color.gray_50));
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
            if (Util.isLightTheme(mSettings.getTheme()))
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
            bodyView.setText(item.getSpannedBody());
    
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
			i.setData(Util.createCommentUri(mVoteTargetThingInfo, 0));
			i.putExtra(Constants.EXTRA_SUBREDDIT, mVoteTargetThingInfo.getSubreddit());
			i.putExtra(Constants.EXTRA_TITLE, mVoteTargetThingInfo.getTitle());
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
    	findViewById(R.id.loading_light).setVisibility(View.GONE);
    	findViewById(R.id.loading_dark).setVisibility(View.GONE);

    	if (mSettings.isAlwaysShowNextPrevious()) {
    		if (mNextPreviousView != null) {
    			getListView().removeFooterView(mNextPreviousView);
    			mNextPreviousView = null;
    		}
    	} else {
    		findViewById(R.id.next_previous_layout).setVisibility(View.GONE);
    		if (getListView().getFooterViewsCount() == 0) {
	            // If we are not using the persistent navbar, then show as ListView footer instead
		        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		        mNextPreviousView = inflater.inflate(R.layout.next_previous_list_item, null);
		        getListView().addFooterView(mNextPreviousView);
    		}
        }

        synchronized (MESSAGE_ADAPTER_LOCK) {
	    	if (messagesAdapter == null) {
	            // Reset the list to be empty.
	    		mMessagesList = new ArrayList<ThingInfo>();
	    		mMessagesAdapter = new MessagesListAdapter(this, mMessagesList);
	    	} else {
	    		mMessagesAdapter = messagesAdapter;
	    	}
		    setListAdapter(mMessagesAdapter);
		    mMessagesAdapter.mIsLoading = false;
		    mMessagesAdapter.notifyDataSetChanged();  // Just in case
		}
    	getListView().setDivider(null);
        Common.updateListDrawables(this, mSettings.getTheme());
        updateNextPreviousButtons();
    }
    
    private void enableLoadingScreen() {
    	if (Util.isLightTheme(mSettings.getTheme())) {
        	findViewById(R.id.loading_light).setVisibility(View.VISIBLE);
        	findViewById(R.id.loading_dark).setVisibility(View.GONE);
    	} else {
        	findViewById(R.id.loading_light).setVisibility(View.GONE);
        	findViewById(R.id.loading_dark).setVisibility(View.VISIBLE);
    	}
    	synchronized (MESSAGE_ADAPTER_LOCK) {
	    	if (mMessagesAdapter != null)
	    		mMessagesAdapter.mIsLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_START);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mMessagesAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);
    }

    private void updateNextPreviousButtons() {
    	Common.updateNextPreviousButtons(this, mNextPreviousView, mAfter, mBefore, mCount, mSettings,
    			downloadAfterOnClickListener, downloadBeforeOnClickListener);
    }

        
    
    /**
     * Task takes in a subreddit name string and thread id, downloads its data, parses
     * out the comments, and communicates them back to the UI as they are read.
     */
    private class DownloadMessagesTask extends AsyncTask<Integer, Long, Void>
    		implements PropertyChangeListener {
    	
    	private ArrayList<ThingInfo> _mThingInfos = new ArrayList<ThingInfo>();
    	private long _mContentLength;
    	
    	private String mAfter = null;
    	private String mBefore = null;
    	private int mCount = Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
    	private String mLastAfter = null;
    	private String mLastBefore = null;
    	private int mLastCount = 0;
    	
    	private String mWhichInbox = "inbox";
    	
    	public DownloadMessagesTask(String whichInbox) {
    		this.mWhichInbox = whichInbox;
    	}
    	
    	public DownloadMessagesTask(String whichInbox, String after, String before, int count) {
    		this(whichInbox);
    		
    		mAfter = after;
    		mBefore = before;
    		mCount = count;
    	}
    	
    	protected void saveState() {
			InboxListActivity.this.mLastAfter = mLastAfter;
			InboxListActivity.this.mLastBefore = mLastBefore;
			InboxListActivity.this.mLastCount = mLastCount;
			InboxListActivity.this.mAfter = mAfter;
			InboxListActivity.this.mBefore = mBefore;
			InboxListActivity.this.mCount = mCount;
    	}
    	
    	// XXX: maxComments is unused for now
    	public Void doInBackground(Integer... maxComments) {
    		HttpEntity entity = null;
    		boolean isAfter = false;
    		boolean isBefore = false;
    		InputStream in = null;
    		ProgressInputStream pin = null;
            
    		try {
            	String url;
            	StringBuilder sb = new StringBuilder(Constants.REDDIT_BASE_URL + "/message/")
            			.append(mWhichInbox)
            			.append("/.json?");
            	
            	// "before" always comes back null unless you provide correct "count"
        		if (mAfter != null) {
        			// count: 25, 50, ...
    				sb = sb.append("count=").append(mCount)
    					.append("&after=").append(mAfter).append("&");
    				isAfter = true;
        		}
        		else if (mBefore != null) {
        			// count: nothing, 26, 51, ...
        			sb = sb.append("count=").append(mCount + 1 - Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT)
        				.append("&before=").append(mBefore).append("&");
        			isBefore = true;
        		}
        		
        		url = sb.toString();
        		if (Constants.LOGGING) Log.d(TAG, "url=" + url);
        		
        		HttpGet request = new HttpGet(url);
            	HttpResponse response = mClient.execute(request);
            	
            	// Read the header to get Content-Length since entity.getContentLength() returns -1
            	Header contentLengthHeader = response.getFirstHeader("Content-Length");
            	if (contentLengthHeader != null) {
            		_mContentLength = Long.valueOf(contentLengthHeader.getValue());
            		if (Constants.LOGGING) Log.d(TAG, "Content length: "+_mContentLength);
            	} else {
            		_mContentLength = -1;
            		if (Constants.LOGGING) Log.d(TAG, "Content length: UNAVAILABLE");
            	}

            	entity = response.getEntity();
            	in = entity.getContent();
            	
            	// setup a special InputStream to report progress
            	pin = new ProgressInputStream(in, _mContentLength);
            	pin.addPropertyChangeListener(this);
            	
                parseInboxJSON(pin);
                
                // XXX: HACK: http://code.reddit.com/ticket/709
                // Marking messages as read is currently broken (even with mark=true)
                // For now, just send an extra request to the regular non-JSON inbox
                mClient.execute(new HttpGet(Constants.REDDIT_BASE_URL + "/message/" + mWhichInbox));

            	mLastCount = mCount;
            	if (isAfter)
            		mCount += Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
            	else if (isBefore)
            		mCount -= Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
            	
                saveState();

            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "failed", e);
        	} finally {
        		if (pin != null) {
        			try {
						pin.close();
					} catch (IOException e2) {
						if (Constants.LOGGING) Log.e(TAG, "pin.close()", e2);
					}
        		}
        		if (in != null) {
        			try {
						in.close();
					} catch (IOException e2) {
						if (Constants.LOGGING) Log.e(TAG, "in.close()", e2);
					}
        		}
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
        			}
        		}
            }
            return null;
	    }
    	
    	private void parseInboxJSON(InputStream in) throws IOException,
		    	JsonParseException, IllegalStateException {
		
    		String genericListingError = "Not an inbox listing";
    		try {
    			Listing listing = mObjectMapper.readValue(in, Listing.class);
    			Assert.assertEquals(Constants.JSON_LISTING, listing.getKind(), genericListingError);
    			
    			// Save the modhash, after, and before
    			ListingData data = listing.getData();
    			if (StringUtils.isEmpty(data.getModhash()))
    				mSettings.setModhash(null);
    			else
    				mSettings.setModhash(data.getModhash());
    			
    			mLastAfter = mAfter;
    			mLastBefore = mBefore;
    			mAfter = data.getAfter();
    			mBefore = data.getBefore();
    			
    			// Go through the children and get the ThingInfos
    			for (ThingListing tiContainer : data.getChildren()) {
   					ThingInfo ti = tiContainer.getData();
   					// HTML to Spanned
   					String unescapedHtmlBody = Html.fromHtml(ti.getBody_html()).toString();
   					Spanned body = Html.fromHtml(Util.convertHtmlTags(unescapedHtmlBody));
   					// remove last 2 newline characters
   					if (body.length() > 2)
   						ti.setSpannedBody(body.subSequence(0, body.length()-2));
   					else
   						ti.setSpannedBody("");
   					_mThingInfos.add(ti);
    			}
    		} catch (Exception ex) {
    			if (Constants.LOGGING) Log.e(TAG, "parseInboxJSON", ex);
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
    		if (_mContentLength == -1)
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);
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
    		
    		if (_mContentLength == -1)
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_OFF);
    		else
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);
    		
			disableLoadingScreen();
			Common.cancelMailNotification(InboxListActivity.this.getApplicationContext());
    	}
		
    	@Override
    	public void onProgressUpdate(Long... progress) {
    		if (_mContentLength != -1)
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * (Window.PROGRESS_END-1) / (int) _mContentLength);
    	}
    	
    	public void propertyChange(PropertyChangeEvent event) {
    		publishProgress((Long) event.getNewValue());
    	}
    }
    
    
    private class MyLoginTask extends LoginTask {
    	public MyLoginTask(String username, String password) {
    		super(username, password, mSettings, mClient, getApplicationContext());
    	}
    	
    	@Override
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	@Override
    	protected void onPostExecute(Boolean success) {
    		removeDialog(Constants.DIALOG_LOGGING_IN);
    		if (success) {
    			Toast.makeText(InboxListActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
	    		// Refresh the threads list
    			new DownloadMessagesTask(mWhichInbox).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		} else {
            	Common.showErrorToast(mUserError, Toast.LENGTH_LONG, InboxListActivity.this);
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
        	
        	if (!mSettings.isLoggedIn()) {
        		_mUserError = "You must be logged in to read the message.";
        		return false;
        	}
        	
        	// Update the modhash if necessary
        	if (mSettings.getModhash() == null) {
        		String modhash = Common.doUpdateModhash(mClient);
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
    			nvps.add(new BasicNameValuePair("uh", mSettings.getModhash().toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost(Constants.REDDIT_BASE_URL + "/api/read_message");
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
            	if (StringUtils.isEmpty(line)) {
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
        		if (Constants.LOGGING) Log.e(TAG, "ReadMessageTask", e);
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
        			}
        		}
        	}
        	return false;
        }
    	
    	@Override
    	public void onPreExecute() {
    		if (!mSettings.isLoggedIn()) {
        		Common.showErrorToast("You must be logged in to read message.", Toast.LENGTH_LONG, InboxListActivity.this);
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
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxListActivity.this);
    		}
    	}
    }
    
    
    private class MessageReplyTask extends AsyncTask<String, Void, Boolean> {
    	private String _mParentThingId;
    	String _mUserError = "Error submitting reply. Please try again.";
    	
    	MessageReplyTask(String parentThingId) {
    		_mParentThingId = parentThingId;
    	}
    	
    	@Override
        public Boolean doInBackground(String... text) {
        	HttpEntity entity = null;
        	
        	if (!mSettings.isLoggedIn()) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, InboxListActivity.this);
        		_mUserError = "Not logged in";
        		return false;
        	}
        	// Update the modhash if necessary
        	if (mSettings.getModhash() == null) {
        		String modhash = Common.doUpdateModhash(mClient);
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
    			nvps.add(new BasicNameValuePair("thing_id", _mParentThingId));
    			nvps.add(new BasicNameValuePair("text", text[0]));
    			nvps.add(new BasicNameValuePair("uh", mSettings.getModhash()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost(Constants.REDDIT_BASE_URL + "/api/comment");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	entity = response.getEntity();

            	// Don't need return value id since reply isn't posted to inbox
            	Common.checkIDResponse(response, entity);
            	
            	return true;
            	
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "MessageReplyTask", e);
        		_mUserError = e.getMessage();
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (IOException e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
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
    		removeDialog(Constants.DIALOG_REPLYING);
    		if (success) {
    			Toast.makeText(InboxListActivity.this, "Reply sent.", Toast.LENGTH_SHORT).show();
    			// TODO: add the reply beneath the original, OR redirect to sent messages page
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxListActivity.this);
    		}
    	}
    }
    
    
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
				public void onLoginChosen(String user, String password) {
					removeDialog(Constants.DIALOG_LOGIN);
		        	new MyLoginTask(user, password).execute();
				}
			};
    		break;
    		
    	case Constants.DIALOG_REPLY:
    		dialog = new Dialog(this, mSettings.getDialogTheme());
    		dialog.setContentView(R.layout.compose_reply_dialog);
    		final EditText replyBody = (EditText) dialog.findViewById(R.id.body);
    		final Button replySaveButton = (Button) dialog.findViewById(R.id.reply_save_button);
    		final Button replyCancelButton = (Button) dialog.findViewById(R.id.reply_cancel_button);
    		replySaveButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				if(mReplyTargetName != null){
        				new MessageReplyTask(mReplyTargetName).execute(replyBody.getText().toString());
        				removeDialog(Constants.DIALOG_REPLY);
    				}
    				else{
    					Common.showErrorToast("Error replying. Please try again.", Toast.LENGTH_SHORT, InboxListActivity.this);
    				}
    			}
    		});
    		replyCancelButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				removeDialog(Constants.DIALOG_REPLY);
    			}
    		});
    		break;
   		// "Please wait"
    	case Constants.DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		pdialog.setMessage("Logging in...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case Constants.DIALOG_REPLYING:
    		pdialog = new ProgressDialog(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		pdialog.setMessage("Sending reply...");
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
    	super.onPrepareDialog(id, dialog);
    	    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		if (mSettings.getUsername() != null) {
	    		final TextView loginUsernameInput = (TextView) dialog.findViewById(R.id.login_username_input);
	    		loginUsernameInput.setText(mSettings.getUsername());
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
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
	private final OnClickListener downloadAfterOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			new DownloadMessagesTask(mWhichInbox, mAfter, null, mCount).execute();
		}
	};
	private final OnClickListener downloadBeforeOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			new DownloadMessagesTask(mWhichInbox, null, mBefore, mCount).execute();
		}
	};
	
    
    @Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putString(Constants.REPLY_TARGET_NAME_KEY, mReplyTargetName);
    	state.putString(Constants.AFTER_KEY, mAfter);
    	state.putString(Constants.BEFORE_KEY, mBefore);
    	state.putInt(Constants.THREAD_COUNT_KEY, mCount);
    	state.putString(Constants.LAST_AFTER_KEY, mLastAfter);
    	state.putString(Constants.LAST_BEFORE_KEY, mLastBefore);
    	state.putInt(Constants.THREAD_LAST_COUNT_KEY, mLastCount);
    	state.putParcelable(Constants.VOTE_TARGET_THING_INFO_KEY, mVoteTargetThingInfo);
    	state.putString(Constants.WHICH_INBOX_KEY, mWhichInbox);
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
	        	removeDialog(dialog);
		    } catch (IllegalArgumentException e) {
		    	// Ignore.
		    }
        }
    }
}
