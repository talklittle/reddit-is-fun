package com.andrewshu.android.reddit;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

class ErrorToaster implements Runnable {
	private CharSequence _mError;
	private int _mDuration;
	private LayoutInflater _mInflater;
	private RedditSettings _mSettings;
	
	ErrorToaster(CharSequence error, int duration, RedditSettings settings) {
		_mError = error;
		_mDuration = duration;
		_mInflater = (LayoutInflater) settings.activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		_mSettings = settings;
	}
	
	public void run() {
		if (_mSettings.isAlive) {
			Toast t = new Toast(_mSettings.activity);
			t.setDuration(_mDuration);
			View v = _mInflater.inflate(R.layout.error_toast, null);
			TextView errorMessage = (TextView) v.findViewById(R.id.errorMessage);
			errorMessage.setText(_mError);
			t.setView(v);
			t.show();
		}
	}
}
