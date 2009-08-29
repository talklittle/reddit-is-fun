package com.andrewshu.android.reddit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import android.app.ListActivity;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class UserActivity extends ListActivity {

	private static final String TAG = "UserActivity";
	private final JsonFactory jsonFactory = new JsonFactory();
	
	/** Custom list adapter that fits our threads data into the list. */
    private UserListAdapter mUserListAdapter;
    
    private final RedditSettings mSettings = new RedditSettings();
    private final DefaultHttpClient mClient = Common.createGzipHttpClient();
	
    // ProgressDialogs with percentage bars
    private AutoResetProgressDialog mLoadingThingsProgress;
	
    
    
    /**
     * Resets the output UI list contents, retains session state.
     */
    public void resetUI() {
        // Reset the list to be empty.
    	List<ThreadInfo> items = new ArrayList<ThreadInfo>();
		mUserListAdapter = new UserListAdapter(this, items);
	    setListAdapter(mUserListAdapter);
	    updateListDrawables();
    }
    
    /**
     * Set the Drawable for the list selector etc. based on the current theme.
     */
    private void updateListDrawables() {
    	if (mSettings.theme == R.style.Reddit_Light) {
    		getListView().setSelector(R.drawable.list_selector_blue);
    		// TODO: Set the empty listview image
    	} else if (mSettings.theme == R.style.Reddit_Dark) {
    		getListView().setSelector(android.R.drawable.list_selector_background);
    	}
    }

    
	
	private final class UserListAdapter extends ArrayAdapter<ThreadInfo> {
    	private LayoutInflater mInflater;
//        private boolean mDisplayThumbnails = false; // TODO: use this
//        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null; // TODO?: use this?
        
        
        public UserListAdapter(Context context, List<ThreadInfo> objects) {
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
            if (mSettings.theme == R.style.Reddit_Light) {
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
            
            titleView.setText(item.getTitle());
            if (mSettings.theme == R.style.Reddit_Light) {
	            if (Constants.TRUE_STRING.equals(item.getClicked()))
	            	titleView.setTextColor(res.getColor(R.color.purple));
	            else
	            	titleView.setTextColor(res.getColor(R.color.blue));
            }
            votesView.setText(item.getScore());
            numCommentsView.setText(item.getNumComments());
            subredditView.setText(item.getSubreddit());
            
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
     * Given a subreddit name string, starts the threadlist-download-thread going.
     * 
     * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.) 
     */
    private class DownloadThingsTask extends AsyncTask<CharSequence, Integer, Boolean> {
    	
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
	    		Toast lodToast = Toast.makeText(UserActivity.this, "", Toast.LENGTH_LONG);
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
	        		mUserListAdapter.add(ti);
	    		mUserListAdapter.notifyDataSetChanged();
    		} else {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, UserActivity.this);
    		}
    	}
    	
    	public void onProgressUpdate(Integer... progress) {
    		mLoadingThingsProgress.setProgress(progress[0]);
    	}
    }
    
}
