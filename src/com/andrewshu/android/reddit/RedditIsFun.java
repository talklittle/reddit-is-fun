package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import android.app.Activity;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main Activity class representing a Subreddit, i.e., a ThreadsList.
 * 
 * @author TalkLittle
 *
 */
public final class RedditIsFun extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "RedditIsFun";
	
	public final String THREAD_KIND = "t3";
	public final String SERIALIZE_SEPARATOR = "\r";
	
    private final JsonFactory jsonFactory = new JsonFactory(); 
	
    /** Custom list adapter that fits our rss data into the list. */
    private ThreadsListAdapter mAdapter;
    /** Handler used to post runnables to the UI thread. */
    private Handler mHandler;
    /** Currently running background network thread. */
    private ThreadsWorker mWorker;
    
    private Menu mMenu;
    
    private DefaultHttpClient mClient = null;
    
    private CharSequence mSubreddit = null;
    private CharSequence mThingId = null;
    private Dialog mDialog = null;
    private boolean mLoggedIn = false;
    private CharSequence mUsername = null;
    private CharSequence mModhash = null;
    
    // Menu dialog actions
    static final int DIALOG_PICK_SUBREDDIT = 0;
    static final int DIALOG_REDDIT_COM = 1;
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_POST_THREAD = 5;
    static final int DIALOG_THREAD_CLICK = 6;
	
    // Keys used for data in the onSaveInstanceState() Map.
    public static final String STRINGS_KEY = "strings";
    public static final String SELECTION_KEY = "selection";
    public static final String URL_KEY = "url";
    public static final String STATUS_KEY = "status";
    
    // A short HTML file returned by reddit, so we can get the modhash
    public static final String MODHASH_URL = "http://www.reddit.com/stats";
    
    // The pattern to find modhash from HTML javascript area
    public static final Pattern MODHASH_PATTERN = Pattern.compile("modhash: '(.*?)'");


    /**
     * Called when the activity starts up. Do activity initialization
     * here, not in a constructor.
     * 
     * @see Activity#onCreate
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.threads_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().

        // Install our custom RSSListAdapter.
        List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        mAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mAdapter);

        // Need one of these to post things back to the UI thread.
        mHandler = new Handler();
        
        // Start at /r/reddit.com
        mSubreddit = "reddit.com";
        doGetThreadsList(mSubreddit);
        
        // NOTE: this could use the icicle as done in
        // onRestoreInstanceState().
    }

    // TODO: do something like this when you select a new subreddit through Menu.
    void startQuery() {
        mAdapter.setLoading(true);
        
        // Cancel any pending queries
//        mQueryHandler.cancelOperation(QUERY_TOKEN);

        // Kick off the new query
//        mQueryHandler.startQuery(QUERY_TOKEN, null, People.CONTENT_URI, CONTACTS_PROJECTION,
//                null, null, getSortOrder(CONTACTS_PROJECTION));
    }

    private final class ThreadsListAdapter extends ArrayAdapter<ThreadInfo> {
    	private LayoutInflater mInflater;
        private boolean mLoading = true;
        private boolean mDisplayThumbnails = false; // TODO: use this
        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null; // TODO?: use this?
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;

        
        public ThreadsListAdapter(Context context, List<ThreadInfo> objects) {
            super(context, 0, objects);
            
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mFrequentSeparatorPos) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
            return super.getItemViewType(position);
        }

        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(R.layout.threads_list_item, null);
            } else {
                view = convertView;
            }
            
            ThreadInfo item = this.getItem(position);
            
            // Set the values of the Views for the ThreadsListItem
            
            TextView titleView = (TextView) view.findViewById(R.id.title);
            TextView votesView = (TextView) view.findViewById(R.id.votes);
            TextView linkDomainView = (TextView) view.findViewById(R.id.linkDomain);
            TextView numCommentsView = (TextView) view.findViewById(R.id.numComments);
            TextView submitterView = (TextView) view.findViewById(R.id.submitter);
            TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
            TextView linkView = (TextView) view.findViewById(R.id.link);
            
            titleView.setText(item.getTitle());
            votesView.setText(item.getNumVotes());
            linkDomainView.setText("("+item.getLinkDomain()+")");
            numCommentsView.setText(item.getNumComments());
            submitterView.setText(item.getSubmitter());
            // TODO: convert submission time to a displayable time
            Date submissionTimeDate = new Date((long) (Double.parseDouble(item.getSubmissionTime()) / 1000));
            submissionTimeView.setText("XXX");
            linkView.setText(item.getLink());
            
            // TODO: Thumbnail
//            view.getThumbnail().

            // TODO: If thumbnail, download it and create ImageView
            // Some thumbnails may be absolute paths instead of URLs:
            // "/static/noimage.png"
            
            // Set the proper icon (star or presence or nothing)
//            ImageView presenceView = cache.presenceView;
//            if ((mMode & MODE_MASK_NO_PRESENCE) == 0) {
//                int serverStatus;
//                if (!cursor.isNull(SERVER_STATUS_COLUMN_INDEX)) {
//                    serverStatus = cursor.getInt(SERVER_STATUS_COLUMN_INDEX);
//                    presenceView.setImageResource(
//                            Presence.getPresenceIconResourceId(serverStatus));
//                    presenceView.setVisibility(View.VISIBLE);
//                } else {
//                    presenceView.setVisibility(View.GONE);
//                }
//            } else {
//                presenceView.setVisibility(View.GONE);
//            }
//
//            // Set the photo, if requested
//            if (mDisplayPhotos) {
//                Bitmap photo = null;
//
//                // Look for the cached bitmap
//                int pos = cursor.getPosition();
//                SoftReference<Bitmap> ref = mBitmapCache.get(pos);
//                if (ref != null) {
//                    photo = ref.get();
//                }
//
//                if (photo == null) {
//                    // Bitmap cache miss, decode it from the cursor
//                    if (!cursor.isNull(PHOTO_COLUMN_INDEX)) {
//                        try {
//                            byte[] photoData = cursor.getBlob(PHOTO_COLUMN_INDEX);
//                            photo = BitmapFactory.decodeByteArray(photoData, 0,
//                                    photoData.length);
//                            mBitmapCache.put(pos, new SoftReference<Bitmap>(photo));
//                        } catch (OutOfMemoryError e) {
//                            // Not enough memory for the photo, use the default one instead
//                            photo = null;
//                        }
//                    }
//                }
//
//                // Bind the photo, or use the fallback no photo resource
//                if (photo != null) {
//                    cache.photoView.setImageBitmap(photo);
//                } else {
//                    cache.photoView.setImageResource(R.drawable.ic_contact_list_picture);
//                }
//            }

            return view;
        }
        
    }
    
    public void dismissDialog() {
    	if (mDialog != null) {
    		mDialog.dismiss();
    		mDialog = null;
    	}
    }


    /**
     * Called when user clicks an item in the list. Starts an activity to
     * open the url for that item.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ThreadInfo item = mAdapter.getItem(position);
        
        // Mark the thread as selected
        mThingId = item.getThingFullname();
        
        if (("self."+mSubreddit).toLowerCase().equals(item.getLinkDomain().toLowerCase())) {
        	// It's a self post
        	showDialog(DIALOG_THREAD_CLICK);
            // TODO: new Intent aiming using CommentsListActivity specifically.
        } else {
        	// It should have a web link associated with it
            // TODO: popup dialog: 2 buttons: LINK, COMMENTS. well, 2 big and 2 small buttons (small: user profile, report post)
        	if ("link".equals("true")) {
	            // Creates and starts an intent to open the item.link url.
	            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getLink().toString()));
	            startActivity(intent);
        	} else {
                // TODO: new Intent aiming at CommentsListActivity specifically.
        	}
        }
    }

    /**
     * Resets the output UI -- list and status text empty.
     */
    public void resetUI() {
        // Reset the list to be empty.
        List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        mAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mAdapter);
    }

    /**
     * Sets the currently active running worker. Interrupts any earlier worker,
     * so we only have one at a time.
     * 
     * @param worker the new worker
     */
    public synchronized void setCurrentWorker(ThreadsWorker worker) {
        if (mWorker != null) mWorker.interrupt();
        mWorker = worker;
    }

    /**
     * Is the given worker the currently active one.
     * 
     * @param worker
     * @return
     */
    public synchronized boolean isCurrentWorker(ThreadsWorker worker) {
        return (mWorker == worker);
    }


    /**
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.) 
     */
    private void doGetThreadsList(CharSequence subreddit) {
    	if (subreddit == null)
    		return;
    	
    	ThreadsWorker worker = new ThreadsWorker(subreddit);
    	setCurrentWorker(worker);
    	
    	resetUI();
    	// TODO: nice status screen, like Alien vs. Android had
    	Log.d(TAG, "Downloading\u2026");
    	
    	setTitle("/r/"+subreddit.toString().trim());
    	
    	worker.start();
    }

    /**
     * Runnable that the worker thread uses to post ThreadItems to the
     * UI via mHandler.post
     */
    private class ItemAdder implements Runnable {
        ThreadInfo mItem;

        ItemAdder(ThreadInfo item) {
            mItem = item;
        }

        public void run() {
            mAdapter.add(mItem);
        }

        // NOTE: Performance idea -- would be more efficient to have he option
        // to add multiple items at once, so you get less "update storm" in the UI
        // compared to adding things one at a time.
    }

    /**
     * Worker thread takes in an rss url string, downloads its data, parses
     * out the rss items, and communicates them back to the UI as they are read.
     */
    private class ThreadsWorker extends Thread {
        private CharSequence _mSubreddit;

        public ThreadsWorker(CharSequence subreddit) {
            _mSubreddit = subreddit;
        }

        @Override
        public void run() {
            try {
            	HttpClient client = new DefaultHttpClient();
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(_mSubreddit.toString().trim())
            		.append("/.json").toString());
            	HttpResponse response = client.execute(request);

            	InputStream in = response.getEntity().getContent();
                
                parseSubredditJSON(in, mAdapter);
                
                mSubreddit = _mSubreddit;
            } catch (Exception e) {
                Log.e(TAG, "failed:" + e.getMessage());
            }
        }
    }
    
    public boolean doLogin(CharSequence username, CharSequence password) {
    	String status = "";
    	try {
    		// Construct data
    		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    		nvps.add(new BasicNameValuePair("user", username.toString()));
    		nvps.add(new BasicNameValuePair("passwd", password.toString()));
    		
            DefaultHttpClient client = new DefaultHttpClient();
            client.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 20000);
            HttpPost httppost = new HttpPost("http://www.reddit.com/api/login/"+username);
            httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            
            // TODO: Loading screen
            // Perform the HTTP POST request
        	HttpResponse response = client.execute(httppost);
        	status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();
        	
        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	if (line == null) {
        		throw new HttpException("No content returned from login POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		throw new Exception("Wrong password");
        	}

        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	while ((c = in.read()) >= 0) {
//        		sb.append((char) c);
//        		for (int i = 0; i < 80; i++) {
//        			c = in.read();
//        			if (c < 0) {
//        				done = true;
//        				break;
//        			}
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doLogin response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}
        	
        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
        	List<Cookie> cookies = client.getCookieStore().getCookies();
        	if (cookies.isEmpty()) {
        		Log.d(TAG, "Failed to login: No cookies");
        		mLoggedIn = false;
        		return false;
        	}
        	
        	// Getting here means you successfully logged in.
        	// Congratulations!
        	// You are a true reddit master!
        
        	mClient = client;
        	mUsername = username;
        	
        	mLoggedIn = true;
        	Toast.makeText(this, "Logged in as "+username, Toast.LENGTH_SHORT).show();
        	
            mMenu.findItem(DIALOG_LOGIN).setTitle("Logout")
            	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGOUT));
        } catch (Exception e) {
            // TODO: Login error message
        	mLoggedIn = false;
        }
        Log.d(TAG, status);
        return mLoggedIn;
    }
    
    public boolean doUpdateModhash() {
    	if (!mLoggedIn) {
    		return false;
    	}
    	
    	// If logged in, client should exist. Otherwise logout and display error.
    	if (mClient == null) {
    		doLogout();
    		// TODO: "Error: You have been logged out. Please login again."
    		return false;
    	}
    	
    	try {
    		String status;
    		
    		HttpGet httpget = new HttpGet(MODHASH_URL);
    		// TODO: Decide: background thread or loading screen? 
    		HttpResponse response = mClient.execute(httpget);
    		
    		status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	// modhash should appear within first 1200 chars
        	char[] buffer = new char[2048];
        	in.read(buffer, 0, 2048);
        	String line = String.valueOf(buffer);
        	if (line == null) {
        		throw new HttpException("No content returned from doUpdateModhash GET to "+MODHASH_URL);
        	}
        	if (line.contains("USER_REQUIRED")) {
        		throw new Exception("User session error: USER_REQUIRED");
        	}
        	
        	Matcher modhashMatcher = MODHASH_PATTERN.matcher(line);
        	if (modhashMatcher.find()) {
        		mModhash = modhashMatcher.group(1);
        		if ("".equals(mModhash)) {
        			// Means user is not actually logged in.
        			doLogout();
        			// TODO: "Error: You have been logged out."
        			return false;
        		}
        	} else {
        		throw new Exception("No modhash found at URL "+MODHASH_URL);
        	}

//        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	while ((c = in.read()) >= 0) {
//        		sb.append((char) c);
//        		for (int i = 0; i < 80; i++) {
//        			c = in.read();
//        			if (c < 0) {
//        				done = true;
//        				break;
//        			}
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doLogin response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}

        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
    	} catch (Exception e) {
    		Log.e(TAG, e.getMessage());
    		// TODO: "Error getting credentials. Please try again."
    		return false;
    	}
    	Log.d(TAG, "modhash: "+mModhash);
    	return true;
    }
    
    public void doLogout() {
    	String status;
    	try {
        	HttpClient client = new DefaultHttpClient();
        	HttpGet request = new HttpGet("http://www.reddit.com/logout");
        	HttpResponse response = client.execute(request);

        	status = response.getStatusLine().toString();
        } catch (Exception e) {
            status = "failed:" + e.getMessage();
        }
        
        mUsername = null;
        mClient = null;
        
        mLoggedIn = false;
        mMenu.findItem(DIALOG_LOGIN).setTitle("Login")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGIN));;
        Log.d(TAG, status);
    }
    
    public boolean doVote(CharSequence thingId, int direction, CharSequence subreddit) {
    	String status = "";
    	if (!mLoggedIn) {
    		// TODO: Error dialog saying you must be logged in.
    		return false;
    	}
    	if (direction < -1 || direction > 1) {
    		// TODO: Error dialog saying invalid vote direction
    		return false;
    	}
    	
    	// Update the modhash if necessary
    	if (mModhash == null) {
    		if (!doUpdateModhash()) {
    			// doUpdateModhash should have given an error about credentials
    			Log.e(TAG, "Vote failed because doUpdateModhash() failed");
    			return false;
    		}
    	}
    	
    	try {
	    	// Construct data
    		
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("id", thingId.toString()));
			nvps.add(new BasicNameValuePair("dir", String.valueOf(direction)));
			nvps.add(new BasicNameValuePair("r", subreddit.toString()));
			nvps.add(new BasicNameValuePair("uh", mModhash.toString()));
			// Votehash is currently unused by reddit 
//			nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
			
			HttpPost httppost = new HttpPost("http://www.reddit.com/api/vote");
	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
	        
	        Log.d(TAG, nvps.toString());
	        
            // TODO: Launch voting in a background thread. Need to lock client? Possible to use 2nd client, copy cookies?
	        
	        // Perform the HTTP POST request
	    	HttpResponse response = mClient.execute(httppost);
	    	status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	if (line == null) {
        		throw new HttpException("No content returned from vote POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		throw new Exception("Wrong password");
        	}
        	if (line.contains("USER_REQUIRED")) {
        		// The modhash probably expired
        		mModhash = null;
        		// TODO: "Error voting. Try again."
        		return false;
        	}
        	
        	Log.d(TAG, line);

//        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	while ((c = in.read()) >= 0) {
//        		sb.append((char) c);
//        		for (int i = 0; i < 80; i++) {
//        			c = in.read();
//        			if (c < 0) {
//        				done = true;
//        				break;
//        			}
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doLogin response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}

        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
    	} catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
    	}
    	Log.d(TAG, status);
    	return true;
    }

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenu = menu;
        
        menu.add(0, DIALOG_PICK_SUBREDDIT, 0, "Pick subreddit")
            .setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_PICK_SUBREDDIT));

        menu.add(0, DIALOG_REDDIT_COM, 0, "reddit.com")
            .setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_REDDIT_COM));

        menu.add(0, DIALOG_REFRESH, 0, "Refresh")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_REFRESH));
        
        if (mLoggedIn) {
        	menu.add(0, DIALOG_LOGIN, 0, "Logout")
       			.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGOUT));
        } else {
        	menu.add(0, DIALOG_LOGIN, 0, "Login")
       			.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGIN));
        }
        
        menu.add(0, DIALOG_POST_THREAD, 0, "Post Thread")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_POST_THREAD));
        
        return true;
    }

    /**
     * Puts text in the url text field and gives it focus. Used to make a Runnable
     * for each menu item. This way, one inner class works for all items vs. an
     * anonymous inner class for each menu item.
     */
    private class ThreadsListMenu implements MenuItem.OnMenuItemClickListener {
        private int mAction;

        ThreadsListMenu(int action) {
            mAction = action;
        }

        public boolean onMenuItemClick(MenuItem item) {
        	switch (mAction) {
        	case DIALOG_PICK_SUBREDDIT:
        	case DIALOG_LOGIN:
        	case DIALOG_POST_THREAD:
        		showDialog(mAction);
        		break;
        	case DIALOG_REDDIT_COM:
        		doGetThreadsList("reddit.com");
        		break;
        	case DIALOG_LOGOUT:
        		doLogout();
        		break;
        	case DIALOG_REFRESH:
        		doGetThreadsList(mSubreddit);
        		break;
        	default:
        		throw new IllegalArgumentException("Unexpected action value "+mAction);
        	}
        	
        	return true;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	switch (id) {
    	case DIALOG_PICK_SUBREDDIT:
    		mDialog = new Dialog(this);
    		mDialog.setContentView(R.layout.pick_subreddit_dialog);
    		mDialog.setTitle("Pick a subreddit");
    		final EditText pickSubredditInput = (EditText) mDialog.findViewById(R.id.pick_subreddit_input);
    		pickSubredditInput.setOnKeyListener(new OnKeyListener() {
    			public boolean onKey(View v, int keyCode, KeyEvent event) {
    		        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
    		        	doGetThreadsList(pickSubredditInput.getText());
    		            dismissDialog();
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button pickSubredditButton = (Button) mDialog.findViewById(R.id.pick_subreddit_button);
    		pickSubredditButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    		        doGetThreadsList(pickSubredditInput.getText());
    		        dismissDialog();
    		    }
    		});
    		break;
    		
    	case DIALOG_LOGIN:
    		mDialog = new Dialog(this);
    		mDialog.setContentView(R.layout.login_dialog);
    		mDialog.setTitle("Login to reddit.com");
    		final EditText loginUsernameInput = (EditText) mDialog.findViewById(R.id.login_username_input);
    		final EditText loginPasswordInput = (EditText) mDialog.findViewById(R.id.login_password_input);
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
    		        	doLogin(loginUsernameInput.getText(), loginPasswordInput.getText());
    		            dismissDialog();
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button loginButton = (Button) mDialog.findViewById(R.id.login_button);
    		loginButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				doLogin(loginUsernameInput.getText(), loginPasswordInput.getText());
    		        dismissDialog();
    		    }
    		});
    		break;

    	case DIALOG_THREAD_CLICK:
    		mDialog = new Dialog(this);
    		mDialog.setContentView(R.layout.thread_click_dialog);
    		mDialog.setTitle("What?");
    		// TODO: Don't show upvote/downvote buttons if user isn't logged in
    		// TODO: Detect if user already voted. Then make the appropriate button vote neutral = 0
    		final Button voteUpButton = (Button) mDialog.findViewById(R.id.thread_vote_up_button);
    		voteUpButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				doVote(mThingId, 1, mSubreddit);
    				dismissDialog();
    			}
    		});
    		final Button voteDownButton = (Button) mDialog.findViewById(R.id.thread_vote_down_button);
    		voteDownButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				doVote(mThingId, -1, mSubreddit);
    				dismissDialog();
    			}
    		});
    		break;
    		
    	case DIALOG_POST_THREAD:
    		// TODO: a scrollable Dialog with Title, URL/Selftext, and subreddit.
    		
    	default:
    		throw new IllegalArgumentException("Unexpected dialog id "+id);
    	}
    	return mDialog;
    }
    
    /**
     * Called for us to save out our current state before we are paused,
     * such a for example if the user switches to another app and memory
     * gets scarce. The given outState is a Bundle to which we can save
     * objects, such as Strings, Integers or lists of Strings. In this case, we
     * save out the list of currently downloaded rss data, (so we don't have to
     * re-do all the networking just because the user goes back and forth
     * between aps) which item is currently selected, and the data for the text views.
     * In onRestoreInstanceState() we look at the map to reconstruct the run-state of the
     * application, so returning to the activity looks seamlessly correct.
     * 
     * @see android.app.Activity#onSaveInstanceState
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Make a List of all the ThreadItem data for saving
        // NOTE: there may be a way to save the ThreadItems directly,
        // rather than their string data.
        int count = mAdapter.getCount();

        // Save out the items as a flat list of CharSequence objects --
        // title0, link0, descr0, title1, link1, ...
        ArrayList<CharSequence> strings = new ArrayList<CharSequence>();
        for (int i = 0; i < count; i++) {
            ThreadInfo item = mAdapter.getItem(i);
            for (int k = 0; k < ThreadInfo._KEYS.length; k++) {
            	if (item.mValues.containsKey(ThreadInfo._KEYS[k])) {
            		strings.add(ThreadInfo._KEYS[k]);
            		strings.add(item.mValues.get(ThreadInfo._KEYS[k]));
            	}
            }
            strings.add(SERIALIZE_SEPARATOR);
        }
        outState.putSerializable(STRINGS_KEY, strings);

        // Save current selection index (if focussed)
        if (getListView().hasFocus()) {
            outState.putInt(SELECTION_KEY, Integer.valueOf(getListView().getSelectedItemPosition()));
        }

    }

    /**
     * Called to "thaw" re-animate the app from a previous onSaveInstanceState().
     * 
     * @see android.app.Activity#onRestoreInstanceState
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);

        // Note: null is a legal value for onRestoreInstanceState.
        if (state == null) return;

        // Restore items from the big list of CharSequence objects
        List<CharSequence> strings = (ArrayList<CharSequence>)state.getSerializable(STRINGS_KEY);
        List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        for (int i = 0; i < strings.size(); i++) {
        	ThreadInfo ti = new ThreadInfo();
        	CharSequence key, value;
        	while (!SERIALIZE_SEPARATOR.equals(strings.get(i))) {
        		if (SERIALIZE_SEPARATOR.equals(strings.get(i+1))) {
        			// Well, just skip the value instead of throwing an exception.
        			break;
        		}
        		key = strings.get(i);
        		value = strings.get(i+1);
        		ti.put(key.toString(), value.toString());
        		i += 2;
        	}
            items.add(ti);
        }

        // Reset the list view to show this data.
        mAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mAdapter);

        // Restore selection
        if (state.containsKey(SELECTION_KEY)) {
            getListView().requestFocus(View.FOCUS_FORWARD);
            // todo: is above right? needed it to work
            getListView().setSelection(state.getInt(SELECTION_KEY));
        }
    }


    /**
     * Skips ahead the JsonParser while validating.
     * When the function returns normally, JsonParser.getCurrentToken() should return the
     * JsonToken.START_ARRAY value corresponding to the beginning of the list of threads.
     * @param jp
     * @throws JsonParseException
     * @throws IllegalStateException
     */
    void skipToThreadsJSON(JsonParser jp) throws IOException, JsonParseException, IllegalStateException {
    	String genericListingError = "Not a subreddit listing";
    	if (JsonToken.START_OBJECT != jp.nextToken()) // starts with "{"
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (!"kind".equals(jp.getCurrentName()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (!"Listing".equals(jp.getText()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (!"data".equals(jp.getCurrentName()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (JsonToken.START_OBJECT != jp.getCurrentToken())
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	while (!"children".equals(jp.getCurrentName())) {
    		// Don't care
    		jp.nextToken();
    	}
    	jp.nextToken();
    	if (jp.getCurrentToken() != JsonToken.START_ARRAY)
    		throw new IllegalStateException(genericListingError);
    }
    
    void parseSubredditJSON(InputStream in, ThreadsListAdapter adapter) throws IOException,
		    JsonParseException, IllegalStateException {
		
		JsonParser jp = jsonFactory.createJsonParser(in);
		
		// Validate initial stuff, skip to the JSON List of threads
		skipToThreadsJSON(jp);

		while (jp.nextToken() != JsonToken.END_ARRAY) {
			if (jp.getCurrentToken() != JsonToken.START_OBJECT)
				throw new IllegalStateException("Unexpected non-JSON-object in the children array");
			
			// Process JSON representing one thread
			ThreadInfo ti = new ThreadInfo();
			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jp.getCurrentName();
				jp.nextToken(); // move to value, or START_OBJECT/START_ARRAY
			
				if ("kind".equals(fieldname)) {
					if (!THREAD_KIND.equals(jp.getText())) {
						// Skip this JSON Object since it doesn't represent a thread.
						// May encounter nested objects too.
						int nested = 0;
						for (;;) {
							jp.nextToken();
							if (jp.getCurrentToken() == JsonToken.END_OBJECT && nested == 0)
								break;
							if (jp.getCurrentToken() == JsonToken.START_OBJECT)
								nested++;
							if (jp.getCurrentToken() == JsonToken.END_OBJECT)
								nested--;
						}
						break;  // Go on to the next thread (JSON Object) in the JSON Array.
					}
					ti.put("kind", THREAD_KIND);
				} else if ("data".equals(fieldname)) { // contains an object
					while (jp.nextToken() != JsonToken.END_OBJECT) {
						String namefield = jp.getCurrentName();
						jp.nextToken(); // move to value
						// Should validate each field but I'm lazy
						if ("media".equals(namefield) && jp.getCurrentToken() == JsonToken.START_OBJECT) {
							while (jp.nextToken() != JsonToken.END_OBJECT) {
								String mediaNamefield = jp.getCurrentName();
								jp.nextToken(); // move to value
								ti.put("_media_"+mediaNamefield, jp.getText());
							}
						} else {
							ti.put(namefield, jp.getText());
						}
					}
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
			mHandler.post(new ItemAdder(ti));
		}
	}
    

}
