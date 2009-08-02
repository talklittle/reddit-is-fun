package com.andrewshu.android.reddit;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    
    private static final int QUERY_TOKEN = 42;
    
    /**
     * Used to keep track of the scroll state of the list.
     */
    private Parcelable mListState = null;
    
    private CharSequence mSubreddit;
    private Dialog mDialog = null;
    private boolean mLoggedIn;
    
    // Menu dialog actions
    static final int DIALOG_PICK_SUBREDDIT = 0;
    static final int DIALOG_REDDIT_COM = 1;
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_POST_THREAD = 5;
	
    // Keys used for data in the onSaveInstanceState() Map.
    public static final String STRINGS_KEY = "strings";
    public static final String SELECTION_KEY = "selection";
    public static final String URL_KEY = "url";
    public static final String STATUS_KEY = "status";


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


    /**
     * Called when user clicks an item in the list. Starts an activity to
     * open the url for that item.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ThreadInfo item = mAdapter.getItem(position);
        
        if (("self."+mSubreddit).toLowerCase().equals(item.getLinkDomain().toLowerCase())) {
        	// It's a self post
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
    		subreddit = mSubreddit;
    	ThreadsWorker worker = new ThreadsWorker(subreddit);
    	setCurrentWorker(worker);
    	
    	resetUI();
    	// TODO: nice status screen, like Alien vs. Android had
    	Log.d(TAG, "Downloading\u2026");
    	
    	setTitle("reddit is fun: /r/"+subreddit.toString().trim());
    	
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
        private CharSequence mSubreddit;

        public ThreadsWorker(CharSequence subreddit) {
            mSubreddit = subreddit;
        }

        @Override
        public void run() {
            try {
            	HttpClient client = new DefaultHttpClient();
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(mSubreddit.toString().trim())
            		.append("/.json").toString());
            	HttpResponse response = client.execute(request);

            	InputStream in = response.getEntity().getContent();
                
                parseSubredditJSON(in, mAdapter);
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
            HttpPost httppost = new HttpPost("http://www.reddit.com/post/login");
            httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            
            // TODO: Loading screen
            // Perform the HTTP POST request
        	HttpResponse response = client.execute(httppost);
        	HttpEntity entity = response.getEntity();
        	
        	status = response.getStatusLine().toString();
        	if (entity != null)
        		entity.consumeContent();
        	
        	List<Cookie> cookies = client.getCookieStore().getCookies();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	if (cookies.isEmpty()) {
        		Log.d(TAG, "None");
        	} else {
        		for (int i = 0; i < cookies.size(); i++) {
        			Log.d(TAG, "- " + cookies.get(i).toString());
        		}
        	}
        	
        	// TODO: fast Toast saying you logged in
            status += "logged in";
            mLoggedIn = true;
            mMenu.findItem(DIALOG_LOGIN).setTitle("Logout")
            	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGOUT));
            doVote("t3_8hryo", -1, "test");
        } catch (Exception e) {
            status += "failed:" + e.getMessage();
            mLoggedIn = false;
        }
        Log.d(TAG, status);
        return mLoggedIn;
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
        mLoggedIn = false;
        mMenu.findItem(DIALOG_LOGIN).setTitle("Login")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGIN));;
        Log.d(TAG, status);
    }
    
    public void doVote(String thingId, int direction, String subreddit) {
    	String status = "";
    	if (!mLoggedIn) {
    		// TODO: Error dialog saying you must be logged in.
    		return;
    	}
    	if (direction < -1 || direction > 1) {
    		// TODO: Error dialog saying invalid vote direction
    		return;
    	}
    	
    	try {
	    	// Construct data
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("id", thingId));
			nvps.add(new BasicNameValuePair("dir", String.valueOf(direction)));
			nvps.add(new BasicNameValuePair("r", subreddit));
			
	        DefaultHttpClient client = new DefaultHttpClient();
	        HttpPost httppost = new HttpPost("http://www.reddit.com/api/vote");
	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

            // TODO: Launch this in a background thread
	        
	        // Perform the HTTP POST request
	    	HttpResponse response = client.execute(httppost);
	    	HttpEntity entity = response.getEntity();
	    	
	    	status = response.getStatusLine().toString();
	    	if (entity != null)
	    		entity.consumeContent();
	    	
	    	List<Cookie> cookies = client.getCookieStore().getCookies();
	    	if (!status.contains("OK"))
	    		throw new HttpException(status);
	    	
	    	if (cookies.isEmpty()) {
	    		Log.d(TAG, "None");
	    	} else {
	    		for (int i = 0; i < cookies.size(); i++) {
	    			Log.d(TAG, "- " + cookies.get(i).toString());
	    		}
	    	}
    	} catch (Exception e) {
            status += "failed:" + e.getMessage();
    	}
    	Log.d(TAG, status);
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
        		doGetThreadsList(null);
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
    		            // Get rid of the Dialog
    		        	if (mDialog != null) {
    		        		mDialog.dismiss();
    		        		mDialog = null;
    		        	}
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button pickSubredditButton = (Button) mDialog.findViewById(R.id.pick_subreddit_button);
    		pickSubredditButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    		        doGetThreadsList(pickSubredditInput.getText());
    		        // Get rid of the Dialog
    		        if (mDialog != null) {
    		        	mDialog.dismiss();
    		        	mDialog = null;
    		        }
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
    		        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
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
    		            // Get rid of the Dialog
    		        	if (mDialog != null) {
    		        		mDialog.dismiss();
    		        		mDialog = null;
    		        	}
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button loginButton = (Button) mDialog.findViewById(R.id.login_button);
    		loginButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				doLogin(loginUsernameInput.getText(), loginPasswordInput.getText());
    		        // Get rid of the Dialog
    		        if (mDialog != null) {
    		        	mDialog.dismiss();
    		        	mDialog = null;
    		        }
    		    }
    		});
    		break;

    	case DIALOG_POST_THREAD:
    		// TODO: a scrollable Dialog with Title, URL/Selftext, and subreddit.
    		// Downvote:
    		//POSTDATA=id=t3_7smc4&dir=-1&vh=0d4ab0ffd56ad0f66841c15609e9a45aeec6b015&r=test&uh=4z0mevzb8285f1ccdf2f289dce2a87a4e361b7a75b7fbd46c0
    		// dir in [-1,0,1]
    		// r is subreddit name
    		// vh?
    		// uh? user handle?
//    		Cookie=reddit_first=%7B%22organic_pos%22%3A%201%2C%20%22firsttime%22%3A%20%22first%22%7D; _last_thing=; talklittle_reddit_counts=; talklittle_last_thing=; talklittle_recentclicks2=t3_96jf1%2Ct3_96pf4%2Ct3_96o76%2Ct3_96nm6%2Ct3_96l6g%2Ct3_7y43n%2Ct3_96f84%2Ct3_8wirs%2Ct3_8ecqd%2Ct3_96f84%2Ct3_8ecqd%2Ct3_96m5x%2Ct3_96o1p%2Ct3_96iwc%2Ct3_96dek%2Ct3_96dgw%2Ct3_96iok%2Ct3_96g6t%2Ct3_96cko%2Ct3_96dg0%2Ct3_96dpa%2Ct3_96gc9%2Ct3_96cdt%2Ct3_96eq9%2Ct3_96daz%2Ct3_95y8r%2Ct3_9682f%2Ct3_962ee%2Ct3_963xl%2Ct3_95vil%2Ct3_967b9%2Ct3_95zwm%2Ct3_96066%2Ct3_9614s%2Ct3_960jn%2Ct3_96140%2Ct3_961xe%2Ct3_95y1a%2Ct3_95zs4%2Ct3_96317%2Ct3_95uv7%2Ct3_963bj%2Ct3_965v7%2Ct3_961k1%2Ct3_962se%2Ct3_961k1%2Ct3_95uyy%2Ct3_95tn0%2Ct3_95uu1%2Ct3_95o82%2Ct3_95sgg%2Ct3_95sxx%2Ct3_95h0l%2Ct3_95kfa%2Ct3_7smc4%2Ct3_8w7js%2Ct3_8zayp%2Ct3_95h98%2Ct3_95il8%2Ct3_95krq%2Ct3_95g3n%2Ct3_95ino%2Ct3_7l0fx%2Ct3_8yvu7%2Ct3_956pf%2Ct3_95bf3%2Ct3_959u9%2Ct3_95c6d%2Ct3_959m8%2Ct3_8wirs%2Ct3_959y2%2Ct3_954yd%2Ct3_9579o%2Ct3_94z4i%2Ct3_94vyc%2Ct3_94zhk%2Ct3_94z5w%2Ct3_95462%2Ct3_9528k%2Ct3_94txt%2Ct3_94yh0%2Ct3_950um%2Ct3_94vzs%2Ct3_94v02%2Ct3_94pf2%2Ct3_94txl%2Ct3_94qj9%2Ct3_94qzm%2Ct3_94so6%2Ct3_94s5p%2Ct3_94ow8%2Ct3_94tww%2Ct3_94q6b%2Ct3_94qr0; _recentclicks2=t3_96lrd%2C; talklittle_test_recentclicks2=t3_96pf4%2Ct3_96nfp%2Ct3_96r6k%2Ct3_96i6j%2Ct3_96otl%2Ct3_96nnt%2Ct3_96ppa%2Ct3_96nvv%2C; talklittle_test_reddit_counts=; talklittle_test_last_thing=;
//    			reddit_session=5488034%2C2009-08-01T22%3A52%3A43%2Cf6ddf116ab7bc6b53021fa94e1b070eaccbb80d9
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
			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jp.getCurrentName();
				jp.nextToken(); // move to value, or START_OBJECT/START_ARRAY
			
				if ("data".equals(fieldname)) { // contains an object
					ThreadInfo ti = new ThreadInfo();
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
					mHandler.post(new ItemAdder(ti));
				} else if ("kind".equals(fieldname)) {
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
						break;  // Go on to the next JSON Object in the JSON Array which should hold threads.
					}
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
		}
	}
    

}
