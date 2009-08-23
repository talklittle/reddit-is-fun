package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Browser;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
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
public final class RedditIsFun extends ListActivity {

	private static final String TAG = "RedditIsFun";
	
	private final JsonFactory jsonFactory = new JsonFactory(); 
	
    /** Custom list adapter that fits our threads data into the list. */
    private ThreadsListAdapter mThreadsAdapter;

    private final DefaultHttpClient mClient = Common.createGzipHttpClient();
	String mModhash = null;
	
   
    private final RedditSettings mSettings = new RedditSettings();
    
    // UI State
    private View mVoteTargetView = null;
    private ThreadInfo mVoteTargetThreadInfo = null;
    
    // ProgressDialogs with percentage bars
    private AutoResetProgressDialog mLoadingThreadsProgress;
    
    
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
        setTheme(mSettings.themeResId);
        
        setContentView(R.layout.threads_list_content);
        // The above layout contains a list id "android:list"
        // which ListActivity adopts as its list -- we can
        // access it with getListView().

        // Start at www.reddit.com (front page, not a subreddit)
        mSettings.setSubreddit(Constants.FRONTPAGE_STRING);
        
        new DownloadThreadsTask().execute(mSettings.subreddit);
        
        // NOTE: this could use the icicle as done in
        // onRestoreInstanceState().
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	int previousTheme = mSettings.theme;
    	boolean previousLoggedIn = mSettings.loggedIn;
    	Common.loadRedditPreferences(this, mSettings, mClient);
    	if (mSettings.theme != previousTheme) {
    		setTheme(mSettings.themeResId);
    		setContentView(R.layout.threads_list_content);
    		setListAdapter(mThreadsAdapter);
    		Common.updateListDrawables(this, mSettings.theme);
    	}
    	if (mSettings.loggedIn != previousLoggedIn) {
    		new DownloadThreadsTask().execute(mSettings.subreddit);
    	}
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	Common.saveRedditPreferences(this, mSettings);
    }
    

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	
    	switch(requestCode) {
    	case Constants.ACTIVITY_PICK_SUBREDDIT:
    		if (resultCode == Activity.RESULT_OK) {
    			Bundle extras = intent.getExtras();
	    		String newSubreddit = extras.getString(ThreadInfo.SUBREDDIT);
	    		if (newSubreddit != null && !"".equals(newSubreddit)) {
	    			mSettings.setSubreddit(newSubreddit);
	    			new DownloadThreadsTask().execute(newSubreddit);
	    		}
    		}
    		break;
    	case Constants.ACTIVITY_SUBMIT_LINK:
    		if (resultCode == Activity.RESULT_OK) {
    			Bundle extras = intent.getExtras();
	    		String newSubreddit = extras.getString(ThreadInfo.SUBREDDIT);
	    		String newId = extras.getString(ThreadInfo.ID);
	    		String newTitle = extras.getString(ThreadInfo.TITLE);
	    		mSettings.setSubreddit(newSubreddit);
	    		// Start up comments list with the new thread
	    		Intent i = new Intent(RedditIsFun.this, RedditCommentsListActivity.class);
				i.putExtra(ThreadInfo.SUBREDDIT, newSubreddit);
				i.putExtra(ThreadInfo.ID, newId);
				i.putExtra(ThreadInfo.TITLE, newTitle);
				i.putExtra(ThreadInfo.NUM_COMMENTS, 0);
				startActivity(i);
    		} else if (resultCode == Constants.RESULT_LOGIN_REQUIRED) {
    			Common.showErrorToast("You must be logged in to make a submission.", Toast.LENGTH_LONG, this);
    		}
    		break;
    	default:
    		break;
    	}
    }
    
    
    private class VoteUpOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
			if (isChecked) {
				new VoteTask(mVoteTargetThreadInfo.getName(), 1, mVoteTargetThreadInfo.getSubreddit()).execute();
			} else {
				new VoteTask(mVoteTargetThreadInfo.getName(), 0, mVoteTargetThreadInfo.getSubreddit()).execute();
			}
		}
    }
    
    private class VoteDownOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    	dismissDialog(Constants.DIALOG_THING_CLICK);
			if (isChecked) {
				new VoteTask(mVoteTargetThreadInfo.getName(), -1, mVoteTargetThreadInfo.getSubreddit()).execute();
			} else {
				new VoteTask(mVoteTargetThreadInfo.getName(), 0, mVoteTargetThreadInfo.getSubreddit()).execute();
			}
		}
    }
    
    private final class ThreadsListAdapter extends ArrayAdapter<ThreadInfo> {
    	private LayoutInflater mInflater;
//        private boolean mDisplayThumbnails = false; // TODO: use this
//        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null; // TODO?: use this?
        
        
        public ThreadsListAdapter(Context context, List<ThreadInfo> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
            TextView numCommentsView = (TextView) view.findViewById(R.id.numComments);
            TextView subredditView = (TextView) view.findViewById(R.id.subreddit);
//            TextView submissionTimeView = (TextView) view.findViewById(R.id.submissionTime);
            ImageView voteUpView = (ImageView) view.findViewById(R.id.vote_up_image);
            ImageView voteDownView = (ImageView) view.findViewById(R.id.vote_down_image);
            
            // Set the title and domain using a SpannableStringBuilder
            SpannableStringBuilder builder = new SpannableStringBuilder();
            SpannableString titleSS = new SpannableString(item.getTitle());
            int titleLen = item.getTitle().length();
            AbsoluteSizeSpan titleASS = new AbsoluteSizeSpan(14);
            titleSS.setSpan(titleASS, 0, titleLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (mSettings.theme == Constants.THEME_LIGHT) {
            	// FIXME: This doesn't work persistently, since "clicked" is not delivered to reddit.com
	            if (Constants.TRUE_STRING.equals(item.getClicked())) {
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
            SpannableString domainSS = new SpannableString("("+item.getDomain()+")");
            AbsoluteSizeSpan domainASS = new AbsoluteSizeSpan(10);
            domainSS.setSpan(domainASS, 0, item.getDomain().length()+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append(domainSS);
            titleView.setText(builder);
            
            votesView.setText(item.getScore());
            numCommentsView.setText(item.getNumComments()+" comments");
            if (mSettings.isFrontpage) {
            	subredditView.setVisibility(View.VISIBLE);
            	subredditView.setText(item.getSubreddit());
            } else {
            	subredditView.setVisibility(View.GONE);
            }
//            submitterView.setText(item.getAuthor());
            // TODO: convert submission time to a displayable time
//            Date submissionTimeDate = new Date((long) (Double.parseDouble(item.getCreated()) / 1000));
//            submissionTimeView.setText("5 hours ago");
            
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
	    setListAdapter(mThreadsAdapter);
	    Common.updateListDrawables(this, mSettings.theme);
    }

    /**
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.) 
     */
    private class DownloadThreadsTask extends AsyncTask<CharSequence, Integer, Boolean> {
    	
    	private ArrayList<ThreadInfo> mThreadInfos = new ArrayList<ThreadInfo>();
    	private String _mUserError = "Error retrieving subreddit info.";
    	
    	public Boolean doInBackground(CharSequence... subreddit) {
	    	try {
	    		String url;
	    		if (Constants.FRONTPAGE_STRING.equals(subreddit[0]))
	    			url = "http://www.reddit.com/.json";
	    		else
	    			url = new StringBuilder("http://www.reddit.com/r/")
            			.append(subreddit[0].toString().trim())
            			.append("/.json").toString();
            	HttpGet request = new HttpGet(url);
            	HttpResponse response = mClient.execute(request);
            	
            	InputStream in = response.getEntity().getContent();
                try {
                	parseSubredditJSON(in);
                	mSettings.setSubreddit(subreddit[0]);
                	return true;
                } catch (IllegalStateException e) {
                	_mUserError = "Invalid subreddit.";
                	Log.e(TAG, e.getMessage());
                } catch (Exception e) {
                	Log.e(TAG, e.getMessage());
                }
            } catch (IOException e) {
            	Log.e(TAG, "failed:" + e.getMessage());
            }
            return false;
	    }
    	
    	private void parseSubredditJSON(InputStream in) throws IOException,
		    	JsonParseException, IllegalStateException {
		
			JsonParser jp = jsonFactory.createJsonParser(in);
			
			if (jp.nextToken() == JsonToken.VALUE_NULL)
				return;
			
			// --- Validate initial stuff, skip to the JSON List of threads ---
			String genericListingError = "Not a subreddit listing";
		//	if (JsonToken.START_OBJECT != jp.nextToken()) // starts with "{"
		//		throw new IllegalStateException(genericListingError);
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
			int progressIndex = 0;
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
								ti.put(namefield, StringEscapeUtils.unescapeHtml(jp.getText().replaceAll("\r", "")));
							}
						}
					} else {
						throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
					}
				}
				mThreadInfos.add(ti);
				publishProgress(progressIndex++);
			}
    	}
    	
    	public void onPreExecute() {
    		if (mSettings.subreddit == null)
	    		this.cancel(true);
	    	
    		resetUI();
    		
	    	if ("jailbait".equals(mSettings.subreddit.toString())) {
	    		Toast lodToast = Toast.makeText(RedditIsFun.this, "", Toast.LENGTH_LONG);
	    		View lodView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
	    			.inflate(R.layout.look_of_disapproval_view, null);
	    		lodToast.setView(lodView);
	    		lodToast.show();
	    	}
	    	showDialog(Constants.DIALOG_LOADING_THREADS_LIST);
	    	if (Constants.FRONTPAGE_STRING.equals(mSettings.subreddit))
	    		setTitle("reddit.com: what's new online!");
	    	else
	    		setTitle("/r/"+mSettings.subreddit.toString().trim());
    	}
    	
    	public void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_LOADING_THREADS_LIST);
    		if (success) {
	    		for (ThreadInfo ti : mThreadInfos)
	        		mThreadsAdapter.add(ti);
	    		mThreadsAdapter.notifyDataSetChanged();
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, RedditIsFun.this);
    		}
    	}
    	
    	public void onProgressUpdate(Integer... progress) {
    		mLoadingThreadsProgress.setProgress(progress[0]);
    	}
    }
    
    
    private class LoginTask extends AsyncTask<Void, Void, String> {
    	private CharSequence mUsername, mPassword;
    	
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
    			Toast.makeText(RedditIsFun.this, "Logged in as "+mUsername, Toast.LENGTH_SHORT).show();
    			// Refresh the threads list
    			new DownloadThreadsTask().execute(mSettings.subreddit);
        	} else {
            	mSettings.setLoggedIn(false);
    			Common.showErrorToast(errorMessage, Toast.LENGTH_LONG, RedditIsFun.this);
    		}
    	}
    }
    
    
    private class VoteTask extends AsyncTask<Void, Void, Boolean> {
    	
    	private static final String TAG = "VoteWorker";
    	
    	private CharSequence _mThingFullname, _mSubreddit;
    	private int _mDirection;
    	private String _mUserError = "Error voting.";
    	private ThreadInfo _mTargetThreadInfo;
    	private View _mTargetView;
    	
    	// Save the previous arrow and score in case we need to revert
    	private int _mPreviousScore;
    	private String _mPreviousLikes;
    	
    	VoteTask(CharSequence thingFullname, int direction, CharSequence subreddit) {
    		_mThingFullname = thingFullname;
    		_mDirection = direction;
    		_mSubreddit = subreddit;
    		// Copy these because they can change while voting thread is running
    		_mTargetThreadInfo = mVoteTargetThreadInfo;
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
    			nvps.add(new BasicNameValuePair("r", _mSubreddit.toString()));
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
//            	int c;
//            	boolean done = false;
//            	StringBuilder sb = new StringBuilder();
//            	for (int k = 0; k < line.length(); k += 80) {
//            		for (int i = 0; i < 80; i++) {
//            			if (k + i >= line.length()) {
//            				done = true;
//            				break;
//            			}
//            			c = line.charAt(k + i);
//            			sb.append((char) c);
//            		}
//            		Log.d(TAG, "doReply response content: " + sb.toString());
//            		sb = new StringBuilder();
//            		if (done)
//            			break;
//            	}
//    	        	

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
        		Common.showErrorToast("You must be logged in to vote.", Toast.LENGTH_LONG, RedditIsFun.this);
        		cancel(true);
        		return;
        	}
        	if (_mDirection < -1 || _mDirection > 1) {
        		Log.e(TAG, "WTF: _mDirection = " + _mDirection);
        		throw new RuntimeException("How the hell did you vote something besides -1, 0, or 1?");
        	}
    		final ImageView ivUp = (ImageView) _mTargetView.findViewById(R.id.vote_up_image);
        	final ImageView ivDown = (ImageView) _mTargetView.findViewById(R.id.vote_down_image);
        	final TextView voteCounter = (TextView) _mTargetView.findViewById(R.id.votes);
    		int newImageResourceUp, newImageResourceDown;
        	String newScore;
        	String newLikes;
        	_mPreviousScore = Integer.valueOf(_mTargetThreadInfo.getScore());
        	_mPreviousLikes = _mTargetThreadInfo.getLikes();
        	if (Constants.TRUE_STRING.equals(_mPreviousLikes)) {
        		if (_mDirection == 0) {
        			newScore = String.valueOf(_mPreviousScore - 1);
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.NULL_STRING;
        		} else if (_mDirection == -1) {
        			newScore = String.valueOf(_mPreviousScore - 2);
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_blue;
        			newLikes = Constants.FALSE_STRING;
        		} else {
        			cancel(true);
        			return;
        		}
        	} else if (Constants.FALSE_STRING.equals(_mPreviousLikes)) {
        		if (_mDirection == 1) {
        			newScore = String.valueOf(_mPreviousScore + 2);
        			newImageResourceUp = R.drawable.vote_up_red;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.TRUE_STRING;
        		} else if (_mDirection == 0) {
        			newScore = String.valueOf(_mPreviousScore + 1);
        			newImageResourceUp = R.drawable.vote_up_gray;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.NULL_STRING;
        		} else {
        			cancel(true);
        			return;
        		}
        	} else {
        		if (_mDirection == 1) {
        			newScore = String.valueOf(_mPreviousScore + 1);
        			newImageResourceUp = R.drawable.vote_up_red;
        			newImageResourceDown = R.drawable.vote_down_gray;
        			newLikes = Constants.TRUE_STRING;
        		} else if (_mDirection == -1) {
        			newScore = String.valueOf(_mPreviousScore - 1);
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
    		voteCounter.setText(newScore);
    		_mTargetThreadInfo.setLikes(newLikes);
    		_mTargetThreadInfo.setScore(newScore);
    		mThreadsAdapter.notifyDataSetChanged();
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
        		_mTargetThreadInfo.setLikes(_mPreviousLikes);
        		_mTargetThreadInfo.setScore(String.valueOf(_mPreviousScore));
        		mThreadsAdapter.notifyDataSetChanged();
        		
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, RedditIsFun.this);
    		}
    	}
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
        
        menu.add(0, Constants.DIALOG_SUBMIT_LINK, 3, "Submit link")
        	.setOnMenuItemClickListener(new ThreadsListMenu(Constants.DIALOG_SUBMIT_LINK));
        
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
        		showDialog(mAction);
        		break;
        	case Constants.DIALOG_SUBMIT_LINK:
        		Intent submitLinkIntent = new Intent(RedditIsFun.this, SubmitLinkActivity.class);
        		submitLinkIntent.putExtra(ThreadInfo.SUBREDDIT, mSettings.subreddit);
        		startActivityForResult(submitLinkIntent, Constants.ACTIVITY_SUBMIT_LINK);
        		break;
        	case Constants.DIALOG_PICK_SUBREDDIT:
        		Intent pickSubredditIntent = new Intent(RedditIsFun.this, PickSubredditActivity.class);
        		startActivityForResult(pickSubredditIntent, Constants.ACTIVITY_PICK_SUBREDDIT);
        		break;
        	case Constants.DIALOG_OPEN_BROWSER:
        		String url;
        		if (mSettings.subreddit.equals(Constants.FRONTPAGE_STRING))
        			url = "http://www.reddit.com";
        		else
	        		url = new StringBuilder("http://www.reddit.com/r/").append(mSettings.subreddit).toString();
        		Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        		browser.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
        		RedditIsFun.this.startActivity(browser);
        		break;
            case Constants.DIALOG_LOGOUT:
        		Common.doLogout(mSettings, mClient);
        		Toast.makeText(RedditIsFun.this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        		new DownloadThreadsTask().execute(mSettings.subreddit);
        		break;
        	case Constants.DIALOG_REFRESH:
        		new DownloadThreadsTask().execute(mSettings.subreddit);
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
        		RedditIsFun.this.setListAdapter(mThreadsAdapter);
        		Common.updateListDrawables(RedditIsFun.this, mSettings.theme);
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
    	AutoResetProgressDialog pdialog;
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
    		        	dismissDialog(Constants.DIALOG_LOGIN);
    		        	new LoginTask(loginUsernameInput.getText(), loginPasswordInput.getText()).execute(); 
    		        	return true;
    		        }
    		        return false;
    		    }
    		});
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		loginButton.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				dismissDialog(Constants.DIALOG_LOGIN);
    				new LoginTask(loginUsernameInput.getText(), loginPasswordInput.getText()).execute();
    		    }
    		});
    		break;
    		
    	case Constants.DIALOG_THING_CLICK:
    		inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		builder = new AlertDialog.Builder(this);
    		dialog = builder.setView(inflater.inflate(R.layout.thread_click_dialog, null)).create();
    		break;

    	// "Please wait"
    	case Constants.DIALOG_LOGGING_IN:
    		pdialog = new AutoResetProgressDialog(this);
    		pdialog.setMessage("Logging in...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(true);
    		dialog = pdialog;
    		break;
    	case Constants.DIALOG_LOADING_THREADS_LIST:
    		mLoadingThreadsProgress = new AutoResetProgressDialog(this);
    		mLoadingThreadsProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    		mLoadingThreadsProgress.setMessage("Loading subreddit...");
    		mLoadingThreadsProgress.setCancelable(true);
    		dialog = mLoadingThreadsProgress;
    		break;
    	case Constants.DIALOG_LOADING_LOOK_OF_DISAPPROVAL:
    		pdialog = new AutoResetProgressDialog(this);
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
    		final CheckBox voteUpButton = (CheckBox) dialog.findViewById(R.id.vote_up_button);
    		final CheckBox voteDownButton = (CheckBox) dialog.findViewById(R.id.vote_down_button);
    		final TextView titleView = (TextView) dialog.findViewById(R.id.title);
    		final TextView urlView = (TextView) dialog.findViewById(R.id.url);
    		final TextView submissionStuffView = (TextView) dialog.findViewById(R.id.submissionTime_submitter_subreddit);
    		final Button loginButton = (Button) dialog.findViewById(R.id.login_button);
    		final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
    		final Button commentsButton = (Button) dialog.findViewById(R.id.thread_comments_button);
    		
    		titleView.setText(mVoteTargetThreadInfo.getTitle());
    		urlView.setText(mVoteTargetThreadInfo.getURL());
    		sb = new StringBuilder(Util.getTimeAgo(Double.valueOf(mVoteTargetThreadInfo.getCreatedUtc())))
    			.append(" by ").append(mVoteTargetThreadInfo.getAuthor());
            // Show subreddit if user is currently looking at front page
    		if (mSettings.isFrontpage) {
    			sb.append(" to ").append(mVoteTargetThreadInfo.getSubreddit());
    		}
            submissionStuffView.setText(sb);
            
    		// Only show upvote/downvote if user is logged in
    		if (mSettings.loggedIn) {
    			loginButton.setVisibility(View.GONE);
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
    			voteUpButton.setVisibility(View.GONE);
    			voteDownButton.setVisibility(View.GONE);
    			loginButton.setVisibility(View.VISIBLE);
    			loginButton.setOnClickListener(new OnClickListener() {
    				public void onClick(View v) {
    					dismissDialog(Constants.DIALOG_THING_CLICK);
    					showDialog(Constants.DIALOG_LOGIN);
    				}
    			});
    		}

    		// The "link" and "comments" buttons
    		OnClickListener commentsOnClickListener = new OnClickListener() {
    			public void onClick(View v) {
    				dismissDialog(Constants.DIALOG_THING_CLICK);
    				// Launch an Intent for RedditCommentsListActivity
    				Intent i = new Intent(RedditIsFun.this, RedditCommentsListActivity.class);
    				i.putExtra(ThreadInfo.SUBREDDIT, mVoteTargetThreadInfo.getSubreddit());
    				i.putExtra(ThreadInfo.ID, mVoteTargetThreadInfo.getId());
    				i.putExtra(ThreadInfo.TITLE, mVoteTargetThreadInfo.getTitle());
    				i.putExtra(ThreadInfo.NUM_COMMENTS, Integer.valueOf(mVoteTargetThreadInfo.getNumComments()));
    				startActivity(i);
        		}
    		};
    		commentsButton.setOnClickListener(commentsOnClickListener);
    		// TODO: Handle bestof posts, which aren't self posts
            if (("self.").toLowerCase().equals(mVoteTargetThreadInfo.getDomain().substring(0, 5).toLowerCase())) {
            	// It's a self post. Both buttons do the same thing.
            	linkButton.setOnClickListener(commentsOnClickListener);
            } else {
            	linkButton.setOnClickListener(new OnClickListener() {
            		public void onClick(View v) {
            			dismissDialog(Constants.DIALOG_THING_CLICK);
            			// Launch Intent to goto the URL
            			Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(mVoteTargetThreadInfo.getURL()));
            			browser.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
            			RedditIsFun.this.startActivity(browser);
            		}
            	});
            }
    		break;
    		
    	case Constants.DIALOG_LOADING_THREADS_LIST:
    		mLoadingThreadsProgress.setMax(mSettings.threadDownloadLimit);
    		break;
    		
		default:
			// No preparation based on app state is required.
			break;
    	}
    }
    
    /**
     * Called to "thaw" re-animate the app from a previous onSaveInstanceState().
     * 
     * @see android.app.Activity#onRestoreInstanceState
     */
//    @SuppressWarnings("unchecked")
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        try {
        	dismissDialog(Constants.DIALOG_LOGGING_IN);
	    } catch (IllegalArgumentException e) {
	    	// Ignore.
	    }
	    try {
	    	dismissDialog(Constants.DIALOG_LOADING_THREADS_LIST);
	    } catch (IllegalArgumentException e) {
	    	// Ignore.
	    }
    }
}
