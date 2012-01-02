package com.andrewshu.android.reddit.threads;

import com.andrewshu.android.reddit.things.ThingInfo;

import android.app.Activity;
import android.view.View.OnClickListener;

public interface ThumbnailOnClickListenerFactory {
	OnClickListener getThumbnailOnClickListener(ThingInfo threadThingInfo, Activity activity);
}