package com.andrewshu.android.reddit;

import java.util.Arrays;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public final class PickSubredditActivity extends ListActivity {

    // Themes
    static final int THEME_LIGHT = 0;
    static final int THEME_DARK = 1;
    
//    private int mTheme = THEME_LIGHT;
    private int mThemeResId = android.R.style.Theme_Light;
    
    static final String PREFS_SESSION = "RedditSession";
    
	private PickSubredditAdapter mAdapter;
	private EditText mEt;
	
    public static final String[] SUBREDDITS = {
    	"reddit.com",
    	"pics",
    	"politics",
    	"wtf",
    	"funny",
    	"technology",
    	"askreddit",
    	"science",
    	"programming",
    	"gaming",
    	"worldnews",
    	"comics",
    	"offbeat",
    	"videos",
    	"environment",
    	"iama",
    	"business",
    	"entertainment",
    	"bestof",
    	"economics",
    	"marijuana",
    	"todayilearned",
    	"linux",
    	"android"
    };
    
    


    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        
    	loadRedditPreferences();
    	setTheme(mThemeResId);
    	
        setContentView(R.layout.pick_subreddit_view);
        
        // Set the EditText to do same thing as onListItemClick
        mEt = (EditText) findViewById(R.id.pick_subreddit_input);
		mEt.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
		        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
		        	returnSubreddit(mEt.getText().toString());
		        	return true;
		        }
		        return false;
		    }
		});
		mEt.requestFocus();
        
        // TODO: use the logged in user info to get his favorite reddits
        // to populate the list.
        // For now, use a predefined list.
        
        List<String> items = Arrays.asList(SUBREDDITS);
        mAdapter = new PickSubredditAdapter(this, items);
        getListView().setAdapter(mAdapter);

//        // Need one of these to post things back to the UI thread.
//        mHandler = new Handler();
        
    }
    
    private void loadRedditPreferences() {
        // Retrieve the stored session info
        SharedPreferences sessionPrefs = getSharedPreferences(PREFS_SESSION, 0);
//        mUsername = sessionPrefs.getString("username", null);
//        String cookieValue = sessionPrefs.getString("reddit_sessionValue", null);
//        String cookieDomain = sessionPrefs.getString("reddit_sessionDomain", null);
//        String cookiePath = sessionPrefs.getString("reddit_sessionPath", null);
//        long cookieExpiryDate = sessionPrefs.getLong("reddit_sessionExpiryDate", -1);
//        if (cookieValue != null) {
//        	BasicClientCookie redditSessionCookie = new BasicClientCookie("reddit_session", cookieValue);
//        	redditSessionCookie.setDomain(cookieDomain);
//        	redditSessionCookie.setPath(cookiePath);
//        	if (cookieExpiryDate != -1)
//        		redditSessionCookie.setExpiryDate(new Date(cookieExpiryDate));
//        	else
//        		redditSessionCookie.setExpiryDate(null);
//        	mRedditSessionCookie = redditSessionCookie;
//        	setClient(new DefaultHttpClient());
//        	mClient.getCookieStore().addCookie(mRedditSessionCookie);
//        	mLoggedIn = true;
//        }
//        mTheme = sessionPrefs.getInt("theme", THEME_LIGHT);
        mThemeResId = sessionPrefs.getInt("theme_resid", android.R.style.Theme_Light);
    }

    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String item = mAdapter.getItem(position);
        returnSubreddit(item);
    }
    
    private void returnSubreddit(String subreddit) {
       	Bundle bundle = new Bundle();
       	bundle.putString(ThreadInfo.SUBREDDIT, subreddit);
       	Intent mIntent = new Intent();
       	mIntent.putExtras(bundle);
       	setResult(RESULT_OK, mIntent);
       	finish();	
    }

    private final class PickSubredditAdapter extends ArrayAdapter<String> {
    	private LayoutInflater mInflater;
        private boolean mLoading = true;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;

        
        public PickSubredditAdapter(Context context, List<String> objects) {
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
//            Resources res = getResources();

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(android.R.layout.simple_list_item_1, null);
            } else {
                view = convertView;
            }
            
//            if (mTheme == THEME_LIGHT) {
//	            if (TRUE_STRING.equals(item.getClicked()))
//	            	titleView.setTextColor(res.getColor(R.color.purple));
//	            else
//	            	titleView.setTextColor(res.getColor(R.color.blue));
//            }

//            if (mLoggedIn) {
//            } else {
//            }
            
            TextView text = (TextView) view.findViewById(android.R.id.text1);
            text.setText(mAdapter.getItem(position));
            
            return view;
        }
    }
	
}
