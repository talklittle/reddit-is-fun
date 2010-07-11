package com.andrewshu.android.reddit;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Given a subreddit name string, starts the threadlist-download-thread going.
 * 
 * @param subreddit The name of a subreddit ("reddit.com", "gaming", etc.)
 *        If the number of elements in subreddit is >= 2, treat 2nd element as "after" 
 */
public abstract class DownloadThreadsTask extends AsyncTask<Void, Long, Boolean> implements PropertyChangeListener {
	
	static final String TAG = "DownloadThreadsTask";

	protected Context mContext;
	protected final DefaultHttpClient mClient;
	private ObjectMapper mOm;
	
	protected String mSubreddit;
	protected String mSortByUrl = Constants.ThreadsSort.SORT_BY_HOT_URL;
	protected String mSortByUrlExtra = Constants.EMPTY_STRING;
	protected String mAfter;
	protected String mBefore;
	protected int mCount;
	protected String mLastAfter = null;
	protected String mLastBefore = null;
	protected int mLastCount = 0;
	
	protected String mUserError = "Error retrieving subreddit info.";
	// Progress bar
	protected long mContentLength = 0;
	
	// Downloaded data
	protected ArrayList<ThingInfo> mThingInfos = new ArrayList<ThingInfo>();
	protected String mModhash = null;
	
