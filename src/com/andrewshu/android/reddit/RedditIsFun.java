package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
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
	
	public final String COMMENT_KIND = "t1";
	public final String THREAD_KIND = "t3";
	public final String SERIALIZE_SEPARATOR = "\r";
	public final String LOOK_OF_DISAPPROVAL = "\u0ca0\u005f\u0ca0";
	
    private final JsonFactory jsonFactory = new JsonFactory(); 
	
    /** Custom list adapter that fits our threads data into the list. */
    private ThreadsListAdapter mThreadsAdapter;
    /** Handler used to post runnables to the UI thread. */
    private Handler mHandler;
    /** Currently running background network thread. */
    private Thread mWorker;
    
    static final String PREFS_SESSION = "RedditSession";
    
    // Current HttpClient
    private DefaultHttpClient mClient = null;
    private Cookie mRedditSessionCookie = null;
    
    // UI State
    private CharSequence mSubreddit = null;
    private CharSequence mThingFullname = null;
    private CharSequence mThingId = null;
    private CharSequence mTargetURL = null;
    private CharSequence mTargetDomain = null;
    private View mVoteTargetView = null;
    private ThreadInfo mVoteTargetThreadInfo = null;
    
    // Login status
    private boolean mLoggedIn = false;
    private CharSequence mUsername = null;
    private CharSequence mModhash = null;
    
    // startActivityForResult request codes
    static final int ACTIVITY_PICK_SUBREDDIT = 0;
    
    // Menu and dialog actions
    static final int DIALOG_PICK_SUBREDDIT = 0;
    static final int DIALOG_REDDIT_COM = 1;
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_POST_THREAD = 5;
    static final int DIALOG_THREAD_CLICK = 6;
    static final int DIALOG_LOGGING_IN = 7;
    static final int DIALOG_LOADING_THREADS_LIST = 8;
    static final int DIALOG_LOADING_COMMENTS_LIST = 9;
    static final int DIALOG_LOADING_LOOK_OF_DISAPPROVAL = 10;
    static final int DIALOG_OPEN_BROWSER = 11;
    static final int DIALOG_THEME = 12;
    
    static boolean mIsProgressDialogShowing = false;
    
    // Themes
    static final int THEME_LIGHT = 0;
    static final int THEME_DARK = 1;
    
    private int mTheme = THEME_LIGHT;
    private int mThemeResId = android.R.style.Theme_Light;
    
    // States for StateListDrawables
    static final int[] STATE_CHECKED = new int[]{android.R.attr.state_checked};
    static final int[] STATE_NONE = new int[0];
    
    // Strings
    static final String TRUE_STRING = "true";
    static final String FALSE_STRING = "false";
    static final String NULL_STRING = "null";
    
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
     * Disable this when releasing!
     */
    private static void log_d(String tag, String msg) {
    	Log.d(tag, msg);
    }
    
    /**
     * Disable this when releasing!
     */
    private static void log_e(String tag, String msg) {
    	Log.e(tag, msg);
    }
    
    
    /**
     * Called when the activity starts up. Do activity initialization
     * here, not in a constructor.
     * 
     * @see Activity#onCreate
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadRedditPreferences();
        setTheme(mThemeResId);
        
        setContentView(R.layout.threads_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().

        // Start at /r/reddit.com
        mSubreddit = "reddit.com";
        List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        mThreadsAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mThreadsAdapter);

        // Need one of these to post things back to the UI thread.
        mHandler = new Handler();
        
        doGetThreadsList(mSubreddit);
        
        // NOTE: this could use the icicle as done in
        // onRestoreInstanceState().
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	loadRedditPreferences();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	saveRedditPreferences();
    }
    

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	Bundle extras = intent.getExtras();
    	
    	switch(requestCode) {
    	case ACTIVITY_PICK_SUBREDDIT:
    		mSubreddit = extras.getString(ThreadInfo.SUBREDDIT);
    		doGetThreadsList(mSubreddit);
    		break;
    	}
    }
    
    private void saveRedditPreferences() {
    	SharedPreferences settings = getSharedPreferences(PREFS_SESSION, 0);
    	SharedPreferences.Editor editor = settings.edit();
    	editor.clear();
    	if (mLoggedIn) {
	    	if (mUsername != null)
	    		editor.putString("username", mUsername.toString());
	    	if (mRedditSessionCookie != null) {
	    		editor.putString("reddit_sessionValue",      mRedditSessionCookie.getValue());
	    		editor.putString("reddit_sessionDomain",     mRedditSessionCookie.getDomain());
	    		editor.putString("reddit_sessionPath",       mRedditSessionCookie.getPath());
	    		if (mRedditSessionCookie.getExpiryDate() != null)
	    			editor.putLong("reddit_sessionExpiryDate", mRedditSessionCookie.getExpiryDate().getTime());
	    	}
    	}
    	switch (mTheme) {
    	case THEME_DARK:
    		editor.putInt("theme", THEME_DARK);
    		editor.putInt("theme_resid", android.R.style.Theme);
    		break;
    	default:
    		editor.putInt("theme", THEME_LIGHT);
    		editor.putInt("theme_resid", android.R.style.Theme_Light);
    	}
    	editor.commit();
    }
    
    private void loadRedditPreferences() {
        // Retrieve the stored session info
        SharedPreferences sessionPrefs = getSharedPreferences(PREFS_SESSION, 0);
        mUsername = sessionPrefs.getString("username", null);
        String cookieValue = sessionPrefs.getString("reddit_sessionValue", null);
        String cookieDomain = sessionPrefs.getString("reddit_sessionDomain", null);
        String cookiePath = sessionPrefs.getString("reddit_sessionPath", null);
        long cookieExpiryDate = sessionPrefs.getLong("reddit_sessionExpiryDate", -1);
        if (cookieValue != null) {
        	BasicClientCookie redditSessionCookie = new BasicClientCookie("reddit_session", cookieValue);
        	redditSessionCookie.setDomain(cookieDomain);
        	redditSessionCookie.setPath(cookiePath);
        	if (cookieExpiryDate != -1)
        		redditSessionCookie.setExpiryDate(new Date(cookieExpiryDate));
        	else
        		redditSessionCookie.setExpiryDate(null);
        	mRedditSessionCookie = redditSessionCookie;
        	setClient(new DefaultHttpClient());
        	mClient.getCookieStore().addCookie(mRedditSessionCookie);
        	mLoggedIn = true;
        }
        mTheme = sessionPrefs.getInt("theme", THEME_LIGHT);
        mThemeResId = sessionPrefs.getInt("theme_resid", android.R.style.Theme_Light);
    }
    
    public class VoteUpOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(DIALOG_THREAD_CLICK);
			if (isChecked)
				doVote(mThingFullname, 1, mSubreddit);
			else
				doVote(mThingFullname, 0, mSubreddit);
		}
    }
    
    public class VoteDownOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(DIALOG_THREAD_CLICK);
			if (isChecked)
				doVote(mThingFullname, -1, mSubreddit);
			else
				doVote(mThingFullname, 0, mSubreddit);
		}
    }


    private final class ThreadsListAdapter extends ArrayAdapter<ThreadInfo> {
    	private LayoutInflater mInflater;
        private boolean mLoading = true;
//        private boolean mDisplayThumbnails = false; // TODO: use this
//        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null; // TODO?: use this?
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
            Resources res = getResources();

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
//            TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
            ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
            ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
            
            titleView.setText(item.getTitle());
            if (mTheme == THEME_LIGHT) {
	            if (TRUE_STRING.equals(item.getClicked()))
	            	titleView.setTextColor(res.getColor(R.color.purple));
	            else
	            	titleView.setTextColor(res.getColor(R.color.blue));
            }
            votesView.setText(item.getScore());
            linkDomainView.setText("("+item.getDomain()+")");
            numCommentsView.setText(item.getNumComments());
//            submitterView.setText(item.getAuthor());
            // TODO: convert submission time to a displayable time
//            Date submissionTimeDate = new Date((long) (Double.parseDouble(item.getCreated()) / 1000));
//            submissionTimeView.setText("5 hours ago");
            titleView.setTag(item.getURL());

            // Set the up and down arrow colors based on whether user likes
            if (mLoggedIn) {
            	if (TRUE_STRING.equals(item.getLikes())) {
            		voteUpView.setImageResource(R.drawable.vote_up_red);
            		voteDownView.setImageResource(R.drawable.vote_down_gray);
            		votesView.setTextColor(res.getColor(R.color.arrow_red));
            	} else if (FALSE_STRING.equals(item.getLikes())) {
            		voteUpView.setImageResource(R.drawable.vote_up_gray);
            		voteDownView.setImageResource(R.drawable.vote_down_blue);
            		votesView.setTextColor(res.getColor(R.color.arrow_blue));
            	} else {
            		voteUpView.setImageResource(R.drawable.vote_up_gray);
            		voteDownView.setImageResource(R.drawable.vote_down_gray);
            		votesView.setTextColor(res.getColor(R.color.gray));
            	}
            } else {
        		voteUpView.setImageResource(R.drawable.vote_up_gray);
        		voteDownView.setImageResource(R.drawable.vote_down_gray);
        		votesView.setTextColor(res.getColor(R.color.gray));
            }
            
            // TODO?: Thumbnail
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
        ThreadInfo item = mThreadsAdapter.getItem(position);
        
        // Mark the thread as selected
        mThingFullname = item.getName();
        mThingId = item.getId();
        mVoteTargetThreadInfo = item;
        mTargetURL = item.getURL();
        mTargetDomain = item.getDomain();
        mVoteTargetView = v;
        
        showDialog(DIALOG_THREAD_CLICK);
    }

    /**
     * Resets the output UI list contents, retains session state.
     */
    public void resetUI() {
        // Reset the list to be empty.
	    List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        mThreadsAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mThreadsAdapter);
    }

    /**
     * Sets the currently active running worker. Interrupts any earlier worker,
     * so we only have one at a time.
     * 
     * @param worker the new worker
     */
    public synchronized void setCurrentWorker(Thread worker) {
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
    
    public synchronized void setModhash(CharSequence modhash) {
    	mModhash = modhash;
    }
    
    public synchronized void setClient(DefaultHttpClient client) {
    	mClient = client;
    }


    /**
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.) 
     */
    private void doGetThreadsList(CharSequence subreddit) {
    	if (subreddit == null)
    		return;
    	
    	if ("jailbait".equals(subreddit.toString())) {
    		Toast lodToast = Toast.makeText(this, "", Toast.LENGTH_LONG);
    		View lodView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
    			.inflate(R.layout.look_of_disapproval_view, null);
    		lodToast.setView(lodView);
    		lodToast.show();
    	}
    	
    	ThreadsWorker worker = new ThreadsWorker(subreddit);
    	setCurrentWorker(worker);
    	
    	resetUI();
    	showDialog(DIALOG_LOADING_THREADS_LIST);
    	mIsProgressDialogShowing = true;
    	
    	setTitle("/r/"+subreddit.toString().trim());
    	
    	worker.start();
    }
    
    /**
     * Runnable that the worker thread uses to post ThreadItems to the
     * UI via mHandler.post
     */
    private class ThreadItemAdder implements Runnable {
        ThreadInfo mItem;

        ThreadItemAdder(ThreadInfo item) {
            mItem = item;
        }

        public void run() {
            mThreadsAdapter.add(mItem);
        }

        // NOTE: Performance idea -- would be more efficient to have he option
        // to add multiple items at once, so you get less "update storm" in the UI
        // compared to adding things one at a time.
    }

    /**
     * Worker thread takes in a subreddit name string, downloads its data, parses
     * out the threads, and communicates them back to the UI as they are read.
     */
    private class ThreadsWorker extends Thread {
        private CharSequence _mSubreddit;

        public ThreadsWorker(CharSequence subreddit) {
            _mSubreddit = subreddit;
        }

        @Override
        public void run() {
            try {
            	if (mClient == null)
            		setClient(new DefaultHttpClient());
            	
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(_mSubreddit.toString().trim())
            		.append("/.json").toString());
            	HttpResponse response = mClient.execute(request);
            	
            	// OK to dismiss the progress dialog when threads start loading
            	dismissDialog(DIALOG_LOADING_THREADS_LIST);
            	mIsProgressDialogShowing = false;

            	InputStream in = response.getEntity().getContent();
                
                parseSubredditJSON(in, mThreadsAdapter);
                
                mSubreddit = _mSubreddit;
            } catch (IOException e) {
            	dismissDialog(DIALOG_LOADING_THREADS_LIST);
            	mIsProgressDialogShowing = false;
            	log_e(TAG, "failed:" + e.getMessage());
            }
        }
    }
    
    
    private class ErrorToaster implements Runnable {
    	CharSequence _mError;
    	int _mDuration;
    	private LayoutInflater mInflater;
    	
    	ErrorToaster(CharSequence error, int duration) {
    		_mError = error;
    		_mDuration = duration;
    		mInflater = (LayoutInflater)RedditIsFun.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	}
    	
    	public void run() {
    		Toast t = new Toast(RedditIsFun.this);
    		t.setDuration(_mDuration);
    		View v = mInflater.inflate(R.layout.error_toast, null);
    		TextView errorMessage = (TextView) v.findViewById(R.id.errorMessage);
    		errorMessage.setText(_mError);
    		t.setView(v);
    		t.show();
    	}
    }


    
    /**
     * Worker thread that takes in a thingId, vote direction, and subreddit. Starts
     * a new HTTP Client, copying the main one's cookies, and votes.
     * @param username
     * @param password
     * @return
     */
    private class VoteWorker extends Thread{
    	private CharSequence _mThingId, _mSubreddit;
    	private int _mDirection;
    	
    	public VoteWorker(CharSequence thingId, int direction, CharSequence subreddit) {
    		_mThingId = thingId;
    		_mDirection = direction;
    		_mSubreddit = subreddit;
    	}
    	
    	@Override
    	public void run() {
	    	String status = "";
	    	if (!mLoggedIn) {
	    		return;
	    	}
	    	if (_mDirection < -1 || _mDirection > 1) {
	    		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
	    	}
	    	
	    	// Update the modhash if necessary
	    	if (mModhash == null) {
	    		if (!doUpdateModhash()) {
	    			// doUpdateModhash should have given an error about credentials
	    			return;
	    		}
	    	}
	    	
	    	try {
	    		// Create a new HttpClient and copy cookies over from the main one
	    		DefaultHttpClient client = new DefaultHttpClient();
	    		client.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 30000);
	    		List<Cookie> mainCookies = mClient.getCookieStore().getCookies();
	    		for (Cookie c : mainCookies) {
	    			client.getCookieStore().addCookie(c);
	    		}
	    		
	    		// Construct data
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair("id", _mThingId.toString()));
				nvps.add(new BasicNameValuePair("dir", String.valueOf(_mDirection)));
				nvps.add(new BasicNameValuePair("r", _mSubreddit.toString()));
				nvps.add(new BasicNameValuePair("uh", mModhash.toString()));
				// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
				
				HttpPost httppost = new HttpPost("http://www.reddit.com/api/vote");
		        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
		        
		        log_d(TAG, nvps.toString());
		        
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
	        		setModhash(null);
	        		mHandler.post(new ErrorToaster("Error voting. Please try again.", Toast.LENGTH_LONG));
	        		return;
	        	}
	        	
	        	log_d(TAG, line);

