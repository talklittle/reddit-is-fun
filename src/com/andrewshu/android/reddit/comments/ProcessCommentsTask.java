package com.andrewshu.android.reddit.comments;

import java.util.LinkedList;

import android.os.AsyncTask;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.util.Util;
import com.andrewshu.android.reddit.markdown.Markdown;
import com.andrewshu.android.reddit.things.ThingInfo;

public class ProcessCommentsTask extends AsyncTask<Void, Integer, Void> {
	
	private static final String TAG = "ProcessCommentsSubTask";
	
    private final CommentsListActivity mActivity;
	private final Markdown markdown = new Markdown();

    /**
     * List holding the deferred processing list starting from the first object to handle
     */
    private final LinkedList<DeferredCommentProcessing> mDeferredProcessingList = new LinkedList<DeferredCommentProcessing>();
    /**
     * Helper list holding the "context" comments, given first priority for processing since they'll be shown first.
     */
    private final LinkedList<DeferredCommentProcessing> mDeferredProcessingHighPriorityList = new LinkedList<DeferredCommentProcessing>();
    /**
     * Helper list holding the tail of the deferred processing list, to be appended to mDeferredProcessingList
     */
    private final LinkedList<DeferredCommentProcessing> mDeferredProcessingLowPriorityList = new LinkedList<DeferredCommentProcessing>();
    
	public static class DeferredCommentProcessing {
		public int commentIndex;
		public ThingInfo comment;
		public DeferredCommentProcessing(ThingInfo comment, int commentIndex) {
			this.comment = comment;
			this.commentIndex = commentIndex;
		}
	}
    
	/**
	 * Constructor
	 */
	public ProcessCommentsTask(CommentsListActivity commentsListActivity) {
		this.mActivity = commentsListActivity;
	}
	
	public void addDeferred(DeferredCommentProcessing deferredCommentProcessing) {
		mDeferredProcessingList.add(deferredCommentProcessing);
	}
	
	public void addDeferredHighPriority(DeferredCommentProcessing deferredCommentProcessing) {
		mDeferredProcessingHighPriorityList.add(deferredCommentProcessing);
	}
	
	public void addDeferredLowPriority(DeferredCommentProcessing deferredCommentProcessing) {
		mDeferredProcessingLowPriorityList.add(deferredCommentProcessing);
	}
	
	public void moveHighPriorityOverflowToLowPriority(int highPriorityMaxSize) {
		if (mDeferredProcessingHighPriorityList.size() > highPriorityMaxSize) {
			DeferredCommentProcessing overflow = mDeferredProcessingHighPriorityList.removeFirst();
			mDeferredProcessingLowPriorityList.add(overflow);
		}

	}
	
	public void mergeHighPriorityListToMainList() {
		mDeferredProcessingList.addAll(0, mDeferredProcessingHighPriorityList);
		mDeferredProcessingHighPriorityList.clear();
	}
	
	public void mergeLowPriorityListToMainList() {
		mDeferredProcessingList.addAll(mDeferredProcessingLowPriorityList);
		mDeferredProcessingLowPriorityList.clear();
	}
	
	@Override
	public Void doInBackground(Void... v) {
		for (final DeferredCommentProcessing deferredCommentProcessing : mDeferredProcessingList) {
			processCommentSlowSteps(deferredCommentProcessing.comment);
			publishProgress(deferredCommentProcessing.commentIndex);
		}
		cleanupQueues();
		return null;
	}
	
	private void cleanupQueues() {
    	mDeferredProcessingList.clear();
    	mDeferredProcessingHighPriorityList.clear();
    	mDeferredProcessingLowPriorityList.clear();
	}
	
	@Override
	public void onProgressUpdate(Integer... commentsToShow) {
		for (Integer commentIndex : commentsToShow) {
			refreshDeferredCommentIfVisibleUI(commentIndex);
		}
	}
	
	private void processCommentSlowSteps(ThingInfo comment) {
		if (comment.getBody_html() != null) {
        	CharSequence spanned = createSpanned(comment.getBody_html());
        	comment.setSpannedBody(spanned);
		}
		markdown.getURLs(comment.getBody(), comment.getUrls());
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
    
	private void refreshDeferredCommentIfVisibleUI(final int commentIndex) {
		if (isPositionVisibleUI(commentIndex))
			refreshCommentUI(commentIndex);
	}
	
    private void refreshCommentUI(int commentIndex) {
		refreshCommentBodyTextViewUI(commentIndex);
		refreshCommentSubmitterUI(commentIndex);
    }
    
    private void refreshCommentBodyTextViewUI(int commentIndex) {
    	int positionOnScreen = commentIndex - mActivity.getListView().getFirstVisiblePosition();
		View v = mActivity.getListView().getChildAt(positionOnScreen);
		if (v != null) {
			View bodyTextView = v.findViewById(R.id.body);
			if (bodyTextView != null) {
				((TextView) bodyTextView).setText(mActivity.mCommentsList.get(commentIndex).getSpannedBody());
			}
		}
    }

    private void refreshCommentSubmitterUI(int commentIndex) {
    	int positionOnScreen = commentIndex - mActivity.getListView().getFirstVisiblePosition();
		View v = mActivity.getListView().getChildAt(positionOnScreen);
		if (v != null) {
			View submitterTextView = v.findViewById(R.id.submitter);
			if (submitterTextView != null) {
				ThingInfo comment = mActivity.mCommentsList.get(commentIndex);
				if (comment.getSSAuthor() != null)
					((TextView) submitterTextView).setText(comment.getSSAuthor());
				else
					((TextView) submitterTextView).setText(comment.getAuthor());
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
    
}