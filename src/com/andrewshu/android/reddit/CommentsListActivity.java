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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
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
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
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
public class CommentsListActivity extends ListActivity
		implements View.OnCreateContextMenuListener {

	private static final String TAG = "CommentsListActivity";
	
    // Group 1: fullname. Group 2: kind. Group 3: id36.
    private final Pattern NEW_ID_PATTERN = Pattern.compile("\"id\": \"((.+?)_(.+?))\"");
    // Group 2: subreddit name. Group 3: thread id36. Group 4: Comment id36.
    private final Pattern COMMENT_CONTEXT_PATTERN = Pattern.compile("(http://www.reddit.com)?/r/(.+?)/comments/(.+?)/.+?/([a-zA-Z0-9]+)?");
    // Group 1: whole error. Group 2: the time part
    private final Pattern RATELIMIT_RETRY_PATTERN = Pattern.compile("(you are trying to submit too fast. try again in (.+?)\\.)");

    private final JsonFactory jsonFactory = new JsonFactory();
    private final Markdown markdown = new Markdown();
    private int mNestedCommentsJSONOrder = 0;
	
    /** Custom list adapter that fits our threads data into the list. */
    private CommentsListAdapter mCommentsAdapter = null;
    private ArrayList<CommentInfo> mCommentsList = null;
    // Lock used when modifying the mCommentsAdapter
    private static final Object COMMENT_ADAPTER_LOCK = new Object();
    
    private final DefaultHttpClient mClient = Common.getGzipHttpClient();
    
    
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
    // Whether the cache should be used during onResume().
    volatile private boolean mShouldUseCommentsCache = true;
    
    // Navigation items to be cached
    private long mLastRefreshTime = 0;
    private CharSequence mJumpToCommentId = null;
    private int mJumpToCommentPosition = 0;
//    private CharSequence mMoreChildrenId = "";
    private HashSet<Integer> mMorePositions = new HashSet<Integer>();
    private int mNumVisibleComments = Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT;
    private ThreadInfo mOpThreadInfo = null;
    private CharSequence mSortByUrl = Constants.CommentsSort.SORT_BY_BEST_URL;
    private CharSequence mThreadTitle = null;
    // should also cache mSettings.subreddit and mSettings.threadId
    // End navigation to be cached
	
    // Keep track of the row ids of comments that user has hidden
    private HashSet<Integer> mHiddenCommentHeads = new HashSet<Integer>();
    // Keep track of the row ids of descendants of hidden comment heads
    private HashSet<Integer> mHiddenComments = new HashSet<Integer>();
    
    // UI State
    private CommentInfo mVoteTargetCommentInfo = null;
    private CharSequence mReplyTargetName = null;
    private CharSequence mEditTargetBody = null;
    private String mDeleteTargetKind = null;
    private AsyncTask mCurrentDownloadCommentsTask = null;
    private final Object mCurrentDownloadCommentsTaskLock = new Object();
    
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
        setRequestedOrientation(mSettings.rotation);
        setTheme(mSettings.theme);
        
        setContentView(R.layout.comments_list_content);
        registerForContextMenu(getListView());
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().
        
        // Pull current subreddit and thread info from Intent
        Intent intent = getIntent();
        if (intent == null) {
        	// onResume() should handle this. Probably read from cache.
        	mShouldUseCommentsCache = true;
        	return;
        }
        
        Bundle extras = intent.getExtras();
        if (extras == null) {
        	if (Constants.LOGGING) Log.d(TAG, "No info in Intent. Using comments cache instead.");
        	// See onResume()
			return;
        }
    	// Comment context: a URL pointing directly at a comment, versus a thread
    	String commentContext = extras.getString(Constants.EXTRA_COMMENT_CONTEXT);
    	if (commentContext != null) {
    		Matcher commentContextMatcher = COMMENT_CONTEXT_PATTERN.matcher(commentContext);
    		if (commentContextMatcher.find()) {
        		mSettings.setSubreddit(commentContextMatcher.group(2));
    			mSettings.setThreadId(commentContextMatcher.group(3));
    			mJumpToCommentId = commentContextMatcher.group(4);
    		} else {
    			if (Constants.LOGGING) Log.d(TAG, "No info in Intent. Using comments cache instead.");
            	mShouldUseCommentsCache = true;
    			return;
    		}
    	} else {
        	mSettings.setThreadId(extras.getString(ThreadInfo.ID));
        	mSettings.setSubreddit(extras.getString(ThreadInfo.SUBREDDIT));
        	mThreadTitle = extras.getString(ThreadInfo.TITLE);
        	if (mThreadTitle != null) {
        	  setTitle(mThreadTitle + " : " + mSettings.subreddit);
        	} else {
        	  setTitle("reddit is fun");
        	}
        	int numComments = extras.getInt(ThreadInfo.NUM_COMMENTS);
        	// TODO: Take into account very negative karma comments
        	if (numComments < Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT)
        		mNumVisibleComments = numComments;
        	else
        		mNumVisibleComments = Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT;
        	mShouldUseCommentsCache = false;
    	}
    	
    	if (savedInstanceState != null) {
        	mSortByUrl = savedInstanceState.getCharSequence(Constants.CommentsSort.SORT_BY_KEY);
       		mJumpToCommentId = savedInstanceState.getCharSequence(Constants.JUMP_TO_COMMENT_ID_KEY);
        	mReplyTargetName = savedInstanceState.getCharSequence(Constants.REPLY_TARGET_NAME_KEY);
        	mEditTargetBody = savedInstanceState.getCharSequence(Constants.EDIT_TARGET_BODY_KEY);
        	mDeleteTargetKind = savedInstanceState.getString(Constants.DELETE_TARGET_KIND_KEY);
        	mJumpToCommentPosition = savedInstanceState.getInt(Constants.JUMP_TO_COMMENT_POSITION_KEY);
        	// savedInstanceState means probably rotated screen or something.
        	mShouldUseCommentsCache = true;
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
    		registerForContextMenu(getListView());
    		setListAdapter(mCommentsAdapter);
    		getListView().setDivider(null);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
    	if (mSettings.loggedIn != previousLoggedIn)
    		mShouldUseCommentsCache = false;
    	if (mCommentsAdapter == null)
    		new ReadCacheTask().execute();
	    new Common.PeekEnvelopeTask(this, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
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
	                TextView selftextView = (TextView) view.findViewById(R.id.selftext);
	                
	                submitterView.setVisibility(View.VISIBLE);
	                submissionTimeView.setVisibility(View.VISIBLE);
	                
	                // Set the title and domain using a SpannableStringBuilder
	                SpannableStringBuilder builder = new SpannableStringBuilder();
	                String title = mOpThreadInfo.getTitle().replaceAll("\n ", " ").replaceAll(" \n", " ").replaceAll("\n", " ");
	                SpannableString titleSS = new SpannableString(title);
	                int titleLen = title.length();
	                TextAppearanceSpan titleTAS = new TextAppearanceSpan(getApplicationContext(), R.style.TextAppearance_14sp);
	                titleSS.setSpan(titleTAS, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
	                TextAppearanceSpan domainTAS = new TextAppearanceSpan(getApplicationContext(), R.style.TextAppearance_10sp);
	                domainSS.setSpan(domainTAS, 0, mOpThreadInfo.getDomain().length()+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
	            	if (!Constants.EMPTY_STRING.equals(mOpThreadInfo.getSelftext())
	            			&& !Constants.NULL_STRING.equals(mOpThreadInfo.getSelftext())) {
	            		selftextView.setVisibility(View.VISIBLE);
		                selftextView.setText(mOpThreadInfo.mSSBSelftext);
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
		            	if (Constants.LOGGING) Log.e(TAG, e.getMessage());
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
		            	if (Constants.LOGGING) Log.e(TAG, e.getMessage());
		            }
		            submitterView.setText(item.getAuthor());
		            submissionTimeView.setText(Util.getTimeAgo(Double.valueOf(item.getCreatedUtc())));
		            bodyView.setText(item.mSSBBody);
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
		            
		            if ("[deleted]".equals(item.getAuthor())) {
		            	voteUpView.setVisibility(View.INVISIBLE);
		            	voteDownView.setVisibility(View.INVISIBLE);
		            }
		            // Set the up and down arrow colors based on whether user likes
		            else if (mSettings.loggedIn) {
		            	voteUpView.setVisibility(View.VISIBLE);
		            	voteDownView.setVisibility(View.VISIBLE);
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
		            	voteUpView.setVisibility(View.VISIBLE);
		            	voteDownView.setVisibility(View.VISIBLE);
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
        if (mVoteTargetCommentInfo.getOP() != null) {
        	mReplyTargetName = mVoteTargetCommentInfo.getOP().getName();
		} else {
			mReplyTargetName = mVoteTargetCommentInfo.getName();
		}
		
        if (mMorePositions.contains(position)) {
        	mJumpToCommentPosition = position;
        	// Use this constructor to tell it to load more comments inline
        	new DownloadCommentsTask(item.getId(), position, item.getIndent()).execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
        } else {
        	mJumpToCommentId = item.getId();
        	if (!"[deleted]".equals(item.getAuthor()))
        		showDialog(Constants.DIALOG_THING_CLICK);
        }
    }
    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // When the user presses back, clear the comments cache.
            deleteFile(Constants.FILENAME_COMMENTS_CACHE);
        }
        return super.onKeyDown(keyCode, event);
    }

    
    /**
     * Try jumping to mJumpToCommentPosition. failing that, try mJumpToCommentId.
     */
    private void jumpToComment() {
	    if (mJumpToCommentPosition != 0) {
			getListView().setSelectionFromTop(mJumpToCommentPosition, 10);
			mJumpToCommentPosition = 0;
		} else if (mJumpToCommentId != null && mCommentsAdapter != null) {
			synchronized (COMMENT_ADAPTER_LOCK) {
				for (int k = 0; k < mCommentsAdapter.getCount(); k++) {
					if (mJumpToCommentId.equals(mCommentsAdapter.getItem(k).getId())) {
						getListView().setSelectionFromTop(k, 10);
						mJumpToCommentId = null;
						break;
					}
				}
			}
		}
    }

    /**
     * Resets the output UI list contents, retains session state.
     * @param commentsAdapter A new CommentsListAdapter to use. Pass in null to create a new empty one.
     */
    public void resetUI(CommentsListAdapter commentsAdapter) {
    	synchronized (COMMENT_ADAPTER_LOCK) {
	    	if (commentsAdapter == null) {
	    		// Reset the list to be empty.
	    		mCommentsList = new ArrayList<CommentInfo>();
	            mCommentsAdapter = new CommentsListAdapter(this, mCommentsList);
	    	} else {
	    		mCommentsAdapter = commentsAdapter;
	    	}
	        setListAdapter(mCommentsAdapter);
	        mCommentsAdapter.notifyDataSetChanged();  // Just in case
    	}
        getListView().setDivider(null);
        Common.updateListDrawables(this, mSettings.theme);
        mHiddenComments.clear();
        mHiddenCommentHeads.clear();
    }

        
    
    /**
     * Task takes in a subreddit name string and thread id, downloads its data, parses
     * out the comments, and communicates them back to the UI as they are read.
     * 
     * Requires the following navigation variables to be set:
     * mSettings.subreddit
     * mSettings.threadId
     * mMoreChildrenId (can be "")
     * mSortByUrl
     */
    class DownloadCommentsTask extends AsyncTask<Integer, Integer, Boolean> {
    	
    	private TreeMap<Integer, CommentInfo> mCommentsMap = new TreeMap<Integer, CommentInfo>();
    	private int _mNumComments = mNumVisibleComments;
    	// _mPositionOffset != 0 means that you're doing "load more comments"
    	private int _mPositionOffset;
    	private int _mIndentation;
    	private String _mMoreChildrenId;
    	// Temporary storage for new "load more comments" found in the JSON
    	private HashSet<Integer> _mNewMorePositions = new HashSet<Integer>();
    	
    	/**
    	 * Constructor to do normal comments page
    	 */
    	public DownloadCommentsTask() {
    		_mMoreChildrenId = "";
    		_mPositionOffset = 0;
    		_mIndentation = 0;
    	}
    	
    	/**
    	 * "load more comments" starting at this position
    	 * @param moreChildrenId The reddit thing-id of the "more" children comment
    	 * @param morePosition Position in local list to insert
    	 * @param indentation The indentation level of the child.
    	 */
    	public DownloadCommentsTask(String moreChildrenId, int morePosition, int indentation) {
    		_mMoreChildrenId = moreChildrenId;
    		_mPositionOffset = morePosition;
    		_mIndentation = indentation;
    	}
       
    	// XXX: maxComments is unused for now
    	public Boolean doInBackground(Integer... maxComments) {
    		HttpEntity entity = null;
            try {
            	StringBuilder sb = new StringBuilder("http://www.reddit.com/");
	        		if (mSettings.subreddit != null) {
	        		  sb.append("/r/").append(mSettings.subreddit.toString().trim());
	        		}
	        		sb.append("/comments/")
	        		.append(mSettings.threadId)
	        		.append("/z/").append(_mMoreChildrenId).append(".json?").append(mSortByUrl).append("&");
            	HttpGet request = new HttpGet(sb.toString());
                HttpResponse response = mClient.execute(request);
            	entity = response.getEntity();
            	
            	InputStream in = entity.getContent();
                
                parseCommentsJSON(in);
                
                in.close();
                entity.consumeContent();
                
                // Fill in the list adapter
                synchronized (COMMENT_ADAPTER_LOCK) {
    				// Shift the comments' positions for comments after what is about to be inserted.
    				// Shift them by (number inserted - 1) since there used to be a "load more comments" entry there.
    				if (_mPositionOffset != 0) {
    					int numInserted = mCommentsMap.size();
    					for (int i = _mPositionOffset + 1; i < mCommentsList.size(); i++) {
    						mCommentsList.get(i).setListOrder(i + numInserted - 1);
    						// Also update other "load more comments" list items
    						if (mMorePositions.remove(i))
    							_mNewMorePositions.add(i + numInserted - 1);
    					}
    					// Now remove the "load more comments" entry
    					mMorePositions.remove(_mPositionOffset);
    					mCommentsList.remove(_mPositionOffset);
    				}
    				// Insert the new comments
		    		for (Integer key : mCommentsMap.keySet()) {
		    			CommentInfo ci = mCommentsMap.get(key);
		    			mCommentsList.add(ci.getListOrder(), ci);
		    		}
		    		// Merge the new "load more comments" positions
		    		mMorePositions.addAll(_mNewMorePositions);
		    	}
				
                return true;
                
            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "failed:" + e.getMessage());
                if (entity != null) {
                	try {
                		entity.consumeContent();
                	} catch (Exception e2) {
                		// Ignore.
                	}
                }
            }
            return false;
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
			// Save the modhash
			if (!Constants.JSON_MODHASH.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
			jp.nextToken();
			if (Constants.EMPTY_STRING.equals(jp.getText()))
				mSettings.setModhash(null);
			else
				mSettings.setModhash(jp.getText());
			jp.nextToken();
			if (!Constants.JSON_CHILDREN.equals(jp.getCurrentName()))
				throw new IllegalStateException(genericListingError);
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
								ti.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText().trim().replaceAll("\r", "")));
								if (Constants.JSON_NUM_COMMENTS.equals(namefield)) {
									int numComments = Integer.valueOf(jp.getText());
									if (numComments != _mNumComments)
										_mNumComments = numComments;
								} else if (Constants.JSON_SELFTEXT.equals(namefield)) {
									ti.mSSBSelftext = markdown.markdown(ti.getSelftext(), new SpannableStringBuilder(), ti.mUrls);
								} else if (Constants.JSON_TITLE.equals(namefield)) {
								  // We might not have a title if we've intercepted a plain link to a thread.
								  mThreadTitle = jp.getText();
								} else if (Constants.JSON_SUBREDDIT.equals(namefield)) {
								  mSettings.subreddit = jp.getText();
								}
							}
						}
					} else {
						throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
					}
				}
				// For comments OP, should be only one
				if (_mPositionOffset == 0) {
					mOpThreadInfo = ti;
					CommentInfo ci = new CommentInfo();
					ci.setOpInfo(ti);
					ci.setIndent(0);
					ci.setListOrder(0);
					mCommentsMap.put(0, ci);
				}
			}
			// Wind down the end of the "data" then outermost thread-json-object
			for (int i = 0; i < 2; i++)
		    	while (jp.nextToken() != JsonToken.END_OBJECT)
		    		;
			
			//
			// --- Now, process the comments ---
			//
			if (_mPositionOffset == 0)
				mNestedCommentsJSONOrder = 1;  // position 0 is taken by OP thread
			else
				mNestedCommentsJSONOrder = _mPositionOffset;
			processNestedCommentsJSON(jp, _mIndentation);
			
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
		    	_mNewMorePositions.add(mNestedCommentsJSONOrder);
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
				// Save the modhash
				if (!Constants.JSON_MODHASH.equals(jp.getCurrentName()))
					throw new IllegalStateException(genericListingError);
				jp.nextToken();
				if (Constants.EMPTY_STRING.equals(jp.getText()))
					mSettings.setModhash(null);
				else
					mSettings.setModhash(jp.getText());
				jp.nextToken();
		    	if (!Constants.JSON_CHILDREN.equals(jp.getCurrentName()))
		    		throw new IllegalStateException(genericListingError);
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
					    	_mNewMorePositions.add(ci.getListOrder());
				    		
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
								if (Constants.JSON_BODY.equals(namefield)) {
									ci.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText().trim().replaceAll("\r", "")));
									ci.mSSBBody = markdown.markdown(ci.getBody(), new SpannableStringBuilder(), ci.mUrls);
								} else {
									ci.put(namefield, jp.getText().trim().replaceAll("\r", ""));
								}
							}
						}
					} else {
						throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
					}
				}
				// Finished parsing one of the children
				mCommentsMap.put(ci.getListOrder(), ci);
				publishProgress(mNestedCommentsJSONOrder - _mPositionOffset);
			}
			// Wind down the end of the "data" then "replies" objects
			for (int i = 0; i < 2; i++)
		    	while (jp.nextToken() != JsonToken.END_OBJECT)
		    		;
		}

    	
    	public void onPreExecute() {
    		if (mSettings.threadId == null)
	    		this.cancel(true);
    		synchronized (mCurrentDownloadCommentsTaskLock) {
	    		if (mCurrentDownloadCommentsTask != null)
	    			mCurrentDownloadCommentsTask.cancel(true);
	    		mCurrentDownloadCommentsTask = this;
    		}
    		// Initialize mCommentsList and mCommentsAdapter
    		if (_mPositionOffset == 0)
    			resetUI(null);
    		mCommentsAdapter.mIsLoading = true;
    		// In case a ReadCacheTask tries to preempt this DownloadCommentsTask
    		mShouldUseCommentsCache = false;
			
	    	if (mSettings.subreddit != null && "jailbait".equals(mSettings.subreddit.toString())) {
	    		Toast lodToast = Toast.makeText(CommentsListActivity.this, "", Toast.LENGTH_LONG);
	    		View lodView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
	    			.inflate(R.layout.look_of_disapproval_view, null);
	    		lodToast.setView(lodView);
	    		lodToast.show();
	    	}
	    	showDialog(Constants.DIALOG_LOADING_COMMENTS_LIST);
	    	if (mThreadTitle != null)
	    		setTitle(mThreadTitle + " : " + mSettings.subreddit);
    	}
    	
    	public void onPostExecute(Boolean success) {
    		synchronized (mCurrentDownloadCommentsTaskLock) {
    			mCurrentDownloadCommentsTask = null;
    		}
			dismissDialog(Constants.DIALOG_LOADING_COMMENTS_LIST);
    		if (success) {
    			// We modified mCommentsList, which backs mCommentsAdapter, so mCommentsAdapter has changed too.
    			mCommentsAdapter.notifyDataSetChanged();
    			// Set title in android titlebar
    			if (mThreadTitle == null) {
	    			mThreadTitle = mOpThreadInfo.getTitle().replaceAll("\n ", " ").replaceAll(" \n", " ").replaceAll("\n", " ");
	    		}
    			setTitle(mThreadTitle + " : " + mSettings.subreddit);
	    		// Remember this time for caching purposes
	    		mLastRefreshTime = System.currentTimeMillis();
	    		mShouldUseCommentsCache = true;
	    		// Point the list to last comment user was looking at, if any
	    		jumpToComment();
    		} else {
    			if (!isCancelled())
    				Common.showErrorToast("Error downloading comments. Please try again.", Toast.LENGTH_LONG, CommentsListActivity.this);
    		}
    	}
    	
    	public void onProgressUpdate(Integer... progress) {
    		// The number of comments may have been unknown before, and now found during parsing.
    		if (_mNumComments < mNumVisibleComments) {
    			mNumVisibleComments = _mNumComments;
    			mLoadingCommentsProgress.setMax(_mNumComments);
    		}
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
    		return Common.doLogin(mUsername, mPassword, mClient, mSettings);
        }
    	
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	protected void onPostExecute(String errorMessage) {
    		dismissDialog(Constants.DIALOG_LOGGING_IN);
    		if (errorMessage == null) {
    			Toast.makeText(CommentsListActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
    			// Check mail
    			new Common.PeekEnvelopeTask(CommentsListActivity.this, mClient, mSettings.mailNotificationStyle).execute();
	    		// Refresh the comments list
    			new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		} else {
            	Common.showErrorToast(mUserError, Toast.LENGTH_LONG, CommentsListActivity.this);
    		}
    	}
    }
    
    
    
    
    private class CommentReplyTask extends AsyncTask<CharSequence, Void, CharSequence> {
    	private CharSequence _mParentThingId;
    	String _mUserError = "Error submitting reply. Please try again.";
    	
    	CommentReplyTask(CharSequence parentThingId) {
    		_mParentThingId = parentThingId;
    	}
    	
    	@Override
        public CharSequence doInBackground(CharSequence... text) {
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, CommentsListActivity.this);
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
    			nvps.add(new BasicNameValuePair("thing_id", _mParentThingId.toString()));
    			nvps.add(new BasicNameValuePair("text", text[0].toString()));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/comment");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        HttpParams params = httppost.getParams();
    	        HttpConnectionParams.setConnectionTimeout(params, 40000);
    	        HttpConnectionParams.setSoTimeout(params, 40000);
    	        
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
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);

            	String newId;
            	Matcher idMatcher = NEW_ID_PATTERN.matcher(line);
            	if (idMatcher.find()) {
            		newId = idMatcher.group(3);
            	} else {
            		if (line.contains("RATELIMIT")) {
                		// Try to find the # of minutes using regex
                    	Matcher rateMatcher = RATELIMIT_RETRY_PATTERN.matcher(line);
                    	if (rateMatcher.find())
                    		_mUserError = rateMatcher.group(1);
                    	else
                    		_mUserError = "you are trying to submit too fast. try again in a few minutes.";
                		throw new Exception(_mUserError);
                	}
            		if (line.contains("DELETED_LINK")) {
            			_mUserError = "the link you are commenting on has been deleted";
            			throw new Exception(_mUserError);
            		}
                	throw new Exception("No id returned by reply POST.");
            	}
            	
            	entity.consumeContent();
            	
            	// Getting here means success. Create a new CommentInfo.
            	return newId;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, e2.getMessage());
        			}
        		}
        		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        	}
        	return null;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_REPLYING);
    	}
    	
    	@Override
    	public void onPostExecute(CharSequence newId) {
    		dismissDialog(Constants.DIALOG_REPLYING);
    		if (newId == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, CommentsListActivity.this);
    		} else {
    			// Refresh
    			mJumpToCommentId = newId;
    			new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		}
    	}
    }
    
    private class EditTask extends AsyncTask<CharSequence, Void, CharSequence> {
    	private CharSequence _mThingId;
    	String _mUserError = "Error submitting edit. Please try again.";
    	
    	EditTask(CharSequence thingId) {
    		_mThingId = thingId;
    	}
    	
    	@Override
        public CharSequence doInBackground(CharSequence... text) {
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to edit.";
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
    			nvps.add(new BasicNameValuePair("thing_id", _mThingId.toString()));
    			nvps.add(new BasicNameValuePair("text", text[0].toString()));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/editusertext");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        HttpParams params = httppost.getParams();
    	        HttpConnectionParams.setConnectionTimeout(params, 40000);
    	        HttpConnectionParams.setSoTimeout(params, 40000);
    	        
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
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);

            	String newId;
            	Matcher idMatcher = NEW_ID_PATTERN.matcher(line);
            	if (idMatcher.find()) {
            		newId = idMatcher.group(3);
            	} else {
            		if (line.contains("RATELIMIT")) {
                		// Try to find the # of minutes using regex
                    	Matcher rateMatcher = RATELIMIT_RETRY_PATTERN.matcher(line);
                    	if (rateMatcher.find())
                    		_mUserError = rateMatcher.group(1);
                    	else
                    		_mUserError = "you are trying to submit too fast. try again in a few minutes.";
                		throw new Exception(_mUserError);
                	}
            		if (line.contains("DELETED_LINK")) {
            			_mUserError = "the link you are commenting on has been deleted";
            			throw new Exception(_mUserError);
            		}
                	throw new Exception("No id returned by reply POST.");
            	}
            	
            	entity.consumeContent();
            	
            	// Getting here means success. Create a new CommentInfo.
            	return newId;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, e2.getMessage());
        			}
        		}
        		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        	}
        	return null;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_EDITING);
    	}
    	
    	@Override
    	public void onPostExecute(CharSequence newId) {
    		dismissDialog(Constants.DIALOG_EDITING);
    		if (newId == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, CommentsListActivity.this);
    		} else {
    			// Refresh
    			mJumpToCommentId = newId;
    			new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		}
    	}
    }
    
    private class DeleteTask extends AsyncTask<CharSequence, Void, Boolean> {
    	private String _mUserError = "Error deleting. Please try again.";
    	private String _mKind;
    	
    	public DeleteTask(String kind) {
    		_mKind = kind;
    	}
    	
    	@Override
    	public Boolean doInBackground(CharSequence... thingFullname) {
//    		POSTDATA=id=t1_c0cxa7l&executed=deleted&r=test&uh=f7jb1yjwfqd4ffed8356eb63fcfbeeadad142f57c56e9cbd9e
    		
    		HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to delete.";
        		return false;
        	}
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient);
        			if (Constants.LOGGING) Log.e(TAG, "Reply failed because doUpdateModhash() failed");
        			return false;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("id", thingFullname[0].toString()));
    			nvps.add(new BasicNameValuePair("executed", "deleted"));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/del");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        HttpParams params = httppost.getParams();
    	        HttpConnectionParams.setConnectionTimeout(params, 40000);
    	        HttpConnectionParams.setSoTimeout(params, 40000);
    	        
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
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);

            	// Success
            	return true;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, e2.getMessage());
        			}
        		}
        		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        	}
        	return false;
    	}
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_DELETING);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_DELETING);
    		if (success) {
    			if (Constants.THREAD_KIND.equals(_mKind)) {
    				Toast.makeText(CommentsListActivity.this, "Deleted thread.", Toast.LENGTH_LONG).show();
    				finish();
    			} else {
    				Toast.makeText(CommentsListActivity.this, "Deleted comment.", Toast.LENGTH_SHORT).show();
    				new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    			}
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, CommentsListActivity.this);
    		}
    	}
    }

        
    
    private class VoteTask extends AsyncTask<Void, Void, Boolean> {
    	
    	private static final String TAG = "VoteWorker";
    	
    	private CharSequence _mThingFullname;
    	private int _mDirection;
    	private String _mUserError = "Error voting.";
    	private CommentInfo _mTargetCommentInfo;
    	
    	// Save the previous arrow and score in case we need to revert
    	private int _mPreviousScore, _mPreviousUps, _mPreviousDowns;
    	private String _mPreviousLikes;
    	
    	VoteTask(CharSequence thingFullname, int direction) {
    		_mThingFullname = thingFullname;
    		_mDirection = direction;
    		// Copy these because they can change while voting thread is running
    		_mTargetCommentInfo = mVoteTargetCommentInfo;
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
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient); 
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient);
        			if (Constants.LOGGING) Log.e(TAG, "Vote failed because doUpdateModhash() failed");
        			return false;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("id", _mThingFullname.toString()));
    			nvps.add(new BasicNameValuePair("dir", String.valueOf(_mDirection)));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/vote");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
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
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);

            	entity.consumeContent();
            	
            	return true;
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, e2.getMessage());
        			}
        		}
        		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
        	}
        	return false;
        }
    	
    	public void onPreExecute() {
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to vote.", Toast.LENGTH_LONG, CommentsListActivity.this);
        		cancel(true);
        		return;
        	}
        	if (_mDirection < -1 || _mDirection > 1) {
        		if (Constants.LOGGING) Log.e(TAG, "WTF: _mDirection = " + _mDirection);
        		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
        	}

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
	    			newLikes = Constants.NULL_STRING;
	    		} else if (_mDirection == -1) {
	    			newUps = _mPreviousUps - 1;
	    			newDowns = _mPreviousDowns + 1;
	    			newLikes = Constants.FALSE_STRING;
	    		} else {
	    			cancel(true);
	    			return;
	    		}
	    	} else if (Constants.FALSE_STRING.equals(_mPreviousLikes)) {
	    		if (_mDirection == 1) {
	    			newUps = _mPreviousUps + 1;
	    			newDowns = _mPreviousDowns - 1;
	    			newLikes = Constants.TRUE_STRING;
	    		} else if (_mDirection == 0) {
	    			newDowns = _mPreviousDowns - 1;
	    			newLikes = Constants.NULL_STRING;
	    		} else {
	    			cancel(true);
	    			return;
	    		}
	    	} else {
	    		if (_mDirection == 1) {
	    			newUps = _mPreviousUps + 1;
	    			newLikes = Constants.TRUE_STRING;
	    		} else if (_mDirection == -1) {
	    			newDowns = _mPreviousDowns + 1;
	    			newLikes = Constants.FALSE_STRING;
	    		} else {
	    			cancel(true);
	    			return;
	    		}
	    	}

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
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, CommentsListActivity.this);
    		}
    	}
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
    	
    	// Edit and delete
    	if (mSettings.username != null && mSettings.username.equals(mOpThreadInfo.getAuthor())) {
			if (!Constants.NULL_STRING.equals(mOpThreadInfo.getSelftextHtml()))
				menu.findItem(R.id.op_edit_menu_id).setVisible(true);
			else
				menu.findItem(R.id.op_edit_menu_id).setVisible(false);
			menu.findItem(R.id.op_delete_menu_id).setVisible(true);
		} else {
			menu.findItem(R.id.op_edit_menu_id).setVisible(false);
			menu.findItem(R.id.op_delete_menu_id).setVisible(false);
		}
    	
    	// Theme: Light/Dark
    	src = mSettings.theme == R.style.Reddit_Light ?
        		menu.findItem(R.id.dark_menu_id) :
        			menu.findItem(R.id.light_menu_id);
        dest = menu.findItem(R.id.light_dark_menu_id);
        dest.setTitle(src.getTitle());
        
        // Sort
        if (Constants.CommentsSort.SORT_BY_BEST_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_best_menu_id);
        else if (Constants.CommentsSort.SORT_BY_HOT_URL.equals(mSortByUrl))
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
    		synchronized (COMMENT_ADAPTER_LOCK) {
    			mVoteTargetCommentInfo = mCommentsAdapter.getItem(0);
    		}
    		mReplyTargetName = mVoteTargetCommentInfo.getOP().getName();
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
        	if (mSettings.loggedIn) {
	    		synchronized (COMMENT_ADAPTER_LOCK) {
	    			mVoteTargetCommentInfo = mCommentsAdapter.getItem(0);
	    		}
	    		mReplyTargetName = mVoteTargetCommentInfo.getOP().getName();
	            showDialog(Constants.DIALOG_REPLY);
        	} else {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, this);
        	}
            break;
    	case R.id.sort_by_menu_id:
    		showDialog(Constants.DIALOG_SORT_BY);
    		break;
    	case R.id.open_browser_menu_id:
    		String url = new StringBuilder("http://www.reddit.com/r/")
				.append(mSettings.subreddit).append("/comments/").append(mSettings.threadId).toString();
    		Common.launchBrowser(url, this);
    		break;
    	case R.id.op_delete_menu_id:
    		mReplyTargetName = mOpThreadInfo.getName();
    		mDeleteTargetKind = Constants.THREAD_KIND;
    		showDialog(Constants.DIALOG_DELETE);
    		break;
    	case R.id.op_edit_menu_id:
    		mReplyTargetName = mOpThreadInfo.getName();
    		mEditTargetBody = mOpThreadInfo.getSelftext();
    		showDialog(Constants.DIALOG_EDIT);
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
        	Intent inboxIntent = new Intent(getApplicationContext(), InboxActivity.class);
        	startActivity(inboxIntent);
        	break;
