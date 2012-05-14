package in.shick.diode.threads;

import in.shick.diode.things.ThingInfo;

import android.app.Activity;
import android.view.View.OnClickListener;

public interface ThumbnailOnClickListenerFactory {
	OnClickListener getThumbnailOnClickListener(ThingInfo threadThingInfo, Activity activity);
}