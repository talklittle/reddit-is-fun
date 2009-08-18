package com.andrewshu.android.reddit;

import java.util.Arrays;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public final class PickSubredditActivity extends ListActivity {

	private RedditSettings mSettings = new RedditSettings();
	
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
        
    	Common.loadRedditPreferences(this, mSettings, null);
    	setThemeDrawables();
    	
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
        final Button goButton = (Button) findViewById(R.id.pick_subreddit_button);
        goButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		returnSubreddit(mEt.getText().toString());
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
    
    /**
     * Set the Activity's theme to the theme in mSettings.
     * Then set other Drawables like the list selector.
     */
    private void setThemeDrawables() {
    	setTheme(mSettings.themeResId);
    	if (mSettings.theme == Constants.THEME_LIGHT) {
    		getListView().setSelector(R.drawable.list_selector_solid_pale_blue);
    		// TODO: Set the empty listview image
    	} else if (mSettings.theme == Constants.THEME_DARK) {
    		getListView().setSelector(android.R.drawable.list_selector_background);
    	}
    }
    
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String item = mAdapter.getItem(position);
        returnSubreddit(item);
    }
    
    private void returnSubreddit(String subreddit) {
       	Bundle bundle = new Bundle();
       	bundle.putString(ThreadInfo.SUBREDDIT, subreddit.trim());
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

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(android.R.layout.simple_list_item_1, null);
            } else {
                view = convertView;
            }
                        
            TextView text = (TextView) view.findViewById(android.R.id.text1);
            text.setText(mAdapter.getItem(position));
            
            return view;
        }
    }
	
}
