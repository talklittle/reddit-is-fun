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

package com.andrewshu.android.reddit.threads;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.HttpClient;
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
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieSyncManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.comments.CommentsListActivity;
import com.andrewshu.android.reddit.common.CacheInfo;
import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.RedditIsFunHttpClientFactory;
import com.andrewshu.android.reddit.common.tasks.HideTask;
import com.andrewshu.android.reddit.common.tasks.SaveTask;
import com.andrewshu.android.reddit.common.tasks.VoteTask;
import com.andrewshu.android.reddit.common.util.StringUtils;
import com.andrewshu.android.reddit.common.util.Util;
import com.andrewshu.android.reddit.login.LoginDialog;
import com.andrewshu.android.reddit.login.LoginTask;
import com.andrewshu.android.reddit.mail.InboxActivity;
import com.andrewshu.android.reddit.mail.PeekEnvelopeTask;
import com.andrewshu.android.reddit.reddits.PickSubredditActivity;
import com.andrewshu.android.reddit.reddits.SubscribeTask;
import com.andrewshu.android.reddit.reddits.UnsubscribeTask;
import com.andrewshu.android.reddit.settings.RedditPreferencesPage;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.submit.SubmitLinkActivity;
import com.andrewshu.android.reddit.things.ThingInfo;
import com.andrewshu.android.reddit.threads.ShowThumbnailsTask.ThumbnailLoadAction;
import com.andrewshu.android.reddit.user.ProfileActivity;

/**
 * Main Activity class representing a Subreddit, i.e., a ThreadsList.
 * 
 * @author TalkLittle
 *
 */
public final class ThreadsListActivity extends ListActivity {

	private static final String TAG = "ThreadsListActivity";
	private final Pattern REDDIT_PATH_PATTERN = Pattern.compile(Constants.REDDIT_PATH_PATTERN_STRING);
	
	private final ObjectMapper mObjectMapper = Common.getObjectMapper();

    /** Custom list adapter that fits our threads data into the list. */
    private ThreadsListAdapter mThreadsAdapter = null;
    private ArrayList<ThingInfo> mThreadsList = null;
    private static final Object THREAD_ADAPTER_LOCK = new Object();

    private final HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();
	
   
    private final RedditSettings mSettings = new RedditSettings();
    
    // UI State
    private ThingInfo mVoteTargetThing = null;
    private DownloadThreadsTask mCurrentDownloadThreadsTask = null;
    private final Object mCurrentDownloadThreadsTaskLock = new Object();
    private View mNextPreviousView = null;
    
    private ShowThumbnailsTask mCurrentShowThumbnailsTask = null;
    private final Object mCurrentShowThumbnailsTaskLock = new Object();
    
    // Navigation that can be cached
    private String mSubreddit = Constants.FRONTPAGE_STRING;
    // The after, before, and count to navigate away from current page of results
    private String mAfter = null;
    private String mBefore = null;
    private volatile int mCount = Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
    // The after, before, and count to navigate to current page
    private String mLastAfter = null;
    private String mLastBefore = null;
    private volatile int mLastCount = 0;
    private String mSortByUrl = Constants.ThreadsSort.SORT_BY_HOT_URL;
    private String mSortByUrlExtra = "";
    private String mJumpToThreadId = null;
    // End navigation variables
    
    // Menu
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
		
        mSettings.loadRedditPreferences(getApplicationContext(), mClient);
        setRequestedOrientation(mSettings.getRotation());
        setTheme(mSettings.getTheme());
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(R.layout.threads_list_content);
        registerForContextMenu(getListView());
    	
		if (savedInstanceState != null) {
        	if (Constants.LOGGING) Log.d(TAG, "using savedInstanceState");
			mSubreddit = savedInstanceState.getString(Constants.SUBREDDIT_KEY);
	        if (mSubreddit == null)
	        	mSubreddit = mSettings.getHomepage();
	        mAfter = savedInstanceState.getString(Constants.AFTER_KEY);
	        mBefore = savedInstanceState.getString(Constants.BEFORE_KEY);
	        mCount = savedInstanceState.getInt(Constants.THREAD_COUNT_KEY);
	        mLastAfter = savedInstanceState.getString(Constants.LAST_AFTER_KEY);
	        mLastBefore = savedInstanceState.getString(Constants.LAST_BEFORE_KEY);
	        mLastCount = savedInstanceState.getInt(Constants.THREAD_LAST_COUNT_KEY);
	        mSortByUrl = savedInstanceState.getString(Constants.ThreadsSort.SORT_BY_KEY);
		    mJumpToThreadId = savedInstanceState.getString(Constants.JUMP_TO_THREAD_ID_KEY);
		    mVoteTargetThing = savedInstanceState.getParcelable(Constants.VOTE_TARGET_THING_INFO_KEY);
		    
		    // try to restore mThreadsList using getLastNonConfigurationInstance()
		    // (separate function to avoid a compiler warning casting ArrayList<ThingInfo>
		    restoreLastNonConfigurationInstance();
		    if (mThreadsList == null) {
	        	// Load previous view of threads
		        if (mLastAfter != null) {
		        	new MyDownloadThreadsTask(mSubreddit, mLastAfter, null, mLastCount).execute();
		        } else if (mLastBefore != null) {
		        	new MyDownloadThreadsTask(mSubreddit, null, mLastBefore, mLastCount).execute();
		        } else {
		        	new MyDownloadThreadsTask(mSubreddit).execute();
		        }
		    } else {
		    	// Orientation change. Use prior instance.
		    	resetUI(new ThreadsListAdapter(this, mThreadsList));
		    	if (Constants.FRONTPAGE_STRING.equals(mSubreddit))
		    		setTitle("reddit.com: what's new online!");
		    	else
		    		setTitle("/r/" + mSubreddit.trim());
		    }
        }
		// Handle subreddit Uri passed via Intent
        else if (getIntent().getData() != null) {
	    	Matcher redditContextMatcher = REDDIT_PATH_PATTERN.matcher(getIntent().getData().getPath());
			if (redditContextMatcher.matches()) {
				new MyDownloadThreadsTask(redditContextMatcher.group(1)).execute();
			} else {
				new MyDownloadThreadsTask(mSettings.getHomepage()).execute();
			}
		}
		// No subreddit specified by Intent, so load the user's home reddit
		else {
        	new MyDownloadThreadsTask(mSettings.getHomepage()).execute();
        }
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
        // SOPA blackout: Jan 18, 2012 from 8am-8pm EST (1300-0100 UTC)
    	long timeMillis = System.currentTimeMillis();
        if (timeMillis >= 1326891600000L && timeMillis <= 1326934800000L) {
        	Toast.makeText(this, "Let's Protest SOPA", Toast.LENGTH_LONG).show();
        	Common.launchBrowser(this, "http://www.reddit.com", null, false, true, false, false);
        	finish();
        	return;
        }
        
