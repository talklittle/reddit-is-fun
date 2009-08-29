package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.webkit.WebView;
import android.webkit.WebSettings.TextSize;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
	
    private final JsonFactory jsonFactory = new JsonFactory(); 
    
    /** Custom list adapter that fits our threads data into the list. */
    private MessagesListAdapter mMessagesAdapter;
    
    private final DefaultHttpClient mClient = Common.createGzipHttpClient();
    volatile private String mModhash;
   
    
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
    // UI State
    private View mVoteTargetView = null;
    private MessageInfo mVoteTargetMessageInfo = null;
    private URLSpan[] mVoteTargetSpans = null;
    
    private String mAfter = null;
    private String mBefore = null;
    
    // ProgressDialogs with percentage bars
    private AutoResetProgressDialog mLoadingCommentsProgress;
    private int mNumVisibleMessages;
    
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
        setTheme(mSettings.themeResId);
        
        setContentView(R.layout.comments_list_content);
        registerForContextMenu(getListView());
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().
        
        new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	int previousTheme = mSettings.theme;
    	boolean previousLoggedIn = mSettings.loggedIn;
    	Common.loadRedditPreferences(this, mSettings, mClient);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.themeResId);
    		setContentView(R.layout.threads_list_content);
    		registerForContextMenu(getListView());
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
    
    
    private final class MessagesListAdapter extends ArrayAdapter<MessageInfo> {
    	private LayoutInflater mInflater;
        
        public MessagesListAdapter(Context context, List<MessageInfo> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            
            MessageInfo item = this.getItem(position);
            
            try {
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
	            
	            bodyView.setText(item.getBody());
	            
            } catch (NullPointerException e) {
            	// Probably means that the List is still being built, and OP probably got put in wrong position
            	if (convertView == null) {
            		if (position == 0)
            			view = mInflater.inflate(R.layout.threads_list_item, null);
            		else
            			view = mInflater.inflate(R.layout.comments_list_item, null);
	            } else {
	                view = convertView;
	            }
            }
            return view;
        }
    } // End of CommentsListAdapter

    
    /**
     * Called when user clicks an item in the list. Starts an activity to
     * open the url for that item.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        MessageInfo item = mMessagesAdapter.getItem(position);
        
        // Mark the OP post/regular comment as selected
        mVoteTargetMessageInfo = item;
        mVoteTargetView = v;
        String thingFullname;
    	thingFullname = mVoteTargetMessageInfo.getName();
		
        showDialog(Constants.DIALOG_THING_CLICK);
    }

    /**
     * Resets the output UI list contents, retains session state.
     */
    public void resetUI() {
        // Reset the list to be empty.
        List<MessageInfo> items = new ArrayList<MessageInfo>();
        mMessagesAdapter = new MessagesListAdapter(this, items);
        setListAdapter(mMessagesAdapter);
        Common.updateListDrawables(this, mSettings.theme);
    }

        
    
    /**
     * Task takes in a subreddit name string and thread id, downloads its data, parses
     * out the comments, and communicates them back to the UI as they are read.
     */
    private class DownloadMessagesTask extends AsyncTask<Integer, Integer, Void> {
    	
    	// XXX: maxComments is unused for now
    	public Void doInBackground(Integer... maxComments) {
            try {
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(mSettings.subreddit.toString().trim())
            		.append("/comments/")
            		.append(mSettings.threadId)
            		.append("/.json").toString());
            	HttpResponse response = mClient.execute(request);
            	
            	InputStream in = response.getEntity().getContent();
                
                parseInboxJSON(in);
                
            } catch (Exception e) {
                Log.e(TAG, "failed:" + e.getMessage());
            }
            return null;
	    }
    	
    	private void parseInboxJSON(InputStream in) throws IOException,
		    	JsonParseException, IllegalStateException {
		
			JsonParser jp = jsonFactory.createJsonParser(in);
			
			if (jp.nextToken() == JsonToken.VALUE_NULL)
				return;
			
			// --- Validate initial stuff, skip to the JSON List of threads ---
			String genericListingError = "Not a subreddit listing";
		//	if (JsonToken.START_OBJECT != jp.nextToken()) // starts with "{"
		//		throw new IllegalStateException(genericListingError);
			jp.nextToken();
			if (!Constants.JSON_KIND.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			if (!Constants.JSON_LISTING.equals(jp.getText()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			if (!Constants.JSON_DATA.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			if (JsonToken.START_OBJECT != jp.getCurrentToken())
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			// Save the "after"
			if (!Constants.JSON_AFTER.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			mAfter = jp.getText();
			if (Constants.NULL_STRING.equals(mAfter))
				mAfter = null;
			jp.nextToken();
			if (!Constants.JSON_CHILDREN.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			if (jp.getCurrentToken() != JsonToken.START_ARRAY)
				throw new IllegalStateException(genericListingError);
			
			// --- Main parsing ---
//			int progressIndex = 0;
			while (jp.nextToken() != JsonToken.END_ARRAY) {
				if (jp.getCurrentToken() != JsonToken.START_OBJECT)
					throw new IllegalStateException("Unexpected non-JSON-object in the children array");
			
				// Process JSON representing one message
				MessageInfo mi = new MessageInfo();
				while (jp.nextToken() != JsonToken.END_OBJECT) {
					String fieldname = jp.getCurrentName();
					jp.nextToken(); // move to value, or START_OBJECT/START_ARRAY
				
					if (Constants.JSON_KIND.equals(fieldname)) {
						mi.put(Constants.JSON_KIND, jp.getText());
					} else if (Constants.JSON_DATA.equals(fieldname)) { // contains an object
						while (jp.nextToken() != JsonToken.END_OBJECT) {
							String namefield = jp.getCurrentName();
							jp.nextToken(); // move to value
							// Should validate each field but I'm lazy
							mi.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText().replaceAll("\r", "")));
						}
					} else {
						throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
					}
				}
				mMessagesAdapter.add(mi);
//				publishProgress(progressIndex++);
			}
			// Get the "before"
			jp.nextToken();
			if (!Constants.JSON_BEFORE.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			mBefore = jp.getText();
			if (Constants.NULL_STRING.equals(mBefore))
				mBefore = null;
		}

		@Override
    	public void onPreExecute() {
    		resetUI();
	    	showDialog(Constants.DIALOG_LOADING_INBOX);
    	}
    	
		@Override
    	public void onPostExecute(Void v) {
    		mMessagesAdapter.notifyDataSetChanged();
			dismissDialog(Constants.DIALOG_LOADING_INBOX);
    	}
    	
//		@Override
//    	public void onProgressUpdate(Integer... progress) {
//    		mLoadingCommentsProgress.setProgress(progress[0]);
//    	}
    }
    
    
    private class LoginTask extends AsyncTask<Void, Void, String> {
    	private CharSequence mUsername, mPassword, mUserError;
    	
    	LoginTask(CharSequence username, CharSequence password) {
    		mUsername = username;
    		mPassword = password;
    	}
    	
    	@Override
    	public String doInBackground(Void... v) {
    		return Common.doLogin(mUsername, mPassword, mClient);
        }
    	
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	protected void onPostExecute(String errorMessage) {
    		dismissDialog(Constants.DIALOG_LOGGING_IN);
    		if (errorMessage == null) {
    			List<Cookie> cookies = mClient.getCookieStore().getCookies();
            	for (Cookie c : cookies) {
            		if (c.getName().equals("reddit_session")) {
            			mSettings.setRedditSessionCookie(c);
            			break;
            		}
            	}
            	mSettings.setUsername(mUsername);
            	mSettings.setLoggedIn(true);
    			Toast.makeText(InboxActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
	    		// Refresh the threads list
    			new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		} else {
            	mSettings.setLoggedIn(false);
    			Common.showErrorToast(mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    		}
    	}
    }
    
    
    
    
    
    
    
    private class MessageReplyTask extends AsyncTask<CharSequence, Void, MessageInfo> {
    	private CharSequence _mParentThingId;
    	MessageInfo _mTargetMessageInfo;
    	String _mUserError = "Error submitting reply. Please try again.";
    	
    	MessageReplyTask(CharSequence parentThingId, MessageInfo targetMessageInfo) {
    		_mParentThingId = parentThingId;
    		_mTargetMessageInfo = targetMessageInfo;
    	}
    	
    	@Override
        public MessageInfo doInBackground(CharSequence... text) {
        	MessageInfo newlyCreatedComment = null;
        	String userError = "Error replying. Please try again.";
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, InboxActivity.this);
        		_mUserError = "Not logged in";
        		return null;
        	}
        	// Update the modhash if necessary
        	if (mModhash == null) {
        		if ((mModhash = Common.doUpdateModhash(mClient)) == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient);
        			Log.e(TAG, "Reply failed because doUpdateModhash() failed");
        			return null;
        		}
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("thing_id", _mParentThingId.toString()));
    			nvps.add(new BasicNameValuePair("text", text[0].toString()));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mModhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/comment");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        Log.d(TAG, nvps.toString());
    	        
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
            		mModhash = null;
            		throw new Exception("User required. Huh?");
            	}
            	
            	Log.d(TAG, line);

//            	// DEBUG
//            	int c;
//            	boolean done = false;
//            	StringBuilder sb = new StringBuilder();
//            	for (int k = 0; k < line.length(); k += 80) {
//            		for (int i = 0; i < 80; i++) {
//            			if (k + i >= line.length()) {
//            				done = true;
//            				break;
//            			}
//            			c = line.charAt(k + i);
//            			sb.append((char) c);
//            		}
//            		Log.d(TAG, "doReply response content: " + sb.toString());
//            		sb = new StringBuilder();
//            		if (done)
//            			break;
//            	}
//    	        	

            	String newId, newFullname;
            	Matcher idMatcher = Constants.NEW_ID_PATTERN.matcher(line);
            	if (idMatcher.find()) {
            		newFullname = idMatcher.group(1);
            		newId = idMatcher.group(3);
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
                	throw new Exception("No id returned by reply POST.");
            	}
            	
            	entity.consumeContent();
            	
            	// Getting here means success. Create a new MessageInfo.
            	newlyCreatedComment = new MessageInfo(
            			mSettings.username.toString(),     /* author */
            			text[0].toString(),          /* body */
            			null,                     /* body_html */
            			null,                     /* created */
            			String.valueOf(System.currentTimeMillis()), /* created_utc */
            			"0",                      /* downs */
            			newId,                    /* id */
            			Constants.TRUE_STRING,              /* likes */
            			null,                     /* link_id */
            			newFullname,              /* name */
            			_mParentThingId.toString(), /* parent_id */
            			null,                     /* sr_id */
            			"1"                       /* ups */
            			);
            	newlyCreatedComment.setListOrder(_mTargetMessageInfo.getListOrder()+1);
            	if (_mTargetMessageInfo.getListOrder() == 0)
            		newlyCreatedComment.setIndent(0);
            	else
            		newlyCreatedComment.setIndent(_mTargetMessageInfo.getIndent()+1);
            	
            	return newlyCreatedComment;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (IOException e2) {
        				Log.e(TAG, e.getMessage());
        			}
        		}
                Log.e(TAG, e.getMessage());
        	}
        	return null;
        }
    	
    	
    	@Override
    	public void onPostExecute(MessageInfo newlyCreatedComment) {
    		if (newlyCreatedComment == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, InboxActivity.this);
    		} else {
        		// Success. OK to clear the reply draft.
        		_mTargetMessageInfo.setReplyDraft("");
    		}
    	}

    }
    
        
    
    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(0, Constants.DIALOG_OP, 0, "OP")
        	.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_OP));
        
        // Login and Logout need to use the same ID for menu entry so they can be swapped
        if (mSettings.loggedIn) {
        	menu.add(0, Constants.DIALOG_LOGIN, 1, "Logout: " + mSettings.username)
       			.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_LOGOUT));
        } else {
        	menu.add(0, Constants.DIALOG_LOGIN, 1, "Login")
       			.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_LOGIN));
        }
        
        menu.add(0, Constants.DIALOG_REFRESH, 2, "Refresh")
        	.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_REFRESH));
        
        menu.add(0, Constants.DIALOG_REPLY, 3, "Reply to thread")
    		.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_REPLY));
        
        if (mSettings.theme == Constants.THEME_LIGHT) {
        	menu.add(0, Constants.DIALOG_THEME, 4, "Dark")
//        		.setIcon(R.drawable.dark_circle_menu_icon)
        		.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_THEME));
        } else {
        	menu.add(0, Constants.DIALOG_THEME, 4, "Light")
//	    		.setIcon(R.drawable.light_circle_menu_icon)
	    		.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_THEME));
        }
        
        menu.add(0, Constants.DIALOG_OPEN_BROWSER, 5, "Open in browser")
    		.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_OPEN_BROWSER));
        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	super.onPrepareOptionsMenu(menu);
    	
    	// Login/Logout
    	if (mSettings.loggedIn) {
	        menu.findItem(Constants.DIALOG_LOGIN).setTitle("Logout: " + mSettings.username)
	        	.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_LOGOUT));
    	} else {
            menu.findItem(Constants.DIALOG_LOGIN).setTitle("Login")
            	.setOnMenuItemClickListener(new CommentsListMenu(Constants.DIALOG_LOGIN));
    	}
    	
    	// Theme: Light/Dark
    	if (mSettings.theme == Constants.THEME_LIGHT) {
    		menu.findItem(Constants.DIALOG_THEME).setTitle("Dark");
//    			.setIcon(R.drawable.dark_circle_menu_icon);
    	} else {
    		menu.findItem(Constants.DIALOG_THEME).setTitle("Light");
//    			.setIcon(R.drawable.light_circle_menu_icon);
    	}
        
        return true;
    }


    private class CommentsListMenu implements MenuItem.OnMenuItemClickListener {
        private int mAction;

        CommentsListMenu(int action) {
            mAction = action;
        }

        public boolean onMenuItemClick(MenuItem item) {
        	switch (mAction) {
        	case Constants.DIALOG_OP:
        		mVoteTargetMessageInfo = mMessagesAdapter.getItem(0);
        		showDialog(Constants.DIALOG_THING_CLICK);
        		break;
        	case Constants.DIALOG_REPLY:
        		// From the menu, only used for the OP, which is a thread.
            	mVoteTargetMessageInfo = mMessagesAdapter.getItem(0);
                showDialog(mAction);
                break;
        	case Constants.DIALOG_LOGIN:
        		showDialog(mAction);
        		break;
        	case Constants.DIALOG_LOGOUT:
        		Common.doLogout(mSettings, mClient);
        		Toast.makeText(InboxActivity.this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
        		break;
        	case Constants.DIALOG_REFRESH:
        		new DownloadMessagesTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
        		break;
        	case Constants.DIALOG_OPEN_BROWSER:
        		String url = new StringBuilder("http://www.reddit.com/r/")
        			.append(mSettings.subreddit).append("/comments/").append(mSettings.threadId).toString();
        		Common.launchBrowser(url, InboxActivity.this);
        		break;
        	case Constants.DIALOG_THEME:
        		if (mSettings.theme == Constants.THEME_LIGHT) {
        			mSettings.setTheme(Constants.THEME_DARK);
        			mSettings.setThemeResId(R.style.Reddit_Dark);
        		} else {
        			mSettings.setTheme(Constants.THEME_LIGHT);
        			mSettings.setThemeResId(R.style.Reddit_Light);
        		}
        		InboxActivity.this.setTheme(mSettings.themeResId);
        		InboxActivity.this.setContentView(R.layout.comments_list_content);
        		registerForContextMenu(getListView());
                InboxActivity.this.setListAdapter(mMessagesAdapter);
                Common.updateListDrawables(InboxActivity.this, mSettings.theme);
        		break;
        	default:
        		throw new IllegalArgumentException("Unexpected action value "+mAction);
        	}
        	
        	return true;
        }
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    	int rowId = (int) info.id;
    	
    	if (rowId == 0 || mMorePositions.contains(rowId))
    		;
    	else if (mHiddenCommentHeads.contains(rowId))
    		menu.add(0, Constants.DIALOG_SHOW_COMMENT, 0, "Show comment");
    	else
    		menu.add(0, Constants.DIALOG_HIDE_COMMENT, 0, "Hide comment");
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	int rowId = (int) info.id;
    	
    	switch (item.getItemId()) {
    	case Constants.DIALOG_HIDE_COMMENT:
    		hideComment(rowId);
    		return true;
    	case Constants.DIALOG_SHOW_COMMENT:
    		showComment(rowId);
    		return true;
		default:
    		return super.onContextItemSelected(item);	
    	}
    }
    
    private void hideComment(int rowId) {
    	mHiddenCommentHeads.add(rowId);
    	int myIndent = mMessagesAdapter.getItem(rowId).getIndent();
    	// Hide everything after the row.
    	for (int i = rowId + 1; i < mMessagesAdapter.getCount(); i++) {
    		MessageInfo ci = mMessagesAdapter.getItem(i);
    		if (ci.getIndent() <= myIndent)
    			break;
    		mHiddenComments.add(i);
    	}
    	mMessagesAdapter.notifyDataSetChanged();
    }
    
    private void showComment(int rowId) {
    	if (mHiddenCommentHeads.contains(rowId)) {
    		mHiddenCommentHeads.remove(rowId);
    	}
    	int stopIndent = mMessagesAdapter.getItem(rowId).getIndent();
    	int skipIndentAbove = -1;
    	for (int i = rowId + 1; i < mMessagesAdapter.getCount(); i++) {
    		MessageInfo ci = mMessagesAdapter.getItem(i);
    		int ciIndent = ci.getIndent();
    		if (ciIndent <= stopIndent)
    			break;
    		if (skipIndentAbove != -1 && ciIndent > skipIndentAbove)
    			continue;
    		if (mHiddenCommentHeads.contains(i) && mHiddenComments.contains(i)) {
    			mHiddenComments.remove(i);
    			skipIndentAbove = ci.getIndent();
    		}
    		skipIndentAbove = -1;
    		if (mHiddenComments.contains(i))
    			mHiddenComments.remove(i);
    	}
    	mMessagesAdapter.notifyDataSetChanged();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
    	AlertDialog.Builder builder;
    	LayoutInflater inflater;
    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.login_dialog);
    		dialog.setTitle("Login to reddit.com");
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
    		
    	case Constants.DIALOG_THING_CLICK:
    		inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(this);
    		dialog = builder.setView(inflater.inflate(R.layout.comment_click_dialog, null)).create();
    		break;

    	case Constants.DIALOG_REPLY:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.compose_reply_dialog);
    		final EditText replyBody = (EditText) dialog.findViewById(R.id.body);
    		final Button replySaveButton = (Button) dialog.findViewById(R.id.reply_save_button);
    		final Button replyCancelButton = (Button) dialog.findViewById(R.id.reply_cancel_button);
    		replySaveButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				if (mVoteTargetMessageInfo.getOP() != null) {
    					new MessageReplyTask(mVoteTargetMessageInfo.getOP().getName(), mVoteTargetMessageInfo).execute(replyBody.getText());
    				} else {
    					new MessageReplyTask(mVoteTargetMessageInfo.getName(), mVoteTargetMessageInfo).execute(replyBody.getText());
    				}
    				dismissDialog(Constants.DIALOG_REPLY);
    			}
    		});
    		replyCancelButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				mVoteTargetMessageInfo.setReplyDraft(replyBody.getText().toString());
    				dismissDialog(Constants.DIALOG_REPLY);
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
    	case Constants.DIALOG_LOADING_COMMENTS_LIST:
    		mLoadingCommentsProgress = new AutoResetProgressDialog(this);
    		mLoadingCommentsProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    		mLoadingCommentsProgress.setMessage("Loading comments...");
    		mLoadingCommentsProgress.setCancelable(true);
    		dialog = mLoadingCommentsProgress;
    		break;
    	
    	default:
    		throw new IllegalArgumentException("Unexpected dialog id "+id);
    	}
    	return dialog;
    }
    
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
    	super.onPrepareDialog(id, dialog);
    	StringBuilder sb;
    	    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		if (mSettings.username != null) {
	    		final TextView loginUsernameInput = (TextView) dialog.findViewById(R.id.login_username_input);
	    		loginUsernameInput.setText(mSettings.username);
    		}
    		final TextView loginPasswordInput = (TextView) dialog.findViewById(R.id.login_password_input);
    		loginPasswordInput.setText("");
    		break;
    		
    	case Constants.DIALOG_THING_CLICK:
    		String likes;
    		final TextView titleView = (TextView) dialog.findViewById(R.id.title);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final TextView submissionStuffView = (TextView) dialog.findViewById(R.id.submissionTime_submitter_subreddit);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
			titleView.setText("Comment by " + mVoteTargetMessageInfo.getAuthor());
			likes = mVoteTargetMessageInfo.getLikes();
			urlView.setVisibility(View.INVISIBLE);
			submissionStuffView.setVisibility(View.INVISIBLE);

			// Look for embedded URLs
			final TextView commentBody = (TextView) mVoteTargetView.findViewById(R.id.body);
	        mVoteTargetSpans = commentBody.getUrls();
	        if (mVoteTargetSpans.length == 0) {
    			linkButton.setVisibility(View.INVISIBLE);
	        } else if (mVoteTargetSpans.length == 1) {
	        	linkButton.setVisibility(View.VISIBLE);
	        	linkButton.setText("link");
	        	linkButton.setOnClickListener(new OnClickListener() {
	        		public void onClick(View v) {
	        			dismissDialog(Constants.DIALOG_THING_CLICK);
	        			Common.launchBrowser(mVoteTargetSpans[0].getURL(), InboxActivity.this);
	        		}
	        	});
	        } else {
	        	linkButton.setVisibility(View.VISIBLE);
	        	linkButton.setText("links");
	        	linkButton.setOnClickListener(new OnClickListener() {
	        		public void onClick(View v) {
	        			dismissDialog(Constants.DIALOG_THING_CLICK);
	        			final java.util.ArrayList<String> urls = Util.extractUris(mVoteTargetSpans);

	    	            ArrayAdapter<String> adapter = 
	    	                new ArrayAdapter<String>(InboxActivity.this, android.R.layout.select_dialog_item, urls) {
	    	                public View getView(int position, View convertView, ViewGroup parent) {
	    	                    View v = super.getView(position, convertView, parent);
	    	                    try {
	    	                        String url = getItem(position).toString();
	    	                        TextView tv = (TextView) v;
	    	                        Drawable d = getPackageManager().getActivityIcon(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
	    	                        if (d != null) {
	    	                            d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
	    	                            tv.setCompoundDrawablePadding(10);
	    	                            tv.setCompoundDrawables(d, null, null, null);
	    	                        }
	    	                        final String telPrefix = "tel:";
	    	                        if (url.startsWith(telPrefix)) {
	    	                            url = PhoneNumberUtils.formatNumber(url.substring(telPrefix.length()));
	    	                        }
	    	                        tv.setText(url);
	    	                    } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
	    	                        ;
	    	                    }
	    	                    return v;
	    	                }
	    	            };

	    	            AlertDialog.Builder b = new AlertDialog.Builder(InboxActivity.this);

	    	            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
	    	                public final void onClick(DialogInterface dialog, int which) {
	    	                    if (which >= 0) {
	    	                        Common.launchBrowser(urls.get(which), InboxActivity.this);
	    	                    }
	    	                }
	    	            };
	    	                
	    	            b.setTitle(R.string.select_link_title);
	    	            b.setCancelable(true);
	    	            b.setAdapter(adapter, click);

	    	            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	                public final void onClick(DialogInterface dialog, int which) {
	    	                    dialog.dismiss();
	    	                }
	    	            });

	    	            b.show();
	        		}
	        	});
	        }
    		final Button replyButton = (Button) dialog.findViewById(R.id.reply_button);
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		
    		// Only show upvote/downvote if user is logged in
    		if (mSettings.loggedIn) {
    			loginButton.setVisibility(View.GONE);
    			replyButton.setVisibility(View.VISIBLE);
    			
	    		// The "reply" button
    			replyButton.setOnClickListener(new OnClickListener() {
	    			public void onClick(View v) {
	    				dismissDialog(Constants.DIALOG_THING_CLICK);
	    				showDialog(Constants.DIALOG_REPLY);
	        		}
	    		});
	    	} else {
    			replyButton.setVisibility(View.INVISIBLE);
    			loginButton.setVisibility(View.VISIBLE);
    			loginButton.setOnClickListener(new OnClickListener() {
    				public void onClick(View v) {
    					dismissDialog(Constants.DIALOG_THING_CLICK);
    					showDialog(Constants.DIALOG_LOGIN);
    				}
    			});
    		}
    		break;
    		
    	case Constants.DIALOG_REPLY:
    		if (mVoteTargetMessageInfo.getReplyDraft() != null) {
    			EditText replyBodyView = (EditText) dialog.findViewById(R.id.body); 
    			replyBodyView.setText(mVoteTargetMessageInfo.getReplyDraft());
    		}
    		break;
    		
    	case Constants.DIALOG_LOADING_INBOX:
    		mLoadingCommentsProgress.setMax(mNumVisibleMessages);
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
    /**
     * Called to "thaw" re-animate the app from a previous onSaveInstanceState().
     * 
     * @see android.app.Activity#onRestoreInstanceState
     */
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        try {
	        dismissDialog(Constants.DIALOG_LOGGING_IN);
	    } catch (IllegalArgumentException e) {
        	// Ignore.
        }
        try {
	        dismissDialog(Constants.DIALOG_LOADING_COMMENTS_LIST);
        } catch (IllegalArgumentException e) {
        	// Ignore.
        }
    }


}
