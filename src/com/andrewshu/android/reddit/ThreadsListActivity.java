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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.impl.client.DefaultHttpClient;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
	// BitmapManager helps with filling in thumbnails
	private final BitmapManager drawableManager = new BitmapManager();

    /** Custom list adapter that fits our threads data into the list. */
    private ThreadsListAdapter mThreadsAdapter = null;
    private ArrayList<ThingInfo> mThreadsList = null;
    private static final Object THREAD_ADAPTER_LOCK = new Object();

    private final DefaultHttpClient mClient = Common.getGzipHttpClient();
	
   
    private final RedditSettings mSettings = new RedditSettings();
    
    // UI State
    private ThingInfo mVoteTargetThingInfo = null;
    private DownloadThreadsTask mCurrentDownloadThreadsTask = null;
    private final Object mCurrentDownloadThreadsTaskLock = new Object();
    
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
    private String mSortByUrlExtra = Constants.EMPTY_STRING;
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
		
        Common.loadRedditPreferences(getApplicationContext(), mSettings, mClient);
        setRequestedOrientation(mSettings.rotation);
        setTheme(mSettings.theme);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    	
		if (savedInstanceState != null) {
        	if (Constants.LOGGING) Log.d(TAG, "using savedInstanceState");
			mSubreddit = savedInstanceState.getString(Constants.SUBREDDIT_KEY);
	        if (mSubreddit == null)
	        	mSubreddit = mSettings.homepage;
	        mAfter = savedInstanceState.getString(Constants.AFTER_KEY);
	        mBefore = savedInstanceState.getString(Constants.BEFORE_KEY);
	        mCount = savedInstanceState.getInt(Constants.THREAD_COUNT_KEY);
	        mLastAfter = savedInstanceState.getString(Constants.LAST_AFTER_KEY);
	        mLastBefore = savedInstanceState.getString(Constants.LAST_BEFORE_KEY);
	        mLastCount = savedInstanceState.getInt(Constants.THREAD_LAST_COUNT_KEY);
	        mSortByUrl = savedInstanceState.getString(Constants.ThreadsSort.SORT_BY_KEY);
		    mJumpToThreadId = savedInstanceState.getString(Constants.JUMP_TO_THREAD_ID_KEY);
		    mVoteTargetThingInfo = savedInstanceState.getParcelable(Constants.VOTE_TARGET_THING_INFO_KEY);
		    
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
				new MyDownloadThreadsTask(mSettings.homepage).execute();
			}
		}
		// No subreddit specified by Intent, so load the user's home reddit
		else {
        	new MyDownloadThreadsTask(mSettings.homepage).execute();
        }
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
		CookieSyncManager.getInstance().startSync();
    	int previousTheme = mSettings.theme;
    	Common.loadRedditPreferences(getApplicationContext(), mSettings, mClient);
    	setRequestedOrientation(mSettings.rotation);
    	if (mSettings.theme != previousTheme) {
    		resetUI(mThreadsAdapter);
    	}
    	updateNextPreviousButtons();
    	if (mThreadsAdapter != null) {
    		jumpToThread();
    	}
    	new PeekEnvelopeTask(this, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
		CookieSyncManager.getInstance().stopSync();
		Common.saveRedditPreferences(getApplicationContext(), mSettings);
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
        if(mSettings.confirmQuit && keyCode == KeyEvent.KEYCODE_BACK && isTaskRoot()) {
            //Ask the user if they want to quit
            new AlertDialog.Builder(this)
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
            fillThreadsListItemView(view, item, ThreadsListActivity.this, mSettings, drawableManager,
            		true, thumbnailOnClickListenerFactory);
            
            return view;
        }
    }
    
    public static void fillThreadsListItemView(View view, ThingInfo item,
    		Activity activity, RedditSettings settings,
    		BitmapManager bitmapManager,
    		boolean defaultUseGoArrow,
    		ThumbnailOnClickListenerFactory thumbnailOnClickListenerFactory) {
    	
    	Resources res = activity.getResources();
    	
    	TextView titleView = (TextView) view.findViewById(R.id.title);
        TextView votesView = (TextView) view.findViewById(R.id.votes);
        TextView numCommentsSubredditView = (TextView) view.findViewById(R.id.numCommentsSubreddit);
        TextView nsfwView = (TextView) view.findViewById(R.id.nsfw);
//        TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
        ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
        ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
        ImageView thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
        View dividerView = view.findViewById(R.id.divider);
        ProgressBar indeterminateProgressBar = (ProgressBar) view.findViewById(R.id.indeterminate_progress);
        
        // Set the title and domain using a SpannableStringBuilder
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String title = item.getTitle();
        if (title == null)
        	title = "";
        SpannableString titleSS = new SpannableString(title);
        int titleLen = title.length();
        titleSS.setSpan(new TextAppearanceSpan(activity,
        		Util.getTextAppearanceResource(settings.theme, android.R.style.TextAppearance_Large)),
        		0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        String domain = item.getDomain();
        if (domain == null)
        	domain = "";
        int domainLen = domain.length();
        SpannableString domainSS = new SpannableString("("+item.getDomain()+")");
        domainSS.setSpan(new TextAppearanceSpan(activity,
        		Util.getTextAppearanceResource(settings.theme, android.R.style.TextAppearance_Small)),
        		0, domainLen+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (Util.isLightTheme(settings.theme)) {
        	// FIXME: This doesn't work persistently, since "clicked" is not delivered to reddit.com
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
        if (thumbnailView != null) {
        	
        	//check for wifi connection and wifi thumbnail setting
        	boolean thumbOkay = true;
        	if (settings.loadThumbnailsOnlyWifi)
        	{
        		thumbOkay = false;
        		ConnectivityManager connMan = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        		NetworkInfo netInfo = connMan.getActiveNetworkInfo();
        		if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI && netInfo.isConnected()) {
        			thumbOkay = true;
        		}
        	}
        	if (settings.loadThumbnails && thumbOkay) {
        		dividerView.setVisibility(View.VISIBLE);
        		thumbnailView.setVisibility(View.VISIBLE);
        		indeterminateProgressBar.setVisibility(View.GONE);
        		
            	if (item.getUrl() != null) {
            		OnClickListener thumbnailOnClickListener = thumbnailOnClickListenerFactory.getThumbnailOnClickListener(
        					item.getId(), item.getUrl(), Util.createThreadUri(item).toString(), activity);
            		if (thumbnailOnClickListener != null) {
		            	thumbnailView.setOnClickListener(thumbnailOnClickListener);
		            	indeterminateProgressBar.setOnClickListener(thumbnailOnClickListener);
            		}
            	}
            	
            	// Fill in the thumbnail using a Thread. Note that thumbnail URL can be absolute path.
            	if (item.getThumbnail() != null && !Constants.EMPTY_STRING.equals(item.getThumbnail())) {
            		bitmapManager.fetchBitmapOnThread(Util.absolutePathToURL(item.getThumbnail()),
            				thumbnailView, indeterminateProgressBar, activity);
            	} else {
            		if (defaultUseGoArrow) {
	            		indeterminateProgressBar.setVisibility(View.GONE);
	            		thumbnailView.setVisibility(View.VISIBLE);
	            		thumbnailView.setImageResource(R.drawable.go_arrow);
            		} else {
            			// if no thumbnail image, hide thumbnail icon
            			dividerView.setVisibility(View.GONE);
            			thumbnailView.setVisibility(View.GONE);
            			indeterminateProgressBar.setVisibility(View.GONE);
            		}
            	}
            	
            	// Set thumbnail background based on current theme
            	if (Util.isLightTheme(settings.theme)) {
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
        			threadClickDialogOnClickListenerFactory.getLinkOnClickListener(thingInfo, settings.useExternalBrowser));
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
    	mVoteTargetThingInfo = item;
    	mJumpToThreadId = item.getId();
    	
    	showDialog(Constants.DIALOG_THREAD_CLICK);
    }

    /**
     * Resets the output UI list contents, retains session state.
     * @param threadsAdapter A ThreadsListAdapter to use. Pass in null if you want a new empty one created.
     */
    void resetUI(ThreadsListAdapter threadsAdapter) {
    	setTheme(mSettings.theme);
    	setContentView(R.layout.threads_list_content);
        registerForContextMenu(getListView());
        getListView().setOnScrollListener(listViewOnScrollListener);

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
	    Common.updateListDrawables(this, mSettings.theme);
	    updateNextPreviousButtons();
    }
    
    private void enableLoadingScreen() {
    	if (Util.isLightTheme(mSettings.theme)) {
    		setContentView(R.layout.loading_light);
    	} else {
    		setContentView(R.layout.loading_dark);
    	}
    	synchronized (THREAD_ADAPTER_LOCK) {
	    	if (mThreadsAdapter != null)
	    		mThreadsAdapter.mIsLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mThreadsAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
    }
    
    private void updateNextPreviousButtons() {
    	View nextPrevious = findViewById(R.id.next_previous_layout);
    	View nextPreviousBorder = findViewById(R.id.next_previous_border_top);
    	
    	if (nextPrevious == null)
    		return;
		
    	boolean shouldShow = (mAfter != null || mBefore != null) &&
			(mSettings.alwaysShowNextPrevious || getListView().getLastVisiblePosition() == getListView().getCount() - 1);
		
		if (shouldShow && nextPrevious.getVisibility() != View.VISIBLE) {
	    	if (nextPrevious != null && nextPreviousBorder != null) {
		    	if (Util.isLightTheme(mSettings.theme)) {
		       		nextPrevious.setBackgroundResource(R.color.white);
		       		nextPreviousBorder.setBackgroundResource(R.color.black);
		    	} else {
		       		nextPreviousBorder.setBackgroundResource(R.color.white);
		    	}
		    	nextPrevious.setVisibility(View.VISIBLE);
	    	}
			// update the "next 25" and "prev 25" buttons
	    	final Button nextButton = (Button) findViewById(R.id.next_button);
	    	final Button previousButton = (Button) findViewById(R.id.previous_button);
	    	if (nextButton != null) {
		    	if (mAfter != null) {
		    		nextButton.setVisibility(View.VISIBLE);
		    		nextButton.setOnClickListener(downloadAfterOnClickListener);
		    	} else {
		    		nextButton.setVisibility(View.INVISIBLE);
		    	}
	    	}
	    	if (previousButton != null) {
		    	if (mBefore != null && mCount != Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT) {
		    		previousButton.setVisibility(View.VISIBLE);
		    		previousButton.setOnClickListener(downloadBeforeOnClickListener);
		    	} else {
		    		previousButton.setVisibility(View.INVISIBLE);
		    	}
	    	}
		} else if (!shouldShow && nextPrevious.getVisibility() == View.VISIBLE) {
    		nextPrevious.setVisibility(View.GONE);
    	}
    }
    
    
    /**
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.)
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

    		if (mContentLength == -1) {
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_OFF);
    		}

    		if (success) {
    			synchronized (THREAD_ADAPTER_LOCK) {
		    		for (ThingInfo ti : mThingInfos)
		        		mThreadsList.add(ti);
		    		drawableManager.clearCache();  // clear thumbnails
		    		mThreadsAdapter.notifyDataSetChanged();
    			}
    			
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
    		// 0-9999 is ok, 10000 means it's finished
    		if (mContentLength == -1) {
//    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * 9999 / (int) mContentLength);
    		}
    		else {
    			getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * 9999 / (int) mContentLength);
    		}
    	}
    	
    	public void propertyChange(PropertyChangeEvent event) {
    		publishProgress((Long) event.getNewValue());
    	}
    }
    
    
    private class LoginTask extends AsyncTask<Void, Void, String> {
    	private String mUsername, mPassword;
    	
    	LoginTask(String username, String password) {
    		mUsername = username;
    		mPassword = password;
    	}
    	
    	@Override
    	public String doInBackground(Void... v) {
    		return Common.doLogin(mUsername, mPassword, mSettings, mClient, getApplicationContext());
        }
    	
    	@Override
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	@Override
    	protected void onPostExecute(String errorMessage) {
    		dismissDialog(Constants.DIALOG_LOGGING_IN);
    		if (errorMessage == null) {
    			Toast.makeText(ThreadsListActivity.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
    			// Check mail
    			new PeekEnvelopeTask(getApplicationContext(), mClient, mSettings.mailNotificationStyle).execute();
    			// Refresh the threads list
    			new MyDownloadThreadsTask(mSubreddit).execute();
        	} else {
            	Common.showErrorToast(errorMessage, Toast.LENGTH_LONG, ThreadsListActivity.this);
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
    	
    	mVoteTargetThingInfo = _item;
    	
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
			Common.launchBrowser(this, _item.getUrl(), Util.createThreadUri(_item).toString(), false, true, true);
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
	        menu.findItem(R.id.login_logout_menu_id).setTitle(
	        		String.format(getResources().getString(R.string.logout), mSettings.username));
	        menu.findItem(R.id.inbox_menu_id).setVisible(true);
	        menu.findItem(R.id.user_profile_menu_id).setVisible(true);
	        menu.findItem(R.id.user_profile_menu_id).setTitle(
	        		String.format(getResources().getString(R.string.user_profile), mSettings.username));
    	} else {
            menu.findItem(R.id.login_logout_menu_id).setTitle(getResources().getString(R.string.login));
            menu.findItem(R.id.inbox_menu_id).setVisible(false);
            menu.findItem(R.id.user_profile_menu_id).setVisible(false);
    	}
    	
    	// Theme: Light/Dark
    	src = Util.isLightTheme(mSettings.theme) ?
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
    	case R.id.login_logout_menu_id:
        	if (mSettings.isLoggedIn()) {
        		Common.doLogout(mSettings, mClient, getApplicationContext());
        		Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new MyDownloadThreadsTask(mSubreddit).execute();
        	} else {
        		showDialog(Constants.DIALOG_LOGIN);
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
    			url = "http://www.reddit.com";
    		else
        		url = new StringBuilder("http://www.reddit.com/r/").append(mSubreddit).toString();
    		Common.launchBrowser(this, url, null, false, true, true);
    		break;
        case R.id.light_dark_menu_id:
    		mSettings.setTheme(Util.getInvertedTheme(mSettings.theme));
    		resetUI(mThreadsAdapter);
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

    	default:
    		throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
    	}
    	
        return true;
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
				public void onLoginChosen(String user, String password) {
					dismissDialog(Constants.DIALOG_LOGIN);
		        	new LoginTask(user, password).execute(); 
				}
			};
    		break;
    		
    	case Constants.DIALOG_THREAD_CLICK:
    		inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(this);
    		dialog = builder.setView(inflater.inflate(R.layout.thread_click_dialog, null)).create();
    		break;
    		
    	case Constants.DIALOG_SORT_BY:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("Sort by:");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_CHOICES,
    				getSelectedSortBy(), sortByOnClickListener);
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_NEW:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("what's new");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_NEW_CHOICES,
    				getSelectedSortByNew(), sortByNewOnClickListener);
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_CONTROVERSIAL:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("most controversial");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_CHOICES,
    				getSelectedSortByControversial(), sortByControversialOnClickListener);
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_TOP:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("top scoring");
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_TOP_CHOICES,
    				getSelectedSortByTop(), sortByTopOnClickListener);
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
    		
    	case Constants.DIALOG_THREAD_CLICK:
    		if (mVoteTargetThingInfo == null)
    			break;
    		fillThreadClickDialog(dialog, mVoteTargetThingInfo, mSettings, threadClickDialogOnClickListenerFactory);
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
		int selectedSortBy = -1;
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_URL_CHOICES[i].equals(mSortByUrl)) {
				selectedSortBy = i;
				break;
			}
		}
		return selectedSortBy;
	}
	private int getSelectedSortByNew() {
		int selectedSortByNew = -1;
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES[i].equals(mSortByUrlExtra)) {
				selectedSortByNew = i;
				break;
			}
		}
		return selectedSortByNew;
	}
	private int getSelectedSortByControversial() {
		int selectedSortByControversial = -1;
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES[i].equals(mSortByUrlExtra)) {
				selectedSortByControversial = i;
				break;
			}
		}
		return selectedSortByControversial;
	}
	private int getSelectedSortByTop() {
		int selectedSortByTop = -1;
		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES.length; i++) {
			if (Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES[i].equals(mSortByUrlExtra)) {
				selectedSortByTop = i;
				break;
			}
		}
		return selectedSortByTop;
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
				mSortByUrlExtra = Constants.EMPTY_STRING;
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
	
	private final AbsListView.OnScrollListener listViewOnScrollListener = new AbsListView.OnScrollListener() {
		@Override
		public void onScroll(AbsListView view, int firstVisibleItem,
				int visibleItemCount, int totalItemCount) {
			if (!mSettings.alwaysShowNextPrevious) {
				updateNextPreviousButtons();
			}
		}

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			// NOOP
		}
	};
	
	private final ThumbnailOnClickListenerFactory thumbnailOnClickListenerFactory
			= new ThumbnailOnClickListenerFactory() {
		public OnClickListener getThumbnailOnClickListener(String jumpToId, String url, String threadUrl, Context context) {
			final String fJumpToId = jumpToId;
			final String fUrl = url;
			final String fThreadUrl = threadUrl;
			return new OnClickListener() {
				public void onClick(View v) {
					ThreadsListActivity.this.mJumpToThreadId = fJumpToId;
					Common.launchBrowser(ThreadsListActivity.this, fUrl, fThreadUrl,
							false, false, ThreadsListActivity.this.mSettings.useExternalBrowser);
				}
			};
		}
	};
	
	private final ThreadClickDialogOnClickListenerFactory threadClickDialogOnClickListenerFactory
			= new ThreadClickDialogOnClickListenerFactory() {
		public OnClickListener getLoginOnClickListener() {
			return new OnClickListener() {
				public void onClick(View v) {
					dismissDialog(Constants.DIALOG_THREAD_CLICK);
					showDialog(Constants.DIALOG_LOGIN);
				}
			};
		}
		public OnClickListener getLinkOnClickListener(ThingInfo thingInfo, boolean useExternalBrowser) {
			final ThingInfo info = thingInfo;
    		final boolean fUseExternalBrowser = useExternalBrowser;
    		return new OnClickListener() {
				public void onClick(View v) {
					dismissDialog(Constants.DIALOG_THREAD_CLICK);
					// Launch Intent to goto the URL
					Common.launchBrowser(ThreadsListActivity.this, info.getUrl(),
							Util.createThreadUri(info).toString(),
							false, false, fUseExternalBrowser);
				}
			};
    	}
		public OnClickListener getCommentsOnClickListener(ThingInfo thingInfo) {
			final ThingInfo info = thingInfo;
			return new OnClickListener() {
				public void onClick(View v) {
					dismissDialog(Constants.DIALOG_THREAD_CLICK);
					// Launch an Intent for CommentsListActivity
					CacheInfo.invalidateCachedThread(ThreadsListActivity.this);
					Intent i = new Intent(ThreadsListActivity.this, CommentsListActivity.class);
					i.setData(Util.createThreadUri(info));
					i.putExtra(Constants.EXTRA_SUBREDDIT, info.getSubreddit());
					i.putExtra(Constants.EXTRA_TITLE, info.getTitle());
					i.putExtra(Constants.EXTRA_NUM_COMMENTS, Integer.valueOf(info.getNum_comments()));
					startActivity(i);
				}
			};
		}
		public CompoundButton.OnCheckedChangeListener getVoteUpOnCheckedChangeListener(ThingInfo thingInfo) {
			final ThingInfo info = thingInfo;
			return new CompoundButton.OnCheckedChangeListener() {
		    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		    		dismissDialog(Constants.DIALOG_THREAD_CLICK);
			    	if (isChecked) {
						new MyVoteTask(info, 1, info.getSubreddit()).execute();
					} else {
						new MyVoteTask(info, 0, info.getSubreddit()).execute();
					}
				}
		    };
		}
	    public CompoundButton.OnCheckedChangeListener getVoteDownOnCheckedChangeListener(ThingInfo thingInfo) {
	    	final ThingInfo info = thingInfo;
	    	return new CompoundButton.OnCheckedChangeListener() {
		        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			    	dismissDialog(Constants.DIALOG_THREAD_CLICK);
					if (isChecked) {
						new MyVoteTask(info, -1, info.getSubreddit()).execute();
					} else {
						new MyVoteTask(info, 0, info.getSubreddit()).execute();
					}
				}
		    };
	    }
	};
	
	public interface ThumbnailOnClickListenerFactory {
		public OnClickListener getThumbnailOnClickListener(String jumpToId, String url, String threadUrl, Context context);
	}
	
	public interface ThreadClickDialogOnClickListenerFactory {
		public OnClickListener getLoginOnClickListener();
		public OnClickListener getLinkOnClickListener(ThingInfo thingInfo, boolean useExternalBrowser);
		public OnClickListener getCommentsOnClickListener(ThingInfo thingInfo);
		public CompoundButton.OnCheckedChangeListener getVoteUpOnCheckedChangeListener(ThingInfo thingInfo);
		public CompoundButton.OnCheckedChangeListener getVoteDownOnCheckedChangeListener(ThingInfo thingInfo);
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
    	state.putParcelable(Constants.VOTE_TARGET_THING_INFO_KEY, mVoteTargetThingInfo);
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
	        	dismissDialog(dialog);
		    } catch (IllegalArgumentException e) {
		    	// Ignore.
		    }
        }
    }
}
