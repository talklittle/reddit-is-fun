package com.andrewshu.android.reddit.mail;

import com.andrewshu.android.reddit.common.Constants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootAlarmSetter extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences sessionPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		String mailNotificationPref = sessionPrefs.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF);
		
		if (!Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF.equals(mailNotificationPref)) {
			Intent envelopeIntent = new Intent(context, EnvelopeService.class);
			context.startService(envelopeIntent);
		}
	}

}
