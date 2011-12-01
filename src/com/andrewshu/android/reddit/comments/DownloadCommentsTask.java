package com.andrewshu.android.reddit.comments;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
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
import com.andrewshu.android.reddit.threads.ShowThumbnailsTask;
import com.andrewshu.android.reddit.threads.ShowThumbnailsTask.ThumbnailLoadAction;

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

    private static AsyncTask<?, ?, ?> mCurrentDownloadCommentsTask = null;
    private static final Object mCurrentDownloadCommentsTaskLock = new Object();
    
    private ShowThumbnailsTask mCurrentShowThumbnailsTask = null;
    private final Object mCurrentShowThumbnailsTaskLock = new Object();
    
    private CommentsListActivity mActivity;
    private String mSubreddit;
    private String mThreadId;
    private String mThreadTitle;
    private RedditSettings mSettings;
    private HttpClient mClient;
	
	// offset of the first comment being loaded; 0 if it includes OP
	private int mPositionOffset = 0;
	private int mIndentation = 0;
	private String mMoreChildrenId = "";
    private ThingInfo mOpThingInfo = null;

    /**
     * List holding the comments to be appended at the end.
     * Used when loading an entire thread.
     */
    private final LinkedList<ThingInfo> mDeferredAppendList = new LinkedList<ThingInfo>();
    /**
     * List holding the comments to be inserted at mPositionOffset; the existing comment there will be removed.
     * Used for "load more comments" links.
     */
    private final LinkedList<ThingInfo> mDeferredReplacementList = new LinkedList<ThingInfo>();
	
    /**
     * List holding the deferred processing list starting from the first object to handle
     */
    private final LinkedList<DeferredCommentProcessing> mDeferredProcessingList = new LinkedList<DeferredCommentProcessing>();
    /**
     * Helper list holding the "context" comments, given first priority for processing since they'll be shown first.
     */
    private final LinkedList<DeferredCommentProcessing> mDeferredProcessingContextList = new LinkedList<DeferredCommentProcessing>();
    /**
     * Helper list holding the tail of the deferred processing list, to be appended to mDeferredProcessingList
     */
    private final LinkedList<DeferredCommentProcessing> mDeferredProcessingTailList = new LinkedList<DeferredCommentProcessing>();
    
	private class DeferredCommentProcessing {
		public int commentIndex;
		public ThingInfo comment;
		public DeferredCommentProcessing(ThingInfo comment, int commentIndex) {
			this.comment = comment;
			this.commentIndex = commentIndex;
		}
	}
    
    // Progress bar
	private long mContentLength = 0;
	
	private String mJumpToCommentId = "";
	private int mJumpToCommentFoundIndex = -1;
	
	private int mJumpToCommentContext = 0;
	
	/**
	 * Default constructor to do normal comments page
	 */
	public DownloadCommentsTask(
			CommentsListActivity activity,
			String subreddit,
			String threadId,
			RedditSettings settings,
			HttpClient client
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
		mJumpToCommentContext = context;
		return this;
	}
	
	// XXX: maxComments is unused for now
	public Boolean doInBackground(Integer... maxComments) {
		HttpEntity entity = null;
        try {
        	StringBuilder sb = new StringBuilder(Constants.REDDIT_BASE_URL);
    		if (mSubreddit != null) {
    			sb.append("/r/").append(mSubreddit.trim());
    		}
    		sb.append("/comments/")
        		.append(mThreadId)
        		.append("/z/").append(mMoreChildrenId).append("/.json?")
        		.append(mSettings.getCommentsSortByUrl()).append("&");
        	if (mJumpToCommentContext != 0)
        		sb.append("context=").append(mJumpToCommentContext).append("&");
        	
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
	
	private void replaceCommentsAtPositionUI(final Collection<ThingInfo> comments, final int position) {
		synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
			mActivity.mCommentsList.remove(position);
			mActivity.mCommentsList.addAll(position, comments);
		}
		mActivity.mCommentsAdapter.notifyDataSetChanged();
	}
	
	/**
	 * defer insertion of comment for adding at end of entire comments list
	 */
	private void deferCommentAppend(ThingInfo comment) {
		mDeferredAppendList.add(comment);
	}
	
	/**
	 * defer insertion of comment for "more" case
	 */
	private void deferCommentReplacement(ThingInfo comment) {
		mDeferredReplacementList.add(comment);
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
			
			mDeferredProcessingList.addAll(mDeferredProcessingTailList);
			
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
		
		// Add comment to deferred append/replace list
		if (isInsertingEntireThread())
			deferCommentAppend(ci);
		else
			deferCommentReplacement(ci);
		
		// Keep track of jump target
		if (isHasJumpTarget()) {
			if (!isFoundJumpTargetComment() && mJumpToCommentId.equals(ci.getId()))
				processJumpTarget(ci, insertedCommentIndex);
		}
		
		if (isHasJumpTarget()) {
			// if we have found the jump target, then we did the messy stuff already. just append to main processing list. 
			if (isFoundJumpTargetComment()) {
				mDeferredProcessingList.add(new DeferredCommentProcessing(ci, insertedCommentIndex));
			}
			// try to handle the context search, if we want context
			else if (mJumpToCommentContext > 0) {
				// any comment could be in the context; we don't know yet. so append to the high-priority "context" list
				mDeferredProcessingContextList.add(new DeferredCommentProcessing(ci, insertedCommentIndex));
				
				// we push overflow onto the low priority list, since overflow will end up above the jump target, off the top of the screen.
				// TODO don't use LinkedList.size()
				if (mDeferredProcessingContextList.size() > mJumpToCommentContext) {
					DeferredCommentProcessing overflow = mDeferredProcessingContextList.removeFirst();
					mDeferredProcessingTailList.add(overflow);
				}
			}
			// if no context search, then push comments to low priority list until we find the jump target comment
			else {
				mDeferredProcessingTailList.add(new DeferredCommentProcessing(ci, insertedCommentIndex));
			}
		}
		// if there is no jump target, there's just a single deferred-processing list to worry about.
		else {
			mDeferredProcessingList.add(new DeferredCommentProcessing(ci, insertedCommentIndex));
		}
			
		// Formatting that applies to all items, both real comments and "more" entries
		ci.setIndent(mIndentation + indentLevel);
		
		// Handle "more" entry
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
	
	private void processJumpTarget(ThingInfo comment, int commentIndex) {
		mJumpToCommentFoundIndex = (commentIndex - mJumpToCommentContext) > 0 ? (commentIndex - mJumpToCommentContext) : 0;
		
		mDeferredProcessingList.addAll(0, mDeferredProcessingContextList);
		mDeferredProcessingContextList.clear();
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
    
    /**
     * Call from UI Thread
     */
    private void insertCommentsUI() {
		synchronized (CommentsListActivity.COMMENT_ADAPTER_LOCK) {
			mActivity.mCommentsList.addAll(mDeferredAppendList);
		}
		mActivity.mCommentsAdapter.notifyDataSetChanged();
    }
	
	private void processCommentSlowSteps(ThingInfo comment) {
		if (comment.getBody_html() != null) {
        	CharSequence spanned = createSpanned(comment.getBody_html());
        	comment.setSpannedBody(spanned);
		}
		markdown.getURLs(comment.getBody(), comment.getUrls());
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
     * Process the slow steps and refresh each new comment
     */
	private void processDeferredComments() {
		new ProcessCommentsSubTask().execute(mDeferredProcessingList.toArray(new DeferredCommentProcessing[0]));
	}
	
	private void showOPThumbnail() {
		if (mOpThingInfo != null) {
	    	synchronized (mCurrentShowThumbnailsTaskLock) {
	    		if (mCurrentShowThumbnailsTask != null)
	    			mCurrentShowThumbnailsTask.cancel(true);
	    		mCurrentShowThumbnailsTask = new ShowThumbnailsTask(mActivity, mClient, null);
	    	}
	    	mCurrentShowThumbnailsTask.execute(new ThumbnailLoadAction(mOpThingInfo, null, 0));
		}
	}
	
    private void cleanupDeferred() {
    	mDeferredAppendList.clear();
    	mDeferredReplacementList.clear();
    	mDeferredProcessingList.clear();
    	mDeferredProcessingContextList.clear();
    	mDeferredProcessingTailList.clear();
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
		if (isInsertingEntireThread())
			insertCommentsUI();
		else if (!mDeferredReplacementList.isEmpty())
    		replaceCommentsAtPositionUI(mDeferredReplacementList, mPositionOffset);
		
		// have to wait till onPostExecute to do this, to ensure they've been inserted by UI thread
		processDeferredComments();
		
		if (Common.shouldLoadThumbnails(mActivity, mSettings))
			showOPThumbnail();

        // label the OP's comments with [S]
        mActivity.markSubmitterComments();
		
		if (mContentLength == -1)
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_OFF);
		else
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);
		
		if (success) {
			// We should clear any replies the user was composing.
			mActivity.setShouldClearReply(true);

			refreshVisibleCommentsUI();
			
			// Set title in android titlebar
			if (mThreadTitle != null)
				mActivity.setTitle(mThreadTitle + " : " + mSubreddit);
		} else {
			if (!isCancelled())
				Common.showErrorToast("Error downloading comments. Please try again.", Toast.LENGTH_LONG, mActivity);
		}

		synchronized (mCurrentDownloadCommentsTaskLock) {
			mCurrentDownloadCommentsTask = null;
		}
	}
	
	@Override
	public void onProgressUpdate(Long... progress) {
		if (mContentLength == -1)
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);
		else
			mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * (Window.PROGRESS_END-1) / (int) mContentLength);
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		publishProgress((Long) event.getNewValue());
	}

	private class ProcessCommentsSubTask extends AsyncTask<DeferredCommentProcessing, Integer, Void> {
		@Override
		public Void doInBackground(DeferredCommentProcessing... deferredCommentProcessingList) {
			for (final DeferredCommentProcessing deferredCommentProcessing : deferredCommentProcessingList) {
				processCommentSlowSteps(deferredCommentProcessing.comment);
				publishProgress(deferredCommentProcessing.commentIndex);
			}
			cleanupDeferred();
			return null;
		}
		
		@Override
		public void onProgressUpdate(Integer... commentsToShow) {
			for (Integer commentIndex : commentsToShow) {
				refreshDeferredCommentIfVisible(commentIndex);
			}
		}
	}
}

