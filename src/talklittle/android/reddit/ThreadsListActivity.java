package talklittle.android.reddit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Class representing a Subreddit, i.e., a Thread List.
 * 
 * @author TalkLittle
 *
 */
public final class ThreadsListActivity extends ListActivity {
	
	public final String THREAD_KIND = "t3";
	public final String SERIALIZE_SEPARATOR = "\r";
	
	private final JsonFactory jsonFactory = new JsonFactory(); 
	
    /**
     * Custom list adapter that fits our rss data into the list.
     */
    private ThreadsListAdapter mAdapter;
    
    /**
     * Url edit text field.
     */
    private EditText mUrlText;

    /**
     * Status text field.
     */
    private TextView mStatusText;

    /**
     * Handler used to post runnables to the UI thread.
     */
    private Handler mHandler;

    /**
     * Currently running background network thread.
     */
    private ThreadsWorker mWorker;

    // Take this many chars from the front of the description.
    public static final int SNIPPET_LENGTH = 90;
    
    
    // Keys used for data in the onSaveInstanceState() Map.
    public static final String STRINGS_KEY = "strings";

    public static final String SELECTION_KEY = "selection";

    public static final String URL_KEY = "url";
    
    public static final String STATUS_KEY = "status";

    /**
     * Called when the activity starts up. Do activity initialization
     * here, not in a constructor.
     * 
     * @see Activity#onCreate
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.threads_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().

        // Install our custom RSSListAdapter.
        List<ThreadItem> items = new ArrayList<ThreadItem>();
        mAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mAdapter);

        // Get pointers to the UI elements in the threads_list_content layout
        mUrlText = (EditText)findViewById(R.id.urltext);
        mStatusText = (TextView)findViewById(R.id.statustext);
        
        Button download = (Button)findViewById(R.id.download);
        download.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doGetThreadsList(mUrlText.getText());
            }
        });

        // Need one of these to post things back to the UI thread.
        mHandler = new Handler();
        
        // NOTE: this could use the icicle as done in
        // onRestoreInstanceState().
    }

    /**
     * ArrayAdapter encapsulates a java.util.List of T, for presentation in a
     * ListView. This subclass specializes it to hold ThreadItems and display
     * their title/description data in a TwoLineListItem.
     */
    private class ThreadsListAdapter extends ArrayAdapter<ThreadItem> {
        private LayoutInflater mInflater;

        public ThreadsListAdapter(Context context, List<ThreadItem> objects) {
            super(context, 0, objects);

            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        /**
         * This is called to render a particular item for the on screen list.
         * Uses an off-the-shelf TwoLineListItem view, which contains text1 and
         * text2 TextViews. We pull data from the ThreadItem and set it into the
         * view. The convertView is the view from a previous getView(), so
         * we can re-use it.
         * 
         * @see ArrayAdapter#getView
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ThreadsListItem view;

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = (ThreadsListItem) mInflater.inflate(R.layout.threads_list_item,
                        null);
            } else {
                view = (ThreadsListItem) convertView;
            }

            ThreadItem item = this.getItem(position);

            // Set the item title and description into the view.
            // This example does not render real HTML, so as a hack to make
            // the description look better, we strip out the
            // tags and take just the first SNIPPET_LENGTH chars.
            view.getTitle().setText(item.getTitle());
//            view.getText2().setText(descr.substring(0, Math.min(descr.length(), SNIPPET_LENGTH)));
            view.getNumComments().setText(item.getNumComments());
            view.getLinkDomain().setText(item.getLinkDomain());
            view.getSubmissionTime().setText(item.getSubmissionTime());
            view.getSubmitter().setText(item.getSubmitter());
            return view;
        }

    }


    /**
     * Called when user clicks an item in the list. Starts an activity to
     * open the url for that item.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ThreadItem item = mAdapter.getItem(position);
        
        if ("self.reddit.com".equals(item.getLinkDomain())) {
            // TODO: new Intent aiming using CommentsListActivity specifically.
        } else {
            // TODO: popup dialog: 2 buttons: LINK, COMMENTS. well, 2 big and 2 small buttons (small: user profile, report post)
        	if ("link".equals("true")) {
	            // Creates and starts an intent to open the item.link url.
	            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getLink().toString()));
	            startActivity(intent);
        	} else {
                // TODO: new Intent aiming using CommentsListActivity specifically.
        	}
        }
    }

    /**
     * Resets the output UI -- list and status text empty.
     */
    public void resetUI() {
        // Reset the list to be empty.
        List<ThreadItem> items = new ArrayList<ThreadItem>();
        mAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mAdapter);

        mStatusText.setText("");
        mUrlText.requestFocus();
    }

