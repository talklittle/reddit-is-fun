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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.webkit.CookieSyncManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.andrewshu.android.reddit.ThreadsListActivity.ThumbnailOnClickListenerFactory;

/**
 * Activity to view user submissions and comments.
 * Also check their link and comment karma.
 * 
 * @author TalkLittle
 *
 */
public final class ProfileActivity extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "ProfileActivity";
	
    private final ObjectMapper mObjectMapper = Common.getObjectMapper();
    private final Markdown markdown = new Markdown();
    private final CommentManager mCommentManager = new CommentManager();
    private BitmapManager mBitmapManager = new BitmapManager();
    
    /** Custom list adapter that fits our threads data into the list. */
    private ThingsListAdapter mThingsAdapter;
    private ArrayList<ThingInfo> mThingsList;
    // Lock used when modifying the mMessagesAdapter
    private static final Object MESSAGE_ADAPTER_LOCK = new Object();
    
    
    private final DefaultHttpClient mClient = Common.getGzipHttpClient();
    
    
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
    // UI State
    private View mVoteTargetView = null;
    private ThingInfo mVoteTargetThingInfo = null;
    private URLSpan[] mVoteTargetSpans = null;
    // TODO: String mVoteTargetId so when you rotate, you can find the TargetThingInfo again
    private DownloadThingsTask mCurrentDownloadThingsTask = null;
    private final Object mCurrentDownloadThingsTaskLock = new Object();
    
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
        
		CookieSyncManager.createInstance(getApplicationContext());
		
        Common.loadRedditPreferences(this, mSettings, mClient);
        setRequestedOrientation(mSettings.rotation);
        setTheme(mSettings.theme);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(R.layout.profile_list_content);
        
		if (mSettings.isLoggedIn()) {
			if (savedInstanceState != null) {
	        	mThingsList = (ArrayList<ThingInfo>) getLastNonConfigurationInstance();
	        	if (mThingsList == null) {
	        		new DownloadThingsTask(mSettings.username).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
	        	} else {
			    	// Orientation change. Use prior instance.
	        		resetUI(new ThingsListAdapter(this, mThingsList));
	        	}
			} else {
				new DownloadThingsTask(mSettings.username).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			}
		} else {
			showDialog(Constants.DIALOG_LOGIN);
		}
    }
    
    /**
     * Hack to explicitly set background color whenever changing ListView.
     */
    public void setContentView(int layoutResID) {
    	super.setContentView(layoutResID);
    	// HACK: set background color directly for android 2.0
        if (Util.isLightTheme(mSettings.theme))
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
		CookieSyncManager.getInstance().startSync();
    	int previousTheme = mSettings.theme;
    	boolean previousLoggedIn = mSettings.isLoggedIn();
    	Common.loadRedditPreferences(this, mSettings, mClient);
    	setRequestedOrientation(mSettings.rotation);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.theme);
    		setContentView(R.layout.threads_list_content);
    		setListAdapter(mThingsAdapter);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
    	if (mSettings.isLoggedIn() != previousLoggedIn) {
    		new DownloadThingsTask(mSettings.username).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    	}
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
		CookieSyncManager.getInstance().stopSync();
		Common.saveRedditPreferences(this, mSettings);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        // Avoid having to re-download and re-parse the messages list
    	// when rotating or opening keyboard.
    	return mThingsList;
    }
    
    
    
    /**
     * Return the ThingInfo based on linear search over the names
     */
    private ThingInfo findThingInfoByName(String name) {
    	if (name == null)
    		return null;
    	synchronized(MESSAGE_ADAPTER_LOCK) {
    		for (int i = 0; i < mThingsAdapter.getCount(); i++) {
    			if (mThingsAdapter.getItem(i).getName().equals(name))
    				return mThingsAdapter.getItem(i);
    		}
    	}
    	return null;
    }
    
    
    private final class ThingsListAdapter extends ArrayAdapter<ThingInfo> {
    	static final int THREAD_ITEM_VIEW_TYPE = 0;
    	static final int COMMENT_ITEM_VIEW_TYPE = 1;
    	
    	private static final int VIEW_TYPE_COUNT = 2;
    	
    	public boolean mIsLoading = true;
    	
    	private LayoutInflater mInflater;
        
        @Override
        public int getItemViewType(int position) {
        	ThingInfo item = getItem(position);
        	if (item.getName().startsWith(Constants.THREAD_KIND)) {
        		return THREAD_ITEM_VIEW_TYPE;
        	}
        	if (item.getName().startsWith(Constants.COMMENT_KIND)) {
        		return COMMENT_ITEM_VIEW_TYPE;
        	}
            return COMMENT_ITEM_VIEW_TYPE;
        }
        
        @Override
        public int getViewTypeCount() {
        	return VIEW_TYPE_COUNT;
        }
        
    	public boolean isEmpty() {
    		if (mIsLoading)
    			return false;
    		return super.isEmpty();
    	}
    	
        public ThingsListAdapter(Context context, List<ThingInfo> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = null;
            
            ThingInfo item = this.getItem(position);
            
            if (getItemViewType(position) == THREAD_ITEM_VIEW_TYPE) {
	            // Here view may be passed in for re-use, or we make a new one.
	            if (convertView == null) {
	                view = mInflater.inflate(R.layout.threads_list_item, null);
	            } else {
	                view = convertView;
	            }
	            
	            ThreadsListActivity.fillThreadsListItemView(view, item, ProfileActivity.this, mSettings,
	            		mBitmapManager, true, thumbnailOnClickListenerFactory);
            }
            
            else if (getItemViewType(position) == COMMENT_ITEM_VIEW_TYPE) {
	            // Here view may be passed in for re-use, or we make a new one.
	            if (convertView == null) {
	                view = mInflater.inflate(R.layout.comments_list_item, null);
	            } else {
	                view = convertView;
	            }
	            
            	CommentsListActivity.fillCommentsListItemView(view, item, ProfileActivity.this, mSettings, mCommentManager);
            }
            
	        return view;
        }
    } // End of MessagesListAdapter

    
    /**
     * Called when user clicks an item in the list. Mark message read.
     * If item was already focused, open a dialog.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ThingInfo item = mThingsAdapter.getItem(position);
        
        // Mark the message/comment as selected
        mVoteTargetThingInfo = item;
        mVoteTargetView = v;
        
        openContextMenu(v);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    	int rowId = (int) info.id;
    	ThingInfo item = mThingsAdapter.getItem(rowId);
    	
        // Mark the message/comment as selected
        mVoteTargetThingInfo = item;
        mVoteTargetView = v;
        
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
			i.setData(Util.createCommentUri(mVoteTargetThingInfo));
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
    void resetUI(ThingsListAdapter messagesAdapter) {
    	setContentView(R.layout.inbox_list_content);
    	synchronized (MESSAGE_ADAPTER_LOCK) {
	    	if (messagesAdapter == null) {
	            // Reset the list to be empty.
	    		mThingsList = new ArrayList<ThingInfo>();
	    		mThingsAdapter = new ThingsListAdapter(this, mThingsList);
	    	} else {
	    		mThingsAdapter = messagesAdapter;
	    	}
		    setListAdapter(mThingsAdapter);
		    mThingsAdapter.mIsLoading = false;
		    mThingsAdapter.notifyDataSetChanged();  // Just in case
		}
    	getListView().setDivider(null);
        Common.updateListDrawables(this, mSettings.theme);
    }
    
    private void enableLoadingScreen() {
    	if (Util.isLightTheme(mSettings.theme)) {
    		setContentView(R.layout.loading_light);
    	} else {
    		setContentView(R.layout.loading_dark);
    	}
    	synchronized (MESSAGE_ADAPTER_LOCK) {
	    	if (mThingsAdapter != null)
	    		mThingsAdapter.mIsLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mThingsAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
    }

        
    
    private class DownloadThingsTask extends AsyncTask<Integer, Long, Void>
    		implements PropertyChangeListener {
    	
    	private ArrayList<ThingInfo> _mThingInfos = new ArrayList<ThingInfo>();
    	private long _mContentLength;
    	
    	private String _mUsername;
    	
    	public DownloadThingsTask(String username) {
    		_mUsername = username;
    	}
    	
    	// XXX: maxComments is unused for now
    	public Void doInBackground(Integer... maxComments) {
    		HttpEntity entity = null;
            try {
            	HttpGet request = new HttpGet("http://api.reddit.com/user/"+_mUsername);
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
            	
                parseThingsJSON(pin);
                
                pin.close();
                in.close();
                
                // XXX: HACK: http://code.reddit.com/ticket/709
                // Marking messages as read is currently broken (even with mark=
                // For now, just send an extra request to the regular non-JSON i
                mClient.execute(new HttpGet("http://www.reddit.com/message/inbox"));

            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "failed", e);
        	} finally {
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
    	
    	private void parseThingsJSON(InputStream in) throws IOException,
		    	JsonParseException, IllegalStateException {
		
    		String genericListingError = "Not an inbox listing";
    		try {
    			Listing listing = mObjectMapper.readValue(in, Listing.class);
    			
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
    				if (Constants.COMMENT_KIND.equals(tiContainer.getKind())) {
	   					ThingInfo ti = tiContainer.getData();
	   					// HTML to Spanned
	   					String unescapedHtmlBody = Html.fromHtml(ti.getBody_html()).toString();
	   					Spanned body = Html.fromHtml(Util.convertHtmlTags(unescapedHtmlBody));
	   					// remove last 2 newline characters
	   					if (body.length() > 2)
	   						ti.setSpannedBody(body.subSequence(0, body.length()-2));
	   					else
	   						ti.setSpannedBody(Constants.EMPTY_STRING);
	   					_mThingInfos.add(ti);
    				} else if (Constants.THREAD_KIND.equals(tiContainer.getKind())) {
    					ThingInfo ti = tiContainer.getData();
    					
    					String unescapedHtmlTitle = Html.fromHtml(ti.getTitle()).toString();
    					ti.setTitle(unescapedHtmlTitle);
    					
    					_mThingInfos.add(tiContainer.getData());
    				}
    			}
    		} catch (Exception ex) {
    			if (Constants.LOGGING) Log.e(TAG, "parseInboxJSON", ex);
    		}
    	}

		@Override
    	public void onPreExecute() {
			synchronized (mCurrentDownloadThingsTaskLock) {
				if (mCurrentDownloadThingsTask != null)
					mCurrentDownloadThingsTask.cancel(true);
				mCurrentDownloadThingsTask = this;
			}
    		resetUI(null);
    		enableLoadingScreen();
    	}
    	
		@Override
    	public void onPostExecute(Void v) {
			synchronized (mCurrentDownloadThingsTaskLock) {
				mCurrentDownloadThingsTask = null;
			}
    		synchronized(MESSAGE_ADAPTER_LOCK) {
    			for (ThingInfo mi : _mThingInfos)
    				mThingsAdapter.add(mi);
    		}
			disableLoadingScreen();
			Common.cancelMailNotification(ProfileActivity.this.getApplicationContext());
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
    	private String mUsername, mPassword, mUserError;
    	
    	LoginTask(String username, String password) {
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
    			Toast.makeText(ProfileActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
	    		// Refresh the threads list
    			new DownloadThingsTask(mUsername).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		} else {
            	Common.showErrorToast(mUserError, Toast.LENGTH_LONG, ProfileActivity.this);
    			returnStatus(Constants.RESULT_LOGIN_REQUIRED);
    		}
    	}
    }
    
    
    private class MyMessageComposeTask extends MessageComposeTask {
    	MyMessageComposeTask(Dialog dialog, ThingInfo targetThingInfo, String captcha) {
			super(dialog, targetThingInfo, captcha, mCaptchaIden, mSettings, mClient, getApplicationContext());
		}

		@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_COMPOSING);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_COMPOSING);
    		if (success) {
    			Toast.makeText(ProfileActivity.this, "Message sent.", Toast.LENGTH_SHORT).show();
    			// TODO: add the reply beneath the original, OR redirect to sent messages page
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, ProfileActivity.this);
    			new MyCaptchaDownloadTask(_mDialog).execute();
    		}
    	}
    }
    
    private class MyCaptchaCheckRequiredTask extends CaptchaCheckRequiredTask {
    	
    	Dialog _mDialog;
    	
		public MyCaptchaCheckRequiredTask(Dialog dialog) {
			super("http://www.reddit.com/message/compose/", mClient);
			_mDialog = dialog;
		}
		
		@Override
		protected void saveState() {
			ProfileActivity.this.mCaptchaIden = _mCaptchaIden;
			ProfileActivity.this.mCaptchaUrl = _mCaptchaUrl;
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
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, ProfileActivity.this);
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
    	}
    	
		@Override
		public void onPostExecute(Drawable captcha) {
			if (captcha == null) {
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, ProfileActivity.this);
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
			new DownloadThingsTask(mSettings.username).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
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
    	case Constants.DIALOG_LOGIN:
    		dialog = new LoginDialog(this, mSettings, true) {
				@Override
				public void onLoginChosen(String user, String password) {
					dismissDialog(Constants.DIALOG_LOGIN);
		        	new LoginTask(user, password).execute();
				}
			};
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
				public void onClick(View v) {
		    		ThingInfo hi = new ThingInfo();
		    		// reddit.com performs these sanity checks too.
		    		if ("".equals(composeDestination.getText().toString().trim())) {
		    			Toast.makeText(ProfileActivity.this, "please enter a username", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		if ("".equals(composeSubject.getText().toString().trim())) {
		    			Toast.makeText(ProfileActivity.this, "please enter a subject", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		if ("".equals(composeText.getText().toString().trim())) {
		    			Toast.makeText(ProfileActivity.this, "you need to enter a message", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		if (composeCaptcha.getVisibility() == View.VISIBLE && "".equals(composeCaptcha.getText().toString().trim())) {
		    			Toast.makeText(ProfileActivity.this, "", Toast.LENGTH_LONG).show();
		    			return;
		    		}
		    		hi.setDest(composeDestination.getText().toString().trim());
		    		hi.setSubject(composeSubject.getText().toString().trim());
		    		new MyMessageComposeTask(composeDialog, hi, composeCaptcha.getText().toString().trim())
		    			.execute(composeText.getText().toString().trim());
		    		dismissDialog(Constants.DIALOG_COMPOSE);
				}
    		});
    		composeCancelButton.setOnClickListener(new OnClickListener() {
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
    		new MyCaptchaCheckRequiredTask(dialog).execute();
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
	private final ThumbnailOnClickListenerFactory thumbnailOnClickListenerFactory
			= new ThumbnailOnClickListenerFactory() {
		public OnClickListener getThumbnailOnClickListener(String jumpToId, String url, String threadUrl, Context context) {
//			final String fJumpToId = jumpToId;
			final String fUrl = url;
			final String fThreadUrl = threadUrl;
			return new OnClickListener() {
				public void onClick(View v) {
//					ProfileActivity.this.mJumpToThreadId = fJumpToId;
					Common.launchBrowser(ProfileActivity.this, fUrl, fThreadUrl,
							false, false, ProfileActivity.this.mSettings.useExternalBrowser);
				}
			};
		}
	};

	@Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
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
