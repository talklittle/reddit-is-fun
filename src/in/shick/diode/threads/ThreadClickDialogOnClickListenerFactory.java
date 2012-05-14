package in.shick.diode.threads;

import android.view.View.OnClickListener;
import android.widget.CompoundButton;

import in.shick.diode.things.ThingInfo;

public interface ThreadClickDialogOnClickListenerFactory {
	OnClickListener getLoginOnClickListener();
	OnClickListener getLinkOnClickListener(ThingInfo thingInfo, boolean useExternalBrowser);
	OnClickListener getCommentsOnClickListener(ThingInfo thingInfo);
	CompoundButton.OnCheckedChangeListener getVoteUpOnCheckedChangeListener(ThingInfo thingInfo);
	CompoundButton.OnCheckedChangeListener getVoteDownOnCheckedChangeListener(ThingInfo thingInfo);
}