    /**
     * Sets the currently active running worker. Interrupts any earlier worker,
     * so we only have one at a time.
     * 
     * @param worker the new worker
     */
    public synchronized void setCurrentWorker(ThreadsWorker worker) {
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
    	ThreadsWorker worker = new ThreadsWorker(subreddit);
    	setCurrentWorker(worker);
    	
    	resetUI();
    	// TODO: nice status screen, like Alien vs. Android had
    	mStatusText.setText("Downloading\u2026");
    	
    	worker.start();
    }

    /**
     * Runnable that the worker thread uses to post ThreadItems to the
     * UI via mHandler.post
     */
    private class ItemAdder implements Runnable {
        ThreadItem mItem;

        ItemAdder(ThreadItem item) {
            mItem = item;
        }

        public void run() {
            mAdapter.add(mItem);
        }

        // NOTE: Performance idea -- would be more efficient to have he option
        // to add multiple items at once, so you get less "update storm" in the UI
        // compared to adding things one at a time.
    }

    /**
     * Worker thread takes in an rss url string, downloads its data, parses
     * out the rss items, and communicates them back to the UI as they are read.
     */
    private class ThreadsWorker extends Thread {
        private CharSequence mSubreddit;

        public ThreadsWorker(CharSequence subreddit) {
            mSubreddit = subreddit;
        }

        @Override
        public void run() {
            String status = "";
            try {
                // Standard code to make an HTTP connection.
                URL url = new URL("http://www.reddit.com/r/" + mSubreddit.toString());
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(10000);

                connection.connect();
                InputStream in = connection.getInputStream();
                
                parseSubredditJSON(in, mAdapter);
                status = "done";
            } catch (Exception e) {
                status = "failed:" + e.getMessage();
            }

            // Send status to UI (unless a newer worker has started)
            // To communicate back to the UI from a worker thread,
            // pass a Runnable to handler.post().
            final String temp = status;
            if (isCurrentWorker(this)) {
                mHandler.post(new Runnable() {
                    public void run() {
                        mStatusText.setText(temp);
                    }
                });
            }
        }
    }

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // FIXME: login/logout, goto subreddit, 
        menu.add(0, 0, 0, "Slashdot")
            .setOnMenuItemClickListener(new RSSMenu("http://rss.slashdot.org/Slashdot/slashdot"));

        menu.add(0, 0, 0, "Google News")
            .setOnMenuItemClickListener(new RSSMenu("http://news.google.com/?output=rss"));
        
        menu.add(0, 0, 0, "News.com")
            .setOnMenuItemClickListener(new RSSMenu("http://news.com.com/2547-1_3-0-20.xml"));

        menu.add(0, 0, 0, "Bad Url")
            .setOnMenuItemClickListener(new RSSMenu("http://nifty.stanford.edu:8080"));

