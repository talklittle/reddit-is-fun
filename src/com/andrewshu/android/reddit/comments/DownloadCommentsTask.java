package com.andrewshu.android.reddit.comments;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import android.os.AsyncTask;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.common.CacheInfo;
import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.ProgressInputStream;
import com.andrewshu.android.reddit.common.util.Assert;
import com.andrewshu.android.reddit.common.util.StringUtils;
import com.andrewshu.android.reddit.common.util.Util;
import com.andrewshu.android.reddit.markdown.Markdown;
import com.andrewshu.android.reddit.settings.RedditSettings;
import com.andrewshu.android.reddit.things.Listing;
import com.andrewshu.android.reddit.things.ListingData;
import com.andrewshu.android.reddit.things.ThingInfo;
import com.andrewshu.android.reddit.things.ThingListing;

/**
 * Task takes in a subreddit name string and thread id, downloads its data, parses
 * out the comments, and communicates them back to the UI as they are read.
 * 
 * Requires the following navigation variables to be set:
 * mSettings.subreddit
 * mSettings.threadId
 * mMoreChildrenId (can be "")
 * mSortByUrl
 */
public class DownloadCommentsTask extends AsyncTask<Integer, Long, Boolean>
		implements PropertyChangeListener {
	
	private static final String TAG = "CommentsListActivity.DownloadCommentsTask";
	
    private final ObjectMapper mObjectMapper = Common.getObjectMapper();
    private final Markdown markdown = new Markdown();

    private AsyncTask<?, ?, ?> mCurrentDownloadCommentsTask = null;
    private static final Object mCurrentDownloadCommentsTaskLock = new Object();
    
    private CommentsListActivity mActivity;
    private String mSubreddit;
    private String mThreadId;
    private String mThreadTitle;
    private RedditSettings mSettings;
    private DefaultHttpClient mClient;
	
	// offset of the first comment being loaded; 0 if it includes OP
	private int mPositionOffset = 0;
	private int mIndentation = 0;
	private String mMoreChildrenId = "";
    private ThingInfo mOpThingInfo = null;

	private LinkedList<ThingInfo> mDeferredInsertList = new LinkedList<ThingInfo>();
	private LinkedList<DeferredCommentProcessing> mDeferredProcessingList = new LinkedList<DeferredCommentProcessing>();
	
	// Progress bar
	private long mContentLength = 0;
	
	private String mJumpToCommentId = "";
	private ThingInfo[] mJumpToCommentContext = new ThingInfo[0];
	private int mJumpToCommentContextIndex = 0;  // keep track of insertion index, act like circular array overwriting
	private int mJumpToCommentFoundIndex = -1;
	
	private class DeferredCommentProcessing {
		public int commentIndex;
		public ThingInfo comment;
		public DeferredCommentProcessing(ThingInfo comment, int commentIndex) {
			this.comment = comment;
			this.commentIndex = commentIndex;
		}
	}
   
	/**
	 * Default constructor to do normal comments page
	 */
	public DownloadCommentsTask(
			CommentsListActivity activity,
			String subreddit,
			String threadId,
			RedditSettings settings,
			DefaultHttpClient client
	) {
		this.mActivity = activity;
		this.mSubreddit = subreddit;
		this.mThreadId = threadId;
		this.mSettings = settings;
		this.mClient = client;
	}
	
	/**
	 * "load more comments" starting at this position
	 * @param moreChildrenId The reddit thing-id of the "more" children comment
	 * @param morePosition Position in local list to insert
	 * @param indentation The indentation level of the child.
	 */
	public DownloadCommentsTask prepareLoadMoreComments(String moreChildrenId, int morePosition, int indentation) {
		mMoreChildrenId = moreChildrenId;
		mPositionOffset = morePosition;
		mIndentation = indentation;
		return this;
	}
	
	public DownloadCommentsTask prepareLoadAndJumpToComment(String commentId, int context) {
		mJumpToCommentId = commentId;
		mJumpToCommentContext = new ThingInfo[context];
		return this;
	}
	
	// XXX: maxComments is unused for now
	public Boolean doInBackground(Integer... maxComments) {
		HttpEntity entity = null;
        try {
        	StringBuilder sb = new StringBuilder("http://api.reddit.com");
    		if (mSubreddit != null) {
    			sb.append("/r/").append(mSubreddit.trim());
    		}
    		sb.append("/comments/")
        		.append(mThreadId)
        		.append("/z/").append(mMoreChildrenId).append("/?")
        		.append(mSettings.getCommentsSortByUrl()).append("&");
        	if (mJumpToCommentContext.length != 0)
        		sb.append("context=").append(mJumpToCommentContext.length).append("&");
        	
        	String url = sb.toString();
        	
        	InputStream in = null;
    		boolean currentlyUsingCache = false;
    		
        	if (Constants.USE_COMMENTS_CACHE) {
    			try {
	    			if (CacheInfo.checkFreshThreadCache(mActivity.getApplicationContext())
	    					&& url.equals(CacheInfo.getCachedThreadUrl(mActivity.getApplicationContext()))) {
	    				in = mActivity.openFileInput(Constants.FILENAME_THREAD_CACHE);
	    				mContentLength = mActivity.getFileStreamPath(Constants.FILENAME_THREAD_CACHE).length();
	    				currentlyUsingCache = true;
	    				if (Constants.LOGGING) Log.d(TAG, "Using cached thread JSON, length=" + mContentLength);
	    			}
    			} catch (Exception cacheEx) {
    				if (Constants.LOGGING) Log.w(TAG, "skip cache", cacheEx);
    			}
    		}
    		
    		// If we couldn't use the cache, then do HTTP request
        	if (!currentlyUsingCache) {
		    	HttpGet request = new HttpGet(url);
                HttpResponse response = mClient.execute(request);
            	
                // Read the header to get Content-Length since entity.getContentLength() returns -1
            	Header contentLengthHeader = response.getFirstHeader("Content-Length");
            	if (contentLengthHeader != null) {
            		mContentLength = Long.valueOf(contentLengthHeader.getValue());
	            	if (Constants.LOGGING) Log.d(TAG, "Content length: "+mContentLength);
            	}
            	else {
            		mContentLength = -1; 
	            	if (Constants.LOGGING) Log.d(TAG, "Content length: UNAVAILABLE");
            	}

            	entity = response.getEntity();
            	in = entity.getContent();
            	
            	if (Constants.USE_COMMENTS_CACHE) {
                	in = CacheInfo.writeThenRead(mActivity.getApplicationContext(), in, Constants.FILENAME_THREAD_CACHE);
                	try {
                		CacheInfo.setCachedThreadUrl(mActivity.getApplicationContext(), url);
                	} catch (IOException e) {
                		if (Constants.LOGGING) Log.e(TAG, "error on setCachedThreadId", e);
                	}
            	}
        	}
            
        	// setup a special InputStream to report progress
        	ProgressInputStream pin = new ProgressInputStream(in, mContentLength);
        	pin.addPropertyChangeListener(this);
        	
        	parseCommentsJSON(pin);
        	if (Constants.LOGGING) Log.d(TAG, "parseCommentsJSON completed");
        	
        	pin.close();
            in.close();
            
            return true;
            
        } catch (Exception e) {
        	if (Constants.LOGGING) Log.e(TAG, "DownloadCommentsTask", e);
        } finally {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
    			}
    		}
        }
        return false;
    }
	
	private void appendComment(final ThingInfo comment) {
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
	    		synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
	    			mActivity.mCommentsList.add(comment);
	    		}
    			mActivity.mCommentsAdapter.notifyDataSetChanged();
			}
		});
	}
	
	private void replaceCommentsAtPosition(final Collection<ThingInfo> comments, final int position) {
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
	    		synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
	    			mActivity.mCommentsList.remove(position);
	    			mActivity.mCommentsList.addAll(position, comments);
	    		}
	    		mActivity.mCommentsAdapter.notifyDataSetChanged();
			}
		});
	}
	
	/**
	 * defer insertion of comment, in case we want to insert a group of comments at the same time for convenience.
	 */
	private void deferCommentInsertion(ThingInfo comment) {
		mDeferredInsertList.add(comment);
	}
	
	/**
	 * defer the slow processing step of a comment, in case we want to prioritize processing of comments over others.
	 */
	private void deferCommentProcessing(ThingInfo comment, int commentIndex) {
		mDeferredProcessingList.addFirst(new DeferredCommentProcessing(comment, commentIndex));
	}
	
	/**
	 * tell if inserting entire thread, versus loading "more comments"
	 */
	private boolean isInsertingEntireThread() {
		return mPositionOffset == 0;
	}
	
	private void disableLoadingScreenKeepProgress() {
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
	    		mActivity.resetUI(mActivity.mCommentsAdapter);
			}
		});
	}
	
	private void parseCommentsJSON(
			InputStream in
	) throws IOException, JsonParseException {
		int insertedCommentIndex;
		String genericListingError = "Not a comments listing";
		try {
			Listing[] listings = mObjectMapper.readValue(in, Listing[].class);

			// listings[0] is a thread Listing for the OP.
			// process same as a thread listing more or less
			
			Assert.assertEquals(Constants.JSON_LISTING, listings[0].getKind(), genericListingError);
			
			// Save modhash, ignore "after" and "before" which are meaningless in this context (and probably null)
			ListingData threadListingData = listings[0].getData();
			if (StringUtils.isEmpty(threadListingData.getModhash()))
				mSettings.setModhash(null);
			else
				mSettings.setModhash(threadListingData.getModhash());
			
			if (Constants.LOGGING) Log.d(TAG, "Successfully got OP listing[0]: modhash "+mSettings.getModhash());
			
			ThingListing threadThingListing = threadListingData.getChildren()[0];
			Assert.assertEquals(Constants.THREAD_KIND, threadThingListing.getKind(), genericListingError);

			if (isInsertingEntireThread()) {
				parseOP(threadThingListing.getData());
				insertedCommentIndex = 0;  // we just inserted the OP into position 0
			}
			else {
				insertedCommentIndex = mPositionOffset - 1;  // -1 because we +1 for the first comment
			}
			
			// at this point we've started displaying comments, so disable the loading screen
			disableLoadingScreenKeepProgress();
			
			// listings[1] is a comment Listing for the comments
			// Go through the children and get the ThingInfos
			ListingData commentListingData = listings[1].getData();
			for (ThingListing commentThingListing : commentListingData.getChildren()) {
				// insert the comment and its replies, prefix traversal order
				insertedCommentIndex = insertNestedComment(commentThingListing, 0, insertedCommentIndex + 1);
			}
			
			processDeferredComments();
			
		} catch (Exception ex) {
			if (Constants.LOGGING) Log.e(TAG, "parseCommentsJSON", ex);
		}
	}
	
	private void parseOP(ThingInfo data) {
		mOpThingInfo = data;
		mOpThingInfo.setIndent(0);
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
					mActivity.mCommentsList.add(0, mOpThingInfo);
				}
			}
		});

		if (mOpThingInfo.isIs_self() && mOpThingInfo.getSelftext_html() != null) {
			// HTML to Spanned
			String unescapedHtmlSelftext = Html.fromHtml(mOpThingInfo.getSelftext_html()).toString();
			Spanned selftext = Html.fromHtml(Util.convertHtmlTags(unescapedHtmlSelftext));
			
    		// remove last 2 newline characters
			if (selftext.length() > 2)
				mOpThingInfo.setSpannedSelftext(selftext.subSequence(0, selftext.length()-2));
			else
				mOpThingInfo.setSpannedSelftext("");

			// Get URLs from markdown
			markdown.getURLs(mOpThingInfo.getSelftext(), mOpThingInfo.getUrls());
		}
		// We might not have a title if we've intercepted a plain link to a thread.
		mThreadTitle = mOpThingInfo.getTitle();
		mActivity.setThreadTitle(mThreadTitle);
		mSubreddit = mOpThingInfo.getSubreddit();
		mThreadId = mOpThingInfo.getId();
	}
	
	/**
	 * Recursive method to insert comment tree into the mCommentsList,
	 * with proper list order and indentation
	 */
	int insertNestedComment(ThingListing commentThingListing, int indentLevel, int insertedCommentIndex) {
		ThingInfo ci = commentThingListing.getData();
		ci.setIndent(mIndentation + indentLevel);
		
		if (isShouldDoSlowProcessing())
    		processCommentSlowSteps(ci);
		else
			deferCommentProcessing(ci, insertedCommentIndex);

		// Insert the comment
		if (isInsertingEntireThread())
			appendComment(ci);
		else
			deferCommentInsertion(ci);
		
		if (isHasJumpTarget()) {
			if (mJumpToCommentId.equals(ci.getId()))
				processJumpTarget(ci, insertedCommentIndex);
			else if (!isFoundJumpTargetComment())
				addJumpTargetContext(ci);
		}

		// handle "more" entry
		if (Constants.MORE_KIND.equals(commentThingListing.getKind())) {
			ci.setLoadMoreCommentsPlaceholder(true);
			if (Constants.LOGGING) Log.v(TAG, "new more position at " + (insertedCommentIndex));
	    	return insertedCommentIndex;
		}
		
		// Regular comment
		
		// Skip things that are not comments, which shouldn't happen
		if (!Constants.COMMENT_KIND.equals(commentThingListing.getKind())) {
			if (Constants.LOGGING) Log.e(TAG, "comment whose kind is \""+commentThingListing.getKind()+"\" (expected "+Constants.COMMENT_KIND+")");
			return insertedCommentIndex;
		}
		
		// handle the replies
		Listing repliesListing = ci.getReplies();
		if (repliesListing == null)
			return insertedCommentIndex;
		ListingData repliesListingData = repliesListing.getData();
		if (repliesListingData == null)
			return insertedCommentIndex;
		ThingListing[] replyThingListings = repliesListingData.getChildren();
		if (replyThingListings == null)
			return insertedCommentIndex;
		
		for (ThingListing replyThingListing : replyThingListings) {
			insertedCommentIndex = insertNestedComment(replyThingListing, indentLevel + 1, insertedCommentIndex + 1);
		}
		return insertedCommentIndex;
	}
	
	private boolean isHasJumpTarget() {
		return ! StringUtils.isEmpty(mJumpToCommentId);
	}
	
	private boolean isFoundJumpTargetComment() {
		return mJumpToCommentFoundIndex != -1;
	}
	
	private boolean isShouldDoSlowProcessing() {
		return !isHasJumpTarget() || isFoundJumpTargetComment();
	}
	
	private void processJumpTarget(ThingInfo comment, int commentIndex) {
		int numContext = mJumpToCommentContext.length;
		mJumpToCommentFoundIndex = (commentIndex - numContext) > 0 ? (commentIndex - numContext) : 0;
		
		// load the jump target, plus the comments that are the context of the jump target
		processCommentSlowSteps(comment);
		for (ThingInfo contextComment : mJumpToCommentContext) {
			if (contextComment == null)
				break;
			processCommentSlowSteps(contextComment);
		}
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				refreshVisibleCommentsUI();
				mActivity.getListView().setSelection(mJumpToCommentFoundIndex);
			}
		});
	}
	
    /**
     * Refresh the body TextView of visible comments. Call from UI Thread.
     */
    private void refreshVisibleCommentsUI() {
		int firstPosition = mActivity.getListView().getFirstVisiblePosition();
		int lastPosition = mActivity.getListView().getLastVisiblePosition();
		for (int i = firstPosition; i <= lastPosition; i++)
			refreshCommentUI(i);
	}
    
    private void refreshCommentUI(int commentIndex) {
		refreshCommentBodyTextViewUI(commentIndex);
		refreshCommentSubmitterUI(commentIndex);
    }
    
    private void refreshCommentBodyTextViewUI(int commentIndex) {
		View v = mActivity.getListView().getChildAt(commentIndex);
		if (v != null) {
			View bodyTextView = v.findViewById(R.id.body);
			if (bodyTextView != null) {
				synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
					((TextView) bodyTextView).setText(mActivity.mCommentsList.get(commentIndex).getSpannedBody());
				}
			}
		}
    }

    private void refreshCommentSubmitterUI(int commentIndex) {
		View v = mActivity.getListView().getChildAt(commentIndex);
		if (v != null) {
			View submitterTextView = v.findViewById(R.id.submitter);
			if (submitterTextView != null) {
				synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
					ThingInfo comment = mActivity.mCommentsList.get(commentIndex);
					if (comment.getSSAuthor() != null)
						((TextView) submitterTextView).setText(comment.getSSAuthor());
					else
						((TextView) submitterTextView).setText(comment.getAuthor());
				}
			}
		}
    }

	/**
	 * Call from UI Thread
	 */
    boolean isPositionVisibleUI(int position) {
		return position <= mActivity.getListView().getLastVisiblePosition() &&
				position >= mActivity.getListView().getFirstVisiblePosition();
	}
	
	private void addJumpTargetContext(ThingInfo comment) {
		if (mJumpToCommentContext.length > 0) {
			mJumpToCommentContext[mJumpToCommentContextIndex] = comment;
			mJumpToCommentContextIndex = (mJumpToCommentContextIndex + 1) % mJumpToCommentContext.length;
		}
	}
	
	private void processCommentSlowSteps(ThingInfo comment) {
		if (comment.getBody_html() != null) {
        	//get title and put in body since images aren't shown
			String useMeForSpan = comment.getBody_html();
        	if(useMeForSpan.contains("title=")) {
			String[] splitHTML = useMeForSpan.split("title="); 
			for (int i =1; i<splitHTML.length;i++){
				String[] tags=splitHTML[i].split("&gt;");
				tags[2]="["+splitHTML[i].split("\"")[1]+"]"+tags[2];
				splitHTML[i]=join(tags,"&gt;");
			}
			useMeForSpan=join(splitHTML,"title=");
        	}
			CharSequence spanned = createSpanned(useMeForSpan);
        	comment.setSpannedBody(spanned);
		}
		markdown.getURLs(comment.getBody(), comment.getUrls());
	}
	
	public static String join(String[] strings, String separator) {
	    StringBuffer sb = new StringBuffer();
	    for (int i=0; i < strings.length; i++) {
	        if (i != 0) sb.append(separator);
	  	    sb.append(strings[i]);
	  	}
	  	return sb.toString();
	}

	
	private void processDeferredComments() {
    	if (!mDeferredInsertList.isEmpty()) {
    		replaceCommentsAtPosition(mDeferredInsertList, mPositionOffset);
    	}
        
    	if (!mDeferredProcessingList.isEmpty()) {
    		for (DeferredCommentProcessing deferredCommentProcessing : mDeferredProcessingList) {
    			processCommentSlowSteps(deferredCommentProcessing.comment);
    			refreshDeferredCommentIfVisible(deferredCommentProcessing.commentIndex);
    		}
    	}
	}
	
	private void refreshDeferredCommentIfVisible(final int commentIndex) {
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
    			if (isPositionVisibleUI(commentIndex)) {
    				refreshCommentUI(commentIndex);
    				mActivity.getListView().setSelection(mJumpToCommentFoundIndex);
    			}
			}
		});
	}
	
	/**
	 * @param bodyHtml escaped HTML (like in reddit Thing's body_html)
	 */
    private CharSequence createSpanned(String bodyHtml) {
    	try {
    		// get unescaped HTML
    		bodyHtml = Html.fromHtml(bodyHtml).toString();
    		// fromHtml doesn't support all HTML tags. convert <code> and <pre>
    		bodyHtml = Util.convertHtmlTags(bodyHtml);
    		
    		Spanned body = Html.fromHtml(bodyHtml);
    		// remove last 2 newline characters
    		if (body.length() > 2)
    			return body.subSequence(0, body.length()-2);
    		else
    			return "";
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, "createSpanned failed", e);
    		return null;
    	}
    }
    
    /**
     * cleanup deferred in onPostExecute(), otherwise you clear it too soon and end up with race condition vs. UI thread
     */
    private void cleanupDeferred() {
    	mDeferredInsertList.clear();
    	mDeferredProcessingList.clear();
    }
    
    @Override
	public void onPreExecute() {
		if (mThreadId == null) {
			if (Constants.LOGGING) Log.e(TAG, "mSettings.threadId == null");
    		this.cancel(true);
    		return;
		}
		synchronized (mCurrentDownloadCommentsTaskLock) {
    		if (mCurrentDownloadCommentsTask != null) {
    			this.cancel(true);
    			return;
    		}
    		mCurrentDownloadCommentsTask = this;
		}
		
		if (isInsertingEntireThread()) {
    		// Initialize mCommentsList and mCommentsAdapter
    		synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
    			mActivity.resetUI(null);
    		}
    		// Do loading screen when loading new thread; otherwise when "loading more comments" don't show it
			mActivity.enableLoadingScreen();
		}
		
		if (mContentLength == -1)
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);

		if (mThreadTitle != null)
			mActivity.setTitle(mThreadTitle + " : " + mSubreddit);
	}
	
	@Override
	public void onPostExecute(Boolean success) {
		cleanupDeferred();

        // label the OP's comments with [S]
        mActivity.markSubmitterComments();
		
		synchronized (mCurrentDownloadCommentsTaskLock) {
			mCurrentDownloadCommentsTask = null;
		}
		
		if (mContentLength == -1)
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_OFF);
		else
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
		
		if (success) {
			// We should clear any replies the user was composing.
			mActivity.setShouldClearReply(true);

			mActivity.mCommentsAdapter.notifyDataSetChanged();
			refreshVisibleCommentsUI();
			
			// Set title in android titlebar
			if (mThreadTitle != null)
				mActivity.setTitle(mThreadTitle + " : " + mSubreddit);
		} else {
			if (!isCancelled())
				Common.showErrorToast("Error downloading comments. Please try again.", Toast.LENGTH_LONG, mActivity);
		}
	}
	
	@Override
	public void onProgressUpdate(Long... progress) {
		// 0-9999 is ok, 10000 means it's finished
		if (mContentLength == -1) {
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);
		}
		else {
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * 9999 / (int) mContentLength);
		}
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		publishProgress((Long) event.getNewValue());
	}
}

