package com.andrewshu.android.reddit;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class CommentsListView extends ListView {
	private CommentsListActivity mActivity = null;
	
	public CommentsListView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	public CommentsListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public CommentsListView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public void setCommentsListActivity(CommentsListActivity activity) {
		this.mActivity = activity;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (mActivity != null) {
			mActivity.jumpToComment();
		}
	}
}