//        case R.id.user_profile_menu_id:
//        	Intent profileIntent = new Intent(getApplicationContext(), UserActivity.class);
//        	startActivity(profileIntent);
//        	break;
    	case R.id.preferences_menu_id:
            Intent prefsIntent = new Intent(getApplicationContext(), RedditPreferencesPage.class);
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
    	
    	if (rowId == 0) {
    		;
    	} else if (mMorePositions.contains(rowId)) {
    		menu.add(0, Constants.DIALOG_GOTO_PARENT, Menu.NONE, "Go to parent");
    	} else if (mHiddenCommentHeads.contains(rowId)) {
    		menu.add(0, Constants.DIALOG_SHOW_COMMENT, Menu.NONE, "Show comment");
    		menu.add(0, Constants.DIALOG_GOTO_PARENT, Menu.NONE, "Go to parent");
    	} else {
    		synchronized (COMMENT_ADAPTER_LOCK) {
	    		if (mSettings.username != null && mSettings.username.equals(mCommentsAdapter.getItem(rowId).getAuthor())) {
	    			menu.add(0, Constants.DIALOG_EDIT, Menu.NONE, "Edit");
	    			menu.add(0, Constants.DIALOG_DELETE, Menu.NONE, "Delete");
	    		}
    		}
    		menu.add(0, Constants.DIALOG_HIDE_COMMENT, Menu.NONE, "Hide comment");
    		menu.add(0, Constants.DIALOG_GOTO_PARENT, Menu.NONE, "Go to parent");
    	}
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
    	case Constants.DIALOG_GOTO_PARENT:
    		synchronized (COMMENT_ADAPTER_LOCK) {
    			int myIndent = mCommentsAdapter.getItem(rowId).getIndent();
	    		int parentRowId;
	    		for (parentRowId = rowId - 1; parentRowId >= 0; parentRowId--)
	    			if (mCommentsAdapter.getItem(parentRowId).getIndent() < myIndent)
	    				break;
	    		getListView().setSelectionFromTop(parentRowId, 10);
    		}
    		return true;
    	case Constants.DIALOG_EDIT:
    		synchronized (COMMENT_ADAPTER_LOCK) {
	    		mReplyTargetName = mCommentsAdapter.getItem(rowId).getName();
	    		mEditTargetBody = mCommentsAdapter.getItem(rowId).getBody();
    		}
    		showDialog(Constants.DIALOG_EDIT);
    		return true;
    	case Constants.DIALOG_DELETE:
    		synchronized (COMMENT_ADAPTER_LOCK) {
    			mReplyTargetName = mCommentsAdapter.getItem(rowId).getName();
    		}
    		// It must be a comment, since the OP selftext is reached via options menu, not context menu
    		mDeleteTargetKind = Constants.COMMENT_KIND;
    		showDialog(Constants.DIALOG_DELETE);
    		return true;
		default:
    		return super.onContextItemSelected(item);	
    	}
    }
    
    private void hideComment(int rowId) {
    	mHiddenCommentHeads.add(rowId);
    	synchronized (COMMENT_ADAPTER_LOCK) {
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
    	getListView().setSelectionFromTop(rowId, 10);
    }
    
    private void showComment(int rowId) {
    	if (mHiddenCommentHeads.contains(rowId)) {
    		mHiddenCommentHeads.remove(rowId);
    	}
    	synchronized (COMMENT_ADAPTER_LOCK) {
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
    	getListView().setSelectionFromTop(rowId, 10);
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
        				CharSequence user = loginUsernameInput.getText().toString().trim();
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
    				CharSequence user = loginUsernameInput.getText().toString().trim();
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
    	case Constants.DIALOG_EDIT:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.compose_reply_dialog);
    		final EditText replyBody = (EditText) dialog.findViewById(R.id.body);
    		final Button replySaveButton = (Button) dialog.findViewById(R.id.reply_save_button);
    		final Button replyCancelButton = (Button) dialog.findViewById(R.id.reply_cancel_button);
    		if (id == Constants.DIALOG_REPLY) {
	    		replySaveButton.setOnClickListener(new OnClickListener() {
	    			public void onClick(View v) {
	    				if (mReplyTargetName != null) {
		    				new CommentReplyTask(mReplyTargetName).execute(replyBody.getText());
		    				dismissDialog(Constants.DIALOG_REPLY);
	    				}
	    				else {
	    					Common.showErrorToast("Error replying. Please try again.", Toast.LENGTH_SHORT, CommentsListActivity.this);
	    				}
	    			}
	    		});
	    		replyCancelButton.setOnClickListener(new OnClickListener() {
	    			public void onClick(View v) {
	    				dismissDialog(Constants.DIALOG_REPLY);
	    			}
	    		});
    		} else /* if (id == Constants.DIALOG_EDIT) */ {
    			replyBody.setText(mEditTargetBody);
    			replySaveButton.setOnClickListener(new OnClickListener() {
	    			public void onClick(View v) {
	    				if (mReplyTargetName != null) {
		    				new EditTask(mReplyTargetName).execute(replyBody.getText());
		    				dismissDialog(Constants.DIALOG_EDIT);
	    				}
	    				else {
	    					Common.showErrorToast("Error editing. Please try again.", Toast.LENGTH_SHORT, CommentsListActivity.this);
	    				}
	    			}
	    		});
    			replyCancelButton.setOnClickListener(new OnClickListener() {
        			public void onClick(View v) {
        				dismissDialog(Constants.DIALOG_EDIT);
        			}
        		});
    		}
    		break;
    		
    	case Constants.DIALOG_DELETE:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("Really delete this?");
    		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_DELETE);
    				new DeleteTask(mDeleteTargetKind).execute(mReplyTargetName);
    			}
    		})
    		.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
    		dialog = builder.create();
    		break;
    		
    	case Constants.DIALOG_SORT_BY:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("Sort by:");
    		builder.setSingleChoiceItems(Constants.CommentsSort.SORT_BY_CHOICES, 0, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_SORT_BY);
    				CharSequence itemCS = Constants.CommentsSort.SORT_BY_CHOICES[item];
    				if (Constants.CommentsSort.SORT_BY_BEST.equals(itemCS)) {
    					mSortByUrl = Constants.CommentsSort.SORT_BY_BEST_URL;
        			} else if (Constants.CommentsSort.SORT_BY_HOT.equals(itemCS)) {
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
    	case Constants.DIALOG_DELETING:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Deleting...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
    	case Constants.DIALOG_EDITING:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Submitting edit...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
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
    	case Constants.DIALOG_LOADING_COMMENTS_CACHE:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Loading cached comments...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case Constants.DIALOG_REPLYING:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Sending reply...");
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
    		if (mVoteTargetCommentInfo == null)
    			break;
    		String likes;
    		final TextView titleView = (TextView) dialog.findViewById(R.id.title);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final TextView submissionStuffView = (TextView) dialog.findViewById(R.id.submissionTime_submitter_subreddit);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
			if (mVoteTargetCommentInfo.getOP() != null) {
				likes = mVoteTargetCommentInfo.getOP().getLikes();
    			titleView.setVisibility(View.VISIBLE);
    			titleView.setText(mOpThreadInfo.getTitle().replaceAll("\n ", " ").replaceAll(" \n", " ").replaceAll("\n", " "));
    			urlView.setVisibility(View.VISIBLE);
    			urlView.setText(mOpThreadInfo.getURL());
    			submissionStuffView.setVisibility(View.VISIBLE);
        		sb = new StringBuilder(Util.getTimeAgo(Double.valueOf(mOpThreadInfo.getCreatedUtc())))
	    			.append(" by ").append(mOpThreadInfo.getAuthor());
        		submissionStuffView.setText(sb);
    			// For self posts, you're already there!
    			if (("self.").toLowerCase().equals(mOpThreadInfo.getDomain().substring(0, 5).toLowerCase())) {
    				linkButton.setText(R.string.comment_links_button);
    				linkToEmbeddedURLs(linkButton);
    			} else {
    				final String url = mOpThreadInfo.getURL();
    				linkButton.setText(R.string.thread_link_button);
	    			linkButton.setOnClickListener(new OnClickListener() {
	    				public void onClick(View v) {
	    					dismissDialog(Constants.DIALOG_THING_CLICK);
	    					// Launch Intent to goto the URL
	    					Common.launchBrowser(url, CommentsListActivity.this);
	    				}
	    			});
	    			linkButton.setVisibility(View.VISIBLE);
    			}
    		} else {
    			titleView.setText("Comment by " + mVoteTargetCommentInfo.getAuthor());
    			likes = mVoteTargetCommentInfo.getLikes();
    			urlView.setVisibility(View.INVISIBLE);
    			submissionStuffView.setVisibility(View.INVISIBLE);

    			// Get embedded URLs
    			linkToEmbeddedURLs(linkButton);
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
	    		voteUpButton.setOnCheckedChangeListener(voteUpOnCheckedChangeListener);
	    		voteDownButton.setOnCheckedChangeListener(voteDownOnCheckedChangeListener);

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
    		if (mVoteTargetCommentInfo != null && mVoteTargetCommentInfo.getReplyDraft() != null) {
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
    
    /**
     * Helper function to add links from mVoteTargetCommentInfo to the button
     * @param linkButton Button that should open list of links
     */
    private void linkToEmbeddedURLs(Button linkButton) {
		final ArrayList<String> urls = new ArrayList<String>();
		final ArrayList<MarkdownURL> vtUrls;
		final ThreadInfo opTi = mVoteTargetCommentInfo.getOP();
		if (opTi != null)
			vtUrls = opTi.mUrls;
		else
			vtUrls = mVoteTargetCommentInfo.mUrls;
		int urlsCount = vtUrls.size();
		for (int i = 0; i < urlsCount; i++)
			urls.add(vtUrls.get(i).url);
        if (urlsCount == 0) {
			linkButton.setVisibility(View.INVISIBLE);
        } else {
        	linkButton.setVisibility(View.VISIBLE);
        	linkButton.setText(R.string.comment_links_button);
        	linkButton.setOnClickListener(new OnClickListener() {
        		public void onClick(View v) {
        			dismissDialog(Constants.DIALOG_THING_CLICK);
        			
    	            ArrayAdapter<String> adapter = 
    	                new ArrayAdapter<String>(CommentsListActivity.this, android.R.layout.select_dialog_item, urls) {
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

    	            AlertDialog.Builder b = new AlertDialog.Builder(CommentsListActivity.this);

    	            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
    	                public final void onClick(DialogInterface dialog, int which) {
    	                    if (which >= 0) {
    	                        Common.launchBrowser(urls.get(which), CommentsListActivity.this);
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
    
    private final CompoundButton.OnCheckedChangeListener voteUpOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
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
    };
    private final CompoundButton.OnCheckedChangeListener voteDownOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
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
    };
    
    
    private class ReadCacheTask extends AsyncTask<Void, Void, Boolean> {
    	@Override
    	public Boolean doInBackground(Void... zzz) {
    		if (!mShouldUseCommentsCache)
    			return false;
    		FileInputStream fis = null;
    		ObjectInputStream in = null;
    		try {
    			// read the time
    			fis = openFileInput(Constants.FILENAME_CACHE_TIME);
    			in = new ObjectInputStream(fis);
    			mLastRefreshTime = in.readLong();
    			in.close();
    			fis.close();
    			
    			// Restore previous session from cache, if the cache isn't too old
    		    if (Common.isFreshCache(mLastRefreshTime)) {
        			fis = openFileInput(Constants.FILENAME_COMMENTS_CACHE);
        			in = new ObjectInputStream(fis);
        			
        			mCommentsList = (ArrayList<CommentInfo>) in.readObject();
        			// Process nonserializable (transient) members of the CommentInfos
        			for (CommentInfo ci : mCommentsList) {
        				ci.mSSBBody = markdown.markdown(ci.getBody(), new SpannableStringBuilder(), ci.mUrls); 
        			}
        			mJumpToCommentId = (CharSequence) in.readObject();
        			mJumpToCommentPosition = in.readInt();
        			mMorePositions = (HashSet<Integer>) in.readObject();
        			mNumVisibleComments = in.readInt();
        			mOpThreadInfo = (ThreadInfo) in.readObject();
        			mOpThreadInfo.mSSBSelftext = markdown.markdown(mOpThreadInfo.getSelftext(), new SpannableStringBuilder(), mOpThreadInfo.mUrls);
    		    	mSettings.setSubreddit((CharSequence) in.readObject());
    				mSettings.setThreadId((CharSequence) in.readObject());
    				mSortByUrl = (CharSequence) in.readObject();
    				mThreadTitle = (CharSequence) in.readObject();
    				
    				return true;
    		    }
    		    // Cache is old
    		    return false;
    		} catch (Exception ex) {
    			if (Constants.LOGGING) Log.e(TAG, "ReadCacheTask exception: " + ex.getMessage());
    			deleteFile(Constants.FILENAME_COMMENTS_CACHE);
    			return false;
    		} finally {
	    		try {
	    			in.close();
	    		} catch (Exception ignore) {}
	    		try {
	    			fis.close();
	    		} catch (Exception ignore) {}
    		}
    	}
    	
    	@Override
    	public void onPreExecute() {
    		synchronized (mCurrentDownloadCommentsTaskLock) {
	    		if (mCurrentDownloadCommentsTask != null)
	    			mCurrentDownloadCommentsTask.cancel(true);
	    		mCurrentDownloadCommentsTask = this;
    		}
    		showDialog(Constants.DIALOG_LOADING_COMMENTS_CACHE);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		try {
    			dismissDialog(Constants.DIALOG_LOADING_COMMENTS_CACHE);
    		} catch (Exception e) {
    			// Ignore. Probably caused by screen rotation.
    		}
    		if (success) {
    			// Use the cached comments list
		    	resetUI(new CommentsListAdapter(CommentsListActivity.this, mCommentsList));
		    	setTitle(mThreadTitle + " : " + mSettings.subreddit);
		    	// Point the list to whichever comment the user was looking at
		    	jumpToComment();
    		} else {
    			//Common.showErrorToast("Reading comments cache failed.", Toast.LENGTH_SHORT, CommentsListActivity.this);
    			// Download from Internet if DownloadCommentsTask params are available
    			if (mSettings.threadId != null) {
    				new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    			} else {
	    			// Quit, because the Comments List requires subreddit and thread id from Intent.
	    			if (Constants.LOGGING) Log.e(TAG, "Quitting because of bad comment URL context and no cache.");
	    			deleteFile(Constants.FILENAME_COMMENTS_CACHE);
	    			finish();
    			}
    		}
    	}
    }
    

    
    @Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putCharSequence(Constants.CommentsSort.SORT_BY_KEY, mSortByUrl);
    	state.putInt(Constants.JUMP_TO_COMMENT_POSITION_KEY, mJumpToCommentPosition);
    	state.putCharSequence(Constants.JUMP_TO_COMMENT_ID_KEY, mJumpToCommentId);
    	state.putCharSequence(Constants.REPLY_TARGET_NAME_KEY, mReplyTargetName);
    	state.putCharSequence(Constants.EDIT_TARGET_BODY_KEY, mEditTargetBody);
    	state.putString(Constants.DELETE_TARGET_KIND_KEY, mDeleteTargetKind);
    	
    	// Cache
		if (mCommentsList == null || mSettings.threadId == null)
			return;
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			// write the time
			fos = openFileOutput(Constants.FILENAME_CACHE_TIME, MODE_PRIVATE);
			out = new ObjectOutputStream(fos);
			out.writeLong(mLastRefreshTime);
			out.close();
			fos.close();
		} catch (IOException ex) {
			if (Constants.LOGGING) Log.e(TAG, ex.getLocalizedMessage());
			Common.deleteAllCaches(getApplicationContext());
		} finally {
			try {
				out.close();
			} catch (Exception ignore) {}
			try {
				fos.close();
			} catch (Exception ignore) {}			
		}
		
		try {
			// Write cache variables in alphabetical order by variable name.
			fos = openFileOutput(Constants.FILENAME_COMMENTS_CACHE, MODE_PRIVATE);
			out = new ObjectOutputStream(fos);
			out.writeObject(mCommentsList);
			out.writeObject(mJumpToCommentId);
		    out.writeInt(mJumpToCommentPosition);
		    out.writeObject(mMorePositions);
			out.writeInt(mNumVisibleComments);
			out.writeObject(mOpThreadInfo);
			out.writeObject(mSettings.subreddit);
			out.writeObject(mSettings.threadId);
			out.writeObject(mSortByUrl);
			out.writeObject(mThreadTitle);
		} catch (IOException ex) {
			if (Constants.LOGGING) Log.e(TAG, ex.getLocalizedMessage());
			deleteFile(Constants.FILENAME_COMMENTS_CACHE);
		} finally {
			try {
				out.close();
			} catch (Exception ignore) {}
			try {
				fos.close();
			} catch (Exception ignore) {}			
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
        final int[] myDialogs = {
        	Constants.DIALOG_DELETE,
        	Constants.DIALOG_DELETING,
        	Constants.DIALOG_EDIT,
        	Constants.DIALOG_EDITING,
        	Constants.DIALOG_LOADING_COMMENTS_CACHE,
        	Constants.DIALOG_LOADING_COMMENTS_LIST,
        	Constants.DIALOG_LOGGING_IN,
        	Constants.DIALOG_LOGIN,
        	Constants.DIALOG_REPLY,
        	Constants.DIALOG_REPLYING,
        	Constants.DIALOG_SORT_BY,
        	Constants.DIALOG_THING_CLICK,
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
