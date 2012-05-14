package in.shick.diode.comments;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Display;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;

import in.shick.diode.R;
import in.shick.diode.settings.RedditSettings;

public class CommentClickDialog extends Dialog {

	public CommentClickDialog(Context context, RedditSettings settings) {
		super(context, settings.getDialogNoTitleTheme());
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.comment_click_dialog);
		
		Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		LayoutParams params = getWindow().getAttributes();
		params.width = LayoutParams.FILL_PARENT;
		if (display.getOrientation() == Configuration.ORIENTATION_LANDSCAPE)
			params.height = LayoutParams.FILL_PARENT;
		getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
		
		setCanceledOnTouchOutside(true);
	}

}
