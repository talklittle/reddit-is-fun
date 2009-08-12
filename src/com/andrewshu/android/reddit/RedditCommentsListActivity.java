package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
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
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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

/**
 * Main Activity class representing a Subreddit, i.e., a ThreadsList.
 * 
 * @author TalkLittle
 *
 */
public final class RedditCommentsListActivity extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "RedditCommentsListActivity";
	
	public final String COMMENT_KIND = "t1";
	public final String THREAD_KIND = "t3";
	public final String SERIALIZE_SEPARATOR = "\r";
	
    private final JsonFactory jsonFactory = new JsonFactory(); 
    static int mNestedCommentsJSONOrder = 0;
	
    /** Custom list adapter that fits our threads data into the list. */
    private CommentsListAdapter mCommentsAdapter;
    /** Handler used to post runnables to the UI thread. */
    private Handler mHandler;
    /** Currently running background network thread. */
    private Thread mWorker;
    
    private ThreadInfo mOpThreadInfo;
    private TreeMap<Integer, CommentInfo> mCommentsMap = new TreeMap<Integer, CommentInfo>();
    
    private Menu mMenu;
    private int mMode = MODE_THREADS_LIST;
    
    static final int MODE_THREADS_LIST  = 0x00000001;
    static final int MODE_COMMENTS_LIST = 0x00000002;

    // Current HttpClient
    private DefaultHttpClient mClient = null;
    private Cookie mRedditSessionCookie = null;
    
    // UI State
    private CharSequence mSubreddit = null;  // Should remain constant for the life of this instance
    private CharSequence mThreadId = null;   // Should remain constant for the life of this instance
    private CharSequence mThingId = null;
    private CharSequence mTargetURL = null;
    private View mVoteTargetView = null;
    private CommentInfo mVoteTargetCommentInfo = null;
    
    // Login status
    private boolean mLoggedIn = false;
    private CharSequence mUsername = null;
    private CharSequence mModhash = null;
    
    // Menu dialog actions
    static final int DIALOG_THING_CLICK = 0;
    static final int DIALOG_LOGIN = 2;
    static final int DIALOG_LOGOUT = 3;
    static final int DIALOG_REFRESH = 4;
    static final int DIALOG_POST_THREAD = 5;
    static final int DIALOG_LOGGING_IN = 7;
    static final int DIALOG_LOADING_THREADS_LIST = 8;
    static final int DIALOG_LOADING_COMMENTS_LIST = 9;
    
    // Themes
    static final int THEME_LIGHT = 0;
    static final int THEME_DARK = 1;
    
    private int mTheme = 0;
    
    // States for StateListDrawables
    static final int[] STATE_CHECKED = new int[]{android.R.attr.state_checked};
    static final int[] STATE_NONE = new int[0];
    
    // Strings
    static final String TRUE_STRING = "true";
    static final String FALSE_STRING = "false";
    static final String NULL_STRING = "null";
    static final String EMPTY_STRING = "";
    
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
        
        setContentView(R.layout.comments_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().
        
        // Retrieve the stored session info
        SharedPreferences sessionPrefs = getSharedPreferences(RedditIsFun.PREFS_SESSION, 0);
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

        // Pull current subreddit and thread info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	mThreadId = extras.getString(ThreadInfo.ID);
        	mSubreddit = extras.getString(ThreadInfo.SUBREDDIT);
        } else {
        	// Quit, because the Comments List requires subreddit and thread id from Intent.
        	Log.e(TAG, "Quitting because no subreddit and thread id data was passed into the Intent.");
        	finish();
        }
    }
    
    @Override
    protected void onStart() {
    	super.onStart();
    	
        List<CommentInfo> commentItems = new ArrayList<CommentInfo>();
        mCommentsAdapter = new CommentsListAdapter(this, commentItems);
        getListView().setAdapter(mCommentsAdapter);

        // Need one of these to post things back to the UI thread.
        mHandler = new Handler();
        
        doGetCommentsList(mSubreddit, mThreadId);
    }
    
    public class VoteUpOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(DIALOG_THING_CLICK);
			if (isChecked)
				doVote(mThingId, 1, mSubreddit);
			else
				doVote(mThingId, 0, mSubreddit);
		}
    }
    
    public class VoteDownOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(DIALOG_THING_CLICK);
			if (isChecked)
				doVote(mThingId, -1, mSubreddit);
			else
				doVote(mThingId, 0, mSubreddit);
		}
    }


    private final class CommentsListAdapter extends ArrayAdapter<CommentInfo> {
    	static final int OP_ITEM_VIEW_TYPE = 0;
    	static final int COMMENT_ITEM_VIEW_TYPE = 1;
    	
    	private LayoutInflater mInflater;
        private boolean mLoading = true;
        private boolean mDisplayThumbnails = false; // TODO: use this
        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null; // TODO?: use this?
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;

        
        public CommentsListAdapter(Context context, List<CommentInfo> objects) {
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
        	if (position == 0) {
        		return OP_ITEM_VIEW_TYPE;
        	}
            if (position == mFrequentSeparatorPos) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
            return COMMENT_ITEM_VIEW_TYPE;
        }
        
        @Override
        public int getViewTypeCount() {
        	return 2;
        }

        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            Resources res = getResources();
            
            CommentInfo item = this.getItem(position);
            
            // TODO?: replace this logic with ListView.addHeaderView()
            try {
	            if (position == 0) {
	            	// The OP is rendered as a WebView
	            	if (convertView == null) {
	            		view = mInflater.inflate(R.layout.threads_list_item_expanded, null);
	            	} else {
	            		view = convertView;
	            	}
	            	
	            	// --- Copied from ThreadsListAdapter ---
	
	                // Set the values of the Views for the CommentsListItem
	                
	                TextView titleView = (TextView) view.findViewById(R.id.title);
	                TextView votesView = (TextView) view.findViewById(R.id.votes);
	                TextView linkDomainView = (TextView) view.findViewById(R.id.linkDomain);
	                TextView numCommentsView = (TextView) view.findViewById(R.id.numComments);
	                TextView submitterView = (TextView) view.findViewById(R.id.submitter);
	//                TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
	                ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
	                ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
	                WebView selftextView = (WebView) view.findViewById(R.id.selftext);
	                
	                titleView.setText(mOpThreadInfo.getTitle());
	                if (mTheme == THEME_LIGHT) {
	    	            if (TRUE_STRING.equals(mOpThreadInfo.getClicked()))
	    	            	titleView.setTextColor(res.getColor(R.color.purple));
	    	            else
	    	            	titleView.setTextColor(res.getColor(R.color.blue));
	                }
	                votesView.setText(mOpThreadInfo.getScore());
	                linkDomainView.setText("("+mOpThreadInfo.getDomain()+")");
	                numCommentsView.setText(mOpThreadInfo.getNumComments());
	                submitterView.setText("submitted by "+mOpThreadInfo.getAuthor());
	                // TODO: convert submission time to a displayable time
	//                Date submissionTimeDate = new Date((long) (Double.parseDouble(mOp.getCreated()) / 1000));
	//                submissionTimeView.setText("XXX");
	                titleView.setTag(mOpThreadInfo.getURL());
	
	                // Set the up and down arrow colors based on whether user likes
	                if (mLoggedIn) {
	                	if (TRUE_STRING.equals(mOpThreadInfo.getLikes())) {
	                		voteUpView.setImageResource(R.drawable.vote_up_red);
	                		voteDownView.setImageResource(R.drawable.vote_down_gray);
	                		votesView.setTextColor(res.getColor(R.color.arrow_red));
	                	} else if (FALSE_STRING.equals(mOpThreadInfo.getLikes())) {
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
	                
	                // --- End part copied from ThreadsListAdapter ---
	                
	            	if (!NULL_STRING.equals(mOpThreadInfo.getSelftext())) {
	            		selftextView.getSettings().setTextSize(TextSize.SMALLER);
	            		selftextView.loadData(StringEscapeUtils.unescapeHtml(mOpThreadInfo.getSelftext()), "text/html", "UTF-8");
	            	} else {
	            		selftextView.setVisibility(View.INVISIBLE);
	            	}
	            } else {
		            // Here view may be passed in for re-use, or we make a new one.
		            if (convertView == null) {
		                view = mInflater.inflate(R.layout.comments_list_item, null);
		            } else {
		                view = convertView;
		            }
		            
		            // Set the values of the Views for the CommentsListItem
		            
		            TextView votesView = (TextView) view.findViewById(R.id.votes);
		            TextView submitterView = (TextView) view.findViewById(R.id.submitter);
		            TextView bodyView = (TextView) view.findViewById(R.id.body);
		            TextView leftIndent = (TextView) view.findViewById(R.id.left_indent);
		            
	//                TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
		            ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
		            ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
		            
		            try {
		            	votesView.setText(String.valueOf(
		            			Integer.valueOf(item.getUps()) - Integer.valueOf(item.getDowns())
		            			) + " points");
		            } catch (NumberFormatException e) {
		            	// This happens because "ups" comes after the potentially long "replies" object,
		            	// so the ListView might try to display the View before "ups" in JSON has been parsed.
		            	Log.e(TAG, e.getMessage());
		            }
		            submitterView.setText(item.getAuthor());
		            bodyView.setText(item.getBody());
		            switch (item.getIndent()) {
		            case 0:  leftIndent.setText(""); break;
		            case 1:  leftIndent.setText("w"); break;
		            case 2:  leftIndent.setText("ww"); break;
		            case 3:  leftIndent.setText("www"); break;
		            case 4:  leftIndent.setText("wwww"); break;
		            case 5:  leftIndent.setText("wwwww"); break;
		            case 6:  leftIndent.setText("wwwwww"); break;
		            case 7:  leftIndent.setText("wwwwwww"); break;
		            default: leftIndent.setText("wwwwwww"); break;
		            }
		            
		//            submitterView.setText(item.getAuthor());
		            // TODO: convert submission time to a displayable time
		//            Date submissionTimeDate = new Date((long) (Double.parseDouble(item.getCreated()) / 1000));
		//            submissionTimeView.setText("XXX");
		            
		            // Set the up and down arrow colors based on whether user likes
		            if (mLoggedIn) {
		            	if (TRUE_STRING.equals(item.getLikes())) {
		            		voteUpView.setImageResource(R.drawable.vote_up_red);
		            		voteDownView.setImageResource(R.drawable.vote_down_gray);
//		            		votesView.setTextColor(res.getColor(R.color.arrow_red));
		            	} else if (FALSE_STRING.equals(item.getLikes())) {
		            		voteUpView.setImageResource(R.drawable.vote_up_gray);
		            		voteDownView.setImageResource(R.drawable.vote_down_blue);
//		            		votesView.setTextColor(res.getColor(R.color.arrow_blue));
		            	} else {
		            		voteUpView.setImageResource(R.drawable.vote_up_gray);
		            		voteDownView.setImageResource(R.drawable.vote_down_gray);
//		            		votesView.setTextColor(res.getColor(R.color.gray));
		            	}
		            } else {
		        		voteUpView.setImageResource(R.drawable.vote_up_gray);
		        		voteDownView.setImageResource(R.drawable.vote_down_gray);
//		        		votesView.setTextColor(res.getColor(R.color.gray));
		            }
	            }
            } catch (NullPointerException e) {
            	// Probably means that the List is still being built, and OP probably got put in wrong position
            	if (convertView == null) {
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
        CommentInfo item = mCommentsAdapter.getItem(position);
        
        // Mark the OP post/regular comment as selected
        if (item.getOP() != null)
        	mThingId = item.getOP().getName();
        else
        	mThingId = item.getName();
        mVoteTargetCommentInfo = item;
        mVoteTargetView = v;
        
       	showDialog(DIALOG_THING_CLICK);
    }

    /**
     * Resets the output UI list contents, retains session state.
     */
    public void resetUI() {
        // Reset the list to be empty.
        List<CommentInfo> items = new ArrayList<CommentInfo>();
        mCommentsAdapter = new CommentsListAdapter(this, items);
        getListView().setAdapter(mCommentsAdapter);
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

    public synchronized void setModhash(CharSequence modhash) {
    	mModhash = modhash;
    }
    
    public synchronized void setClient(DefaultHttpClient client) {
    	mClient = client;
    }


    /**
     * Given a subreddit name string and thread id36, starts the commentslist-download-thread going.
     */
    private void doGetCommentsList(CharSequence subreddit, CharSequence id36) {
    	if (subreddit == null || id36 == null)
    		return;
    	
    	CommentsWorker worker = new CommentsWorker(subreddit, id36);
    	setCurrentWorker(worker);
    	
    	resetUI();
    	showDialog(DIALOG_LOADING_COMMENTS_LIST);
    	
    	setTitle("/r/"+subreddit.toString().trim());
    	
    	worker.start();
    }

    /**
     * Runnable that the worker thread uses to post CommentItems to the
     * UI via mHandler.post
     */
    private class CommentItemAdder implements Runnable {
        CommentInfo mItem;

        CommentItemAdder(CommentInfo item) {
            mItem = item;
        }

        public void run() {
            mCommentsAdapter.add(mItem);
        }

        // NOTE: Performance idea -- would be more efficient to have he option
        // to add multiple items at once, so you get less "update storm" in the UI
        // compared to adding things one at a time.
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
	    		// TODO: Error dialog saying you must be logged in.
	    		return;
	    	}
	    	if (_mDirection < -1 || _mDirection > 1) {
	    		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
	    	}
	    	
	    	// Update the modhash if necessary
	    	if (mModhash == null) {
	    		if (!doUpdateModhash()) {
	    			// doUpdateModhash should have given an error about credentials
	    			throw new RuntimeException("Vote failed because doUpdateModhash() failed");
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
		        
		        Log.d(TAG, nvps.toString());
		        
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
	        		// TODO: "Error voting. Try again."
	        		return;
	        	}
	        	
	        	Log.d(TAG, line);

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
//    	        		Log.d(TAG, "doLogin response content: " + sb.toString());
//    	        		sb = new StringBuilder();
//    	        		if (done)
//    	        			break;
//    	        	}

	        	in.close();
	        	if (entity != null)
	        		entity.consumeContent();
	        	
	    	} catch (Exception e) {
	            Log.e(TAG, e.getMessage());
	    	}
	    	Log.d(TAG, status);
	    }
    }
    
    /**
     * Worker thread takes in a subreddit name string and thread id, downloads its data, parses
     * out the comments, and communicates them back to the UI as they are read.
     */
    private class CommentsWorker extends Thread {
        private CharSequence _mSubreddit, _mId36;

        public CommentsWorker(CharSequence subreddit, CharSequence id36) {
            _mSubreddit = subreddit;
            _mId36 = id36;
        }

        @Override
        public void run() {
            try {
            	if (mClient == null)
            		setClient(new DefaultHttpClient());
            	
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(_mSubreddit.toString().trim())
            		.append("/comments/")
            		.append(_mId36)
            		.append("/.json").toString());
            	HttpResponse response = mClient.execute(request);
            	
            	InputStream in = response.getEntity().getContent();
                
                parseCommentsJSON(in, mCommentsAdapter);
                
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
//        		Log.d(TAG, "doLogin response content: " + sb.toString());
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
        	
        	// Getting here means you successfully logged in.
        	// Congratulations!
        	// You are a true reddit master!
        
        	mUsername = username;
        	
        	mLoggedIn = true;
        	Toast.makeText(this, "Logged in as "+username, Toast.LENGTH_SHORT).show();
        	
            mMenu.findItem(DIALOG_LOGIN).setTitle("Logout")
            	.setOnMenuItemClickListener(new CommentsListMenu(DIALOG_LOGOUT));
        } catch (Exception e) {
            // TODO: Login error message
        	mLoggedIn = false;
        }
        dismissDialog(DIALOG_LOGGING_IN);
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
        		setModhash(modhashMatcher.group(1));
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
    	String status = "";
    	if (mClient != null) {
        	mClient.getCookieStore().clear();
    	}
        
    	mUsername = null;
        setClient(null);
        
        mLoggedIn = false;
        mMenu.findItem(DIALOG_LOGIN).setTitle("Login")
        	.setOnMenuItemClickListener(new CommentsListMenu(DIALOG_LOGIN));;
        Log.d(TAG, status);
    }
    
    public boolean doVote(CharSequence thingId, int direction, CharSequence subreddit) {
    	if (!mLoggedIn) {
    		// TODO: Error dialog saying you must be logged in.
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
    	String newLikes;
    	int previousUps = Integer.valueOf(mVoteTargetCommentInfo.getUps());
    	int previousDowns = Integer.valueOf(mVoteTargetCommentInfo.getDowns());
    	int newUps = previousUps;
    	int newDowns = previousDowns;
    	if (TRUE_STRING.equals(mVoteTargetCommentInfo.getLikes())) {
    		if (direction == 0) {
    			newUps = previousUps - 1;
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = NULL_STRING;
    		} else if (direction == -1) {
    			newUps = previousUps - 1;
    			newDowns = previousDowns + 1;
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_blue;
    			newLikes = FALSE_STRING;
    		} else {
    			return false;
    		}
    	} else if (FALSE_STRING.equals(mVoteTargetCommentInfo.getLikes())) {
    		if (direction == 1) {
    			newUps = previousUps + 1;
    			newDowns = previousDowns - 1;
    			newImageResourceUp = R.drawable.vote_up_red;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = TRUE_STRING;
    		} else if (direction == 0) {
    			newDowns = previousDowns - 1;
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = NULL_STRING;
    		} else {
    			return false;
    		}
    	} else {
    		if (direction == 1) {
    			newUps = previousUps + 1;
    			newImageResourceUp = R.drawable.vote_up_red;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = TRUE_STRING;
    		} else if (direction == -1) {
    			newDowns = previousDowns + 1;
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_blue;
    			newLikes = FALSE_STRING;
    		} else {
    			return false;
    		}
    	}
    	
    	ivUp.setImageResource(newImageResourceUp);
		ivDown.setImageResource(newImageResourceDown);
		String newScore = String.valueOf(newUps - newDowns);
		voteCounter.setText(newScore + " points");
		mVoteTargetCommentInfo.setLikes(newLikes);
		mVoteTargetCommentInfo.setUps(String.valueOf(newUps));
		mVoteTargetCommentInfo.setDowns(String.valueOf(newDowns));
		mCommentsAdapter.notifyDataSetChanged();
    	
    	VoteWorker worker = new VoteWorker(thingId, direction, subreddit);
    	setCurrentWorker(worker);
    	worker.start();
    	
    	return true;
    }
    
    public boolean doComment(CharSequence parentThingId, CharSequence subreddit) {
//    	if (!mLoggedIn) {
//    		// TODO: Error dialog saying you must be logged in.
//    		return false;
//    	}
//    	if (direction < -1 || direction > 1) {
//    		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
//    	}
//    	
//    	// Update UI: 6 cases (3 original directions, each with 2 possible changes)
//    	// UI is updated *before* the transaction actually happens. If the connection breaks for
//    	// some reason, then the vote will be lost.
//    	// Oh well, happens on reddit.com too, occasionally.
//    	final ImageView ivUp = (ImageView) mVoteTargetView.findViewById(R.id.vote_up_image);
//    	final ImageView ivDown = (ImageView) mVoteTargetView.findViewById(R.id.vote_down_image);
//    	final TextView voteCounter = (TextView) mVoteTargetView.findViewById(R.id.votes);
//		int newImageResourceUp, newImageResourceDown;
//    	String newScore;
//    	String newLikes;
//    	int previousScore = Integer.valueOf(mVoteTargetThreadInfo.getScore());
//    	if (TRUE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
//    		if (direction == 0) {
//    			newScore = String.valueOf(previousScore - 1);
//    			newImageResourceUp = R.drawable.vote_up_gray;
//    			newImageResourceDown = R.drawable.vote_down_gray;
//    			newLikes = NULL_STRING;
//    		} else if (direction == -1) {
//    			newScore = String.valueOf(previousScore - 2);
//    			newImageResourceUp = R.drawable.vote_up_gray;
//    			newImageResourceDown = R.drawable.vote_down_blue;
//    			newLikes = FALSE_STRING;
//    		} else {
//    			return false;
//    		}
//    	} else if (FALSE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
//    		if (direction == 1) {
//    			newScore = String.valueOf(previousScore + 2);
//    			newImageResourceUp = R.drawable.vote_up_red;
//    			newImageResourceDown = R.drawable.vote_down_gray;
//    			newLikes = TRUE_STRING;
//    		} else if (direction == 0) {
//    			newScore = String.valueOf(previousScore + 1);
//    			newImageResourceUp = R.drawable.vote_up_gray;
//    			newImageResourceDown = R.drawable.vote_down_gray;
//    			newLikes = NULL_STRING;
//    		} else {
//    			return false;
//    		}
//    	} else {
//    		if (direction == 1) {
//    			newScore = String.valueOf(previousScore + 1);
//    			newImageResourceUp = R.drawable.vote_up_red;
//    			newImageResourceDown = R.drawable.vote_down_gray;
//    			newLikes = TRUE_STRING;
//    		} else if (direction == -1) {
//    			newScore = String.valueOf(previousScore - 1);
//    			newImageResourceUp = R.drawable.vote_up_gray;
//    			newImageResourceDown = R.drawable.vote_down_blue;
//    			newLikes = FALSE_STRING;
//    		} else {
//    			return false;
//    		}
//    	}
//    	
//    	ivUp.setImageResource(newImageResourceUp);
//		ivDown.setImageResource(newImageResourceDown);
//		voteCounter.setText(newScore);
//		mVoteTargetThreadInfo.setLikes(newLikes);
//		mVoteTargetThreadInfo.setScore(newScore);
//		mAdapter.notifyDataSetChanged();
//    	
//    	VoteWorker worker = new VoteWorker(thingId, direction, subreddit);
//    	setCurrentWorker(worker);
//    	worker.start();
//    	
    	return true;
    }

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenu = menu;
        
        // TODO: Menu
//        menu.add(0, DIALOG_PICK_SUBREDDIT, 0, "Pick subreddit")
//            .setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_PICK_SUBREDDIT));
//
//        menu.add(0, DIALOG_REDDIT_COM, 0, "reddit.com")
//            .setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_REDDIT_COM));
//
//        menu.add(0, DIALOG_REFRESH, 0, "Refresh")
//        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_REFRESH));
//        
//        // Login and Logout need to use the same ID for menu entry so they can be swapped
//        if (mLoggedIn) {
//        	menu.add(0, DIALOG_LOGIN, 0, "Logout")
//       			.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGOUT));
//        } else {
//        	menu.add(0, DIALOG_LOGIN, 0, "Login")
//       			.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_LOGIN));
//        }
//        
//        menu.add(0, DIALOG_POST_THREAD, 0, "Post Thread")
//        	.setOnMenuItemClickListener(new ThreadsListMenu(DIALOG_POST_THREAD));
        
        return true;
    }

    /**
     * Puts text in the url text field and gives it focus. Used to make a Runnable
     * for each menu item. This way, one inner class works for all items vs. an
     * anonymous inner class for each menu item.
     */
    private class CommentsListMenu implements MenuItem.OnMenuItemClickListener {
        private int mAction;

        CommentsListMenu(int action) {
            mAction = action;
        }

        public boolean onMenuItemClick(MenuItem item) {
        	// TODO: Menu
//        	switch (mAction) {
//        	case DIALOG_PICK_SUBREDDIT:
//        	case DIALOG_LOGIN:
//        	case DIALOG_POST_THREAD:
//        		showDialog(mAction);
//        		break;
//        	case DIALOG_REDDIT_COM:
//        		doGetThreadsList("reddit.com");
//        		break;
//        	case DIALOG_LOGOUT:
//        		doLogout();
//        		Toast.makeText(RedditCommentsListActivity.this, "You have been logged out.", Toast.LENGTH_SHORT).show();
//        		doGetThreadsList(mSubreddit);
//        		break;
//        	case DIALOG_REFRESH:
//        		doGetThreadsList(mSubreddit);
//        		break;
//        	default:
//        		throw new IllegalArgumentException("Unexpected action value "+mAction);
//        	}
        	
        	return true;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
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
    		
    	case DIALOG_THING_CLICK:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.comment_click_dialog);
    		
    		break;

    	case DIALOG_POST_THREAD:
    		// TODO: a scrollable Dialog with Title, URL/Selftext, and subreddit.
    		// Or one of those things that pops up at bottom of screen, like browser "Find on page"
    		dialog = null;
    		break;
    		
   		// "Please wait"
    	case DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Logging in...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case DIALOG_LOADING_COMMENTS_LIST:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Loading comments...");
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
    	case DIALOG_THING_CLICK:
    		if (mVoteTargetCommentInfo.getOP() != null)
    			dialog.setTitle("OP:");
    		else
    			dialog.setTitle("Comment:");
    		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.comment_vote_up_button);
    		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.comment_vote_down_button);
    		final Button replyButton = (Button) dialog.findViewById(R.id.reply_button);
    		
    		// Only show upvote/downvote if user is logged in
    		if (mLoggedIn) {
    			voteUpButton.setVisibility(View.VISIBLE);
    			voteDownButton.setVisibility(View.VISIBLE);
    			// Set initial states of the vote buttons based on user's past actions
	    		if (TRUE_STRING.equals(mVoteTargetCommentInfo.getLikes())) {
	    			// User currenty likes it
	    			voteUpButton.setChecked(true);
	    			voteDownButton.setChecked(false);
	    		} else if (FALSE_STRING.equals(mVoteTargetCommentInfo.getLikes())) {
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

	    		// The "reply" button
	    		OnClickListener replyOnClickListener = new OnClickListener() {
	    			public void onClick(View v) {
	    				mMode = MODE_COMMENTS_LIST;
	    				// TODO: Post comment reply
	        		}
	    		};
    		} else {
    			// TODO: "login" button.
    			voteUpButton.setVisibility(View.INVISIBLE);
    			voteDownButton.setVisibility(View.INVISIBLE);
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
    @SuppressWarnings("unchecked")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Make a List of all the ThreadItem data for saving
        // NOTE: there may be a way to save the ThreadItems directly,
        // rather than their string data.
        int count = mCommentsAdapter.getCount();

        // Save out the items as a flat list of CharSequence objects --
        // title0, link0, descr0, title1, link1, ...
        ArrayList<CharSequence> strings = new ArrayList<CharSequence>();
        for (int i = 0; i < count; i++) {
            CommentInfo item = mCommentsAdapter.getItem(i);
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
        List<CommentInfo> items = new ArrayList<CommentInfo>();
        for (int i = 0; i < strings.size(); i++) {
        	CommentInfo ci = new CommentInfo();
        	CharSequence key, value;
        	while (!SERIALIZE_SEPARATOR.equals(strings.get(i))) {
        		if (SERIALIZE_SEPARATOR.equals(strings.get(i+1))) {
        			// Well, just skip the value instead of throwing an exception.
        			break;
        		}
        		key = strings.get(i);
        		value = strings.get(i+1);
        		ci.put(key.toString(), value.toString());
        		i += 2;
        	}
            items.add(ci);
        }

        // Reset the list view to show this data.
        mCommentsAdapter = new CommentsListAdapter(this, items);
        getListView().setAdapter(mCommentsAdapter);

        // Restore selection
        if (state.containsKey(SELECTION_KEY)) {
            getListView().requestFocus(View.FOCUS_FORWARD);
            // todo: is above right? needed it to work
            getListView().setSelection(state.getInt(SELECTION_KEY));
        }
        
        // Close any ProgressDialogs
        dismissDialog(DIALOG_LOADING_THREADS_LIST);
    }


    
    void parseCommentsJSON(InputStream in, CommentsListAdapter adapter) throws IOException,
    		JsonParseException, IllegalStateException {

		JsonParser jp = jsonFactory.createJsonParser(in);
		
		/* The comments JSON file is a JSON array with 2 elements. First element is a thread JSON object,
		 * equivalent to the thread object you get from a subreddit .json file.
		 * Second element is a similar JSON object, but the "children" array is an array of comments
		 * instead of threads. 
		 */
		if (jp.nextToken() != JsonToken.START_ARRAY)
			throw new IllegalStateException("Unexpected non-JSON-array in the comments");
		
		// The thread, copied from above but instead of ThreadsListAdapter, use CommentsListAdapter.
		
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
							ti.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText()));
						}
					}
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
			// For comments OP, should be only one
			mOpThreadInfo = ti;
			CommentInfo ci = new CommentInfo();
			ci.setOpInfo(ti);
			ci.setIndent(0);
			ci.setListOrder(0);
			mHandler.post(new CommentItemAdder(ci));
		}
		// Wind down the end of the "data" then outermost thread-json-object
    	for (int i = 0; i < 2; i++)
	    	while (jp.nextToken() != JsonToken.END_OBJECT)
	    		;
		
		//
		// --- Now, process the comments ---
		//
    	mNestedCommentsJSONOrder = 1;
		processNestedCommentsJSON(jp, adapter, 0);
		
		// Add the comments after parsing to preserve correct order.
		// OK to dismiss dialog when we start adding comments
		dismissDialog(DIALOG_LOADING_COMMENTS_LIST);
		
		for (Integer key : mCommentsMap.keySet()) {
			mHandler.post(new CommentItemAdder(mCommentsMap.get(key)));
		}
		
		// Don't care about the remaining END_ARRAY
    }
    
    void processNestedCommentsJSON(JsonParser jp, CommentsListAdapter adapter, int commentsNested)
    		throws IOException, JsonParseException, IllegalStateException {
    	String genericListingError = "Not a valid listing";
    	
    	boolean more = false;
    	
    	if (jp.nextToken() != JsonToken.START_OBJECT) {
        	// It's OK for replies to be empty.
	    	if (EMPTY_STRING.equals(jp.getText()))
	    		return;
	    	else
	    		throw new IllegalStateException(genericListingError);
    	}
    	// Skip over to children
    	jp.nextToken();
    	if (!"kind".equals(jp.getCurrentName()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	// Handle "more" link
    	if ("more".equals(jp.getText())) {
    		more = true;
	    	jp.nextToken();
	    	if (!"data".equals(jp.getCurrentName()))
	    		throw new IllegalStateException(genericListingError);
	    	jp.nextToken();
	    	if (JsonToken.START_OBJECT != jp.getCurrentToken())
	    		throw new IllegalStateException(genericListingError);
	    	jp.nextToken();
    		// TODO: handle "more" -- "name" and "id"
    		// Skip to end of "children"
	    	while (jp.nextToken() != JsonToken.END_ARRAY)
	    		;
	    	// Skip to end of "data", then "replies" object
	    	for (int i = 0; i < 2; i++)
		    	while (jp.nextToken() != JsonToken.END_OBJECT)
		    		;
	    	return;
    	} else if ("Listing".equals(jp.getText())) {
	    	jp.nextToken();
	    	if (!"data".equals(jp.getCurrentName()))
	    		throw new IllegalStateException(genericListingError);
	    	if (jp.nextToken() != JsonToken.START_OBJECT)
	    		throw new IllegalStateException(genericListingError);
	    	jp.nextToken();
	    	while (!"children".equals(jp.getCurrentName())) {
	    		// Don't care about "after"
	    		jp.nextToken();
	    	}
	    	jp.nextToken();
	    	if (jp.getCurrentToken() != JsonToken.START_ARRAY)
	    		throw new IllegalStateException(genericListingError);
    	} else {
    		throw new IllegalStateException(genericListingError);
    	}
		
		while (jp.nextToken() != JsonToken.END_ARRAY) {
			if (jp.getCurrentToken() != JsonToken.START_OBJECT)
				throw new IllegalStateException("Unexpected non-JSON-object in the children array");
			
			// --- Process JSON representing one regular, non-OP comment ---
			CommentInfo ci = new CommentInfo();
			ci.setIndent(commentsNested);
			// Post the comments in prefix order.
			ci.setListOrder(mNestedCommentsJSONOrder++);
			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jp.getCurrentName();
				jp.nextToken(); // move to value, or START_OBJECT/START_ARRAY
			
				if ("kind".equals(fieldname)) {
					if (!COMMENT_KIND.equals(jp.getText())) {
						// Skip this JSON Object since it doesn't represent a comment.
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
					ci.put("kind", COMMENT_KIND);
				} else if ("data".equals(fieldname)) { // contains an object
					while (jp.nextToken() != JsonToken.END_OBJECT) {
						String namefield = jp.getCurrentName();
						
						// Should validate each field but I'm lazy
						if ("replies".equals(namefield)) {
							// Nested replies beginning with same "kind": "Listing" stuff
							processNestedCommentsJSON(jp, adapter, commentsNested + 1);
						} else {
							jp.nextToken(); // move to value
							if ("body".equals(namefield))
								ci.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText()));
							else
								ci.put(namefield, jp.getText());
						}
					}
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
			mCommentsMap.put(ci.getListOrder(), ci);
		}
		// Wind down the end of the "data" then "replies" objects
    	for (int i = 0; i < 2; i++)
	    	while (jp.nextToken() != JsonToken.END_OBJECT)
	    		;
	}

}
