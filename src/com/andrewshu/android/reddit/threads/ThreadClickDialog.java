package com.andrewshu.android.reddit.threads;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;

import com.andrewshu.android.reddit.R;

public class ThreadClickDialog extends Dialog {

	public ThreadClickDialog(Context context) {
		super(context);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.thread_click_dialog);

		LayoutParams params = getWindow().getAttributes(); 
		params.width = LayoutParams.FILL_PARENT;
		params.height = LayoutParams.FILL_PARENT;
		getWindow().setAttributes((android.view.WindowManager.LayoutParams) params); 
	}

}