	public DownloadThreadsTask(Context context, DefaultHttpClient client, ObjectMapper om,
			String sortByUrl, String sortByUrlExtra,
			String subreddit) {
		this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, null, null, Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT);
	}
	
	public DownloadThreadsTask(Context context, DefaultHttpClient client, ObjectMapper om,
			String sortByUrl, String sortByUrlExtra,
			String subreddit, String after, String before, int count) {
		mContext = context;
		mClient = client;
		mOm = om;
		mSortByUrl = sortByUrl;
		mSortByUrlExtra = sortByUrlExtra;
		if (subreddit != null)
			mSubreddit = subreddit;
		else
			mSubreddit = Constants.FRONTPAGE_STRING;

		mAfter = after;
		mBefore = before;
		mCount = count;
	}
	
	public Boolean doInBackground(Void... zzz) {
		HttpEntity entity = null;
		boolean isAfter = false;
		boolean isBefore = false;
    	try {
    		String url;
    		StringBuilder sb;
    		// If refreshing or something, use the previously used URL to get the threads.
    		// Picking a new subreddit will erase the saved URL, getting rid of after= and before=.
    		// subreddit.length != 0 means you are going Next or Prev, which creates new URL.
			if (Constants.FRONTPAGE_STRING.equals(mSubreddit)) {
    			sb = new StringBuilder("http://www.reddit.com/").append(mSortByUrl)
    				.append(".json?").append(mSortByUrlExtra).append("&");
    		} else {
    			sb = new StringBuilder("http://www.reddit.com/r/")
        			.append(mSubreddit.toString().trim())
        			.append("/").append(mSortByUrl).append(".json?")
        			.append(mSortByUrlExtra).append("&");
    		}
			// "before" always comes back null unless you provide correct "count"
    		if (mAfter != null) {
    			// count: 25, 50, ...
				sb = sb.append("count=").append(mCount)
					.append("&after=").append(mAfter).append("&");
				isAfter = true;
    		}
    		else if (mBefore != null) {
    			// count: nothing, 26, 51, ...
    			sb = sb.append("count=").append(mCount + 1 - Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT)
    				.append("&before=").append(mBefore).append("&");
    			isBefore = true;
    		}
    		
    		url = sb.toString();

    		InputStream in = null;
    		boolean currentlyUsingCache = false;
    		
    		if (Constants.USE_CACHE) {
    			try {
	    			if (CacheInfo.checkFreshSubredditCache(mContext)
	    					&& url.equals(CacheInfo.getCachedSubredditUrl(mContext))) {
	    				in = mContext.openFileInput(Constants.FILENAME_SUBREDDIT_CACHE);
	    				mContentLength = mContext.getFileStreamPath(Constants.FILENAME_SUBREDDIT_CACHE).length();
	    				currentlyUsingCache = true;
	    				if (Constants.LOGGING) Log.d(TAG, "Using cached subreddit JSON, length=" + mContentLength);
	    			}
    			} catch (Exception cacheEx) {
    				if (Constants.LOGGING) Log.w(TAG, "skip cache because of: "+cacheEx.getMessage());
    			}
    		}
    		
    		// If we couldn't use the cache, then do HTTP request
    		if (!currentlyUsingCache) {
	    		HttpGet request;
    			try {
    				request = new HttpGet(url);
    			} catch (IllegalArgumentException e) {
    				mUserError = "Invalid subreddit.";
                	if (Constants.LOGGING) Log.e(TAG, "IllegalArgumentException:" + e.getMessage());
                	return false;
    			}
            	HttpResponse response = mClient.execute(request);

            	// Read the header to get Content-Length since entity.getContentLength() returns -1
            	Header contentLengthHeader = response.getFirstHeader("Content-Length");
            	mContentLength = Long.valueOf(contentLengthHeader.getValue());
            	if (Constants.LOGGING) Log.d(TAG, "Content length: "+mContentLength);

            	entity = response.getEntity();
            	in = entity.getContent();
            	
            	if (Constants.USE_CACHE) {
                	in = CacheInfo.writeThenRead(mContext, in, Constants.FILENAME_SUBREDDIT_CACHE);
                	try {
                		CacheInfo.setCachedSubredditUrl(mContext, url);
                	} catch (IOException e) {
                		if (Constants.LOGGING) Log.e(TAG, "error on setCachedSubreddit: " + e.getMessage());
                	}
            	}
    		}
        	
    		ProgressInputStream pin = new ProgressInputStream(in, mContentLength);
        	pin.addPropertyChangeListener(this);
        	
        	try {
            	parseSubredditJSON(pin);
            	
            	mLastCount = mCount;
            	if (isAfter)
            		mCount += Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
            	else if (isBefore)
            		mCount -= Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT;
            	
            	saveState();
            	
            	return true;
            	
            } catch (IllegalStateException e) {
            	mUserError = "Invalid subreddit.";
            	if (Constants.LOGGING) Log.e(TAG, "IllegalStateException:" + e.getMessage());
            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "Exception:" + e.getMessage());
            } finally {
            	pin.close();
            	in.close();
            }
        } catch (Exception e) {
        	if (Constants.LOGGING) Log.e(TAG, "DownloadThreadsTask:" + e.getMessage());
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
	
	protected void parseSubredditJSON(InputStream in)
			throws IOException, JsonParseException, IllegalStateException {
		
		String genericListingError = "Not a subreddit listing";
		try {
			Listing listing = mOm.readValue(in, Listing.class);
			
			if (!Constants.JSON_LISTING.equals(listing.getKind()))
				throw new IllegalStateException(genericListingError);
			// Save the modhash, after, and before
			ListingData data = listing.getData();
			if (Constants.EMPTY_STRING.equals(data.getModhash()))
				mModhash = null;
			else
				mModhash = data.getModhash();
			
			mLastAfter = mAfter;
			mLastBefore = mBefore;
			mAfter = data.getAfter();
			mBefore = data.getBefore();
			
			// Go through the children and get the ThingInfos
			for (ThingListing tiContainer : data.getChildren()) {
				// Only add entries that are threads. kind="t3"
				if (Constants.THREAD_KIND.equals(tiContainer.getKind())) {
					ThingInfo ti = tiContainer.getData();
					
					// Additional formatting on the threads
					ti.setTitle(StringEscapeUtils.unescapeHtml(ti.getTitle().trim()
							.replaceAll("\r", "").replaceAll("\n ", " ").replaceAll(" \n", " ").replaceAll("\n", " ")));
					
					mThingInfos.add(tiContainer.getData());
				}
			}
		} catch (Exception ex) {
			if (Constants.LOGGING) Log.e(TAG, "parseSubredditJSON:" + ex.getMessage());
		}
	}
	
	abstract protected void saveState();
}
