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
import android.view.MenuInflater;
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
public final class RedditCommentsListActivity extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "RedditCommentsListActivity";
	
    private final JsonFactory jsonFactory = new JsonFactory(); 
    private int mNestedCommentsJSONOrder = 0;
    private HashSet<Integer> mMorePositions = new HashSet<Integer>(); 
	
    /** Custom list adapter that fits our threads data into the list. */
    private CommentsListAdapter mCommentsAdapter;
    
    private final DefaultHttpClient mClient = Common.createGzipHttpClient();
    volatile private String mModhash;
   
    
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
    private ThreadInfo mOpThreadInfo;
    private CharSequence mThreadTitle;
    private int mNumVisibleComments;
    private CharSequence mSortByUrl = Constants.CommentsSort.SORT_BY_HOT_URL;
    
    // Keep track of the row ids of comments that user has hidden
    private HashSet<Integer> mHiddenCommentHeads = new HashSet<Integer>();
    // Keep track of the row ids of descendants of hidden comment heads
    private HashSet<Integer> mHiddenComments = new HashSet<Integer>();

    // UI State
    private View mVoteTargetView = null;
    private CommentInfo mVoteTargetCommentInfo = null;
    private URLSpan[] mVoteTargetSpans = null;
    
    // ProgressDialogs with percentage bars
    private AutoResetProgressDialog mLoadingCommentsProgress;
    
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
        setTheme(mSettings.theme);
        
        setContentView(R.layout.comments_list_content);
        registerForContextMenu(getListView());
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().
        
        // Pull current subreddit and thread info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	mSettings.setThreadId(extras.getString(ThreadInfo.ID));
        	mSettings.setSubreddit(extras.getString(ThreadInfo.SUBREDDIT));
        	mThreadTitle = extras.getString(ThreadInfo.TITLE);
        	setTitle(mThreadTitle + " : " + mSettings.subreddit);
        	int numComments = extras.getInt(ThreadInfo.NUM_COMMENTS);
        	// TODO: Take into account very negative karma comments
        	if (numComments < Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT)
        		mNumVisibleComments = numComments;
        	else
        		mNumVisibleComments = Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT;
        } else {
        	// Quit, because the Comments List requires subreddit and thread id from Intent.
        	Log.e(TAG, "Quitting because no subreddit and thread id data was passed into the Intent.");
        	finish();
        }
        
        if (savedInstanceState != null) {
        	mSortByUrl = savedInstanceState.getCharSequence(Constants.CommentsSort.SORT_BY_KEY);
        }
        
        new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	int previousTheme = mSettings.theme;
    	boolean previousLoggedIn = mSettings.loggedIn;
    	Common.loadRedditPreferences(this, mSettings, mClient);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.theme);
    		setContentView(R.layout.threads_list_content);
    		registerForContextMenu(getListView());
    		setListAdapter(mCommentsAdapter);
    		getListView().setDivider(null);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
    	if (mSettings.loggedIn != previousLoggedIn) {
    		new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    	}
    	new Common.PeekEnvelopeTask(this, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
    }
    
    public class VoteUpOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
	    	String thingFullname;
	    	if (mVoteTargetCommentInfo.getOP() != null)
	    		thingFullname = mVoteTargetCommentInfo.getOP().getName();
	    	else
	    		thingFullname = mVoteTargetCommentInfo.getName();
			if (isChecked)
				new VoteTask(thingFullname, 1).execute();
			else
				new VoteTask(thingFullname, 0).execute();
		}
    }
    
    public class VoteDownOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
	    	String thingFullname;
	    	if (mVoteTargetCommentInfo.getOP() != null)
	    		thingFullname = mVoteTargetCommentInfo.getOP().getName();
	    	else
	    		thingFullname = mVoteTargetCommentInfo.getName();
			if (isChecked)
				new VoteTask(thingFullname, -1).execute();
			else
				new VoteTask(thingFullname, 0).execute();
		}
    }
    


    private final class CommentsListAdapter extends ArrayAdapter<CommentInfo> {
    	static final int OP_ITEM_VIEW_TYPE = 0;
    	static final int COMMENT_ITEM_VIEW_TYPE = 1;
    	static final int MORE_ITEM_VIEW_TYPE = 2;
    	static final int HIDDEN_ITEM_HEAD_VIEW_TYPE = 3;
    	// The number of view types
    	static final int VIEW_TYPE_COUNT = 4;
    	
    	public boolean mIsLoading = true;
    	
    	private LayoutInflater mInflater;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
        
        public CommentsListAdapter(Context context, List<CommentInfo> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getItemViewType(int position) {
        	if (position == 0)
        		return OP_ITEM_VIEW_TYPE;
        	if (position == mFrequentSeparatorPos || mHiddenComments.contains(position)) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
            if (mHiddenCommentHeads.contains(position))
            	return HIDDEN_ITEM_HEAD_VIEW_TYPE;
            if (mMorePositions.contains(position))
            	return MORE_ITEM_VIEW_TYPE;
            return COMMENT_ITEM_VIEW_TYPE;
        }
        
        @Override
        public int getViewTypeCount() {
        	return VIEW_TYPE_COUNT;
        }
        
        @Override
        public boolean isEmpty() {
        	if (mIsLoading)
        		return false;
        	return super.isEmpty();
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            Resources res = getResources();
            
            CommentInfo item = this.getItem(position);
            
            try {
	            if (position == 0) {
	            	// The OP
	            	if (convertView == null) {
	            		view = mInflater.inflate(R.layout.threads_list_item, null);
	            	} else {
	            		view = convertView;
	            	}
	            	
	            	// --- Copied from ThreadsListAdapter ---
	
	                // Set the values of the Views for the CommentsListItem
	                
	                TextView titleView = (TextView) view.findViewById(R.id.title);
	                TextView votesView = (TextView) view.findViewById(R.id.votes);
	                TextView numCommentsView = (TextView) view.findViewById(R.id.numComments);
	                TextView subredditView = (TextView) view.findViewById(R.id.subreddit);
	                TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
	                TextView submitterView = (TextView) view.findViewById(R.id.submitter);
	                ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
	                ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
	                WebView selftextView = (WebView) view.findViewById(R.id.selftext);
	                
	                submitterView.setVisibility(View.VISIBLE);
	                submissionTimeView.setVisibility(View.VISIBLE);
	                selftextView.setVisibility(View.VISIBLE);
	                
	                // Set the title and domain using a SpannableStringBuilder
	                SpannableStringBuilder builder = new SpannableStringBuilder();
	                SpannableString titleSS = new SpannableString(mOpThreadInfo.getTitle());
	                int titleLen = mOpThreadInfo.getTitle().length();
	                AbsoluteSizeSpan titleASS = new AbsoluteSizeSpan(14);
	                titleSS.setSpan(titleASS, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	                if (mSettings.theme == R.style.Reddit_Light) {
	                	// FIXME: This doesn't work persistently, since "clicked" is not delivered to reddit.com
	    	            if (Constants.TRUE_STRING.equals(mOpThreadInfo.getClicked())) {
	    	            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.purple));
	    	            	titleView.setTextColor(res.getColor(R.color.purple));
	    	            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	    	            } else {
	    	            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.blue));
	    	            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	    	            }
	                }
	                builder.append(titleSS);
	                builder.append(" ");
	                SpannableString domainSS = new SpannableString("("+mOpThreadInfo.getDomain()+")");
	                AbsoluteSizeSpan domainASS = new AbsoluteSizeSpan(10);
	                domainSS.setSpan(domainASS, 0, mOpThreadInfo.getDomain().length()+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	                builder.append(domainSS);
	                titleView.setText(builder);
	                
	                votesView.setText(mOpThreadInfo.getScore());
	                numCommentsView.setText(mOpThreadInfo.getNumComments()+" comments");
	                subredditView.setText(mOpThreadInfo.getSubreddit());
	                submissionTimeView.setText(Util.getTimeAgo(Double.valueOf(mOpThreadInfo.getCreatedUtc())));
	                submitterView.setText("by "+mOpThreadInfo.getAuthor());
	                
	                // Set the up and down arrow colors based on whether user likes
	                if (mSettings.loggedIn) {
	                	if (Constants.TRUE_STRING.equals(mOpThreadInfo.getLikes())) {
	                		voteUpView.setImageResource(R.drawable.vote_up_red);
	                		voteDownView.setImageResource(R.drawable.vote_down_gray);
	                		votesView.setTextColor(res.getColor(R.color.arrow_red));
	                	} else if (Constants.FALSE_STRING.equals(mOpThreadInfo.getLikes())) {
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
	                
	                // Selftext is rendered in a WebView
	            	if (!Constants.NULL_STRING.equals(mOpThreadInfo.getSelftext())) {
	            		selftextView.getSettings().setTextSize(TextSize.SMALLER);
	            		String baseURL = new StringBuilder("http://www.reddit.com/r/")
	            				.append(mSettings.subreddit).append("/comments/").append(item.getId()).toString();
	            		String selftextHtml;
	            		if (mSettings.theme == R.style.Reddit_Dark)
	            			selftextHtml = Constants.CSS_DARK;
	            		else
	            			selftextHtml = "";
	            		selftextHtml += mOpThreadInfo.getSelftext(); 
	            		selftextView.loadDataWithBaseURL(baseURL, selftextHtml, "text/html", "UTF-8", null);
	            	} else {
	            		selftextView.setVisibility(View.GONE);
	            	}
	            } else if (mHiddenComments.contains(position)) { 
	            	if (convertView == null) {
	            		// Doesn't matter which view we inflate since it's gonna be invisible
	            		view = mInflater.inflate(R.layout.zero_size_layout, null);
	            	} else {
	            		view = convertView;
	            	}
	            } else if (mHiddenCommentHeads.contains(position)) {
	            	if (convertView == null) {
	            		view = mInflater.inflate(R.layout.comments_list_item_hidden, null);
	            	} else {
	            		view = convertView;
	            	}
	            	TextView votesView = (TextView) view.findViewById(R.id.votes);
		            TextView submitterView = (TextView) view.findViewById(R.id.submitter);
	                TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
		            TextView leftIndent = (TextView) view.findViewById(R.id.left_indent);
		            
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
		            submissionTimeView.setText(Util.getTimeAgo(Double.valueOf(item.getCreatedUtc())));
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
            	} else if (mMorePositions.contains(position)) {
	            	// "load more comments"
	            	if (convertView == null) {
	            		view = mInflater.inflate(R.layout.more_comments_view, null);
	            	} else {
	            		view = convertView;
	            	}
	            	TextView leftIndent = (TextView) view.findViewById(R.id.left_indent);
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
	            	// TODO: Show number of replies, if possible
	            	
	            } else {  // Regular comment
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
		            
	                TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
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
		            submissionTimeView.setText(Util.getTimeAgo(Double.valueOf(item.getCreatedUtc())));
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
		            
		            // Set the up and down arrow colors based on whether user likes
		            if (mSettings.loggedIn) {
		            	if (Constants.TRUE_STRING.equals(item.getLikes())) {
		            		voteUpView.setImageResource(R.drawable.vote_up_red);
		            		voteDownView.setImageResource(R.drawable.vote_down_gray);
//		            		votesView.setTextColor(res.getColor(R.color.arrow_red));
		            	} else if (Constants.FALSE_STRING.equals(item.getLikes())) {
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
        CommentInfo item = mCommentsAdapter.getItem(position);
        
        if (mHiddenCommentHeads.contains(position)) {
        	showComment(position);
        	return;
        }
        
        // Mark the OP post/regular comment as selected
        mVoteTargetCommentInfo = item;
        mVoteTargetView = v;
        String thingFullname;
    	if (mVoteTargetCommentInfo.getOP() != null)
    		thingFullname = mVoteTargetCommentInfo.getOP().getName();
    	else
    		thingFullname = mVoteTargetCommentInfo.getName();
		
        if (mMorePositions.contains(position))
        	doLoadMoreComments(position, thingFullname, mSettings.subreddit);
        else
        	showDialog(Constants.DIALOG_THING_CLICK);
    }

    /**
     * Resets the output UI list contents, retains session state.
     */
    public void resetUI() {
        // Reset the list to be empty.
        List<CommentInfo> items = new ArrayList<CommentInfo>();
        mCommentsAdapter = new CommentsListAdapter(this, items);
        setListAdapter(mCommentsAdapter);
        getListView().setDivider(null);
        Common.updateListDrawables(this, mSettings.theme);
        mHiddenComments.clear();
        mHiddenCommentHeads.clear();
    }

        
    
    /**
     * Task takes in a subreddit name string and thread id, downloads its data, parses
     * out the comments, and communicates them back to the UI as they are read.
     */
    private class DownloadCommentsTask extends AsyncTask<Integer, Integer, Void> {
    	
    	private TreeMap<Integer, CommentInfo> mCommentsMap = new TreeMap<Integer, CommentInfo>();
       
    	// XXX: maxComments is unused for now
    	public Void doInBackground(Integer... maxComments) {
    		HttpEntity entity = null;
            try {
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(mSettings.subreddit.toString().trim())
            		.append("/comments/")
            		.append(mSettings.threadId)
            		.append("/.json?")
            		.append(mSortByUrl).append("&").toString());
            	HttpResponse response = mClient.execute(request);
            	entity = response.getEntity();
            	
            	InputStream in = entity.getContent();
                
                parseCommentsJSON(in);
                
                in.close();
                entity.consumeContent();
                
            } catch (Exception e) {
                Log.e(TAG, "failed:" + e.getMessage());
                if (entity != null) {
                	try {
                		entity.consumeContent();
                	} catch (IOException e2) {
                		// Ignore.
                	}
                }
            }
            return null;
	    }
    	
        void parseCommentsJSON(InputStream in) throws IOException,
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
			while (!Constants.JSON_CHILDREN.equals(jp.getCurrentName())) {
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
				
					if (Constants.JSON_KIND.equals(fieldname)) {
						if (!Constants.THREAD_KIND.equals(jp.getText())) {
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
						ti.put(Constants.JSON_KIND, Constants.THREAD_KIND);
					} else if (Constants.JSON_DATA.equals(fieldname)) { // contains an object
						while (jp.nextToken() != JsonToken.END_OBJECT) {
							String namefield = jp.getCurrentName();
							jp.nextToken(); // move to value
							// Should validate each field but I'm lazy
							if (Constants.JSON_MEDIA.equals(namefield) && jp.getCurrentToken() == JsonToken.START_OBJECT) {
								while (jp.nextToken() != JsonToken.END_OBJECT) {
									String mediaNamefield = jp.getCurrentName();
									jp.nextToken(); // move to value
									ti.put(Constants.JSON_MEDIA+"/"+mediaNamefield, jp.getText());
								}
							} else if (Constants.JSON_MEDIA_EMBED.equals(namefield) && jp.getCurrentToken() == JsonToken.START_OBJECT) {
								while (jp.nextToken() != JsonToken.END_OBJECT) {
									String mediaNamefield = jp.getCurrentName();
									jp.nextToken(); // move to value
									ti.put(Constants.JSON_MEDIA_EMBED+"/"+mediaNamefield, jp.getText());
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
				mCommentsMap.put(0, ci);
			}
			// Wind down the end of the "data" then outermost thread-json-object
			for (int i = 0; i < 2; i++)
		    	while (jp.nextToken() != JsonToken.END_OBJECT)
		    		;
			
			//
			// --- Now, process the comments ---
			//
			mNestedCommentsJSONOrder = 1;
			processNestedCommentsJSON(jp, 0);
			
		}
		
		void processNestedCommentsJSON(JsonParser jp, int commentsNested)
				throws IOException, JsonParseException, IllegalStateException {
			String genericListingError = "Not a valid listing";
			
		//	boolean more = false;
			
			if (jp.nextToken() != JsonToken.START_OBJECT) {
		    	// It's OK for replies to be empty.
		    	if (Constants.EMPTY_STRING.equals(jp.getText()))
		    		return;
		    	else
		    		throw new IllegalStateException(genericListingError);
			}
			// Skip over to children
			jp.nextToken();
			if (!Constants.JSON_KIND.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			// Handle "more" link (child)
			if (Constants.JSON_MORE.equals(jp.getText())) {
		//		more = true;
				CommentInfo moreCi = new CommentInfo();
				moreCi.setListOrder(mNestedCommentsJSONOrder);
				moreCi.setIndent(commentsNested);
		    	mMorePositions.add(mNestedCommentsJSONOrder);
		    	mNestedCommentsJSONOrder++;
				
		    	jp.nextToken();
		    	if (!Constants.JSON_DATA.equals(jp.getCurrentName()))
		    		throw new IllegalStateException(genericListingError);
		    	jp.nextToken();
		    	if (JsonToken.START_OBJECT != jp.getCurrentToken())
		    		throw new IllegalStateException(genericListingError);
		    	// handle "more" -- "name" and "id"
		    	while (jp.nextToken() != JsonToken.END_OBJECT) {
		    		String fieldname = jp.getCurrentName();
		    		jp.nextToken();
		    		moreCi.put(fieldname, jp.getText());
		    	}
		    	// Skip to the end of children array ("more" is first and only child)
		    	while (jp.nextToken() != JsonToken.END_ARRAY)
		    		;
		    	// Skip to end of "data", then "replies" object
		    	for (int i = 0; i < 2; i++)
			    	while (jp.nextToken() != JsonToken.END_OBJECT)
			    		;
		    	mCommentsMap.put(moreCi.getListOrder(), moreCi);
		    	return;
			} else if (Constants.JSON_LISTING.equals(jp.getText())) {
		    	jp.nextToken();
		    	if (!Constants.JSON_DATA.equals(jp.getCurrentName()))
		    		throw new IllegalStateException(genericListingError);
		    	if (jp.nextToken() != JsonToken.START_OBJECT)
		    		throw new IllegalStateException(genericListingError);
		    	jp.nextToken();
		    	while (!Constants.JSON_CHILDREN.equals(jp.getCurrentName())) {
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
		//			more = false;
					String fieldname = jp.getCurrentName();
					jp.nextToken(); // move to value, or START_OBJECT/START_ARRAY
				
					if (Constants.JSON_KIND.equals(fieldname)) {
						// Handle "more" link (sibling)
						if (Constants.JSON_MORE.equals(jp.getText())) {
		//					more = true;
				    		ci.put(Constants.JSON_KIND, Constants.JSON_MORE);
					    	mMorePositions.add(ci.getListOrder());
				    		
					    	jp.nextToken();
					    	if (!Constants.JSON_DATA.equals(jp.getCurrentName()))
					    		throw new IllegalStateException(genericListingError);
					    	jp.nextToken();
					    	if (JsonToken.START_OBJECT != jp.getCurrentToken())
					    		throw new IllegalStateException(genericListingError);
					    	// handle "more" -- "name" and "id"
					    	while (jp.nextToken() != JsonToken.END_OBJECT) {
					    		String moreFieldname = jp.getCurrentName();
					    		jp.nextToken();
					    		ci.put(moreFieldname, jp.getText());
					    	}
						}
						else if (!Constants.COMMENT_KIND.equals(jp.getText())) {
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
						} else {
							ci.put(Constants.JSON_KIND, Constants.COMMENT_KIND);
						}
					} else if (Constants.JSON_DATA.equals(fieldname)) { // contains an object
						while (jp.nextToken() != JsonToken.END_OBJECT) {
							String namefield = jp.getCurrentName();
							
							// Should validate each field but I'm lazy
							if (Constants.JSON_REPLIES.equals(namefield)) {
								// Nested replies beginning with same "kind": "Listing" stuff
								processNestedCommentsJSON(jp, commentsNested + 1);
							} else {
								jp.nextToken(); // move to value
								if (Constants.JSON_BODY.equals(namefield))
									ci.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText().replaceAll("\r", "")));
								else
									ci.put(namefield, jp.getText().replaceAll("\r", ""));
							}
						}
					} else {
						throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
					}
				}
				// Finished parsing one of the children
				mCommentsMap.put(ci.getListOrder(), ci);
				publishProgress(mNestedCommentsJSONOrder);
			}
			// Wind down the end of the "data" then "replies" objects
			for (int i = 0; i < 2; i++)
		    	while (jp.nextToken() != JsonToken.END_OBJECT)
		    		;
		}

    	
    	public void onPreExecute() {
    		if (mSettings.subreddit == null || mSettings.threadId == null)
	    		this.cancel(true);
    		
    		resetUI();
    		mCommentsAdapter.mIsLoading = true;
	    	
	    	if ("jailbait".equals(mSettings.subreddit.toString())) {
	    		Toast lodToast = Toast.makeText(RedditCommentsListActivity.this, "", Toast.LENGTH_LONG);
	    		View lodView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
	    			.inflate(R.layout.look_of_disapproval_view, null);
	    		lodToast.setView(lodView);
	    		lodToast.show();
	    	}
	    	showDialog(Constants.DIALOG_LOADING_COMMENTS_LIST);
	    	setTitle(mThreadTitle + " : " + mSettings.subreddit);
    	}
    	
    	public void onPostExecute(Void v) {
    		for (Integer key : mCommentsMap.keySet())
        		mCommentsAdapter.add(mCommentsMap.get(key));
    		mCommentsAdapter.mIsLoading = false;
    		mCommentsAdapter.notifyDataSetChanged();
			dismissDialog(Constants.DIALOG_LOADING_COMMENTS_LIST);
    	}
    	
    	public void onProgressUpdate(Integer... progress) {
    		mLoadingCommentsProgress.setProgress(progress[0]);
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
    			Toast.makeText(RedditCommentsListActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
	    		// Refresh the threads list
    			new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		} else {
            	mSettings.setLoggedIn(false);
    			Common.showErrorToast(mUserError, Toast.LENGTH_LONG, RedditCommentsListActivity.this);
    		}
    	}
    }
    
    
    
    
    
    
    
    private class CommentReplyTask extends AsyncTask<CharSequence, Void, CommentInfo> {
    	private CharSequence _mParentThingId;
    	CommentInfo _mTargetCommentInfo;
    	String _mUserError = "Error submitting reply. Please try again.";
    	
    	CommentReplyTask(CharSequence parentThingId, CommentInfo targetCommentInfo) {
    		_mParentThingId = parentThingId;
    		_mTargetCommentInfo = targetCommentInfo;
    	}
    	
    	@Override
        public CommentInfo doInBackground(CharSequence... text) {
        	CommentInfo newlyCreatedComment = null;
        	String userError = "Error replying. Please try again.";
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, RedditCommentsListActivity.this);
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
            	
//            	// DEBUG
//	    	    Log.dLong(TAG, line);

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
            	
            	// Getting here means success. Create a new CommentInfo.
            	newlyCreatedComment = new CommentInfo(
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
            	newlyCreatedComment.setListOrder(_mTargetCommentInfo.getListOrder()+1);
            	if (_mTargetCommentInfo.getListOrder() == 0)
            		newlyCreatedComment.setIndent(0);
            	else
            		newlyCreatedComment.setIndent(_mTargetCommentInfo.getIndent()+1);
            	
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
    	public void onPostExecute(CommentInfo newlyCreatedComment) {
    		if (newlyCreatedComment == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, RedditCommentsListActivity.this);
    		} else {
        		// Success. OK to clear the reply draft.
        		_mTargetCommentInfo.setReplyDraft("");
        		// Increment op thread's number comments
        		mOpThreadInfo.setNumComments(String.valueOf(Integer.valueOf(mOpThreadInfo.getNumComments())+1));
        		
    			// Bump the list order of everything starting from where new comment will go.
    			int count = mCommentsAdapter.getCount();
    			for (int i = newlyCreatedComment.getListOrder(); i < count; i++) {
    				mCommentsAdapter.getItem(i).setListOrder(i+1);
    			}
    			// Finally, insert the new comment where it should go.
    			mCommentsAdapter.insert(newlyCreatedComment, newlyCreatedComment.getListOrder());
    			mCommentsAdapter.notifyDataSetChanged();
    		}
    	}

    }
    
        
    
    private class VoteTask extends AsyncTask<Void, Void, Boolean> {
    	
    	private static final String TAG = "VoteWorker";
    	
    	private CharSequence _mThingFullname;
    	private int _mDirection;
    	private String _mUserError = "Error voting.";
    	private CommentInfo _mTargetCommentInfo;
    	private View _mTargetView;
    	
    	// Save the previous arrow and score in case we need to revert
    	private int _mPreviousScore, _mPreviousUps, _mPreviousDowns;
    	private String _mPreviousLikes;
    	
    	VoteTask(CharSequence thingFullname, int direction) {
    		_mThingFullname = thingFullname;
    		_mDirection = direction;
    		// Copy these because they can change while voting thread is running
    		_mTargetCommentInfo = mVoteTargetCommentInfo;
    		_mTargetView = mVoteTargetView;
    	}
    	
    	@Override
    	public Boolean doInBackground(Void... v) {
        	String status = "";
        	HttpEntity entity = null;
        	
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to vote.";
        		return false;
        	}
        	
        	// Update the modhash if necessary
        	if (mModhash == null) {
        		if ((mModhash = Common.doUpdateModhash(mClient)) == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient);
        			Log.e(TAG, "Vote failed because doUpdateModhash() failed");
        			return false;
        		}
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("id", _mThingFullname.toString()));
    			nvps.add(new BasicNameValuePair("dir", String.valueOf(_mDirection)));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mModhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/vote");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	status = response.getStatusLine().toString();
            	if (!status.contains("OK")) {
            		_mUserError = "HTTP error when voting. Try again.";
            		throw new HttpException(status);
            	}
            	
            	entity = response.getEntity();

            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();
            	if (line == null || Constants.EMPTY_STRING.equals(line)) {
            		_mUserError = "Connection error when voting. Try again.";
            		throw new HttpException("No content returned from vote POST");
            	}
            	if (line.contains("WRONG_PASSWORD")) {
            		_mUserError = "Wrong password.";
            		throw new Exception("Wrong password.");
            	}
            	if (line.contains("USER_REQUIRED")) {
            		// The modhash probably expired
            		throw new Exception("User required. Huh?");
            	}
            	
            	Log.d(TAG, line);

//            	// DEBUG
//    	        Log.dLong(TAG, line);

            	entity.consumeContent();
            	
            	return true;
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
        	return false;
        }
    	
    	public void onPreExecute() {
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to vote.", Toast.LENGTH_LONG, RedditCommentsListActivity.this);
        		cancel(true);
        		return;
        	}
        	if (_mDirection < -1 || _mDirection > 1) {
        		Log.e(TAG, "WTF: _mDirection = " + _mDirection);
        		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
        	}

        	// Update UI: 6 cases (3 original directions, each with 2 possible changes)
        	// UI is updated *before* the transaction actually happens. If the connection breaks for
        	// some reason, then the vote will be lost.
        	// Oh well, happens on reddit.com too, occasionally.
        	final ImageView ivUp = (ImageView) _mTargetView.findViewById(R.id.vote_up_image);
        	final ImageView ivDown = (ImageView) _mTargetView.findViewById(R.id.vote_down_image);
        	final TextView voteCounter = (TextView) _mTargetView.findViewById(R.id.votes);
    		int newImageResourceUp, newImageResourceDown;
    		int newUps, newDowns;
        	String newLikes;
        	
        	if (_mTargetCommentInfo.getOP() != null) {
        		_mPreviousUps = Integer.valueOf(_mTargetCommentInfo.getOP().getUps());
        		_mPreviousDowns = Integer.valueOf(_mTargetCommentInfo.getOP().getDowns());
    	    	newUps = _mPreviousUps;
    	    	newDowns = _mPreviousDowns;
    	    	_mPreviousLikes = _mTargetCommentInfo.getOP().getLikes();
        	} else {
        		_mPreviousUps = Integer.valueOf(_mTargetCommentInfo.getUps());
        		_mPreviousDowns = Integer.valueOf(_mTargetCommentInfo.getDowns());
    	    	newUps = _mPreviousUps;
    	    	newDowns = _mPreviousDowns;
    	    	_mPreviousLikes = _mTargetCommentInfo.getLikes();
        	}
        	if (Constants.TRUE_STRING.equals(_mPreviousLikes)) {
        		if (_mDirection == 0) {
        			newUps = _mPreviousUps - 1;
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.NULL_STRING;
        		} else if (_mDirection == -1) {
        			newUps = _mPreviousUps - 1;
        			newDowns = _mPreviousDowns + 1;
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_blue;
        			newLikes = Constants.FALSE_STRING;
        		} else {
        			cancel(true);
        			return;
        		}
        	} else if (Constants.FALSE_STRING.equals(_mPreviousLikes)) {
        		if (_mDirection == 1) {
        			newUps = _mPreviousUps + 1;
        			newDowns = _mPreviousDowns - 1;
        			newImageResourceUp = R.drawable.vote_up_red;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.TRUE_STRING;
        		} else if (_mDirection == 0) {
        			newDowns = _mPreviousDowns - 1;
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.NULL_STRING;
        		} else {
        			cancel(true);
        			return;
        		}
        	} else {
        		if (_mDirection == 1) {
        			newUps = _mPreviousUps + 1;
        			newImageResourceUp = R.drawable.vote_up_red;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.TRUE_STRING;
        		} else if (_mDirection == -1) {
        			newDowns = _mPreviousDowns + 1;
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_blue;
        			newLikes = Constants.FALSE_STRING;
        		} else {
        			cancel(true);
        			return;
        		}
        	}
        	
        	ivUp.setImageResource(newImageResourceUp);
    		ivDown.setImageResource(newImageResourceDown);
    		String newScore = String.valueOf(newUps - newDowns);
    		voteCounter.setText(newScore + " points");
    		if (_mTargetCommentInfo.getOP() != null) {
    			_mTargetCommentInfo.getOP().setLikes(newLikes);
    			_mTargetCommentInfo.getOP().setUps(String.valueOf(newUps));
    			_mTargetCommentInfo.getOP().setDowns(String.valueOf(newDowns));
    			_mTargetCommentInfo.getOP().setScore(String.valueOf(newUps - newDowns));
    		} else{
    			_mTargetCommentInfo.setLikes(newLikes);
    			_mTargetCommentInfo.setUps(String.valueOf(newUps));
    			_mTargetCommentInfo.setDowns(String.valueOf(newDowns));
    		}
    		mCommentsAdapter.notifyDataSetChanged();
    	}
    	
    	public void onPostExecute(Boolean success) {
    		if (!success) {
    			// Vote failed. Undo the arrow and score.
        		final ImageView ivUp = (ImageView) _mTargetView.findViewById(R.id.vote_up_image);
            	final ImageView ivDown = (ImageView) _mTargetView.findViewById(R.id.vote_down_image);
            	final TextView voteCounter = (TextView) _mTargetView.findViewById(R.id.votes);
            	int oldImageResourceUp, oldImageResourceDown;
        		if (Constants.TRUE_STRING.equals(_mPreviousLikes)) {
            		oldImageResourceUp = R.drawable.vote_up_red;
            		oldImageResourceDown = R.drawable.vote_down_gray;
            	} else if (Constants.FALSE_STRING.equals(_mPreviousLikes)) {
            		oldImageResourceUp = R.drawable.vote_up_gray;
            		oldImageResourceDown = R.drawable.vote_down_blue;
            	} else {
            		oldImageResourceUp = R.drawable.vote_up_gray;
            		oldImageResourceDown = R.drawable.vote_down_gray;
            	}
        		ivUp.setImageResource(oldImageResourceUp);
        		ivDown.setImageResource(oldImageResourceDown);
        		voteCounter.setText(String.valueOf(_mPreviousScore));
        		if (_mTargetCommentInfo.getOP() != null) {
        			_mTargetCommentInfo.getOP().setLikes(_mPreviousLikes);
        			_mTargetCommentInfo.getOP().setUps(String.valueOf(_mPreviousUps));
        			_mTargetCommentInfo.getOP().setDowns(String.valueOf(_mPreviousDowns));
        			_mTargetCommentInfo.getOP().setScore(String.valueOf(_mPreviousUps - _mPreviousDowns));
        		} else{
        			_mTargetCommentInfo.setLikes(_mPreviousLikes);
        			_mTargetCommentInfo.setUps(String.valueOf(_mPreviousUps));
        			_mTargetCommentInfo.setDowns(String.valueOf(_mPreviousDowns));
        		}
        		mCommentsAdapter.notifyDataSetChanged();
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, RedditCommentsListActivity.this);
    		}
    	}
    }

        

    public boolean doLoadMoreComments(int position, CharSequence thingId, CharSequence subreddit) {
    	// TODO: download, parse, insert the results. use Tamper Data Firefox extension (view source)
    	Toast.makeText(this, "Sorry, load more comments not implemented yet. Open in browser for now.", Toast.LENGTH_LONG).show();
    	return false;
    }

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.comments, menu);
        return true;
    }
        
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This happens when the user begins to hold down the menu key, so
        // allow them to chord to get a shortcut.
        mCanChord = true;

    	super.onPrepareOptionsMenu(menu);
    	
    	MenuItem src, dest;
    	
        // Login/Logout
    	if (mSettings.loggedIn) {
	        menu.findItem(R.id.login_logout_menu_id).setTitle(
	        		getResources().getString(R.string.logout)+": " + mSettings.username);
	        menu.findItem(R.id.inbox_menu_id).setVisible(true);
    	} else {
            menu.findItem(R.id.login_logout_menu_id).setTitle(getResources().getString(R.string.login));
            menu.findItem(R.id.inbox_menu_id).setVisible(false);
    	}
    	
    	// Theme: Light/Dark
    	src = mSettings.theme == R.style.Reddit_Light ?
        		menu.findItem(R.id.dark_menu_id) :
        			menu.findItem(R.id.light_menu_id);
        dest = menu.findItem(R.id.light_dark_menu_id);
        dest.setTitle(src.getTitle());
        
        // Sort
        if (Constants.CommentsSort.SORT_BY_HOT_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_hot_menu_id);
        else if (Constants.CommentsSort.SORT_BY_NEW_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_new_menu_id);
        else if (Constants.CommentsSort.SORT_BY_CONTROVERSIAL_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_controversial_menu_id);
        else if (Constants.CommentsSort.SORT_BY_TOP_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_top_menu_id);
        else if (Constants.CommentsSort.SORT_BY_OLD_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_old_menu_id);
        dest = menu.findItem(R.id.sort_by_menu_id);
        dest.setTitle(src.getTitle());
    	
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mCanChord) {
            // The user has already fired a shortcut with this hold down of the
            // menu key.
            return false;
        }
        
        switch (item.getItemId()) {
        case R.id.op_menu_id:
    		mVoteTargetCommentInfo = mCommentsAdapter.getItem(0);
    		showDialog(Constants.DIALOG_THING_CLICK);
    		break;
    	case R.id.login_logout_menu_id:
        	if (mSettings.loggedIn) {
        		Common.doLogout(mSettings, mClient);
        		Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
        	} else {
        		showDialog(Constants.DIALOG_LOGIN);
        	}
    		break;
    	case R.id.refresh_menu_id:
    		new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		break;
    	case R.id.reply_thread_menu_id:
    		// From the menu, only used for the OP, which is a thread.
        	mVoteTargetCommentInfo = mCommentsAdapter.getItem(0);
            showDialog(Constants.DIALOG_REPLY);
            break;
    	case R.id.sort_by_menu_id:
    		showDialog(Constants.DIALOG_SORT_BY);
    		break;
    	case R.id.open_browser_menu_id:
    		String url = new StringBuilder("http://www.reddit.com/r/")
				.append(mSettings.subreddit).append("/comments/").append(mSettings.threadId).toString();
    		Common.launchBrowser(url, this);
    		break;
        case R.id.light_dark_menu_id:
    		if (mSettings.theme == R.style.Reddit_Light) {
    			mSettings.setTheme(R.style.Reddit_Dark);
    		} else {
    			mSettings.setTheme(R.style.Reddit_Light);
    		}
    		setTheme(mSettings.theme);
    		setContentView(R.layout.threads_list_content);
    		registerForContextMenu(getListView());
    		setListAdapter(mCommentsAdapter);
    		getListView().setDivider(null);
    		Common.updateListDrawables(this, mSettings.theme);
    		break;
        case R.id.inbox_menu_id:
        	Intent inboxIntent = new Intent(this, InboxActivity.class);
        	startActivity(inboxIntent);
        	break;
//        case R.id.user_profile_menu_id:
//        	Intent profileIntent = new Intent(this, UserActivity.class);
//        	startActivity(profileIntent);
//        	break;
    	case R.id.preferences_menu_id:
            Intent prefsIntent = new Intent(this,
                    RedditPreferencesPage.class);
            startActivity(prefsIntent);
            break;

    	default:
    		throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
    	}
    	
        return true;
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
    	int myIndent = mCommentsAdapter.getItem(rowId).getIndent();
    	// Hide everything after the row.
    	for (int i = rowId + 1; i < mCommentsAdapter.getCount(); i++) {
    		CommentInfo ci = mCommentsAdapter.getItem(i);
    		if (ci.getIndent() <= myIndent)
    			break;
    		mHiddenComments.add(i);
    	}
    	mCommentsAdapter.notifyDataSetChanged();
    }
    
    private void showComment(int rowId) {
    	if (mHiddenCommentHeads.contains(rowId)) {
    		mHiddenCommentHeads.remove(rowId);
    	}
    	int stopIndent = mCommentsAdapter.getItem(rowId).getIndent();
    	int skipIndentAbove = -1;
    	for (int i = rowId + 1; i < mCommentsAdapter.getCount(); i++) {
    		CommentInfo ci = mCommentsAdapter.getItem(i);
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
    	mCommentsAdapter.notifyDataSetChanged();
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
    				if (mVoteTargetCommentInfo.getOP() != null) {
    					new CommentReplyTask(mVoteTargetCommentInfo.getOP().getName(), mVoteTargetCommentInfo).execute(replyBody.getText());
    				} else {
    					new CommentReplyTask(mVoteTargetCommentInfo.getName(), mVoteTargetCommentInfo).execute(replyBody.getText());
    				}
    				dismissDialog(Constants.DIALOG_REPLY);
    			}
    		});
    		replyCancelButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				mVoteTargetCommentInfo.setReplyDraft(replyBody.getText().toString());
    				dismissDialog(Constants.DIALOG_REPLY);
    			}
    		});
    		break;
    		
    	case Constants.DIALOG_SORT_BY:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("Sort by:");
    		builder.setSingleChoiceItems(Constants.CommentsSort.SORT_BY_CHOICES, 0, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_SORT_BY);
    				CharSequence itemCS = Constants.CommentsSort.SORT_BY_CHOICES[item];
    				if (Constants.CommentsSort.SORT_BY_HOT.equals(itemCS)) {
    					mSortByUrl = Constants.CommentsSort.SORT_BY_HOT_URL;
        			} else if (Constants.CommentsSort.SORT_BY_NEW.equals(itemCS)) {
        				mSortByUrl = Constants.CommentsSort.SORT_BY_NEW_URL;
    				} else if (Constants.CommentsSort.SORT_BY_CONTROVERSIAL.equals(itemCS)) {
    					mSortByUrl = Constants.CommentsSort.SORT_BY_CONTROVERSIAL_URL;
    				} else if (Constants.CommentsSort.SORT_BY_TOP.equals(itemCS)) {
    					mSortByUrl = Constants.CommentsSort.SORT_BY_TOP_URL;
    				} else if (Constants.CommentsSort.SORT_BY_OLD.equals(itemCS)) {
    					mSortByUrl = Constants.CommentsSort.SORT_BY_OLD_URL;
    				}
    				new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    			}
    		});
    		dialog = builder.create();
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
			if (mVoteTargetCommentInfo.getOP() != null) {
				likes = mVoteTargetCommentInfo.getOP().getLikes();
    			titleView.setVisibility(View.VISIBLE);
    			titleView.setText(mOpThreadInfo.getTitle());
    			urlView.setVisibility(View.VISIBLE);
    			urlView.setText(mOpThreadInfo.getURL());
    			submissionStuffView.setVisibility(View.VISIBLE);
        		sb = new StringBuilder(Util.getTimeAgo(Double.valueOf(mOpThreadInfo.getCreatedUtc())))
	    			.append(" by ").append(mOpThreadInfo.getAuthor());
        		submissionStuffView.setText(sb);
    			// For self posts, you're already there!
    			if (("self.").toLowerCase().equals(mOpThreadInfo.getDomain().substring(0, 5).toLowerCase())) {
    				linkButton.setVisibility(View.INVISIBLE);
    			} else {
	    			linkButton.setOnClickListener(new OnClickListener() {
	    				public void onClick(View v) {
	    					dismissDialog(Constants.DIALOG_THING_CLICK);
	    					Common.launchBrowser(mOpThreadInfo.getURL(), RedditCommentsListActivity.this);
	    				}
	    			});
	    			linkButton.setVisibility(View.VISIBLE);
    			}
    		} else {
    			titleView.setText("Comment by " + mVoteTargetCommentInfo.getAuthor());
    			likes = mVoteTargetCommentInfo.getLikes();
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
    	        			Common.launchBrowser(mVoteTargetSpans[0].getURL(), RedditCommentsListActivity.this);
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
    	    	                new ArrayAdapter<String>(RedditCommentsListActivity.this, android.R.layout.select_dialog_item, urls) {
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

    	    	            AlertDialog.Builder b = new AlertDialog.Builder(RedditCommentsListActivity.this);

    	    	            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
    	    	                public final void onClick(DialogInterface dialog, int which) {
    	    	                    if (which >= 0) {
    	    	                        Common.launchBrowser(urls.get(which), RedditCommentsListActivity.this);
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
    		}
    		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.vote_up_button);
    		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.vote_down_button);
    		final Button replyButton = (Button) dialog.findViewById(R.id.reply_button);
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		
    		// Only show upvote/downvote if user is logged in
    		if (mSettings.loggedIn) {
    			loginButton.setVisibility(View.GONE);
    			voteUpButton.setVisibility(View.VISIBLE);
    			voteDownButton.setVisibility(View.VISIBLE);
    			replyButton.setVisibility(View.VISIBLE);
    			
    			// Make sure the setChecked() actions don't actually vote just yet.
    			voteUpButton.setOnCheckedChangeListener(null);
    			voteDownButton.setOnCheckedChangeListener(null);
    			
    			// Set initial states of the vote buttons based on user's past actions
	    		if (Constants.TRUE_STRING.equals(likes)) {
	    			// User currenty likes it
	    			voteUpButton.setChecked(true);
	    			voteDownButton.setChecked(false);
	    		} else if (Constants.FALSE_STRING.equals(likes)) {
	    			// User currently dislikes it
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(true);
	    		} else {
	    			// User is currently neutral
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(false);
	    		}
	    		// Now we want the user to be able to vote.
	    		voteUpButton.setOnCheckedChangeListener(new VoteUpOnCheckedChangeListener());
	    		voteDownButton.setOnCheckedChangeListener(new VoteDownOnCheckedChangeListener());

	    		// The "reply" button
    			replyButton.setOnClickListener(new OnClickListener() {
	    			public void onClick(View v) {
	    				dismissDialog(Constants.DIALOG_THING_CLICK);
	    				showDialog(Constants.DIALOG_REPLY);
	        		}
	    		});
	    	} else {
    			voteUpButton.setVisibility(View.GONE);
    			voteDownButton.setVisibility(View.GONE);
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
    		if (mVoteTargetCommentInfo.getReplyDraft() != null) {
    			EditText replyBodyView = (EditText) dialog.findViewById(R.id.body); 
    			replyBodyView.setText(mVoteTargetCommentInfo.getReplyDraft());
    		}
    		break;
    		
    	case Constants.DIALOG_LOADING_COMMENTS_LIST:
    		mLoadingCommentsProgress.setMax(mNumVisibleComments);
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putCharSequence(Constants.CommentsSort.SORT_BY_KEY, mSortByUrl);
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
