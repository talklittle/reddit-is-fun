package com.andrewshu.android.reddit.threads;

import android.app.Activity;
import android.view.View.OnClickListener;

public interface ThumbnailOnClickListenerFactory {
	OnClickListener getThumbnailOnClickListener(String jumpToThreadId, String url, String threadUrl, Activity activity);
}