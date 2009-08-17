package com.andrewshu.android.reddit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
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
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
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
	
	private final JsonFactory jsonFactory = new JsonFactory(); 
	
    /** Custom list adapter that fits our threads data into the list. */
    private ThreadsListAdapter mThreadsAdapter;
    /** Currently running background network thread. */
    private Thread mWorker;
    
    private RedditSettings mSettings = new RedditSettings(this);
    
    // UI State
    private View mVoteTargetView = null;
    private ThreadInfo mVoteTargetThreadInfo = null;
    static boolean mIsProgressDialogShowing = false;
    
    /**
     * Called when the activity starts up. Do activity initialization
     * here, not in a constructor.
     * 
     * @see Activity#onCreate
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Common.loadRedditPreferences(this, mSettings);
        setTheme(mSettings.themeResId);
        
        setContentView(R.layout.threads_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().

        // Start at /r/reddit.com
        mSettings.setSubreddit("reddit.com");
        List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        mThreadsAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mThreadsAdapter);

        doGetThreadsList(mSettings.subreddit);
        
        // NOTE: this could use the icicle as done in
        // onRestoreInstanceState().
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	int previousTheme = mSettings.theme;
    	boolean previousLoggedIn = mSettings.loggedIn;
    	Common.loadRedditPreferences(this, mSettings);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.themeResId);
    		setContentView(R.layout.threads_list_content);
    		getListView().setAdapter(mThreadsAdapter);
    	}
    	if (mSettings.loggedIn != previousLoggedIn) {
    		doGetThreadsList(mSettings.subreddit);
    	}
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
    	if (isFinishing())
    		mSettings.setIsAlive(false);
    }
    

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	Bundle extras = intent.getExtras();
    	
    	switch(requestCode) {
    	case Constants.ACTIVITY_PICK_SUBREDDIT:
    		mSettings.setSubreddit(extras.getString(ThreadInfo.SUBREDDIT));
    		doGetThreadsList(mSettings.subreddit);
    		break;
    	}
    }
    
    
    public class VoteUpOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
			if (isChecked)
				doVote(mVoteTargetThreadInfo.getName(), 1, mSettings.subreddit);
			else
				doVote(mVoteTargetThreadInfo.getName(), 0, mSettings.subreddit);
		}
    }
    
    public class VoteDownOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
			if (isChecked)
				doVote(mVoteTargetThreadInfo.getName(), -1, mSettings.subreddit);
			else
				doVote(mVoteTargetThreadInfo.getName(), 0, mSettings.subreddit);
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
            if (mSettings.theme == Constants.THEME_LIGHT) {
	            if (Constants.TRUE_STRING.equals(item.getClicked()))
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
            if (mSettings.loggedIn) {
            	if (Constants.TRUE_STRING.equals(item.getLikes())) {
            		voteUpView.setImageResource(R.drawable.vote_up_red);
            		voteDownView.setImageResource(R.drawable.vote_down_gray);
            		votesView.setTextColor(res.getColor(R.color.arrow_red));
            	} else if (Constants.FALSE_STRING.equals(item.getLikes())) {
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
        mVoteTargetThreadInfo = item;
        mVoteTargetView = v;
        
        showDialog(Constants.DIALOG_THING_CLICK);
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
    	showDialog(Constants.DIALOG_LOADING_THREADS_LIST);
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
            	HttpGet request = new HttpGet(new StringBuilder("http://www.reddit.com/r/")
            		.append(_mSubreddit.toString().trim())
            		.append("/.json").toString());
            	HttpResponse response = mSettings.client.execute(request);
            	
            	// OK to dismiss the progress dialog when threads start loading
            	dismissDialog(Constants.DIALOG_LOADING_THREADS_LIST);
            	mIsProgressDialogShowing = false;

            	InputStream in = response.getEntity().getContent();
                
                parseSubredditJSON(in, mThreadsAdapter);
                
                mSettings.setSubreddit(_mSubreddit);
            } catch (IOException e) {
            	dismissDialog(Constants.DIALOG_LOADING_THREADS_LIST);
            	mIsProgressDialogShowing = false;
            	Log.e(TAG, "failed:" + e.getMessage());
            }
        }
    }
    
    
    
    public boolean doVote(CharSequence thingFullname, int direction, CharSequence subreddit) {
    	if (!mSettings.loggedIn) {
    		mSettings.handler.post(new ErrorToaster("You must be logged in to vote.", Toast.LENGTH_LONG, mSettings));
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
    	if (Constants.TRUE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
    		if (direction == 0) {
    			newScore = String.valueOf(previousScore - 1);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = Constants.NULL_STRING;
    		} else if (direction == -1) {
    			newScore = String.valueOf(previousScore - 2);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_blue;
    			newLikes = Constants.FALSE_STRING;
    		} else {
    			return false;
    		}
    	} else if (Constants.FALSE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
    		if (direction == 1) {
    			newScore = String.valueOf(previousScore + 2);
    			newImageResourceUp = R.drawable.vote_up_red;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = Constants.TRUE_STRING;
    		} else if (direction == 0) {
    			newScore = String.valueOf(previousScore + 1);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = Constants.NULL_STRING;
    		} else {
    			return false;
    		}
    	} else {
    		if (direction == 1) {
    			newScore = String.valueOf(previousScore + 1);
    			newImageResourceUp = R.drawable.vote_up_red;
    			newImageResourceDown = R.drawable.vote_down_gray;
    			newLikes = Constants.TRUE_STRING;
    		} else if (direction == -1) {
    			newScore = String.valueOf(previousScore - 1);
    			newImageResourceUp = R.drawable.vote_up_gray;
    			newImageResourceDown = R.drawable.vote_down_blue;
    			newLikes = Constants.FALSE_STRING;
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
    	
    	VoteWorker worker = new VoteWorker(thingFullname, direction, subreddit, mSettings);
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
        
        menu.add(0, Constants.DIALOG_PICK_SUBREDDIT, 0, "Pick subreddit")
            .setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_PICK_SUBREDDIT));

        // Login and Logout need to use the same ID for menu entry so they can be swapped
        if (mSettings.loggedIn) {
        	menu.add(0, Constants.DIALOG_LOGIN, 1, "Logout: " + mSettings.username)
       			.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_LOGOUT));
        } else {
        	menu.add(0, Constants.DIALOG_LOGIN, 1, "Login")
       			.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_LOGIN));
        }
        
        menu.add(0, Constants.DIALOG_REFRESH, 2, "Refresh")
        	.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_REFRESH));
        
        menu.add(0, Constants.DIALOG_POST_THREAD, 3, "Post Thread")
        	.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_POST_THREAD));
        
        if (mSettings.theme == Constants.THEME_LIGHT) {
        	menu.add(0, Constants.DIALOG_THEME, 4, "Dark")
//        		.setIcon(R.drawable.dark_circle_menu_icon)
        		.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_THEME));
        } else {
        	menu.add(0, Constants.DIALOG_THEME, 4, "Light")
//	    		.setIcon(R.drawable.light_circle_menu_icon)
	    		.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_THEME));
        }
        
        menu.add(0, Constants.DIALOG_OPEN_BROWSER, 5, "Open in browser")
        	.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_OPEN_BROWSER));
        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	super.onPrepareOptionsMenu(menu);
    	
    	// Login/Logout
    	if (mSettings.loggedIn) {
	        menu.findItem(Constants.DIALOG_LOGIN).setTitle("Logout: " + mSettings.username)
	        	.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_LOGOUT));
    	} else {
            menu.findItem(Constants.DIALOG_LOGIN).setTitle("Login")
            	.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_LOGIN));
    	}
    	
    	// Theme: Light/Dark
    	if (mSettings.theme == Constants.THEME_LIGHT) {
    		menu.findItem(Constants.DIALOG_THEME).setTitle("Dark");
//    			.setIcon(R.drawable.dark_circle_menu_icon);
    	} else {
    		menu.findItem(Constants.DIALOG_THEME).setTitle("Light");
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
        	case Constants.DIALOG_LOGIN:
        	case Constants.DIALOG_POST_THREAD:
        		showDialog(mAction);
        		break;
        	case Constants.DIALOG_PICK_SUBREDDIT:
        		Intent pickSubredditIntent = new Intent(RedditIsFun.this, PickSubredditActivity.class);
        		startActivityForResult(pickSubredditIntent, Constants.ACTIVITY_PICK_SUBREDDIT);
        		break;
        	case Constants.DIALOG_OPEN_BROWSER:
        		String url = new StringBuilder("http://www.reddit.com/r/")
        			.append(mSettings.subreddit).toString();
        		RedditIsFun.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        		break;
            case Constants.DIALOG_LOGOUT:
        		Common.doLogout(mSettings);
        		Toast.makeText(RedditIsFun.this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		doGetThreadsList(mSettings.subreddit);
        		break;
        	case Constants.DIALOG_REFRESH:
        		doGetThreadsList(mSettings.subreddit);
        		break;
        	case Constants.DIALOG_THEME:
        		if (mSettings.theme == Constants.THEME_LIGHT) {
        			mSettings.setTheme(Constants.THEME_DARK);
        			mSettings.setThemeResId(android.R.style.Theme);
        		} else {
        			mSettings.setTheme(Constants.THEME_LIGHT);
        			mSettings.setThemeResId(android.R.style.Theme_Light);
        		}
        		RedditIsFun.this.setTheme(mSettings.themeResId);
        		RedditIsFun.this.setContentView(R.layout.threads_list_content);
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
    		        	dismissDialog(Constants.DIALOG_LOGIN);
    		        	showDialog(Constants.DIALOG_LOGGING_IN);
    		        	Common.doLogin(loginUsernameInput.getText(), loginPasswordInput.getText(), mSettings);
    		            dismissDialog(Constants.DIALOG_LOGGING_IN);
    		        	// Refresh the threads list
    		        	doGetThreadsList(mSettings.subreddit);
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		loginButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				dismissDialog(Constants.DIALOG_LOGIN);
    	        	showDialog(Constants.DIALOG_LOGGING_IN);
    				Common.doLogin(loginUsernameInput.getText(), loginPasswordInput.getText(), mSettings);
    				dismissDialog(Constants.DIALOG_LOGGING_IN);
    		        // Refresh the threads list
    	        	doGetThreadsList(mSettings.subreddit);
    		    }
    		});
    		break;
    		
    	case Constants.DIALOG_THING_CLICK:
    		dialog = new Dialog(this);
    		dialog.setContentView(R.layout.thread_click_dialog);
    		dialog.findViewById(R.id.thread_vote_up_button);
    		dialog.findViewById(R.id.thread_vote_down_button);
    		break;

    	case Constants.DIALOG_POST_THREAD:
    		// TODO: a scrollable Dialog with Title, URL/Selftext, and subreddit.
    		// Or one of those things that pops up at bottom of screen, like browser "Find on page"
    		alertBuilder = new AlertDialog.Builder(this);
    		alertBuilder.setMessage("Sorry, this feature isn't implemented yet. Open in browser instead.")
    				.setCancelable(true)
    				.setPositiveButton("OK", null);
    		dialog = alertBuilder.create();
    		break;
    		
   		// "Please wait"
    	case Constants.DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Logging in...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case Constants.DIALOG_LOADING_THREADS_LIST:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Loading subreddit...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case Constants.DIALOG_LOADING_LOOK_OF_DISAPPROVAL:
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
    	case Constants.DIALOG_LOGIN:
    		if (mSettings.username != null) {
	    		final TextView loginUsernameInput = (TextView) dialog.findViewById(R.id.login_username_input);
	    		loginUsernameInput.setText(mSettings.username);
    		}
    		final TextView loginPasswordInput = (TextView) dialog.findViewById(R.id.login_password_input);
    		loginPasswordInput.setText("");
    		break;
    		
    	case Constants.DIALOG_THING_CLICK:
    		dialog.setTitle("Submitted by " + mVoteTargetThreadInfo.getAuthor());
    		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.thread_vote_up_button);
    		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.thread_vote_down_button);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
    		final Button commentsButton = (Button) dialog.findViewById(R.id.thread_comments_button);
    		
    		urlView.setText(mVoteTargetThreadInfo.getURL());

    		// Only show upvote/downvote if user is logged in
    		if (mSettings.loggedIn) {
    			voteUpButton.setVisibility(View.VISIBLE);
    			voteDownButton.setVisibility(View.VISIBLE);
    			// Set initial states of the vote buttons based on user's past actions
	    		if (Constants.TRUE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
	    			// User currenty likes it
	    			voteUpButton.setChecked(true);
	    			voteDownButton.setChecked(false);
	    		} else if (Constants.FALSE_STRING.equals(mVoteTargetThreadInfo.getLikes())) {
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
    				dismissDialog(Constants.DIALOG_THING_CLICK);
    				// Launch an Intent for RedditCommentsListActivity
    				Intent i = new Intent(RedditIsFun.this, RedditCommentsListActivity.class);
    				i.putExtra(ThreadInfo.SUBREDDIT, mSettings.subreddit);
    				i.putExtra(ThreadInfo.ID, mVoteTargetThreadInfo.getId());
    				i.putExtra(ThreadInfo.TITLE, mVoteTargetThreadInfo.getTitle());
    				startActivity(i);
        		}
    		};
    		commentsButton.setOnClickListener(commentsOnClickListener);
    		// TODO: Handle bestof posts, which aren't self posts
            if (("self."+mSettings.subreddit).toLowerCase().equals(mVoteTargetThreadInfo.getDomain().toLowerCase())) {
            	// It's a self post. Both buttons do the same thing.
            	linkButton.setOnClickListener(commentsOnClickListener);
            } else {
            	linkButton.setOnClickListener(new OnClickListener() {
            		public void onClick(View v) {
            			dismissDialog(Constants.DIALOG_THING_CLICK);
            			// Launch Intent to goto the URL
            			RedditIsFun.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mVoteTargetThreadInfo.getURL())));
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
            strings.add(Constants.SERIALIZE_SEPARATOR);
        }
        outState.putSerializable(Constants.STRINGS_KEY, strings);

        // Save current selection index (if focussed)
        if (getListView().hasFocus()) {
            outState.putInt(Constants.SELECTION_KEY, Integer.valueOf(getListView().getSelectedItemPosition()));
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
        List<CharSequence> strings = (ArrayList<CharSequence>)state.getSerializable(Constants.STRINGS_KEY);
        List<ThreadInfo> items = new ArrayList<ThreadInfo>();
        for (int i = 0; i < strings.size(); i++) {
        	ThreadInfo ti = new ThreadInfo();
        	CharSequence key, value;
        	while (!Constants.SERIALIZE_SEPARATOR.equals(strings.get(i))) {
        		if (Constants.SERIALIZE_SEPARATOR.equals(strings.get(i+1))) {
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
        if (state.containsKey(Constants.SELECTION_KEY)) {
            getListView().requestFocus(View.FOCUS_FORWARD);
            // todo: is above right? needed it to work
            getListView().setSelection(state.getInt(Constants.SELECTION_KEY));
        }
        
        if (mIsProgressDialogShowing) {
        	dismissDialog(Constants.DIALOG_LOADING_THREADS_LIST);
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
    	
		// --- Main parsing ---
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
								ti.put("media/"+mediaNamefield, jp.getText());
							}
						} else if (Constants.JSON_MEDIA_EMBED.equals(namefield) && jp.getCurrentToken() == JsonToken.START_OBJECT) {
							while (jp.nextToken() != JsonToken.END_OBJECT) {
								String mediaNamefield = jp.getCurrentName();
								jp.nextToken(); // move to value
								ti.put("media_embed/"+mediaNamefield, jp.getText());
							}
						} else {
							ti.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText()));
						}
					}
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
			mSettings.handler.post(new ThreadItemAdder(ti));
		}
	}
}
