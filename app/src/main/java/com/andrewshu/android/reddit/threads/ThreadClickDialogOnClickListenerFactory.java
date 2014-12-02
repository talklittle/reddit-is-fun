package com.andrewshu.android.reddit.threads;

import android.view.View.OnClickListener;
import android.widget.CompoundButton;

import com.andrewshu.android.reddit.things.ThingInfo;

public interface ThreadClickDialogOnClickListenerFactory {
	OnClickListener getLoginOnClickListener();
	OnClickListener getLinkOnClickListener(ThingInfo thingInfo, boolean useExternalBrowser);
	OnClickListener getCommentsOnClickListener(ThingInfo thingInfo);
	CompoundButton.OnCheckedChangeListener getVoteUpOnCheckedChangeListener(ThingInfo thingInfo);
	CompoundButton.OnCheckedChangeListener getVoteDownOnCheckedChangeListener(ThingInfo thingInfo);
}