package com.andrewshu.android.reddit.common;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class FormValidation {

    public static boolean validateComposeMessageInputFields(
    		Context context,
    		final EditText composeDestination,
			final EditText composeSubject,
			final EditText composeText,
			final EditText composeCaptcha
	) {
		// reddit.com performs these sanity checks too.
		if ("".equals(composeDestination.getText().toString().trim())) {
			Toast.makeText(context, "please enter a username", Toast.LENGTH_LONG).show();
			return false;
		}
		if ("".equals(composeSubject.getText().toString().trim())) {
			Toast.makeText(context, "please enter a subject", Toast.LENGTH_LONG).show();
			return false;
		}
		if ("".equals(composeText.getText().toString().trim())) {
			Toast.makeText(context, "you need to enter a message", Toast.LENGTH_LONG).show();
			return false;
		}
		if (composeCaptcha.getVisibility() == View.VISIBLE && "".equals(composeCaptcha.getText().toString().trim())) {
			Toast.makeText(context, "", Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}


}
