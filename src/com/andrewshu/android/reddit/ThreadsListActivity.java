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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
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
	
	private final ObjectMapper om = new ObjectMapper();
	// BitmapManager helps with filling in thumbnails
	private BitmapManager drawableManager = new BitmapManager();

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
		        	new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
		        			mSubreddit, mLastAfter, null, mLastCount).execute();
		        } else if (mLastBefore != null) {
		        	new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
		        			mSubreddit, null, mLastBefore, mLastCount).execute();
		        } else {
		        	new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
		        			mSubreddit).execute();
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
				new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
						redditContextMatcher.group(1)).execute();
			} else {
				new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
	           			mSettings.homepage).execute();
			}
		}
		// No subreddit specified by Intent, so load the user's home reddit
		else {
        	new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
           			mSettings.homepage).execute();
        }
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	int previousTheme = mSettings.theme;
    	Common.loadRedditPreferences(getApplicationContext(), mSettings, mClient);
    	setRequestedOrientation(mSettings.rotation);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.theme);
    		setContentView(R.layout.threads_list_content);
    		setListAdapter(mThreadsAdapter);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
    	if (mThreadsAdapter != null) {
    		jumpToThread();
    	}
    	new PeekEnvelopeTask(this, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    /**
     * Wrapper method to do additional changes associated with changing the content view.
     */
    public void setContentView(int layoutResID) {
    	super.setContentView(layoutResID);

    	// HACK: set background color directly for android 2.0
        if (mSettings.theme == R.style.Reddit_Light)
        	getListView().setBackgroundResource(R.color.white);
        registerForContextMenu(getListView());

        // allow the trackball to select next25 and prev25 buttons
        getListView().setItemsCanFocus(true);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
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
    				new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
    						redditContextMatcher.group(1)).execute();
    			}
    		}
    		break;
    	case Constants.ACTIVITY_SUBMIT_LINK:
    		if (resultCode == Activity.RESULT_OK) {
    	    	// Returns the new thread's Uri as the Intent.getData()
    			// Extras: subreddit, thread title
    			
    			// Set the new subreddit
    			Bundle extras = intent.getExtras();
    			if (extras == null) {
    				if (Constants.LOGGING) Log.e(TAG, "onActivityResult: ACTIVITY_SUBMIT_LINK: extras unexpectedly null");
    			} else {
    				mSubreddit = extras.getString(Constants.EXTRA_SUBREDDIT);
    			}
				// Start up comments list with the new thread
	    		CacheInfo.invalidateCachedThread(getApplicationContext());
	    		Intent i = new Intent(getApplicationContext(), CommentsListActivity.class);
				i.putExtras(intent.getExtras());
				i.putExtra(Constants.EXTRA_NUM_COMMENTS, 0);
				startActivity(i);
    		} else if (resultCode == Constants.RESULT_LOGIN_REQUIRED) {
    			Common.showErrorToast("You must be logged in to make a submission.", Toast.LENGTH_LONG, this);
    		}
    		break;
    	default:
    		break;
    	}
    }
    
    
    final class ThreadsListAdapter extends ArrayAdapter<ThingInfo> {
    	static final int THREAD_ITEM_VIEW_TYPE = 0;
    	static final int MORE_ITEM_VIEW_TYPE = 1;
    	// The number of view types
    	static final int VIEW_TYPE_COUNT = 2;
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
        	// If we are on a subsequent page, return the item view type 
            if (position < getCount() - 1 || (mAfter == null && mBefore == null))
        		return THREAD_ITEM_VIEW_TYPE;
        	return MORE_ITEM_VIEW_TYPE;
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

            if (position < getCount() - 1 || (mAfter == null && mBefore == null)) {
	            // Here view may be passed in for re-use, or we make a new one.
	            if (convertView == null) {
	                view = mInflater.inflate(R.layout.threads_list_item, null);
	            } else {
	                view = convertView;
	            }
	            
	            ThingInfo item = this.getItem(position);
	            
	            // Set the values of the Views for the ThreadsListItem
	            
	            TextView titleView = (TextView) view.findViewById(R.id.title);
	            TextView votesView = (TextView) view.findViewById(R.id.votes);
	            TextView numCommentsView = (TextView) view.findViewById(R.id.numComments);
	            TextView subredditView = (TextView) view.findViewById(R.id.subreddit);
	            TextView nsfwView = (TextView) view.findViewById(R.id.nsfw);
	//            TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
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
	            TextAppearanceSpan titleTAS = new TextAppearanceSpan(getApplicationContext(), R.style.TextAppearance_14sp);
	            titleSS.setSpan(titleTAS, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	            if (mSettings.theme == R.style.Reddit_Light) {
	            	// FIXME: This doesn't work persistently, since "clicked" is not delivered to reddit.com
		            if (item.isClicked()) {
		            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.purple));
		            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		            } else {
		            	ForegroundColorSpan fcs = new ForegroundColorSpan(res.getColor(R.color.blue));
		            	titleSS.setSpan(fcs, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		            }
	            }
	            builder.append(titleSS);
	            builder.append(" ");
	            String domain = item.getDomain();
	            if (domain == null)
	            	domain = "";
	            int domainLen = domain.length();
	            SpannableString domainSS = new SpannableString("("+item.getDomain()+")");
	            TextAppearanceSpan domainTAS = new TextAppearanceSpan(getApplicationContext(), R.style.TextAppearance_10sp);
	            domainSS.setSpan(domainTAS, 0, domainLen+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	            builder.append(domainSS);
	            titleView.setText(builder);
	            
	            votesView.setText("" + item.getScore());
	            numCommentsView.setText(Util.showNumComments(item.getNum_comments()));
	            subredditView.setText(item.getSubreddit());
	            
                if(item.isOver_18()){
                    nsfwView.setVisibility(View.VISIBLE);
                } else {
                    nsfwView.setVisibility(View.GONE);
                }
                
	            // Set the up and down arrow colors based on whether user likes
	            if (mSettings.loggedIn) {
	            	if (item.getLikes() == null) {
	            		voteUpView.setImageResource(R.drawable.vote_up_gray);
	            		voteDownView.setImageResource(R.drawable.vote_down_gray);
	            		votesView.setTextColor(res.getColor(R.color.gray));
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
	        		votesView.setTextColor(res.getColor(R.color.gray));
	            }
	            
	            // Thumbnails open links
	            if (thumbnailView != null) {
	            	
	            	//check for wifi connection and wifi thumbnail setting
	            	boolean thumbOkay = true;
	            	if (mSettings.loadThumbnailsOnlyWifi)
	            	{
	            		thumbOkay = false;
	            		ConnectivityManager connMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	            		NetworkInfo netInfo = connMan.getActiveNetworkInfo();
	            		if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI && netInfo.isConnected()) {
	            			thumbOkay = true;
	            		}
	            	}
	            	if (mSettings.loadThumbnails && thumbOkay) {
	            		dividerView.setVisibility(View.VISIBLE);
	            		thumbnailView.setVisibility(View.VISIBLE);
	            		indeterminateProgressBar.setVisibility(View.GONE);
	            		
		            	final String url = item.getUrl();
		            	final String jumpToId = item.getId();
		            	if (url != null) {
			            	thumbnailView.setOnClickListener(new OnClickListener() {
			            		public void onClick(View v) {
			            			mJumpToThreadId = jumpToId;
			            			Common.launchBrowser(url, ThreadsListActivity.this, false, false, mSettings.useExternalBrowser);
			            		}
			            	});
			            	indeterminateProgressBar.setOnClickListener(new OnClickListener() {
			            		public void onClick(View v) {
			            			mJumpToThreadId = jumpToId;
			            			Common.launchBrowser(url, ThreadsListActivity.this, false, false, mSettings.useExternalBrowser);
			            		}
			            	});
		            	}
		            	
		            	// Fill in the thumbnail using a Thread. Note that thumbnail URL can be absolute path.
		            	if (item.getThumbnail() != null && !Constants.EMPTY_STRING.equals(item.getThumbnail())) {
		            		drawableManager.fetchBitmapOnThread(Util.absolutePathToURL(item.getThumbnail()),
		            				thumbnailView, indeterminateProgressBar, ThreadsListActivity.this);
		            	} else {
		            		indeterminateProgressBar.setVisibility(View.GONE);
		            		thumbnailView.setVisibility(View.VISIBLE);
		            		thumbnailView.setImageResource(R.drawable.go_arrow);
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
            } else {
            	// The "25 more" list item
            	if (convertView == null)
            		view = mInflater.inflate(R.layout.more_threads_view, null);
            	else
            		view = convertView;
            	final Button nextButton = (Button) view.findViewById(R.id.next_button);
            	final Button previousButton = (Button) view.findViewById(R.id.previous_button);
            	if (mAfter != null) {
            		nextButton.setVisibility(View.VISIBLE);
            		nextButton.setOnClickListener(downloadAfterOnClickListener);
            	} else {
            		nextButton.setVisibility(View.INVISIBLE);
            	}
            	if (mBefore != null && mCount != Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT) {
            		previousButton.setVisibility(View.VISIBLE);
            		previousButton.setOnClickListener(downloadBeforeOnClickListener);
            	} else {
            		previousButton.setVisibility(View.INVISIBLE);
            	}
            }
            return view;
        }
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

    	// if mThreadsAdapter.getCount() - 1 contains the "next 25, prev 25" buttons,
        // or if there are fewer than 25 threads...
        if (position < mThreadsAdapter.getCount() - 1 || mThreadsAdapter.getCount() < Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT + 1) {
            showDialog(Constants.DIALOG_THING_CLICK);
        } else {
        	// 25 more. Use buttons.
        }
    }

    /**
     * Resets the output UI list contents, retains session state.
     * @param threadsAdapter A ThreadsListAdapter to use. Pass in null if you want a new empty one created.
     */
    void resetUI(ThreadsListAdapter threadsAdapter) {
    	setContentView(R.layout.threads_list_content);
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
    }
    
    private void enableLoadingScreen() {
    	if (mSettings.theme == R.style.Reddit_Light) {
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
    
    
    /**
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.)
     *        If the number of elements in subreddit is >= 2, treat 2nd element as "after" 
     */
    private class MyDownloadThreadsTask extends DownloadThreadsTask {
    	
    	public MyDownloadThreadsTask(Context context, DefaultHttpClient client, ObjectMapper om,
				String sortByUrl, String sortByUrlExtra, String subreddit) {
			super(context, client, om, sortByUrl, sortByUrlExtra, subreddit);
			// TODO Auto-generated constructor stub
		}
    	
    	public MyDownloadThreadsTask(Context context, DefaultHttpClient client, ObjectMapper om,
				String sortByUrl, String sortByUrlExtra, String subreddit,
				String after, String before, int count) {
			super(context, client, om, sortByUrl, sortByUrlExtra, subreddit, after, before, count);
			// TODO Auto-generated constructor stub
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
			
	    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
	    	
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

    		if (success) {
    			synchronized (THREAD_ADAPTER_LOCK) {
		    		for (ThingInfo ti : mThingInfos)
		        		mThreadsList.add(ti);
		    		// "25 more" button.
		    		if (mThreadsList.size() >= Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT)
		    			mThreadsList.add(new ThingInfo());
		    		drawableManager = new BitmapManager();  // clear thumbnails
		    		mThreadsAdapter.notifyDataSetChanged();
    			}
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
    		getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * 9999 / (int) mContentLength);
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
    			new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
        	} else {
            	Common.showErrorToast(errorMessage, Toast.LENGTH_LONG, ThreadsListActivity.this);
    		}
    	}
    }
    
    private class VoteTask extends AsyncTask<Void, Void, Boolean> {
    	
    	private static final String TAG = "VoteWorker";
    	
    	private String _mThingFullname, _mSubreddit;
    	private int _mDirection;
    	private String _mUserError = "Error voting.";
    	private ThingInfo _mTargetThingInfo;
    	
    	// Save the previous arrow and score in case we need to revert
    	private int _mPreviousScore;
    	private Boolean _mPreviousLikes;
    	
    	VoteTask(String thingFullname, int direction, String subreddit) {
    		_mThingFullname = thingFullname;
    		_mDirection = direction;
    		_mSubreddit = subreddit;
    		// Copy these because they can change while voting thread is running
    		_mTargetThingInfo = mVoteTargetThingInfo;
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
        		String modhash = Common.doUpdateModhash(mClient);
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
    			nvps.add(new BasicNameValuePair("id", _mThingFullname));
    			nvps.add(new BasicNameValuePair("dir", String.valueOf(_mDirection)));
    			nvps.add(new BasicNameValuePair("r", _mSubreddit));
    			nvps.add(new BasicNameValuePair("uh", mSettings.modhash));
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
        		if (Constants.LOGGING) Log.e(TAG, "VoteTask", e);
        		_mUserError = e.getMessage();
        	} finally {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent", e2);
        			}
        		}
        	}
        	return false;
        }
    	
    	@Override
    	public void onPreExecute() {
    		if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to vote.", Toast.LENGTH_LONG, ThreadsListActivity.this);
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
    			CacheInfo.invalidateCachedSubreddit(getApplicationContext());
    		} else {
    			// Vote failed. Undo the score.
            	_mTargetThingInfo.setLikes(_mPreviousLikes);
        		_mTargetThingInfo.setScore(_mPreviousScore);
        		mThreadsAdapter.notifyDataSetChanged();
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, ThreadsListActivity.this);
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
					mThreadsAdapter.remove(mVoteTargetThingInfo);
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
    	
    	menu.add(0, Constants.OPEN_IN_BROWSER_CONTEXT_ITEM, 0, "Open in browser");
    	menu.add(0, Constants.SHARE_CONTEXT_ITEM, 0, "Share");
    	menu.add(0, Constants.OPEN_COMMENTS_CONTEXT_ITEM, 0, "Comments");
    	
    	if(mSettings.loggedIn){
    		if(!_item.isSaved()){
    			menu.add(0, Constants.SAVE_CONTEXT_ITEM, 0, "Save");
    		} else {
    			menu.add(0, Constants.UNSAVE_CONTEXT_ITEM, 0, "Unsave");
    		}
    		menu.add(0, Constants.HIDE_CONTEXT_ITEM, 0, "Hide");
    	}
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterView.AdapterContextMenuInfo info;
        info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        
        ThingInfo _item = mThreadsAdapter.getItem(info.position);
        
        switch (item.getItemId()) {
		case Constants.SHARE_CONTEXT_ITEM:
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, _item.getUrl());
			
			try {
				startActivity(Intent.createChooser(intent, "Share Link"));
			} catch (android.content.ActivityNotFoundException ex) {
				
			}
			
			return true;
		case Constants.OPEN_IN_BROWSER_CONTEXT_ITEM:
			Common.launchBrowser(_item.getUrl(), this, false, true, true);
			return true;
			
		case Constants.OPEN_COMMENTS_CONTEXT_ITEM:
			Intent i = new Intent(getApplicationContext(), CommentsListActivity.class);
			i.setData(Util.createThreadUri(_item));
			i.putExtra(Constants.EXTRA_SUBREDDIT, _item.getSubreddit());
			i.putExtra(Constants.EXTRA_TITLE, _item.getTitle());
			i.putExtra(Constants.EXTRA_NUM_COMMENTS, _item.getNum_comments());
			startActivity(i);
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
        	if (mSettings.loggedIn) {
        		Common.doLogout(mSettings, mClient, getApplicationContext());
        		Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
        	} else {
        		showDialog(Constants.DIALOG_LOGIN);
        	}
    		break;
    	case R.id.refresh_menu_id:
    		CacheInfo.invalidateCachedSubreddit(getApplicationContext());
    		new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
    		break;
    	case R.id.submit_link_menu_id:
    		Intent submitLinkIntent = new Intent(getApplicationContext(), SubmitLinkActivity.class);
    		submitLinkIntent.setData(Util.createSubmitUri(mSubreddit));
    		startActivityForResult(submitLinkIntent, Constants.ACTIVITY_SUBMIT_LINK);
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
    		Common.launchBrowser(url, this, false, true, true);
    		break;
        case R.id.light_dark_menu_id:
    		if (mSettings.theme == R.style.Reddit_Light) {
    			mSettings.setTheme(R.style.Reddit_Dark);
    		} else {
    			mSettings.setTheme(R.style.Reddit_Light);
    		}
    		setTheme(mSettings.theme);
    		setContentView(R.layout.threads_list_content);
    		setListAdapter(mThreadsAdapter);
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
    		
    	case Constants.DIALOG_THING_CLICK:
    		inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(this);
    		dialog = builder.setView(inflater.inflate(R.layout.thread_click_dialog, null)).create();
    		break;
    		
    	case Constants.DIALOG_SORT_BY:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("Sort by:");
    		int selectedSortBy = 0;
    		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_URL_CHOICES.length; i++) {
    			if (Constants.ThreadsSort.SORT_BY_URL_CHOICES[i].equals(mSortByUrl)) {
    				selectedSortBy = i;
    				break;
    			}
    		}
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_CHOICES, selectedSortBy, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_SORT_BY);
    				String itemString = Constants.ThreadsSort.SORT_BY_CHOICES[item];
    				if (Constants.ThreadsSort.SORT_BY_HOT.equals(itemString)) {
    					mSortByUrl = Constants.ThreadsSort.SORT_BY_HOT_URL;
    					mSortByUrlExtra = Constants.EMPTY_STRING;
    					new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
        			} else if (Constants.ThreadsSort.SORT_BY_NEW.equals(itemString)) {
    					showDialog(Constants.DIALOG_SORT_BY_NEW);
    				} else if (Constants.ThreadsSort.SORT_BY_CONTROVERSIAL.equals(itemString)) {
    					showDialog(Constants.DIALOG_SORT_BY_CONTROVERSIAL);
    				} else if (Constants.ThreadsSort.SORT_BY_TOP.equals(itemString)) {
    					showDialog(Constants.DIALOG_SORT_BY_TOP);
    				}
    			}
    		});
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_NEW:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("what's new");
    		int selectedSortByNew = 0;
    		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES.length; i++) {
    			if (Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES[i].equals(mSortByUrlExtra)) {
    				selectedSortByNew = i;
    				break;
    			}
    		}
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_NEW_CHOICES, selectedSortByNew,
    				new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_SORT_BY_NEW);
    				mSortByUrl = Constants.ThreadsSort.SORT_BY_NEW_URL;
    				mSortByUrlExtra = Constants.ThreadsSort.SORT_BY_NEW_URL_CHOICES[item];
    				new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
    			}
    		});
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_CONTROVERSIAL:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("most controversial");
    		int selectedSortByControversial = 0;
    		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES.length; i++) {
    			if (Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES[i].equals(mSortByUrlExtra)) {
    				selectedSortByControversial = i;
    				break;
    			}
    		}
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_CHOICES, selectedSortByControversial,
    				new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_SORT_BY_CONTROVERSIAL);
    				mSortByUrl = Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL;
    				mSortByUrlExtra = Constants.ThreadsSort.SORT_BY_CONTROVERSIAL_URL_CHOICES[item];
    				new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
    			}
    		});
    		dialog = builder.create();
    		break;
    	case Constants.DIALOG_SORT_BY_TOP:
    		builder = new AlertDialog.Builder(this);
    		builder.setTitle("top scoring");
    		int selectedSortByTop = 0;
    		for (int i = 0; i < Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES.length; i++) {
    			if (Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES[i].equals(mSortByUrlExtra)) {
    				selectedSortByTop = i;
    				break;
    			}
    		}
    		builder.setSingleChoiceItems(Constants.ThreadsSort.SORT_BY_TOP_CHOICES, selectedSortByTop,
    				new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				dismissDialog(Constants.DIALOG_SORT_BY_TOP);
    				mSortByUrl = Constants.ThreadsSort.SORT_BY_TOP_URL;
    				mSortByUrlExtra = Constants.ThreadsSort.SORT_BY_TOP_URL_CHOICES[item];
    				new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra, mSubreddit).execute();
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
    		if (mVoteTargetThingInfo == null)
    			break;
    		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.vote_up_button);
    		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.vote_down_button);
    		final TextView titleView = (TextView) dialog.findViewById(R.id.title);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final TextView submissionStuffView = (TextView) dialog.findViewById(R.id.submissionTime_submitter_subreddit);
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
    		final Button commentsButton = (Button) dialog.findViewById(R.id.thread_comments_button);
    		
    		titleView.setText(mVoteTargetThingInfo.getTitle());
    		urlView.setText(mVoteTargetThingInfo.getUrl());
    		sb = new StringBuilder(Util.getTimeAgo(mVoteTargetThingInfo.getCreated_utc()))
    			.append(" by ").append(mVoteTargetThingInfo.getAuthor());
            // Show subreddit
    		sb.append(" to ").append(mVoteTargetThingInfo.getSubreddit());
    		submissionStuffView.setText(sb);
            
    		// Only show upvote/downvote if user is logged in
    		if (mSettings.loggedIn) {
    			loginButton.setVisibility(View.GONE);
    			voteUpButton.setVisibility(View.VISIBLE);
    			voteDownButton.setVisibility(View.VISIBLE);
    			// Set initial states of the vote buttons based on user's past actions
	    		if (mVoteTargetThingInfo.getLikes() == null) {
	    			// User is currently neutral
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(false);
	    		} else if (mVoteTargetThingInfo.getLikes() == true) {
	    			// User currenty likes it
	    			voteUpButton.setChecked(true);
	    			voteDownButton.setChecked(false);
	    		} else {
	    			// User currently dislikes it
	    			voteUpButton.setChecked(false);
	    			voteDownButton.setChecked(true);
	    		}
	    		voteUpButton.setOnCheckedChangeListener(voteUpOnCheckedChangeListener);
	    		voteDownButton.setOnCheckedChangeListener(voteDownOnCheckedChangeListener);
    		} else {
    			voteUpButton.setVisibility(View.GONE);
    			voteDownButton.setVisibility(View.GONE);
    			loginButton.setVisibility(View.VISIBLE);
    			loginButton.setOnClickListener(loginOnClickListener);
    		}

    		// "link" button behaves differently for regular links vs. self posts and links to comments pages (e.g., bestof)
            if (mVoteTargetThingInfo.isIs_self()) {
            	// It's a self post. Both buttons do the same thing.
            	linkButton.setEnabled(false);
            } else {
            	final String url = mVoteTargetThingInfo.getUrl();
            	linkButton.setOnClickListener(new OnClickListener() {
    				public void onClick(View v) {
    					dismissDialog(Constants.DIALOG_THING_CLICK);
    					// Launch Intent to goto the URL
    					Common.launchBrowser(url, ThreadsListActivity.this, false, false, mSettings.useExternalBrowser);
    				}
    			});
            	linkButton.setEnabled(true);
            }
            
            // "comments" button is easy: always does the same thing
            commentsButton.setOnClickListener(commentsOnClickListener);
    		
    		break;
    	
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
    private final OnClickListener downloadAfterOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
					mSubreddit, mAfter, null, mCount).execute();
		}
	};
	private final OnClickListener downloadBeforeOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			new MyDownloadThreadsTask(getApplicationContext(), mClient, om, mSortByUrl, mSortByUrlExtra,
					mSubreddit, null, mBefore, mCount).execute();
		}
	};
    
	private final OnClickListener loginOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			dismissDialog(Constants.DIALOG_THING_CLICK);
			showDialog(Constants.DIALOG_LOGIN);
		}
	};
	
	// Be sure to set mVoteTargetThingInfo before enabling this OnClickListener
	private final OnClickListener commentsOnClickListener = new OnClickListener() {
		public void onClick(View v) {
			dismissDialog(Constants.DIALOG_THING_CLICK);
			// Launch an Intent for CommentsListActivity
			CacheInfo.invalidateCachedThread(getApplicationContext());
			Intent i = new Intent(getApplicationContext(), CommentsListActivity.class);
			i.setData(Util.createThreadUri(mVoteTargetThingInfo));
			i.putExtra(Constants.EXTRA_SUBREDDIT, mVoteTargetThingInfo.getSubreddit());
			i.putExtra(Constants.EXTRA_TITLE, mVoteTargetThingInfo.getTitle());
			i.putExtra(Constants.EXTRA_NUM_COMMENTS, Integer.valueOf(mVoteTargetThingInfo.getNum_comments()));
			startActivity(i);
		}
	};
	
	private final CompoundButton.OnCheckedChangeListener voteUpOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
			if (isChecked) {
				new VoteTask(mVoteTargetThingInfo.getName(), 1, mVoteTargetThingInfo.getSubreddit()).execute();
			} else {
				new VoteTask(mVoteTargetThingInfo.getName(), 0, mVoteTargetThingInfo.getSubreddit()).execute();
			}
		}
    };
    private final CompoundButton.OnCheckedChangeListener voteDownOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
			if (isChecked) {
				new VoteTask(mVoteTargetThingInfo.getName(), -1, mVoteTargetThingInfo.getSubreddit()).execute();
			} else {
				new VoteTask(mVoteTargetThingInfo.getName(), 0, mVoteTargetThingInfo.getSubreddit()).execute();
			}
		}
    };
    
	
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