		int previousTheme = mSettings.getTheme();

    	mSettings.loadRedditPreferences(this, mClient);

    	if (mSettings.getTheme() != previousTheme) {
    		relaunchActivity();
    	}
    	else {
	    	CookieSyncManager.getInstance().startSync();
	    	setRequestedOrientation(mSettings.getRotation());
	    	
	    	updateNextPreviousButtons();
	    	
	    	if (mThreadsAdapter != null)
	    		jumpToThread();
	    	
	    	if (mSettings.isLoggedIn())
	    		new PeekEnvelopeTask(this, mClient, mSettings.getMailNotificationStyle()).execute();
    	}
    }
    
    private void relaunchActivity() {
    	finish();
    	startActivity(getIntent());
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
		CookieSyncManager.getInstance().stopSync();
		mSettings.saveRedditPreferences(this);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        // Avoid having to re-download and re-parse the threads list
    	// when rotating or opening keyboard.
    	return mThreadsList;
    }
    
    @SuppressWarnings("unchecked")
	private void restoreLastNonConfigurationInstance() {
    	mThreadsList = (ArrayList<ThingInfo>) getLastNonConfigurationInstance();
    }
    

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	
    	switch(requestCode) {
    	case Constants.ACTIVITY_PICK_SUBREDDIT:
    		if (resultCode == Activity.RESULT_OK) {
    	    	Matcher redditContextMatcher = REDDIT_PATH_PATTERN.matcher(intent.getData().getPath());
    			if (redditContextMatcher.matches()) {
    				new MyDownloadThreadsTask(redditContextMatcher.group(1)).execute();
    			}
    		}
    		break;
    	default:
    		break;
    	}
    }
    
    /**
     * http://stackoverflow.com/questions/2257963/android-how-to-show-dialog-to-confirm-user-wishes-to-exit-activity
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Handle the back button
        if(mSettings.isConfirmQuitOrLogout() && keyCode == KeyEvent.KEYCODE_BACK && isTaskRoot()) {
            //Ask the user if they want to quit
            new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.quit)
            .setMessage(R.string.really_quit)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //Stop the activity
                    finish();    
                }
            })
            .setNegativeButton(R.string.no, null)
            .show();

            return true;
        }
        else {
            return super.onKeyDown(keyCode, event);
        }
    }
    
    
    final class ThreadsListAdapter extends ArrayAdapter<ThingInfo> {
    	static final int THREAD_ITEM_VIEW_TYPE = 0;
    	// The number of view types
    	static final int VIEW_TYPE_COUNT = 1;
    	public boolean mIsLoading = true;
    	private LayoutInflater mInflater;
    	private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
        
        public ThreadsListAdapter(Context context, List<ThingInfo> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        
        @Override
        public int getItemViewType(int position) {
        	if (position == mFrequentSeparatorPos) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
    		return THREAD_ITEM_VIEW_TYPE;
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

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(R.layout.threads_list_item, null);
            } else {
                view = convertView;
            }
            
            ThingInfo item = this.getItem(position);
            
            // Set the values of the Views for the ThreadsListItem
            fillThreadsListItemView(
            		position, view, item, ThreadsListActivity.this, mClient, mSettings, mThumbnailOnClickListenerFactory
    		);
            
            return view;
        }
    }
    
    public static void fillThreadsListItemView(
    		int position,
    		View view,
    		ThingInfo item,
    		ListActivity activity,
    		HttpClient client,
    		RedditSettings settings,
    		ThumbnailOnClickListenerFactory thumbnailOnClickListenerFactory
	) {
    	
    	Resources res = activity.getResources();
    	
    	TextView titleView = (TextView) view.findViewById(R.id.title);
        TextView votesView = (TextView) view.findViewById(R.id.votes);
        TextView numCommentsSubredditView = (TextView) view.findViewById(R.id.numCommentsSubreddit);
        TextView nsfwView = (TextView) view.findViewById(R.id.nsfw);
//        TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
        ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
        ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
        View thumbnailContainer = view.findViewById(R.id.thumbnail_view);
        FrameLayout thumbnailFrame = (FrameLayout) view.findViewById(R.id.thumbnail_frame);
        ImageView thumbnailImageView = (ImageView) view.findViewById(R.id.thumbnail);
        ProgressBar indeterminateProgressBar = (ProgressBar) view.findViewById(R.id.indeterminate_progress);
        
        // Set the title and domain using a SpannableStringBuilder
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String title = item.getTitle();
        if (title == null)
        	title = "";
        SpannableString titleSS = new SpannableString(title);
        int titleLen = title.length();
        titleSS.setSpan(new TextAppearanceSpan(activity,
        		Util.getTextAppearanceResource(settings.getTheme(), android.R.style.TextAppearance_Large)),
        		0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        String domain = item.getDomain();
        if (domain == null)
        	domain = "";
        int domainLen = domain.length();
        SpannableString domainSS = new SpannableString("("+item.getDomain()+")");
        domainSS.setSpan(new TextAppearanceSpan(activity,
        		Util.getTextAppearanceResource(settings.getTheme(), android.R.style.TextAppearance_Small)),
        		0, domainLen+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (Util.isLightTheme(settings.getTheme())) {
            if (item.isClicked()) {
            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.purple));
            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.blue));
            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            domainSS.setSpan(new ForegroundColorSpan(res.getColor(R.color.gray_50)),
            		0, domainLen+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            if (item.isClicked()) {
            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.gray_50));
            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        	domainSS.setSpan(new ForegroundColorSpan(res.getColor(R.color.gray_75)),
            		0, domainLen+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        builder.append(titleSS).append(" ").append(domainSS);
        titleView.setText(builder);
        
        votesView.setText("" + item.getScore());
        numCommentsSubredditView.setText(Util.showNumComments(item.getNum_comments()) + "  " + item.getSubreddit());
        
        if(item.isOver_18()){
            nsfwView.setVisibility(View.VISIBLE);
        } else {
            nsfwView.setVisibility(View.GONE);
        }
        
        // Set the up and down arrow colors based on whether user likes
        if (settings.isLoggedIn()) {
        	if (item.getLikes() == null) {
        		voteUpView.setImageResource(R.drawable.vote_up_gray);
        		voteDownView.setImageResource(R.drawable.vote_down_gray);
        		votesView.setTextColor(res.getColor(R.color.gray_75));
        	} else if (item.getLikes() == true) {
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
    		votesView.setTextColor(res.getColor(R.color.gray_75));
        }
        
        // Thumbnails open links
        if (thumbnailContainer != null) {
        	if (Common.shouldLoadThumbnails(activity, settings)) {
        		thumbnailContainer.setVisibility(View.VISIBLE);
        		
            	if (item.getUrl() != null) {
            		OnClickListener thumbnailOnClickListener = thumbnailOnClickListenerFactory.getThumbnailOnClickListener(item, activity);
            		if (thumbnailOnClickListener != null) {
		            	thumbnailFrame.setOnClickListener(thumbnailOnClickListener);
            		}
            	}
            	
            	// Show thumbnail based on ThingInfo
            	if ("default".equals(item.getThumbnail()) || "self".equals(item.getThumbnail()) || StringUtils.isEmpty(item.getThumbnail())) {
        			indeterminateProgressBar.setVisibility(View.GONE);
            		thumbnailImageView.setVisibility(View.VISIBLE);
            		thumbnailImageView.setImageResource(R.drawable.go_arrow);
            	}
            	else {
            		indeterminateProgressBar.setVisibility(View.GONE);
            		thumbnailImageView.setVisibility(View.VISIBLE);
            		if (item.getThumbnailBitmap() != null) {
	            		thumbnailImageView.setImageBitmap(item.getThumbnailBitmap());
            		}
            		else {
            			thumbnailImageView.setImageBitmap(null);
            			new ShowThumbnailsTask(activity, client, R.drawable.go_arrow).execute(new ThumbnailLoadAction(item, thumbnailImageView, position));
            		}
            	}
            	
            	// Set thumbnail background based on current theme
            	if (Util.isLightTheme(settings.getTheme()))
            		thumbnailFrame.setBackgroundResource(R.drawable.thumbnail_background_light);
            	else
            		thumbnailFrame.setBackgroundResource(R.drawable.thumbnail_background_dark);
        	} else {
        		// if thumbnails disabled, hide thumbnail icon
        		thumbnailContainer.setVisibility(View.GONE);
        	}
        }
    }
    
	public static void fillThreadClickDialog(Dialog dialog, ThingInfo thingInfo, RedditSettings settings,
			ThreadClickDialogOnClickListenerFactory threadClickDialogOnClickListenerFactory) {
		
		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.vote_up_button);
		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.vote_down_button);
		final TextView titleView = (TextView) dialog.findViewById(R.id.title);
		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
		final TextView submissionStuffView = (TextView) dialog.findViewById(R.id.submissionTime_submitter_subreddit);
		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
		final Button commentsButton = (Button) dialog.findViewById(R.id.thread_comments_button);
		
		titleView.setText(thingInfo.getTitle());
		urlView.setText(thingInfo.getUrl());
		StringBuilder sb = new StringBuilder(Util.getTimeAgo(thingInfo.getCreated_utc()))
			.append(" by ").append(thingInfo.getAuthor())
			.append(" to ").append(thingInfo.getSubreddit());
		submissionStuffView.setText(sb);
        
		// Only show upvote/downvote if user is logged in
		if (settings.isLoggedIn()) {
			loginButton.setVisibility(View.GONE);
			voteUpButton.setVisibility(View.VISIBLE);
			voteDownButton.setVisibility(View.VISIBLE);
			
			// Remove the OnCheckedChangeListeners because we are about to setChecked(),
			// and I think the Buttons are recycled, so old listeners will fire
			// for the previous vote target ThingInfo.
			voteUpButton.setOnCheckedChangeListener(null);
			voteDownButton.setOnCheckedChangeListener(null);
			
			// Set initial states of the vote buttons based on user's past actions
    		if (thingInfo.getLikes() == null) {
    			// User is currently neutral
    			voteUpButton.setChecked(false);
    			voteDownButton.setChecked(false);
    		} else if (thingInfo.getLikes() == true) {
    			// User currenty likes it
    			voteUpButton.setChecked(true);
    			voteDownButton.setChecked(false);
    		} else {
    			// User currently dislikes it
    			voteUpButton.setChecked(false);
    			voteDownButton.setChecked(true);
    		}
    		voteUpButton.setOnCheckedChangeListener(
    				threadClickDialogOnClickListenerFactory.getVoteUpOnCheckedChangeListener(thingInfo));
    		voteDownButton.setOnCheckedChangeListener(
    				threadClickDialogOnClickListenerFactory.getVoteDownOnCheckedChangeListener(thingInfo));
		} else {
			voteUpButton.setVisibility(View.GONE);
			voteDownButton.setVisibility(View.GONE);
			loginButton.setVisibility(View.VISIBLE);
			loginButton.setOnClickListener(
					threadClickDialogOnClickListenerFactory.getLoginOnClickListener());
		}

		// "link" button behaves differently for regular links vs. self posts and links to comments pages (e.g., bestof)
        if (thingInfo.isIs_self()) {
        	// It's a self post. Both buttons do the same thing.
        	linkButton.setEnabled(false);
        } else {
        	linkButton.setOnClickListener(
        			threadClickDialogOnClickListenerFactory.getLinkOnClickListener(thingInfo, settings.isUseExternalBrowser()));
        	linkButton.setEnabled(true);
        }
        
        // "comments" button is easy: always does the same thing
        commentsButton.setOnClickListener(
        		threadClickDialogOnClickListenerFactory.getCommentsOnClickListener(thingInfo));
	}

	
	/**
     * Jump to thread whose id is mJumpToThreadId. Then clear mJumpToThreadId.
     */
    private void jumpToThread() {
    	if (mJumpToThreadId == null || mThreadsAdapter == null)
    		return;
		for (int k = 0; k < mThreadsAdapter.getCount(); k++) {
			if (mJumpToThreadId.equals(mThreadsAdapter.getItem(k).getId())) {
				getListView().setSelection(k);
				mJumpToThreadId = null;
				break;
			}
		}
    }
    
    
    /**
     * Called when user clicks an item in the list. Starts an activity to
     * open the url for that item.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ThingInfo item = mThreadsAdapter.getItem(position);
        
    	// Mark the thread as selected
    	mVoteTargetThing = item;
    	mJumpToThreadId = item.getId();
    	
    	showDialog(Constants.DIALOG_THREAD_CLICK);
    }

    /**
     * Resets the output UI list contents, retains session state.
     * @param threadsAdapter A ThreadsListAdapter to use. Pass in null if you want a new empty one created.
     */
    void resetUI(ThreadsListAdapter threadsAdapter) {
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

    	synchronized (THREAD_ADAPTER_LOCK) {
	    	if (threadsAdapter == null) {
	            // Reset the list to be empty.
		    	mThreadsList = new ArrayList<ThingInfo>();
				mThreadsAdapter = new ThreadsListAdapter(this, mThreadsList);
	    	} else {
	    		mThreadsAdapter = threadsAdapter;
	    	}
		    setListAdapter(mThreadsAdapter);
		    mThreadsAdapter.mIsLoading = false;
		    mThreadsAdapter.notifyDataSetChanged();  // Just in case
		}
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
    	synchronized (THREAD_ADAPTER_LOCK) {
	    	if (mThreadsAdapter != null)
	    		mThreadsAdapter.mIsLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_START);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mThreadsAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);
    }
    
    private void updateNextPreviousButtons() {
    	Common.updateNextPreviousButtons(this, mNextPreviousView, mAfter, mBefore, mCount, mSettings,
    			downloadAfterOnClickListener, downloadBeforeOnClickListener);
    }
    
    
    /**
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("android", "gaming", etc.)
     *        If the number of elements in subreddit is >= 2, treat 2nd element as "after" 
     */
    private class MyDownloadThreadsTask extends DownloadThreadsTask {
    	
    	public MyDownloadThreadsTask(String subreddit) {
			super(getApplicationContext(),
					ThreadsListActivity.this.mClient,
					ThreadsListActivity.this.mObjectMapper,
					ThreadsListActivity.this.mSortByUrl,
					ThreadsListActivity.this.mSortByUrlExtra,
					subreddit);
		}
    	
    	public MyDownloadThreadsTask(String subreddit,
				String after, String before, int count) {
			super(getApplicationContext(),
					ThreadsListActivity.this.mClient,
					ThreadsListActivity.this.mObjectMapper,
					ThreadsListActivity.this.mSortByUrl,
					ThreadsListActivity.this.mSortByUrlExtra,
					subreddit, after, before, count);
		}

		@Override
    	protected void saveState() {
			mSettings.setModhash(mModhash);
			ThreadsListActivity.this.mSubreddit = mSubreddit;
			ThreadsListActivity.this.mLastAfter = mLastAfter;
			ThreadsListActivity.this.mLastBefore = mLastBefore;
			ThreadsListActivity.this.mLastCount = mLastCount;
			ThreadsListActivity.this.mAfter = mAfter;
			ThreadsListActivity.this.mBefore = mBefore;
			ThreadsListActivity.this.mCount = mCount;
			ThreadsListActivity.this.mSortByUrl = mSortByUrl;
			ThreadsListActivity.this.mSortByUrlExtra = mSortByUrlExtra;
    	}
    	
    	@Override
    	public void onPreExecute() {
    		synchronized (mCurrentDownloadThreadsTaskLock) {
	    		if (mCurrentDownloadThreadsTask != null) {
	    			this.cancel(true);
	    			return;
	    		}
    			mCurrentDownloadThreadsTask = this;
    		}
    		resetUI(null);
    		enableLoadingScreen();
			
    		if (mContentLength == -1) {
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);
    		}
    		else {
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
    		}
    		
	    	if (Constants.FRONTPAGE_STRING.equals(mSubreddit))
	    		setTitle("reddit.com: what's new online!");
	    	else
	    		setTitle("/r/" + mSubreddit.trim());
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		synchronized (mCurrentDownloadThreadsTaskLock) {
    			mCurrentDownloadThreadsTask = null;
    		}

    		disableLoadingScreen();

    		if (mContentLength == -1)
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_OFF);
    		else
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);

    		if (success) {
    			synchronized (THREAD_ADAPTER_LOCK) {
    				mThreadsList.addAll(mThingInfos);
		    		mThreadsAdapter.notifyDataSetChanged();
    			}
    			
    			showThumbnails(mThingInfos);
    			
    			updateNextPreviousButtons();

	    		// Point the list to last thread user was looking at, if any
	    		jumpToThread();
    		} else {
    			if (!isCancelled())
    				Common.showErrorToast(mUserError, Toast.LENGTH_LONG, ThreadsListActivity.this);
    		}
    	}
    	
    	@Override
    	public void onProgressUpdate(Long... progress) {
    		if (mContentLength == -1) {
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);
    		}
    		else {
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * (Window.PROGRESS_END-1) / (int) mContentLength);
    		}
    	}
    	
    	public void propertyChange(PropertyChangeEvent event) {
    		publishProgress((Long) event.getNewValue());
    	}
    }
    
    private void showThumbnails(List<ThingInfo> thingInfos) {
    	if (Common.shouldLoadThumbnails(this, mSettings)) {
	    	int size = thingInfos.size();
	    	ThumbnailLoadAction[] thumbnailLoadActions = new ThumbnailLoadAction[size];
	    	for (int i = 0; i < size; i++) {
	    		thumbnailLoadActions[i] = new ThumbnailLoadAction(thingInfos.get(i), null, i);
	    	}
	    	synchronized (mCurrentShowThumbnailsTaskLock) {
	    		if (mCurrentShowThumbnailsTask != null)
	    			mCurrentShowThumbnailsTask.cancel(true);
	    		mCurrentShowThumbnailsTask = new ShowThumbnailsTask(this, mClient, R.drawable.go_arrow);
	    	}
	    	mCurrentShowThumbnailsTask.execute(thumbnailLoadActions);
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
    			Toast.makeText(ThreadsListActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
    			// Check mail
    			new PeekEnvelopeTask(getApplicationContext(), mClient, mSettings.getMailNotificationStyle()).execute();
    			// Refresh the threads list
    			new MyDownloadThreadsTask(mSubreddit).execute();
        	} else {
            	Common.showErrorToast(mUserError, Toast.LENGTH_LONG, ThreadsListActivity.this);
    		}
    	}
    }
    
    
    private class MyVoteTask extends VoteTask {
    	
    	private int _mPreviousScore;
    	private Boolean _mPreviousLikes;
    	private ThingInfo _mTargetThingInfo;
    	
    	public MyVoteTask(ThingInfo thingInfo, int direction, String subreddit) {
    		super(thingInfo.getName(), direction, subreddit, getApplicationContext(), mSettings, mClient);
    		_mTargetThingInfo = thingInfo;
    		_mPreviousScore = thingInfo.getScore();
    		_mPreviousLikes = thingInfo.getLikes();
    	}
    	
    	@Override
    	public void onPreExecute() {
    		if (!_mSettings.isLoggedIn()) {
        		Common.showErrorToast("You must be logged in to vote.", Toast.LENGTH_LONG, _mContext);
        		cancel(true);
        		return;
        	}
        	if (_mDirection < -1 || _mDirection > 1) {
        		if (Constants.LOGGING) Log.e(TAG, "WTF: _mDirection = " + _mDirection);
        		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
        	}
        	int newScore;
        	Boolean newLikes;
        	_mPreviousScore = Integer.valueOf(_mTargetThingInfo.getScore());
        	_mPreviousLikes = _mTargetThingInfo.getLikes();
        	if (_mPreviousLikes == null) {
        		if (_mDirection == 1) {
        			newScore = _mPreviousScore + 1;
        			newLikes = true;
        		} else if (_mDirection == -1) {
        			newScore = _mPreviousScore - 1;
        			newLikes = false;
        		} else {
        			cancel(true);
        			return;
        		}
        	} else if (_mPreviousLikes == true) {
        		if (_mDirection == 0) {
        			newScore = _mPreviousScore - 1;
        			newLikes = null;
        		} else if (_mDirection == -1) {
        			newScore = _mPreviousScore - 2;
        			newLikes = false;
        		} else {
        			cancel(true);
        			return;
        		}
        	} else {
        		if (_mDirection == 1) {
        			newScore = _mPreviousScore + 2;
        			newLikes = true;
        		} else if (_mDirection == 0) {
        			newScore = _mPreviousScore + 1;
        			newLikes = null;
        		} else {
        			cancel(true);
        			return;
        		}
        	}
    		_mTargetThingInfo.setLikes(newLikes);
    		_mTargetThingInfo.setScore(newScore);
    		mThreadsAdapter.notifyDataSetChanged();
    	}
    	
    	@Override
    	public void onPostExecute(Boolean success) {
    		if (success) {
    			CacheInfo.invalidateCachedSubreddit(_mContext);
    		} else {
    			// Vote failed. Undo the score.
            	_mTargetThingInfo.setLikes(_mPreviousLikes);
        		_mTargetThingInfo.setScore(_mPreviousScore);
        		mThreadsAdapter.notifyDataSetChanged();
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, _mContext);
    		}
    	}
    }
    
    private final class MyHideTask extends HideTask {

		public MyHideTask(boolean hide, ThingInfo mVoteTargetThreadInfo,
				RedditSettings mSettings, Context mContext) {
			super(hide, mVoteTargetThreadInfo, mSettings, mContext);
		}
		
		@Override
		public void onPostExecute(Boolean success) {
			// super shows error on success==false
			super.onPostExecute(success);
			
			if (success) {
				synchronized (THREAD_ADAPTER_LOCK) {
					// Remove from list even if unhiding--because the only place you can
					// unhide from is the list of Hidden threads.
					mThreadsAdapter.remove(mTargetThreadInfo);
					mThreadsAdapter.notifyDataSetChanged();
				}
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
        inflater.inflate(R.menu.subreddit, menu);
        return true;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo){
    	super.onCreateContextMenu(menu, v, menuInfo);
    	
    	AdapterView.AdapterContextMenuInfo info;
    	info = (AdapterView.AdapterContextMenuInfo) menuInfo;
    	ThingInfo _item = mThreadsAdapter.getItem(info.position);
    	
    	mVoteTargetThing = _item;
    	
    	menu.add(0, Constants.VIEW_SUBREDDIT_CONTEXT_ITEM, 0, R.string.view_subreddit);
    	menu.add(0, Constants.SHARE_CONTEXT_ITEM, 0, R.string.share);
    	menu.add(0, Constants.OPEN_IN_BROWSER_CONTEXT_ITEM, 0, R.string.open_browser);
    	
    	if(mSettings.isLoggedIn()){
    		if(!_item.isSaved()){
    			menu.add(0, Constants.SAVE_CONTEXT_ITEM, 0, "Save");
    		} else {
    			menu.add(0, Constants.UNSAVE_CONTEXT_ITEM, 0, "Unsave");
    		}
    		menu.add(0, Constants.HIDE_CONTEXT_ITEM, 0, "Hide");
    	}
    	
		menu.add(0, Constants.DIALOG_VIEW_PROFILE, Menu.NONE,
				String.format(getResources().getString(R.string.user_profile), _item.getAuthor()));
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterView.AdapterContextMenuInfo info;
        info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        
        ThingInfo _item = mThreadsAdapter.getItem(info.position);
        
        switch (item.getItemId()) {
        case Constants.VIEW_SUBREDDIT_CONTEXT_ITEM:
        	new MyDownloadThreadsTask(_item.getSubreddit()).execute();
        	return true;
        
        case Constants.SHARE_CONTEXT_ITEM:
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, _item.getUrl());
			try {
				startActivity(Intent.createChooser(intent, "Share Link"));
			} catch (android.content.ActivityNotFoundException ex) {
				if (Constants.LOGGING) Log.e(TAG, "Share Link", ex);
			}
			return true;
			
		case Constants.OPEN_IN_BROWSER_CONTEXT_ITEM:
			setLinkClicked(_item);
			Common.launchBrowser(this, _item.getUrl(), Util.createThreadUri(_item).toString(), false, true, true, mSettings.isSaveHistory());
			return true;
			
		case Constants.SAVE_CONTEXT_ITEM:
			new SaveTask(true, _item, mSettings, getApplicationContext()).execute();
			return true;
			
		case Constants.UNSAVE_CONTEXT_ITEM:
			new SaveTask(false, _item, mSettings, getApplicationContext()).execute();
			return true;
			
		case Constants.HIDE_CONTEXT_ITEM:
			new MyHideTask(true, _item, mSettings, getApplicationContext()).execute();
			return true;
			
		case Constants.UNHIDE_CONTEXT_ITEM:
			new MyHideTask(false, _item, mSettings, getApplicationContext()).execute();
			
    	case Constants.DIALOG_VIEW_PROFILE:
    		Intent i = new Intent(this, ProfileActivity.class);
    		i.setData(Util.createProfileUri(_item.getAuthor()));
    		startActivity(i);
    		return true;
    		
		default:
			return super.onContextItemSelected(item);
		}
    	
    }
    
	@Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This happens when the user begins to hold down the menu key, so
        // allow them to chord to get a shortcut.
        mCanChord = true;

    	super.onPrepareOptionsMenu(menu);
    	
    	MenuItem src, dest;
    	
        // Login/Logout
    	if (mSettings.isLoggedIn()) {
    		menu.findItem(R.id.login_menu_id).setVisible(false);

    		if(!mSubreddit.equals(Constants.FRONTPAGE_STRING)){
    			ArrayList<String> mSubredditsList = CacheInfo.getCachedSubredditList(getApplicationContext());	
    			
    			if(mSubredditsList != null && StringUtils.listContainsIgnoreCase(mSubredditsList, mSubreddit)){
	    			menu.findItem(R.id.unsubscribe_menu_id).setVisible(true);
	    			menu.findItem(R.id.subscribe_menu_id).setVisible(false);
	    		}
	    		else {
	    			menu.findItem(R.id.subscribe_menu_id).setVisible(true);
	    			menu.findItem(R.id.unsubscribe_menu_id).setVisible(false);
	    		}
    		}
    		
	        menu.findItem(R.id.inbox_menu_id).setVisible(true);
	        menu.findItem(R.id.user_profile_menu_id).setVisible(true);
	        menu.findItem(R.id.user_profile_menu_id).setTitle(
	        		String.format(getResources().getString(R.string.user_profile), mSettings.getUsername())
    		);
	        menu.findItem(R.id.logout_menu_id).setVisible(true);
	        menu.findItem(R.id.logout_menu_id).setTitle(
	        		String.format(getResources().getString(R.string.logout), mSettings.getUsername())
    		);
    	}
    	else {
			menu.findItem(R.id.login_menu_id).setVisible(true);

			menu.findItem(R.id.unsubscribe_menu_id).setVisible(false);
			menu.findItem(R.id.subscribe_menu_id).setVisible(false);

            menu.findItem(R.id.inbox_menu_id).setVisible(false);
            menu.findItem(R.id.user_profile_menu_id).setVisible(false);
            menu.findItem(R.id.logout_menu_id).setVisible(false);
    	}
    	
    	// Theme: Light/Dark
    	src = Util.isLightTheme(mSettings.getTheme()) ?
        		menu.findItem(R.id.dark_menu_id) :
        			menu.findItem(R.id.light_menu_id);
        dest = menu.findItem(R.id.light_dark_menu_id);
        dest.setTitle(src.getTitle());
        
        // Sort
        if (Constants.ThreadsSort.SORT_BY_HOT_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_hot_menu_id);
        else if (Constants.ThreadsSort.SORT_BY_NEW_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_new_menu_id);
        else if (Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_controversial_menu_id);
        else if (Constants.ThreadsSort.SORT_BY_TOP_URL.equals(mSortByUrl))
        	src = menu.findItem(R.id.sort_by_top_menu_id);
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
        case R.id.pick_subreddit_menu_id:
    		Intent pickSubredditIntent = new Intent(getApplicationContext(), PickSubredditActivity.class);
    		startActivityForResult(pickSubredditIntent, Constants.ACTIVITY_PICK_SUBREDDIT);
    		break;
    	case R.id.login_menu_id:
    		showDialog(Constants.DIALOG_LOGIN);
    		break;
    	case R.id.logout_menu_id:
			if (mSettings.isConfirmQuitOrLogout()) {
				// Ask the user if they want to logout
				new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()))
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.confirm_logout_title)
						.setMessage(R.string.confirm_logout)
						.setPositiveButton(R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										ThreadsListActivity.this.logout();
									}
								}
						)
						.setNegativeButton(R.string.no, null)
						.show();
			} else {
				logout();
			}
			break;
    	case R.id.refresh_menu_id:
    		CacheInfo.invalidateCachedSubreddit(getApplicationContext());
    		new MyDownloadThreadsTask(mSubreddit).execute();
    		break;
    	case R.id.submit_link_menu_id:
    		Intent submitLinkIntent = new Intent(getApplicationContext(), SubmitLinkActivity.class);
    		submitLinkIntent.setData(Util.createSubmitUri(mSubreddit));
    		startActivity(submitLinkIntent);
    		break;
    	case R.id.sort_by_menu_id:
    		showDialog(Constants.DIALOG_SORT_BY);
    		break;
    	case R.id.open_browser_menu_id:
    		String url;
    		if (mSubreddit.equals(Constants.FRONTPAGE_STRING))
    			url = Constants.REDDIT_BASE_URL;
    		else
        		url = new StringBuilder(Constants.REDDIT_BASE_URL + "/r/").append(mSubreddit).toString();
    		Common.launchBrowser(this, url, null, false, true, true, false);
    		break;
        case R.id.light_dark_menu_id:
    		mSettings.setTheme(Util.getInvertedTheme(mSettings.getTheme()));
    		relaunchActivity();
    		break;
        case R.id.inbox_menu_id:
        	Intent inboxIntent = new Intent(getApplicationContext(), InboxActivity.class);
        	startActivity(inboxIntent);
        	break;
        case R.id.user_profile_menu_id:
        	Intent profileIntent = new Intent(getApplicationContext(), ProfileActivity.class);
        	startActivity(profileIntent);
        	break;
    	case R.id.preferences_menu_id:
            Intent prefsIntent = new Intent(getApplicationContext(), RedditPreferencesPage.class);
            startActivity(prefsIntent);
            break;
    	case R.id.subscribe_menu_id:
    		CacheInfo.invalidateCachedSubreddit(getApplicationContext());
    		new SubscribeTask(mSubreddit, getApplicationContext(), mSettings).execute();
    		break;
    	case R.id.unsubscribe_menu_id:
    		CacheInfo.invalidateCachedSubreddit(getApplicationContext());
    		new UnsubscribeTask(mSubreddit, getApplicationContext(), mSettings).execute();
    		break;
    	case android.R.id.home:
    		Common.goHome(this);
    		break;
    		
    	default:
    		throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
    	}
    	
        return true;
    }
    
	private void logout() {
		Common.doLogout(mSettings, mClient, getApplicationContext());
		Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT)
				.show();
		new MyDownloadThreadsTask(mSubreddit).execute();
	}

    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
    	AlertDialog.Builder builder;
    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		dialog = new LoginDialog(this, mSettings, false) {
				public void onLoginChosen(String user, String password) {
					removeDialog(Constants.DIALOG_LOGIN);
		        	new MyLoginTask(user, password).execute(); 
				}
			};
    		break;
    		
    	case Constants.DIALOG_THREAD_CLICK:
    		dialog = new ThreadClickDialog(this, mSettings);
    		break;
    		
    	case Constants.DIALOG_SORT_BY:
    		builder = new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		builder.setTitle("Sort by:");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_CHOICES,
    				getSelectedSortBy(), sortByOnClickListener);
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_NEW:
    		builder = new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		builder.setTitle("what's new");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_NEW_CHOICES,
    				getSelectedSortByNew(), sortByNewOnClickListener);
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_CONTROVERSIAL:
    		builder = new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		builder.setTitle("most controversial");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_CHOICES,
    				getSelectedSortByControversial(), sortByControversialOnClickListener);
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_TOP:
    		builder = new AlertDialog.Builder(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		builder.setTitle("top scoring");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_TOP_CHOICES,
    				getSelectedSortByTop(), sortByTopOnClickListener);
    		dialog = builder.create();
    		break;

    	// "Please wait"
    	case Constants.DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
    		pdialog.setMessage("Logging in...");
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
    		
    	case Constants.DIALOG_THREAD_CLICK:
    		if (mVoteTargetThing == null)
    			break;
    		fillThreadClickDialog(dialog, mVoteTargetThing, mSettings, mThreadClickDialogOnClickListenerFactory);
    		break;
    		
    	case Constants.DIALOG_SORT_BY:
    		((AlertDialog) dialog).getListView().setItemChecked(getSelectedSortBy(), true);
    		break;
    	case Constants.DIALOG_SORT_BY_NEW:
    		((AlertDialog) dialog).getListView().setItemChecked(getSelectedSortByNew(), true);
    		break;
    	case Constants.DIALOG_SORT_BY_CONTROVERSIAL:
    		((AlertDialog) dialog).getListView().setItemChecked(getSelectedSortByControversial(), true);
    		break;
    	case Constants.DIALOG_SORT_BY_TOP:
    		((AlertDialog) dialog).getListView().setItemChecked(getSelectedSortByTop(), true);
    		break;
    	
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
	private int getSelectedSortBy() {
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_URL_CHOICES[i].equals(mSortByUrl)) {
				return i;
			}
		}
		return -1;
	}
	private int getSelectedSortByNew() {
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES[i].equals(mSortByUrlExtra)) {
				return i;
			}
		}
		return -1;
	}
	private int getSelectedSortByControversial() {
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES[i].equals(mSortByUrlExtra)) {
				return i;
			}
		}
		return -1;
	}
	private int getSelectedSortByTop() {
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES[i].equals(mSortByUrlExtra)) {
				return i;
			}
		}
		return -1;
	}

	private final OnClickListener downloadAfterOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			new MyDownloadThreadsTask(mSubreddit, mAfter, null, mCount).execute();
		}
	};
	private final OnClickListener downloadBeforeOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			new MyDownloadThreadsTask(mSubreddit, null, mBefore, mCount).execute();
		}
	};
    
    
	private final DialogInterface.OnClickListener sortByOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int item) {
			dialog.dismiss();
			String itemString = Constants.ThreadsSort.SORT_BY_CHOICES[item];
			if (Constants.ThreadsSort.SORT_BY_HOT.equals(itemString)) {
				mSortByUrl = Constants.ThreadsSort.SORT_BY_HOT_URL;
				mSortByUrlExtra = "";
				new MyDownloadThreadsTask(mSubreddit).execute();
			} else if (Constants.ThreadsSort.SORT_BY_NEW.equals(itemString)) {
				showDialog(Constants.DIALOG_SORT_BY_NEW);
			} else if (Constants.ThreadsSort.SORT_BY_CONTROVERSIAL.equals(itemString)) {
				showDialog(Constants.DIALOG_SORT_BY_CONTROVERSIAL);
			} else if (Constants.ThreadsSort.SORT_BY_TOP.equals(itemString)) {
				showDialog(Constants.DIALOG_SORT_BY_TOP);
			}
		}
	};
	private final DialogInterface.OnClickListener sortByNewOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int item) {
			dialog.dismiss();
			mSortByUrl = Constants.ThreadsSort.SORT_BY_NEW_URL;
			mSortByUrlExtra = Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES[item];
			new MyDownloadThreadsTask(mSubreddit).execute();
		}
	};
	private final DialogInterface.OnClickListener sortByControversialOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int item) {
			dialog.dismiss();
			mSortByUrl = Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL;
			mSortByUrlExtra = Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES[item];
			new MyDownloadThreadsTask(mSubreddit).execute();
		}
	};
	private final DialogInterface.OnClickListener sortByTopOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int item) {
			dialog.dismiss();
			mSortByUrl = Constants.ThreadsSort.SORT_BY_TOP_URL;
			mSortByUrlExtra = Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES[item];
			new MyDownloadThreadsTask(mSubreddit).execute();
		}
	};
	
	private final ThumbnailOnClickListenerFactory mThumbnailOnClickListenerFactory
			= new ThumbnailOnClickListenerFactory() {
		@Override
		public OnClickListener getThumbnailOnClickListener(final ThingInfo threadThingInfo, final Activity activity) {
			return new OnClickListener() {
				public void onClick(View v) {
					mJumpToThreadId = threadThingInfo.getId();
					setLinkClicked(threadThingInfo);
					Common.launchBrowser(
							activity,
							threadThingInfo.getUrl(),
							Util.createThreadUri(threadThingInfo).toString(),
							false,
							false,
							mSettings.isUseExternalBrowser(),
							mSettings.isSaveHistory()
					);
				}
			};
		}
	};
	
	private final ThreadClickDialogOnClickListenerFactory mThreadClickDialogOnClickListenerFactory
			= new ThreadClickDialogOnClickListenerFactory() {
		@Override
		public OnClickListener getLoginOnClickListener() {
			return new OnClickListener() {
				public void onClick(View v) {
					removeDialog(Constants.DIALOG_THREAD_CLICK);
					showDialog(Constants.DIALOG_LOGIN);
				}
			};
		}
		@Override
		public OnClickListener getLinkOnClickListener(final ThingInfo thingInfo, final boolean useExternalBrowser) {
    		return new OnClickListener() {
				public void onClick(View v) {
					removeDialog(Constants.DIALOG_THREAD_CLICK);
					setLinkClicked(thingInfo);
					Common.launchBrowser(ThreadsListActivity.this, thingInfo.getUrl(),
							Util.createThreadUri(thingInfo).toString(),
							false, false, useExternalBrowser, mSettings.isSaveHistory());
				}
			};
    	}
		@Override
		public OnClickListener getCommentsOnClickListener(final ThingInfo thingInfo) {
			return new OnClickListener() {
				public void onClick(View v) {
					removeDialog(Constants.DIALOG_THREAD_CLICK);

					CacheInfo.invalidateCachedThread(ThreadsListActivity.this);
					
					// Launch an Intent for CommentsListActivity
					Intent i = new Intent(ThreadsListActivity.this, CommentsListActivity.class);
					i.setData(Util.createThreadUri(thingInfo));
					i.putExtra(Constants.EXTRA_SUBREDDIT, thingInfo.getSubreddit());
					i.putExtra(Constants.EXTRA_TITLE, thingInfo.getTitle());
					i.putExtra(Constants.EXTRA_NUM_COMMENTS, Integer.valueOf(thingInfo.getNum_comments()));
					startActivity(i);
				}
			};
		}
		@Override
		public CompoundButton.OnCheckedChangeListener getVoteUpOnCheckedChangeListener(final ThingInfo thingInfo) {
			return new CompoundButton.OnCheckedChangeListener() {
		    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		    		removeDialog(Constants.DIALOG_THREAD_CLICK);
			    	if (isChecked) {
						new MyVoteTask(thingInfo, 1, thingInfo.getSubreddit()).execute();
					} else {
						new MyVoteTask(thingInfo, 0, thingInfo.getSubreddit()).execute();
					}
				}
		    };
		}
		@Override
	    public CompoundButton.OnCheckedChangeListener getVoteDownOnCheckedChangeListener(final ThingInfo thingInfo) {
	    	return new CompoundButton.OnCheckedChangeListener() {
		        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			    	removeDialog(Constants.DIALOG_THREAD_CLICK);
					if (isChecked) {
						new MyVoteTask(thingInfo, -1, thingInfo.getSubreddit()).execute();
					} else {
						new MyVoteTask(thingInfo, 0, thingInfo.getSubreddit()).execute();
					}
				}
		    };
	    }
	};
	
	private void setLinkClicked(ThingInfo threadThingInfo) {
		threadThingInfo.setClicked(true);
		mThreadsAdapter.notifyDataSetChanged();
	}
	
	@Override
    protected void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putString(Constants.SUBREDDIT_KEY, mSubreddit);
    	state.putString(Constants.ThreadsSort.SORT_BY_KEY, mSortByUrl);
    	state.putString(Constants.JUMP_TO_THREAD_ID_KEY, mJumpToThreadId);
    	state.putString(Constants.AFTER_KEY, mAfter);
    	state.putString(Constants.BEFORE_KEY, mBefore);
    	state.putInt(Constants.THREAD_COUNT_KEY, mCount);
    	state.putString(Constants.LAST_AFTER_KEY, mLastAfter);
    	state.putString(Constants.LAST_BEFORE_KEY, mLastBefore);
    	state.putInt(Constants.THREAD_LAST_COUNT_KEY, mLastCount);
    	state.putParcelable(Constants.VOTE_TARGET_THING_INFO_KEY, mVoteTargetThing);
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
        	Constants.DIALOG_LOGGING_IN,
        	Constants.DIALOG_LOGIN,
        	Constants.DIALOG_SORT_BY,
        	Constants.DIALOG_SORT_BY_CONTROVERSIAL,
        	Constants.DIALOG_SORT_BY_NEW,
        	Constants.DIALOG_SORT_BY_TOP,
        	Constants.DIALOG_THREAD_CLICK,
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
