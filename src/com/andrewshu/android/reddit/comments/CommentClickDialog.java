package com.andrewshu.android.reddit.comments;

import com.andrewshu.android.reddit.R;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;

public class CommentClickDialog extends Dialog {

	public CommentClickDialog(Context context) {
		super(context);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.comment_click_dialog);

		LayoutParams params = getWindow().getAttributes(); 
		params.width = LayoutParams.FILL_PARENT;
		params.height = LayoutParams.FILL_PARENT;
		getWindow().setAttributes((android.view.WindowManager.LayoutParams) params); 
	}

}