        menu.add(0, 0, 0, "Reset")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                resetUI();
                return true;
            }
        });

        return true;
    }

    /**
     * Puts text in the url text field and gives it focus. Used to make a Runnable
     * for each menu item. This way, one inner class works for all items vs. an
     * anonymous inner class for each menu item.
     */
    private class RSSMenu implements MenuItem.OnMenuItemClickListener {
        private CharSequence mUrl;

        RSSMenu(CharSequence url) {
            mUrl = url;
        }

        public boolean onMenuItemClick(MenuItem item) {
            mUrlText.setText(mUrl);
            mUrlText.requestFocus();
            return true;
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
     * TODO: the Activity javadoc should give more detail about what sort of
     * data can go in the outState map.
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
        int count = mAdapter.getCount();

        // Save out the items as a flat list of CharSequence objects --
        // title0, link0, descr0, title1, link1, ...
        ArrayList<CharSequence> strings = new ArrayList<CharSequence>();
        for (int i = 0; i < count; i++) {
            ThreadItem item = mAdapter.getItem(i);
            for (int j = 0; j < ThreadItem.KEYS.length; j++) {
            	if (item.mValues.containsKey(ThreadItem.KEYS[i])) {
            		strings.add(ThreadItem.KEYS[i]);
            		strings.add(item.mValues.get(ThreadItem.KEYS[i]));
            	}
            }
            strings.add(SERIALIZE_SEPARATOR);
        }
        outState.putSerializable(STRINGS_KEY, strings);

        // Save current selection index (if focussed)
        if (getListView().hasFocus()) {
            outState.putInt(SELECTION_KEY, Integer.valueOf(getListView().getSelectedItemPosition()));
        }

        // Save url
        outState.putString(URL_KEY, mUrlText.getText().toString());
        
        // Save status
        outState.putCharSequence(STATUS_KEY, mStatusText.getText());
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
        List<ThreadItem> items = new ArrayList<ThreadItem>();
        for (int i = 0; i < strings.size(); i++) {
        	ThreadItem ti = new ThreadItem();
        	CharSequence key, value;
        	while (!SERIALIZE_SEPARATOR.equals(strings.get(i))) {
        		if (SERIALIZE_SEPARATOR.equals(strings.get(i+1))) {
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
        mAdapter = new ThreadsListAdapter(this, items);
        getListView().setAdapter(mAdapter);

        // Restore selection
        if (state.containsKey(SELECTION_KEY)) {
            getListView().requestFocus(View.FOCUS_FORWARD);
            // todo: is above right? needed it to work
            getListView().setSelection(state.getInt(SELECTION_KEY));
        }
        
        // Restore url
        mUrlText.setText(state.getCharSequence(URL_KEY));
        
        // Restore status
        mStatusText.setText(state.getCharSequence(STATUS_KEY));
    }

    /**
     * Skips ahead the JsonParser while validating.
     * When the function returns normally, JsonParser.getCurrentToken() should return the
     * JsonToken.START_ARRAY value corresponding to the beginning of the list of threads.
     * @param jp
     * @throws JsonParseException
     * @throws IllegalStateException
     */
    void skipToThreadsJSON(JsonParser jp) throws IOException, JsonParseException, IllegalStateException {
    	String genericListingError = "Not a subreddit listing";
    	jp.nextToken(); // will return JsonToken.START_OBJECT (verify?)
    	if (!"kind".equals(jp.getCurrentName()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (!"Listing".equals(jp.getText()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (!"data".equals(jp.getCurrentName()))
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	if (jp.getCurrentToken() != JsonToken.START_OBJECT)
    		throw new IllegalStateException(genericListingError);
    	jp.nextToken();
    	while (!"children".equals(jp.getCurrentName())) {
    		// Don't care
    		jp.nextToken();
    	}
    	jp.nextToken();
    	if (jp.getCurrentToken() != JsonToken.START_ARRAY)
    		throw new IllegalStateException(genericListingError);
    }
    
    void parseSubredditJSON(InputStream in, ThreadsListAdapter adapter) throws IOException,
		    JsonParseException, IllegalStateException {
		
		JsonParser jp = jsonFactory.createJsonParser(in);
		
		// Validate initial stuff, skip to the JSON List of threads
		skipToThreadsJSON(jp);

		while (jp.nextToken() != JsonToken.END_ARRAY) {
			if (jp.getCurrentToken() != JsonToken.START_OBJECT)
				throw new IllegalStateException("Unexpected non-JSON-object in the children array");
			
			// Process JSON representing one thread
			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jp.getCurrentName();
				jp.nextToken(); // move to value, or START_OBJECT/START_ARRAY
			
				if ("data".equals(fieldname)) { // contains an object
					ThreadItem ti = new ThreadItem();
					while (jp.nextToken() != JsonToken.END_OBJECT) {
						String namefield = jp.getCurrentName();
						jp.nextToken(); // move to value
						// TODO: validate each field but I'm lazy
						ti.put(namefield, jp.getText());
					}
					mHandler.post(new ItemAdder(ti));
				} else if ("kind".equals(fieldname)) {
					jp.nextToken();
					if (!THREAD_KIND.equals(jp.getText()))
						throw new IllegalStateException("Unexpected kind:"+jp.getText()+" expecting:"+THREAD_KIND);
				} else {
					throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
				}
			}
		}
	}
    

}