//    	        	// DEBUG
//    	        	int c;
//    	        	boolean done = false;
//    	        	StringBuilder sb = new StringBuilder();
//    	        	while ((c = in.read()) >= 0) {
//    	        		sb.append((char) c);
//    	        		for (int i = 0; i < 80; i++) {
//    	        			c = in.read();
//    	        			if (c < 0) {
//    	        				done = true;
//    	        				break;
//    	        			}
//    	        			sb.append((char) c);
//    	        		}
//    	        		log_d(TAG, "doLogin response content: " + sb.toString());
//    	        		sb = new StringBuilder();
//    	        		if (done)
//    	        			break;
//    	        	}

	        	in.close();
	        	if (entity != null)
	        		entity.consumeContent();
	        	
	    	} catch (Exception e) {
	            log_e(TAG, e.getMessage());
	    	}
	    	log_d(TAG, status);
	    }
    }
    
    /**
     * Login. Runs in the UI thread (synchronous).
     * FIXME: Make asynchronous
     * @param username
     * @param password
     * @return
     */
    public boolean doLogin(CharSequence username, CharSequence password) {
    	String status = "";
    	try {
    		// Construct data
    		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    		nvps.add(new BasicNameValuePair("user", username.toString()));
    		nvps.add(new BasicNameValuePair("passwd", password.toString()));
    		
            setClient(new DefaultHttpClient());
            mClient.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 20000);
            HttpPost httppost = new HttpPost("http://www.reddit.com/api/login/"+username);
            httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            
            showDialog(DIALOG_LOGGING_IN);
            
            // Perform the HTTP POST request
        	HttpResponse response = mClient.execute(httppost);
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
//        		log_d(TAG, "doLogin response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}
        	
        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
        	List<Cookie> cookies = mClient.getCookieStore().getCookies();
        	if (cookies.isEmpty()) {
        		throw new HttpException("Failed to login: No cookies");
        	}
        	for (Cookie c : cookies) {
        		if (c.getName().equals("reddit_session")) {
        			mRedditSessionCookie = c;
        			break;
        		}
        	}
        	
        	// Getting here means you successfully logged in.
        	// Congratulations!
        	// You are a true reddit master!
        
        	mUsername = username;
        	
        	mLoggedIn = true;
        	Toast.makeText(this, "Logged in as "+username, Toast.LENGTH_SHORT).show();
        	// Refresh the threads list
        	doGetThreadsList(mSubreddit);
        } catch (Exception e) {
            mHandler.post(new ErrorToaster("Error logging in. Please try again.", Toast.LENGTH_LONG));
        	mLoggedIn = false;
        }
        dismissDialog(DIALOG_LOGGING_IN);
        log_d(TAG, status);
        return mLoggedIn;
    }
    
    public boolean doUpdateModhash() {
    	if (!mLoggedIn) {
    		return false;
    	}
    	
    	// If logged in, client should exist. Otherwise logout and display error.
    	if (mClient == null) {
    		doLogout();
    		mHandler.post(new ErrorToaster("You have been logged out. Please login again.", Toast.LENGTH_LONG));
    		return false;
    	}
    	
    	try {
    		String status;
    		
    		HttpGet httpget = new HttpGet(MODHASH_URL);
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
        		setModhash(modhashMatcher.group(1));
        		if ("".equals(mModhash)) {
        			// Means user is not actually logged in.
        			doLogout();
        			mHandler.post(new ErrorToaster("You have been logged out. Please login again.", Toast.LENGTH_LONG));
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
//        		log_d(TAG, "doLogin response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}

        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
    	} catch (Exception e) {
    		log_e(TAG, e.getMessage());
    		mHandler.post(new ErrorToaster("Error performing action. Please try again.", Toast.LENGTH_LONG));
    		return false;
    	}
    	log_d(TAG, "modhash: "+mModhash);
    	return true;
    }
    
    public void doLogout() {
    	String status = "";
    	if (mClient != null) {
        	mClient.getCookieStore().clear();
    	}
        
    	mUsername = null;
        setClient(null);
        
        mLoggedIn = false;
        log_d(TAG, status);
    }
    
    public boolean doVote(CharSequence thingId, int direction, CharSequence subreddit) {
    	if (!mLoggedIn) {
    		mHandler.post(new ErrorToaster("You must be logged in to vote.", Toast.LENGTH_LONG));
    		return false;
    	}
    	if (direction < -1 || direction > 1) {
    		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
    	}
    	
    	// Update UI: 6 cases (3 original directions, each with 2 possible changes)
    	// UI is updated *before* the transaction actually happens. If the connection breaks for
    	// some reason, then the vote will be lost.
    	// Oh well, happens on reddit.com too, occasionally.
    	final ImageView ivUp = (ImageView) mVoteTargetView.findViewById(R.id.vote_up_image);
    	final ImageView ivDown = (ImageView) mVoteTargetView.findViewById(R.id.vote_down_image);
    	final TextView voteCounter = (TextView) mVoteTargetView.findViewById(R.id.votes);
		int newImageResourceUp, newImageResourceDown;
    	String newScore;
    	String newLikes;
    	int previousScore = Integer.valueOf(mVoteTargetThreadInfo.getScore());
    	if (TRUE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
    		if (direction == 0) {
    			newScore = String.valueOf(previousScore - 1);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = NULL_STRING;
    		} else if (direction == -1) {
    			newScore = String.valueOf(previousScore - 2);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_blue;
    			newLikes = FALSE_STRING;
    		} else {
    			return false;
    		}
    	} else if (FALSE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
    		if (direction == 1) {
    			newScore = String.valueOf(previousScore + 2);
    			newImageResourceUp = R.drawable.vote_up_red;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = TRUE_STRING;
    		} else if (direction == 0) {
    			newScore = String.valueOf(previousScore + 1);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = NULL_STRING;
    		} else {
    			return false;
    		}
    	} else {
    		if (direction == 1) {
    			newScore = String.valueOf(previousScore + 1);
    			newImageResourceUp = R.drawable.vote_up_red;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = TRUE_STRING;
    		} else if (direction == -1) {
    			newScore = String.valueOf(previousScore - 1);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_blue;
    			newLikes = FALSE_STRING;
    		} else {
    			return false;
    		}
    	}
    	ivUp.setImageResource(newImageResourceUp);
		ivDown.setImageResource(newImageResourceDown);
		voteCounter.setText(newScore);
		mVoteTargetThreadInfo.setLikes(newLikes);
		mVoteTargetThreadInfo.setScore(newScore);
		mThreadsAdapter.notifyDataSetChanged();
    	
    	VoteWorker worker = new VoteWorker(thingId, direction, subreddit);
    	setCurrentWorker(worker);
    	worker.start();
    	
    	return true;
    }
    

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(0, DIALOG_PICK_SUBREDDIT, 0, "Pick subreddit")
            .setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_PICK_SUBREDDIT));

        // Login and Logout need to use the same ID for menu entry so they can be swapped
        if (mLoggedIn) {
        	menu.add(0, DIALOG_LOGIN, 1, "Logout: " + mUsername)
       			.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGOUT));
        } else {
        	menu.add(0, DIALOG_LOGIN, 1, "Login")
       			.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGIN));
        }
        
        menu.add(0, DIALOG_REFRESH, 2, "Refresh")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_REFRESH));
        
        menu.add(0, DIALOG_POST_THREAD, 3, "Post Thread")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_POST_THREAD));
        
        if (mTheme == THEME_LIGHT) {
        	menu.add(0, DIALOG_THEME, 4, "Dark")
//        		.setIcon(R.drawable.dark_circle_menu_icon)
        		.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_THEME));
        } else {
        	menu.add(0, DIALOG_THEME, 4, "Light")
//	    		.setIcon(R.drawable.light_circle_menu_icon)
	    		.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_THEME));
        }
        
        menu.add(0, DIALOG_OPEN_BROWSER, 5, "Open in browser")
        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_OPEN_BROWSER));
        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	super.onPrepareOptionsMenu(menu);
    	
    	// Login/Logout
    	if (mLoggedIn) {
	        menu.findItem(DIALOG_LOGIN).setTitle("Logout: " + mUsername)
	        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGOUT));
    	} else {
            menu.findItem(DIALOG_LOGIN).setTitle("Login")
            	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGIN));
    	}
    	
    	// Theme: Light/Dark
    	if (mTheme == THEME_LIGHT) {
    		menu.findItem(DIALOG_THEME).setTitle("Dark");
//    			.setIcon(R.drawable.dark_circle_menu_icon);
    	} else {
    		menu.findItem(DIALOG_THEME).setTitle("Light");
//    			.setIcon(R.drawable.light_circle_menu_icon);
    	}
        
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
        	case DIALOG_LOGIN:
        	case DIALOG_POST_THREAD:
        		showDialog(mAction);
        		break;
        	case DIALOG_PICK_SUBREDDIT:
        		Intent pickSubredditIntent = new Intent(RedditIsFun.this, PickSubredditActivity.class);
        		startActivityForResult(pickSubredditIntent, ACTIVITY_PICK_SUBREDDIT);
        		break;
        	case DIALOG_OPEN_BROWSER:
        		String url = new StringBuilder("http://www.reddit.com/r/")
        			.append(mSubreddit).toString();
        		RedditIsFun.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        		break;
            case DIALOG_LOGOUT:
        		doLogout();
        		Toast.makeText(RedditIsFun.this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		doGetThreadsList(mSubreddit);
        		break;
        	case DIALOG_REFRESH:
        		doGetThreadsList(mSubreddit);
        		break;
        	case DIALOG_THEME:
        		if (mTheme == THEME_LIGHT) {
        			mTheme = THEME_DARK;
        			mThemeResId = android.R.style.Theme;
        		} else {
        			mTheme = THEME_LIGHT;
        			mThemeResId = android.R.style.Theme_Light;
        		}
        		RedditIsFun.this.setTheme(mThemeResId);
        		RedditIsFun.this.setContentView(R.layout.comments_list_content);
        		RedditIsFun.this.getListView().setAdapter(mThreadsAdapter);
        		break;
        	default:
        		throw new IllegalArgumentException("Unexpected action value "+mAction);
        	}
        	
        	return true;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
    	AlertDialog.Builder alertBuilder;
    	
    	switch (id) {
    	case DIALOG_LOGIN:
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
    		        	doLogin(loginUsernameInput.getText(), loginPasswordInput.getText());
    		            dismissDialog(DIALOG_LOGIN);
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		loginButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				doLogin(loginUsernameInput.getText(), loginPasswordInput.getText());
    		        dismissDialog(DIALOG_LOGIN);
    		    }
    		});
    		break;
    		
    	case DIALOG_THREAD_CLICK:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.thread_click_dialog);
    		dialog.findViewById(R.id.thread_vote_up_button);
    		dialog.findViewById(R.id.thread_vote_down_button);
    		dialog.setTitle("Thread:");
    		
    		break;

    	case DIALOG_POST_THREAD:
    		// TODO: a scrollable Dialog with Title, URL/Selftext, and subreddit.
    		// Or one of those things that pops up at bottom of screen, like browser "Find on page"
    		alertBuilder = new AlertDialog.Builder(this);
    		alertBuilder.setMessage("Sorry, this feature isn't implemented yet. Open in browser instead.")
    				.setCancelable(true)
    				.setPositiveButton("OK", null);
    		dialog = alertBuilder.create();
    		break;
    		
   		// "Please wait"
    	case DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Logging in...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case DIALOG_LOADING_THREADS_LIST:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Loading subreddit...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case DIALOG_LOADING_LOOK_OF_DISAPPROVAL:
    		pdialog = new ProgressDialog(this);
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		pdialog.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    		pdialog.setFeatureDrawableResource(Window.FEATURE_INDETERMINATE_PROGRESS, R.drawable.look_of_disapproval);
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
    	case DIALOG_LOGIN:
    		if (mUsername != null) {
	    		final TextView loginUsernameInput = (TextView) dialog.findViewById(R.id.login_username_input);
	    		loginUsernameInput.setText(mUsername);
    		}
    		break;
    		
    	case DIALOG_THREAD_CLICK:
    		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.thread_vote_up_button);
    		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.thread_vote_down_button);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
    		final Button commentsButton = (Button) dialog.findViewById(R.id.thread_comments_button);
    		
    		urlView.setText(mTargetURL);

    		// Only show upvote/downvote if user is logged in
    		if (mLoggedIn) {
    			voteUpButton.setVisibility(View.VISIBLE);
    			voteDownButton.setVisibility(View.VISIBLE);
    			// Set initial states of the vote buttons based on user's past actions
	    		if (TRUE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
	    			// User currenty likes it
	    			voteUpButton.setChecked(true);
	    			voteDownButton.setChecked(false);
	    		} else if (FALSE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
	    			// User currently dislikes it
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(true);
	    		} else {
	    			// User is currently neutral
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(false);
	    		}
	    		voteUpButton.setOnCheckedChangeListener(new VoteUpOnCheckedChangeListener());
	    		voteDownButton.setOnCheckedChangeListener(new VoteDownOnCheckedChangeListener());
    		} else {
    			voteUpButton.setVisibility(View.INVISIBLE);
    			voteDownButton.setVisibility(View.INVISIBLE);
    		}

    		// The "link" and "comments" buttons
    		OnClickListener commentsOnClickListener = new OnClickListener() {
    			public void onClick(View v) {
    				dismissDialog(DIALOG_THREAD_CLICK);
    				// Launch an Intent for RedditCommentsListActivity
    				Intent i = new Intent(RedditIsFun.this, RedditCommentsListActivity.class);
    				i.putExtra(ThreadInfo.SUBREDDIT, mSubreddit);
    				i.putExtra(ThreadInfo.ID, mThingId);
    				startActivity(i);
        		}
    		};
    		commentsButton.setOnClickListener(commentsOnClickListener);
    		// TODO: Handle bestof posts, which aren't self posts
            if (("self."+mSubreddit).toLowerCase().equals(mTargetDomain.toString().toLowerCase())) {
            	// It's a self post. Both buttons do the same thing.
            	linkButton.setOnClickListener(commentsOnClickListener);
            } else {
            	linkButton.setOnClickListener(new OnClickListener() {
            		public void onClick(View v) {
            			dismissDialog(DIALOG_THREAD_CLICK);
            			// Launch Intent to goto the URL
            			RedditIsFun.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mTargetURL.toString())));
            		}
            	});
            }
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
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
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Make a List of all the ThreadItem data for saving
        // NOTE: there may be a way to save the ThreadItems directly,
        // rather than their string data.
        int count = mThreadsAdapter.getCount();

        // Save out the items as a flat list of CharSequence objects --
        // title0, link0, descr0, title1, link1, ...
        ArrayList<CharSequence> strings = new ArrayList<CharSequence>();
        for (int i = 0; i < count; i++) {
            ThreadInfo item = mThreadsAdapter.getItem(i);
            for (int k = 0; k < ThreadInfo.SAVE_KEYS.length; k++) {
            	if (item.mValues.containsKey(ThreadInfo.SAVE_KEYS[k])) {
            		strings.add(ThreadInfo.SAVE_KEYS[k]);
            		strings.add(item.mValues.get(ThreadInfo.SAVE_KEYS[k]));
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
        mThreadsAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mThreadsAdapter);

        // Restore selection
        if (state.containsKey(SELECTION_KEY)) {
            getListView().requestFocus(View.FOCUS_FORWARD);
            // todo: is above right? needed it to work
            getListView().setSelection(state.getInt(SELECTION_KEY));
        }
        
        if (mIsProgressDialogShowing) {
        	dismissDialog(DIALOG_LOADING_THREADS_LIST);
        	mIsProgressDialogShowing = false;
        }
    }


    void parseSubredditJSON(InputStream in, ThreadsListAdapter adapter) throws IOException,
		    JsonParseException, IllegalStateException {
		
		JsonParser jp = jsonFactory.createJsonParser(in);
		
		if (jp.nextToken() == JsonToken.VALUE_NULL)
			return;
		
		// --- Validate initial stuff, skip to the JSON List of threads ---
    	String genericListingError = "Not a subreddit listing";
//    	if (JsonToken.START_OBJECT != jp.nextToken()) // starts with "{"
//    		throw new IllegalStateException(genericListingError);
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
    	
		// --- Main parsing ---
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
						} else if ("media_embed".equals(namefield) && jp.getCurrentToken() == JsonToken.START_OBJECT) {
							while (jp.nextToken() != JsonToken.END_OBJECT) {
								String mediaNamefield = jp.getCurrentName();
								jp.nextToken(); // move to value
								ti.put("_media_embed_"+mediaNamefield, jp.getText());
							}
						} else {
							ti.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText()));
						}
					}
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
			mHandler.post(new ThreadItemAdder(ti));
		}
	}
}
