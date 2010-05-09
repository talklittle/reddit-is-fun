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
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
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
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
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
	
    // Group 2: subreddit name. Group 3: thread id36. Group 4: Comment id36.
    private final Pattern COMMENT_CONTEXT_PATTERN = Pattern.compile("(http://www.reddit.com)?/r/(.+?)/comments/(.+?)/.+?/(?:([a-zA-Z0-9]+)(?:\\?context=(\\d+))?)?");

    private final ObjectMapper om = new ObjectMapper();
    private final DrawableManager drawableManager = new DrawableManager();
    private final Markdown markdown = new Markdown();
    
    /** Custom list adapter that fits our threads data into the list. */
    private CommentsListAdapter mCommentsAdapter = null;
    private ArrayList<ThingInfo> mCommentsList = null;
    // Lock used when modifying the mCommentsAdapter
    private static final Object COMMENT_ADAPTER_LOCK = new Object();
    
    private final DefaultHttpClient mClient = Common.getGzipHttpClient();
    
    
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
    private CharSequence mJumpToCommentId = null;
    private int mJumpToCommentContext = 0;
    private int mJumpToCommentPosition = 0;
//    private CharSequence mMoreChildrenId = "";
    private HashSet<Integer> mMorePositions = new HashSet<Integer>();
    private int mNumVisibleComments = Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT;
    private ThingInfo mOpThingInfo = null;
    private CharSequence mSortByUrl = Constants.CommentsSort.SORT_BY_BEST_URL;
    private CharSequence mThreadTitle = null;
	
    // Keep track of the row ids of comments that user has hidden
    private HashSet<Integer> mHiddenCommentHeads = new HashSet<Integer>();
    // Keep track of the row ids of descendants of hidden comment heads
    private HashSet<Integer> mHiddenComments = new HashSet<Integer>();
    
    // UI State
    private ThingInfo mVoteTargetThing = null;
    private CharSequence mReportTargetName = null;
    private CharSequence mReplyTargetName = null;
    private CharSequence mEditTargetBody = null;
    private String mDeleteTargetKind = null;
    private AsyncTask<?, ?, ?> mCurrentDownloadCommentsTask = null;
    private final Object mCurrentDownloadCommentsTaskLock = new Object();
    
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
    	
        setContentView(R.layout.comments_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().
        
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
        	if (Constants.LOGGING) Log.e(TAG, "Quitting because no subreddit and thread id data was passed into the Intent.");
			finish();
        }
    	// Comment context: a URL pointing directly at a comment, versus a thread
    	String commentContext = extras.getString(Constants.EXTRA_COMMENT_CONTEXT);
    	if (commentContext != null) {
    		if (Constants.LOGGING) Log.d(TAG, "comment context: "+commentContext);
    		
    		Matcher commentContextMatcher = COMMENT_CONTEXT_PATTERN.matcher(commentContext);
    		if (commentContextMatcher.find()) {
        		mSettings.setSubreddit(commentContextMatcher.group(2));
    			mSettings.setThreadId(commentContextMatcher.group(3));
    			mJumpToCommentId = commentContextMatcher.group(4);
    			mJumpToCommentContext = commentContextMatcher.group(5) != null ? Integer.valueOf(commentContextMatcher.group(5)) : 0;
    		} else {
    			if (Constants.LOGGING) Log.e(TAG, "Quitting because of bad comment context.");
    			finish();
    		}
    	} else {
        	mSettings.setThreadId(extras.getString(Constants.EXTRA_ID));
        	mSettings.setSubreddit(extras.getString(Constants.EXTRA_SUBREDDIT));
        	mThreadTitle = extras.getString(Constants.EXTRA_TITLE);
        	if (mThreadTitle != null) {
        	  setTitle(mThreadTitle + " : " + mSettings.subreddit);
        	} else {
        	  setTitle("reddit is fun");
        	}
        	int numComments = extras.getInt(Constants.EXTRA_NUM_COMMENTS);
        	// TODO: Take into account very negative karma comments
        	if (numComments < Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT)
        		mNumVisibleComments = numComments;
        	else
        		mNumVisibleComments = Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT;
    	}
    	
    	if (savedInstanceState != null) {
        	mSortByUrl = savedInstanceState.getCharSequence(Constants.CommentsSort.SORT_BY_KEY);
       		mJumpToCommentId = savedInstanceState.getCharSequence(Constants.JUMP_TO_COMMENT_ID_KEY);
       		mJumpToCommentContext = savedInstanceState.getInt(Constants.JUMP_TO_COMMENT_CONTEXT_KEY);
        	mReplyTargetName = savedInstanceState.getCharSequence(Constants.REPLY_TARGET_NAME_KEY);
        	mReportTargetName = savedInstanceState.getCharSequence(Constants.REPORT_TARGET_NAME_KEY);
        	mEditTargetBody = savedInstanceState.getCharSequence(Constants.EDIT_TARGET_BODY_KEY);
        	mDeleteTargetKind = savedInstanceState.getString(Constants.DELETE_TARGET_KIND_KEY);
        	mJumpToCommentPosition = savedInstanceState.getInt(Constants.JUMP_TO_COMMENT_POSITION_KEY);
        	// savedInstanceState means probably rotated screen or something.
    	}
    	
    	new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
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
    		setListAdapter(mCommentsAdapter);
    		getListView().setDivider(null);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
	    new Common.PeekEnvelopeTask(this, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
    }
    

    private final class CommentsListAdapter extends ArrayAdapter<ThingInfo> {
    	static final int OP_ITEM_VIEW_TYPE = 0;
    	static final int COMMENT_ITEM_VIEW_TYPE = 1;
    	static final int MORE_ITEM_VIEW_TYPE = 2;
    	static final int HIDDEN_ITEM_HEAD_VIEW_TYPE = 3;
    	// The number of view types
    	static final int VIEW_TYPE_COUNT = 4;
    	
    	public boolean mIsLoading = true;
    	
    	private LayoutInflater mInflater;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
        
        public CommentsListAdapter(Context context, List<ThingInfo> objects) {
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
            
            ThingInfo item = this.getItem(position);
            
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
	                TextView nsfwView = (TextView) view.findViewById(R.id.nsfw);
	                TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
	                TextView submitterView = (TextView) view.findViewById(R.id.submitter);
	                ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
	                ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
	                TextView selftextView = (TextView) view.findViewById(R.id.selftext);
	                ImageView thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
	                View dividerView = view.findViewById(R.id.divider);
	                ProgressBar indeterminateProgressBar = (ProgressBar) view.findViewById(R.id.indeterminate_progress);
	                
	                submitterView.setVisibility(View.VISIBLE);
	                submissionTimeView.setVisibility(View.VISIBLE);
	                
	                // Set the title and domain using a SpannableStringBuilder
	                SpannableStringBuilder builder = new SpannableStringBuilder();
	                String title = mOpThingInfo.getTitle();
	                SpannableString titleSS = new SpannableString(title);
	                int titleLen = title.length();
	                TextAppearanceSpan titleTAS = new TextAppearanceSpan(getApplicationContext(), R.style.TextAppearance_14sp);
	                titleSS.setSpan(titleTAS, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	                if (mSettings.theme == R.style.Reddit_Light) {
	                	// FIXME: This doesn't work persistently, since "clicked" is not delivered to reddit.com
	    	            if (mOpThingInfo.isClicked()) {
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
	                SpannableString domainSS = new SpannableString("("+mOpThingInfo.getDomain()+")");
	                TextAppearanceSpan domainTAS = new TextAppearanceSpan(getApplicationContext(), R.style.TextAppearance_10sp);
	                domainSS.setSpan(domainTAS, 0, mOpThingInfo.getDomain().length()+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	                builder.append(domainSS);
	                titleView.setText(builder);
	                
	                votesView.setText("" + mOpThingInfo.getScore());
	                numCommentsView.setText(Util.showNumComments(mOpThingInfo.getNum_comments()));
	                subredditView.setText(mOpThingInfo.getSubreddit());
	                submissionTimeView.setText(Util.getTimeAgo(mOpThingInfo.getCreated_utc()));
	                submitterView.setText("by "+mOpThingInfo.getAuthor());
	                
	                if(item.isOver_18()){
	                    nsfwView.setVisibility(View.VISIBLE);
	                } else {
	                    nsfwView.setVisibility(View.GONE);
	                }
	                
		            // Set the up and down arrow colors based on whether user likes
	                if (mSettings.loggedIn) {
	                	if (mOpThingInfo.getLikes() == null) {
	                		voteUpView.setImageResource(R.drawable.vote_up_gray);
	                		voteDownView.setImageResource(R.drawable.vote_down_gray);
	                		votesView.setTextColor(res.getColor(R.color.gray));
	                	} else if (mOpThingInfo.getLikes() == true) {
	                		voteUpView.setImageResource(R.drawable.vote_up_red);
	                		voteDownView.setImageResource(R.drawable.vote_down_gray);
	                		votesView.setTextColor(res.getColor(R.color.arrow_red));
	                	} else {
	                		voteUpView.setImageResource(R.drawable.vote_up_gray);
	                		voteDownView.setImageResource(R.drawable.vote_down_blue);
	                		votesView.setTextColor(res.getColor(R.color.arrow_blue));
	                	}
	                } else {
	            		voteUpView.setImageResource(R.drawable.vote_up_gray);
	            		voteDownView.setImageResource(R.drawable.vote_down_gray);
	            		votesView.setTextColor(res.getColor(R.color.gray));
	                }
	                
	                // --- End part copied from ThreadsListAdapter ---
	                
	            	if (mOpThingInfo.getSelftext() != null
	            			&& !Constants.EMPTY_STRING.equals(mOpThingInfo.getSelftext())) {
	            		selftextView.setVisibility(View.VISIBLE);
		                selftextView.setText(mOpThingInfo.getSSBSelftext());
	            	} else {
	            		selftextView.setVisibility(View.GONE);
	            	}
	            	
		            // Thumbnails open links
		            if (thumbnailView != null) {
		            	if (mSettings.loadThumbnails) {
		            		dividerView.setVisibility(View.VISIBLE);
		            		thumbnailView.setVisibility(View.VISIBLE);
		            		indeterminateProgressBar.setVisibility(View.GONE);
		            		
		            		// Fill in the thumbnail using a Thread. Note that thumbnail URL can be absolute path.
			            	if (item.getThumbnail() != null && !Constants.EMPTY_STRING.equals(item.getThumbnail())) {
			            		drawableManager.fetchDrawableOnThread(Util.absolutePathToURL(item.getThumbnail()),
			            				thumbnailView, indeterminateProgressBar, CommentsListActivity.this);
			            	} else {
			            		// if no thumbnail image, hide thumbnail icon
			            		dividerView.setVisibility(View.GONE);
			            		thumbnailView.setVisibility(View.GONE);
			            		indeterminateProgressBar.setVisibility(View.GONE);
			            	}
			            	
			            	// Set thumbnail background based on current theme
			            	if (mSettings.theme == R.style.Reddit_Light) {
			            		thumbnailView.setBackgroundResource(R.drawable.thumbnail_background_light);
			            		indeterminateProgressBar.setBackgroundResource(R.drawable.thumbnail_background_light);
			            	} else {
			            		thumbnailView.setBackgroundResource(R.drawable.thumbnail_background_dark);
			            		indeterminateProgressBar.setBackgroundResource(R.drawable.thumbnail_background_dark);
			            	}
		            	} else {
		            		// if thumbnails disabled, hide thumbnail icon
		            		dividerView.setVisibility(View.GONE);
		            		thumbnailView.setVisibility(View.GONE);
		            		indeterminateProgressBar.setVisibility(View.GONE);
		            	}
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
		            	votesView.setText(Util.showNumPoints(item.getUps() - item.getDowns()));
		            } catch (NumberFormatException e) {
		            	// This happens because "ups" comes after the potentially long "replies" object,
		            	// so the ListView might try to display the View before "ups" in JSON has been parsed.
		            	if (Constants.LOGGING) Log.e(TAG, "NumberFormatException:" + e.getMessage());
		            }
		            if (mOpThingInfo != null && item.getAuthor().equals(mOpThingInfo.getAuthor()))
		            	submitterView.setText(item.getAuthor() + " [S]");
		            else
		            	submitterView.setText(item.getAuthor());
		            submissionTimeView.setText(Util.getTimeAgo(item.getCreated_utc()));
		            switch (item.getIndent()) {
		            case 0:  leftIndent.setText(""); break;
		            case 1:  leftIndent.setText("W"); break;
		            case 2:  leftIndent.setText("WW"); break;
		            case 3:  leftIndent.setText("WWW"); break;
		            case 4:  leftIndent.setText("WWWW"); break;
		            case 5:  leftIndent.setText("WWWWW"); break;
		            case 6:  leftIndent.setText("WWWWWW"); break;
		            case 7:  leftIndent.setText("WWWWWWW"); break;
		            default: leftIndent.setText("WWWWWWWW"); break;
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
		            case 1:  leftIndent.setText("W"); break;
		            case 2:  leftIndent.setText("WW"); break;
		            case 3:  leftIndent.setText("WWW"); break;
		            case 4:  leftIndent.setText("WWWW"); break;
		            case 5:  leftIndent.setText("WWWWW"); break;
		            case 6:  leftIndent.setText("WWWWWW"); break;
		            case 7:  leftIndent.setText("WWWWWWW"); break;
		            default: leftIndent.setText("WWWWWWWW"); break;
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
		            	votesView.setText(Util.showNumPoints(item.getUps() - item.getDowns()));
		            } catch (NumberFormatException e) {
		            	// This happens because "ups" comes after the potentially long "replies" object,
		            	// so the ListView might try to display the View before "ups" in JSON has been parsed.
		            	if (Constants.LOGGING) Log.e(TAG, "NumberFormatException:" + e.getMessage());
		            }
		            if (item.getSSAuthor() != null)
		            	submitterView.setText(item.getSSAuthor());
		            else
		            	submitterView.setText(item.getAuthor());
		            submissionTimeView.setText(Util.getTimeAgo(item.getCreated_utc()));
		            bodyView.setText(item.getSSBBody());
		            switch (item.getIndent()) {
		            case 0:  leftIndent.setText(""); break;
		            case 1:  leftIndent.setText("W"); break;
		            case 2:  leftIndent.setText("WW"); break;
		            case 3:  leftIndent.setText("WWW"); break;
		            case 4:  leftIndent.setText("WWWW"); break;
		            case 5:  leftIndent.setText("WWWWW"); break;
		            case 6:  leftIndent.setText("WWWWWW"); break;
		            case 7:  leftIndent.setText("WWWWWWW"); break;
		            default: leftIndent.setText("WWWWWWWW"); break;
		            }
		            
		            if ("[deleted]".equals(item.getAuthor())) {
		            	voteUpView.setVisibility(View.INVISIBLE);
		            	voteDownView.setVisibility(View.INVISIBLE);
		            }
		            // Set the up and down arrow colors based on whether user likes
		            else if (mSettings.loggedIn) {
		            	voteUpView.setVisibility(View.VISIBLE);
		            	voteDownView.setVisibility(View.VISIBLE);
		            	if (item.getLikes() == null) {
		            		voteUpView.setImageResource(R.drawable.vote_up_gray);
		            		voteDownView.setImageResource(R.drawable.vote_down_gray);
//		            		votesView.setTextColor(res.getColor(R.color.gray));
		            	} else if (item.getLikes() == true) {
		            		voteUpView.setImageResource(R.drawable.vote_up_red);
		            		voteDownView.setImageResource(R.drawable.vote_down_gray);
//		            		votesView.setTextColor(res.getColor(R.color.arrow_red));
		            	} else {
		            		voteUpView.setImageResource(R.drawable.vote_up_gray);
		            		voteDownView.setImageResource(R.drawable.vote_down_blue);
//		            		votesView.setTextColor(res.getColor(R.color.arrow_blue));
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
        ThingInfo item = mCommentsAdapter.getItem(position);
        
        if (mHiddenCommentHeads.contains(position)) {
        	showComment(position);
        	return;
        }
        
        // Mark the OP post/regular comment as selected
        mVoteTargetThing = item;
        mReplyTargetName = mVoteTargetThing.getName();
		
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
					ThingInfo item = mCommentsAdapter.getItem(k);
					if (mJumpToCommentId.equals(item.getId())) {
						// jump to comment with correct amount of context
						int targetIndex = k;
						int desiredIndent = item.getIndent() - mJumpToCommentContext;
						while (item.getIndent() > 0 && mCommentsAdapter.getItem(targetIndex).getIndent() != desiredIndent)
							targetIndex--;
						getListView().setSelectionFromTop(targetIndex, 10);
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
    	setContentView(R.layout.comments_list_content);
    	synchronized (COMMENT_ADAPTER_LOCK) {
	    	if (commentsAdapter == null) {
	    		// Reset the list to be empty.
	    		mCommentsList = new ArrayList<ThingInfo>();
	            mCommentsAdapter = new CommentsListAdapter(this, mCommentsList);
	    	} else {
	    		mCommentsAdapter = commentsAdapter;
	    	}
	        setListAdapter(mCommentsAdapter);
	        mCommentsAdapter.mIsLoading = false;
	        mCommentsAdapter.notifyDataSetChanged();  // Just in case
    	}
        getListView().setDivider(null);
        Common.updateListDrawables(this, mSettings.theme);
        mHiddenComments.clear();
        mHiddenCommentHeads.clear();
    }
    
    private void enableLoadingScreen() {
    	if (mSettings.theme == R.style.Reddit_Light) {
    		setContentView(R.layout.loading_light);
    	} else {
    		setContentView(R.layout.loading_dark);
    	}
    	synchronized (COMMENT_ADAPTER_LOCK) {
	    	if (mCommentsAdapter != null)
	    		mCommentsAdapter.mIsLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mCommentsAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
    }

    /**
     * Mark the OP submitter comments
     */
    private void markSubmitterComments() {
    	if (mOpThingInfo == null || mCommentsAdapter == null)
    		return;
    	
		SpannableString authorSS = new SpannableString(mOpThingInfo.getAuthor() + " [S]");
        ForegroundColorSpan fcs;
        if (mSettings.theme == R.style.Reddit_Light)
        	fcs = new ForegroundColorSpan(getResources().getColor(R.color.blue));
        else
        	fcs = new ForegroundColorSpan(getResources().getColor(R.color.pale_blue));
        authorSS.setSpan(fcs, 0, authorSS.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	
        synchronized (COMMENT_ADAPTER_LOCK) {
    		for (int i = 0; i < mCommentsAdapter.getCount(); i++) {
    			ThingInfo ci = mCommentsAdapter.getItem(i);
    			// if it's the OP, mark his name
    			if (mOpThingInfo.getAuthor().equals(ci.getAuthor()))
    	            ci.setSSAuthor(authorSS);
    		}
    	}
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
    class DownloadCommentsTask extends AsyncTask<Integer, Long, Boolean>
    		implements PropertyChangeListener {
    	
    	// _mPositionOffset != 0 means that you're doing "load more comments"
    	private int _mPositionOffset;
    	private int _mIndentation;
    	private int _mListIndex = 0;
    	private String _mMoreChildrenId;
    	// Temporary storage for new "load more comments" found in the JSON
    	private HashSet<Integer> _mNewMorePositions = new HashSet<Integer>();
    	private ArrayList<ThingInfo> _mNewThingInfos = new ArrayList<ThingInfo>();
    	// Progress bar
    	private long _mContentLength = 0;
    	
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
	        	if (mJumpToCommentContext != 0)
	        		sb.append("context="+mJumpToCommentContext+"&");
	        	
	        	String url = sb.toString();
	        	
	        	InputStream in = null;
	    		boolean currentlyUsingCache = false;
	    		
	        	if (Constants.USE_CACHE) {
	    			try {
		    			if (CacheInfo.checkFreshThreadCache(getApplicationContext())
		    					&& url.equals(CacheInfo.getCachedThreadUrl(getApplicationContext()))) {
		    				in = openFileInput(Constants.FILENAME_THREAD_CACHE);
		    				_mContentLength = getFileStreamPath(Constants.FILENAME_THREAD_CACHE).length();
		    				currentlyUsingCache = true;
		    				if (Constants.LOGGING) Log.d(TAG, "Using cached thread JSON, length=" + _mContentLength);
		    			}
	    			} catch (Exception cacheEx) {
	    				if (Constants.LOGGING) Log.w(TAG, "skip cache because of: "+cacheEx.getMessage());
	    			}
	    		}
	    		
	    		// If we couldn't use the cache, then do HTTP request
	        	if (!currentlyUsingCache) {
			    	HttpGet request = new HttpGet(url);
	                HttpResponse response = mClient.execute(request);
	            	
	                // Read the header to get Content-Length since entity.getContentLength() returns -1
	            	Header contentLengthHeader = response.getFirstHeader("Content-Length");
	            	_mContentLength = Long.valueOf(contentLengthHeader.getValue());
	            	if (Constants.LOGGING) Log.d(TAG, "Content length: "+_mContentLength);
	
	            	entity = response.getEntity();
	            	in = entity.getContent();
	            	
	            	if (Constants.USE_CACHE) {
	                	in = CacheInfo.writeThenRead(getApplicationContext(), in, Constants.FILENAME_THREAD_CACHE);
	                	try {
	                		CacheInfo.setCachedThreadUrl(getApplicationContext(), url);
	                	} catch (IOException e) {
	                		if (Constants.LOGGING) Log.e(TAG, "error on setCachedThreadId: " + e.getMessage());
	                	}
	            	}
	        	}
                
            	// setup a special InputStream to report progress
            	ProgressInputStream pin = new ProgressInputStream(in, _mContentLength);
            	pin.addPropertyChangeListener(this);
            	
            	parseCommentsJSON(pin);
            	if (Constants.LOGGING) Log.d(TAG, "parseCommentsJSON completed");
                
            	pin.close();
                in.close();
                
                // Fill in the list adapter
                synchronized (COMMENT_ADAPTER_LOCK) {
                	// Insert the new comments
                	if (_mPositionOffset == 0) {
                		// insert comments after OP
                		mCommentsList.addAll(_mNewThingInfos);
                	} else {
                		int numNewComments = _mNewThingInfos.size();
                		int numRemoved = 0;
    					// First remove the "load more comments" entry
    					if (mMorePositions.remove(_mPositionOffset)) {
    						mCommentsList.remove(_mPositionOffset);
    						numRemoved++;
    					}
                		// Update existing positions in the "more" positions set.
    					for (int i = _mPositionOffset + 1; i < mCommentsList.size() + numRemoved; i++) {
    						if (mMorePositions.remove(i))
    							_mNewMorePositions.add(i + numNewComments - numRemoved);
    					}
    					// insert comments at _mPositionOffset
                		mCommentsList.addAll(_mPositionOffset, _mNewThingInfos);
                	}
                	// Merge the new "load more comments" positions
		    		mMorePositions.addAll(_mNewMorePositions);
		    	}
                // label the OP's comments with [S]
                markSubmitterComments();
				
                return true;
                
            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "DownloadCommentsTask:" + e.getMessage());
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
    	
    	private void parseCommentsJSON(InputStream in)
				throws IOException, JsonParseException, IllegalStateException {
			
			String genericListingError = "Not a comments listing";
			try {
				Listing[] listings = om.readValue(in, Listing[].class);

				// listings[0] is a thread Listing for the OP.
				// process same as a thread listing more or less
				
				if (!Constants.JSON_LISTING.equals(listings[0].getKind()))
					throw new IllegalStateException(genericListingError);
				
				// Save modhash, ignore "after" and "before" which are meaningless in this context (and probably null)
				ListingData threadListingData = listings[0].getData();
				if (Constants.EMPTY_STRING.equals(threadListingData.getModhash()))
					mSettings.setModhash(null);
				else
					mSettings.setModhash(threadListingData.getModhash());
				
				if (Constants.LOGGING) Log.d(TAG, "Successfully got OP listing[0]: modhash "+mSettings.modhash);
				
				ThingListing threadThingListing = threadListingData.getChildren()[0];
				if (!Constants.THREAD_KIND.equals(threadThingListing.getKind()))
					throw new IllegalStateException(genericListingError);

				// For comments OP, should be only one
				if (_mPositionOffset == 0) {
					mOpThingInfo = threadThingListing.getData();
					mOpThingInfo.setIndent(0);
					synchronized (COMMENT_ADAPTER_LOCK) {
						mCommentsList.add(0, mOpThingInfo);
					}
				}
				// Pull other data from the OP
				mOpThingInfo.setTitle(StringEscapeUtils.unescapeHtml(mOpThingInfo.getTitle().trim()
						.replaceAll("\r", "").replaceAll("\n ", " ").replaceAll(" \n", " ").replaceAll("\n", " ")));
				// do markdown
				mOpThingInfo.setSelftext(StringEscapeUtils.unescapeHtml(mOpThingInfo.getSelftext().trim().replaceAll("\r", "")));
    			mOpThingInfo.setSSBSelftext(markdown.markdown(mOpThingInfo.getSelftext(), new SpannableStringBuilder(), mOpThingInfo.getUrls()));
				// We might not have a title if we've intercepted a plain link to a thread.
    			mThreadTitle = mOpThingInfo.getTitle();
				mSettings.setSubreddit(mOpThingInfo.getSubreddit());
				
				// listings[1] is a comment Listing for the comments
				// Go through the children and get the ThingInfos
				
				ListingData commentListingData = listings[1].getData();
				for (ThingListing commentThingListing : commentListingData.getChildren()) {
					// insert the comment and its replies, prefix traversal order
					insertNestedComment(commentThingListing, 0);
				}
			} catch (Exception ex) {
				if (Constants.LOGGING) Log.e(TAG, "parseCommentsJSON:" + ex.getMessage());
			}
		}
    	
    	/**
    	 * Recursive method to insert comment tree into the mCommentsList,
    	 * with proper list order and indentation
    	 */
    	void insertNestedComment(ThingListing commentThingListing, int indentLevel) {
    		ThingInfo ci = commentThingListing.getData();
    		ci.setIndent(_mIndentation + indentLevel);

    		// Insert the comment
    		_mListIndex++;
	    	_mNewThingInfos.add(ci);
    		
    		// handle "more" entry
    		if (Constants.MORE_KIND.equals(commentThingListing.getKind())) {
    			_mNewMorePositions.add(_mPositionOffset + _mListIndex);
    			if (Constants.LOGGING) Log.v(TAG, "new more position at " + (_mPositionOffset + _mListIndex));
		    	return;
    		}
    		
    		// Regular comment
    		
    		// Skip things that are not comments, which shouldn't happen
			if (!Constants.COMMENT_KIND.equals(commentThingListing.getKind())) {
				if (Constants.LOGGING) Log.e(TAG, "comment whose kind is \""+commentThingListing.getKind()+"\" (expected "+Constants.COMMENT_KIND+")");
				return;
			}
			
	        // do markdown
    		ci.setBody(StringEscapeUtils.unescapeHtml(ci.getBody().trim().replaceAll("\r", "")));
			ci.setSSBBody(markdown.markdown(ci.getBody(), new SpannableStringBuilder(), ci.getUrls()));
			
			// handle the replies
			Listing repliesListing = ci.getReplies();
			if (repliesListing == null)
				return;
			ListingData repliesListingData = repliesListing.getData();
			if (repliesListingData == null)
				return;
			ThingListing[] replyThingListings = repliesListingData.getChildren();
			if (replyThingListings == null)
				return;
			for (ThingListing replyThingListing : replyThingListings) {
				insertNestedComment(replyThingListing, indentLevel + 1);
			}
    	}
    	
    	public void onPreExecute() {
    		if (mSettings.threadId == null)
	    		this.cancel(true);
    		synchronized (mCurrentDownloadCommentsTaskLock) {
	    		if (mCurrentDownloadCommentsTask != null) {
	    			this.cancel(true);
	    			return;
	    		}
	    		mCurrentDownloadCommentsTask = this;
    		}
    		
    		// Initialize mCommentsList and mCommentsAdapter
    		synchronized (COMMENT_ADAPTER_LOCK) {
	    		if (_mPositionOffset == 0)
	    			resetUI(null);
    		}

    		// Do loading screen when loading new thread; otherwise when "loading more comments" don't show it
    		if (_mPositionOffset == 0)
    			enableLoadingScreen();
    		
	    	if (mThreadTitle != null)
	    		setTitle(mThreadTitle + " : " + mSettings.subreddit);
    	}
    	
    	public void onPostExecute(Boolean success) {
    		synchronized (mCurrentDownloadCommentsTaskLock) {
    			mCurrentDownloadCommentsTask = null;
    		}
    		
    		// if loading new thread, disable loading screen. otherwise "load more comments" so just stop the progress bar
    		if (_mPositionOffset == 0)
    			disableLoadingScreen();
    		else
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
    		
    		if (success) {
    			// We modified mCommentsList, which backs mCommentsAdapter, so mCommentsAdapter has changed too.
    			mCommentsAdapter.notifyDataSetChanged();
    			// Set title in android titlebar
    			if (mThreadTitle != null)
    				setTitle(mThreadTitle + " : " + mSettings.subreddit);
	    		// Point the list to last comment user was looking at, if any
	    		jumpToComment();
    		} else {
    			if (!isCancelled())
    				Common.showErrorToast("Error downloading comments. Please try again.", Toast.LENGTH_LONG, CommentsListActivity.this);
    		}
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
        			Common.doLogout(mSettings, mClient, getApplicationContext());
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
    	    	entity = response.getEntity();
    	    	
            	// Getting here means success. Create a new CommentInfo.
            	return Common.checkIDResponse(response, entity);
            	
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "CommentReplyTask:" + e.getMessage());
        		_mUserError = e.getMessage();
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
    			CacheInfo.invalidateCachedThread(getApplicationContext());
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
        	
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to edit.";
        		return null;
        	}
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient, getApplicationContext());
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
    	    	entity = response.getEntity();
    	    	
    	    	return Common.checkIDResponse(response, entity);
            	
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "EditTask:" + e.getMessage());
        		_mUserError = e.getMessage();
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
    			CacheInfo.invalidateCachedThread(getApplicationContext());
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
        	
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to delete.";
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
            	entity = response.getEntity();

            	String error = Common.checkResponseErrors(response, entity);
            	if (error != null)
            		throw new Exception(error);

            	// Success
            	return true;
            	
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "DeleteTask:" + e.getMessage());
        		_mUserError = e.getMessage();
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
    		showDialog(Constants.DIALOG_DELETING);
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_DELETING);
    		if (success) {
    			CacheInfo.invalidateCachedThread(getApplicationContext());
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
    	private ThingInfo _mTargetThingInfo;
    	
    	// Save the previous arrow and score in case we need to revert
    	private int _mPreviousUps, _mPreviousDowns;
    	private Boolean _mPreviousLikes;
    	
    	VoteTask(CharSequence thingFullname, int direction) {
    		_mThingFullname = thingFullname;
    		_mDirection = direction;
    		// Copy these because they can change while voting thread is running
    		_mTargetThingInfo = mVoteTargetThing;
    	}
    	
    	@Override
    	public Boolean doInBackground(Void... v) {
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
        			Common.doLogout(mSettings, mClient, getApplicationContext());
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
            	entity = response.getEntity();

            	String error = Common.checkResponseErrors(response, entity);
            	if (error != null)
            		throw new Exception(error);

            	return true;
        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, "VoteTask:" + e.getMessage());
        		_mUserError = e.getMessage();
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
        	Boolean newLikes;
        	_mPreviousUps = Integer.valueOf(_mTargetThingInfo.getUps());
        	_mPreviousDowns = Integer.valueOf(_mTargetThingInfo.getDowns());
    	    newUps = _mPreviousUps;
    	    newDowns = _mPreviousDowns;
    	    _mPreviousLikes = _mTargetThingInfo.getLikes();
        	
    	    if (_mPreviousLikes == null) {
	    		if (_mDirection == 1) {
	    			newUps = _mPreviousUps + 1;
	    			newLikes = true;
	    		} else if (_mDirection == -1) {
	    			newDowns = _mPreviousDowns + 1;
	    			newLikes = false;
	    		} else {
	    			cancel(true);
	    			return;
	    		}
    	    } else if (_mPreviousLikes == true) {
    	    	if (_mDirection == 0) {
	    			newUps = _mPreviousUps - 1;
	    			newLikes = null;
	    		} else if (_mDirection == -1) {
	    			newUps = _mPreviousUps - 1;
	    			newDowns = _mPreviousDowns + 1;
	    			newLikes = false;
	    		} else {
	    			cancel(true);
	    			return;
	    		}
	    	} else {
	    		if (_mDirection == 1) {
	    			newUps = _mPreviousUps + 1;
	    			newDowns = _mPreviousDowns - 1;
	    			newLikes = true;
	    		} else if (_mDirection == 0) {
	    			newDowns = _mPreviousDowns - 1;
	    			newLikes = null;
	    		} else {
	    			cancel(true);
	    			return;
	    		}
	    	}

    		_mTargetThingInfo.setLikes(newLikes);
    		_mTargetThingInfo.setUps(newUps);
    		_mTargetThingInfo.setDowns(newDowns);
    		_mTargetThingInfo.setScore(newUps - newDowns);
        	mCommentsAdapter.notifyDataSetChanged();
    	}
    	
    	public void onPostExecute(Boolean success) {
    		if (success) {
    			CacheInfo.invalidateCachedThread(getApplicationContext());
    		} else {
    			// Vote failed. Undo the arrow and score.
            	int oldImageResourceUp, oldImageResourceDown;
        		if (_mPreviousLikes == null) {
            		oldImageResourceUp = R.drawable.vote_up_gray;
            		oldImageResourceDown = R.drawable.vote_down_gray;
        		} else if (_mPreviousLikes == true) {
        			oldImageResourceUp = R.drawable.vote_up_red;
            		oldImageResourceDown = R.drawable.vote_down_gray;
            	} else {
            		oldImageResourceUp = R.drawable.vote_up_gray;
            		oldImageResourceDown = R.drawable.vote_down_blue;
            	}
       			_mTargetThingInfo.setLikes(_mPreviousLikes);
       			_mTargetThingInfo.setUps(_mPreviousUps);
       			_mTargetThingInfo.setDowns(_mPreviousDowns);
       			_mTargetThingInfo.setScore(_mPreviousUps - _mPreviousDowns);
        		mCommentsAdapter.notifyDataSetChanged();
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, CommentsListActivity.this);
    		}
    	}
    }
    
    

    private class ReportTask extends AsyncTask<Void, Void, Boolean> {
    	
    	private static final String TAG = "ReportTask";
    	
    	private String _mUserError = "Error reporting.";
    	private String _mFullId;
    	
    	ReportTask(String fullname) {
    		this._mFullId = fullname;
    	}
    	
    	@Override
    	public Boolean doInBackground(Void... v) {
        	HttpEntity entity = null;
        	
        	if (!mSettings.loggedIn) {
        		_mUserError = "You must be logged in to report something.";
        		return false;
        	}
        	
        	// Update the modhash if necessary
        	if (mSettings.modhash == null) {
        		CharSequence modhash = Common.doUpdateModhash(mClient); 
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient, getApplicationContext());
        			if (Constants.LOGGING) Log.e(TAG, "Report failed because doUpdateModhash() failed");
        			return false;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("id", _mFullId));
    			nvps.add(new BasicNameValuePair("executed", "reported"));
    			nvps.add(new BasicNameValuePair("r", mSettings.subreddit.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/report");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	entity = response.getEntity();

    	    	String error = Common.checkResponseErrors(response, entity);
            	if (error != null)
            		throw new Exception(error);

            	// Success
            	return true;

        	} catch (Exception e) {
        		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
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
    	
    	public void onPreExecute() {
	        if (!mSettings.loggedIn) {
	        	Common.showErrorToast("You must be logged in to report this.", Toast.LENGTH_LONG, CommentsListActivity.this);
	        	cancel(true);
	        	return;
	        }
    	}
    	
    	public void onPostExecute(Boolean success) {
    		if (success) {
    			Toast.makeText(CommentsListActivity.this, "Reported.", Toast.LENGTH_SHORT);
    		} else {
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
    	if (mOpThingInfo != null) {
	    	if (mSettings.username != null && mSettings.username.equals(mOpThingInfo.getAuthor())) {
				if (mOpThingInfo.getSelftext_html() != null)
					menu.findItem(R.id.op_edit_menu_id).setVisible(true);
				else
					menu.findItem(R.id.op_edit_menu_id).setVisible(false);
				menu.findItem(R.id.op_delete_menu_id).setVisible(true);
			} else {
				menu.findItem(R.id.op_edit_menu_id).setVisible(false);
				menu.findItem(R.id.op_delete_menu_id).setVisible(false);
			}
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
    			mVoteTargetThing = mCommentsAdapter.getItem(0);
    		}
    		mReplyTargetName = mVoteTargetThing.getName();
    		showDialog(Constants.DIALOG_THING_CLICK);
    		break;
    	case R.id.login_logout_menu_id:
        	if (mSettings.loggedIn) {
        		Common.doLogout(mSettings, mClient, getApplicationContext());
        		Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
        	} else {
        		showDialog(Constants.DIALOG_LOGIN);
        	}
    		break;
    	case R.id.refresh_menu_id:
    		CacheInfo.invalidateCachedThread(getApplicationContext());
    		new DownloadCommentsTask().execute(Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
    		break;
    	case R.id.reply_thread_menu_id:
    		// From the menu, only used for the OP, which is a thread.
        	if (mSettings.loggedIn) {
	    		synchronized (COMMENT_ADAPTER_LOCK) {
	    			mVoteTargetThing = mCommentsAdapter.getItem(0);
	    		}
	    		mReplyTargetName = mVoteTargetThing.getName();
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
    		mReplyTargetName = mOpThingInfo.getName();
    		mDeleteTargetKind = Constants.THREAD_KIND;
    		showDialog(Constants.DIALOG_DELETE);
    		break;
    	case R.id.op_edit_menu_id:
    		mReplyTargetName = mOpThingInfo.getName();
    		mEditTargetBody = mOpThingInfo.getSelftext();
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
    		setListAdapter(mCommentsAdapter);
    		getListView().setDivider(null);
    		Common.updateListDrawables(this, mSettings.theme);
    		if (mCommentsAdapter != null) {
    			markSubmitterComments();
    			mCommentsAdapter.notifyDataSetChanged();
    		}
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
    		if(mOpThingInfo.isSaved()){
    			menu.add(0, Constants.UNSAVE_CONTEXT_ITEM, Menu.NONE, "Unsave");
    		} else {
    			menu.add(0, Constants.SAVE_CONTEXT_ITEM, Menu.NONE, "Save");
    		}
    		
    		menu.add(0, Constants.SHARE_CONTEXT_ITEM, Menu.NONE, "Share");
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
//    		if (mSettings.loggedIn)
//    			menu.add(0, Constants.DIALOG_REPORT, Menu.NONE, "Report comment");
    		menu.add(0, Constants.DIALOG_GOTO_PARENT, Menu.NONE, "Go to parent");
    	}
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	int rowId = (int) info.id;
    	
    	switch (item.getItemId()) {
    	case Constants.SAVE_CONTEXT_ITEM:
    		new SaveTask(true, mOpThingInfo, mSettings, this, mCommentsAdapter).execute();
    		return true;
    		
    	case Constants.UNSAVE_CONTEXT_ITEM:
    		new SaveTask(false, mOpThingInfo, mSettings, this, mCommentsAdapter).execute();
    		return true;
    		
    	case Constants.SHARE_CONTEXT_ITEM:
    		Intent intent = new Intent();
			intent.setAction(Intent.ACTION_SEND);
			intent.setType("text/plain");

			intent.putExtra(Intent.EXTRA_TEXT, mOpThingInfo.getUrl());
			
			try {
				startActivity(Intent.createChooser(intent, "Share Link"));
			} catch (android.content.ActivityNotFoundException ex) {
				
			}
			
			return true;
			
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
    		
    	case Constants.DIALOG_REPORT:
    		synchronized (COMMENT_ADAPTER_LOCK) {
    			mReportTargetName = mCommentsAdapter.getItem(rowId).getName();
    		}
    		showDialog(Constants.DIALOG_REPORT);
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
	    		ThingInfo ci = mCommentsAdapter.getItem(i);
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
	    		ThingInfo ci = mCommentsAdapter.getItem(i);
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
    		dialog = new LoginDialog(this, mSettings, false) {
				@Override
				public void onLoginChosen(CharSequence user, CharSequence password) {
					dismissDialog(Constants.DIALOG_LOGIN);
    				new LoginTask(user, password).execute();
				}
			};
    		break;
    		
    	case Constants.DIALOG_THING_CLICK:
    		inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(this);
    		dialog = builder.setView(inflater.inflate(R.layout.comment_click_dialog, null)).create();
    		break;

    	case Constants.DIALOG_REPLY:
    	{
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.compose_reply_dialog);
    		final EditText replyBody = (EditText) dialog.findViewById(R.id.body);
    		final Button replySaveButton = (Button) dialog.findViewById(R.id.reply_save_button);
    		final Button replyCancelButton = (Button) dialog.findViewById(R.id.reply_cancel_button);
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
    	}
    		break;
    		
    	case Constants.DIALOG_EDIT:
    	{
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.compose_reply_dialog);
    		final EditText replyBody = (EditText) dialog.findViewById(R.id.body);
    		final Button replySaveButton = (Button) dialog.findViewById(R.id.reply_save_button);
    		final Button replyCancelButton = (Button) dialog.findViewById(R.id.reply_cancel_button);
		
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
    		
    	case Constants.DIALOG_REPORT:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("Really report this?");
    		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_REPORT);
    				new ReportTask(mReportTargetName.toString()).execute();
    			}
    		})
    		.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
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
    		if (mVoteTargetThing == null)
    			break;
    		Boolean likes;
    		final TextView titleView = (TextView) dialog.findViewById(R.id.title);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final TextView submissionStuffView = (TextView) dialog.findViewById(R.id.submissionTime_submitter_subreddit);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
			
    		if (mVoteTargetThing == mOpThingInfo) {
				likes = mVoteTargetThing.getLikes();
    			titleView.setVisibility(View.VISIBLE);
    			titleView.setText(mOpThingInfo.getTitle());
    			urlView.setVisibility(View.VISIBLE);
    			urlView.setText(mOpThingInfo.getUrl());
    			submissionStuffView.setVisibility(View.VISIBLE);
        		sb = new StringBuilder(Util.getTimeAgo(mOpThingInfo.getCreated_utc()))
	    			.append(" by ").append(mOpThingInfo.getAuthor());
        		submissionStuffView.setText(sb);
    			// For self posts, you're already there!
    			if (("self.").toLowerCase().equals(mOpThingInfo.getDomain().substring(0, 5).toLowerCase())) {
    				linkButton.setText(R.string.comment_links_button);
    				linkToEmbeddedURLs(linkButton);
    			} else {
    				final String url = mOpThingInfo.getUrl();
    				linkButton.setText(R.string.thread_link_button);
	    			linkButton.setOnClickListener(new OnClickListener() {
	    				public void onClick(View v) {
	    					dismissDialog(Constants.DIALOG_THING_CLICK);
	    					// Launch Intent to goto the URL
	    					Common.launchBrowser(url, CommentsListActivity.this);
	    				}
	    			});
	    			linkButton.setEnabled(true);
    			}
    		} else {
    			titleView.setText("Comment by " + mVoteTargetThing.getAuthor());
    			likes = mVoteTargetThing.getLikes();
    			urlView.setVisibility(View.INVISIBLE);
    			submissionStuffView.setVisibility(View.INVISIBLE);

    			// Get embedded URLs
    			linkButton.setText(R.string.comment_links_button);
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
	    		if (likes == null) {
	    			// User is currently neutral
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(false);
	    		} else if (likes == true) {
	    			// User currenty likes it
	    			voteUpButton.setChecked(true);
	    			voteDownButton.setChecked(false);
	    		} else {
	    			// User currently dislikes it
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(true);
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
    		if (mVoteTargetThing != null && mVoteTargetThing.getReplyDraft() != null) {
    			EditText replyBodyView = (EditText) dialog.findViewById(R.id.body); 
    			replyBodyView.setText(mVoteTargetThing.getReplyDraft());
    		}
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
    /**
     * Helper function to add links from mVoteTargetThing to the button
     * @param linkButton Button that should open list of links
     */
    private void linkToEmbeddedURLs(Button linkButton) {
		final ArrayList<String> urls = new ArrayList<String>();
		final ArrayList<MarkdownURL> vtUrls = mVoteTargetThing.getUrls();
		int urlsCount = vtUrls.size();
		for (int i = 0; i < urlsCount; i++)
			urls.add(vtUrls.get(i).url);
		if (urlsCount == 0) {
			linkButton.setEnabled(false);
        } else {
        	linkButton.setEnabled(true);
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
	    	String thingFullname = mVoteTargetThing.getName();
			if (isChecked)
				new VoteTask(thingFullname, 1).execute();
			else
				new VoteTask(thingFullname, 0).execute();
		}
    };
    private final CompoundButton.OnCheckedChangeListener voteDownOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
	    	String thingFullname = mVoteTargetThing.getName();
			if (isChecked)
				new VoteTask(thingFullname, -1).execute();
			else
				new VoteTask(thingFullname, 0).execute();
		}
    };
    
    
    @Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putCharSequence(Constants.CommentsSort.SORT_BY_KEY, mSortByUrl);
    	state.putInt(Constants.JUMP_TO_COMMENT_POSITION_KEY, mJumpToCommentPosition);
    	state.putCharSequence(Constants.JUMP_TO_COMMENT_ID_KEY, mJumpToCommentId);
    	state.putInt(Constants.JUMP_TO_COMMENT_CONTEXT_KEY, mJumpToCommentContext);
    	state.putCharSequence(Constants.REPLY_TARGET_NAME_KEY, mReplyTargetName);
    	state.putCharSequence(Constants.REPORT_TARGET_NAME_KEY, mReportTargetName);
    	state.putCharSequence(Constants.EDIT_TARGET_BODY_KEY, mEditTargetBody);
    	state.putString(Constants.DELETE_TARGET_KIND_KEY, mDeleteTargetKind);
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
        	Constants.DIALOG_LOGGING_IN,
        	Constants.DIALOG_LOGIN,
        	Constants.DIALOG_REPLY,
        	Constants.DIALOG_REPLYING,
        	Constants.DIALOG_SORT_BY,
        	Constants.DIALOG_THING_CLICK,
        	Constants.DIALOG_REPORT
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
