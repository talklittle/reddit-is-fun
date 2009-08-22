package com.andrewshu.android.reddit;

import android.app.ProgressDialog;
import android.content.Context;

public class AutoResetProgressDialog extends ProgressDialog {

	public AutoResetProgressDialog(Context context) {
		super(context);
	}
	public AutoResetProgressDialog(Context context, int theme) {
		super(context, theme);
	}

	@Override
	public void onStart() {
		super.onStart();
		setProgress(0);
	}
}
