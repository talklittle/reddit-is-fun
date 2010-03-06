/*
 * Copyright 2009 Andrew Shu
 *
 * This file is part of "reddit is fun".
 *
 * "reddit is fun" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "reddit is fun" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "reddit is fun".  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrewshu.android.reddit;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class RedditPreferencesPage extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, 
        Preference.OnPreferenceClickListener {
	
	private RedditSettings mSettings = new RedditSettings();
	private PendingIntent mAlarmSender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create an IntentSender that will launch our service, to be scheduled
        // with the alarm manager.
        mAlarmSender = PendingIntent.getService(this, 0, new Intent(getApplicationContext(), EnvelopeService.class), 0);

        
        // Load the XML preferences file
        addPreferencesFromResource(R.xml.reddit_preferences);

        Preference e;
        
        e = findPreference(Constants.PREF_HOMEPAGE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getPreferenceScreen().getSharedPreferences().getString(Constants.PREF_HOMEPAGE, null));
        
        e = findPreference(Constants.PREF_THEME);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualThemeName(
        		getPreferenceScreen().getSharedPreferences()
                .getString(Constants.PREF_THEME, null)));
        
        e = findPreference(Constants.PREF_ROTATION);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualRotationName(
        		getPreferenceScreen().getSharedPreferences()
                .getString(Constants.PREF_ROTATION, null)));
        
        e = findPreference(Constants.PREF_MAIL_NOTIFICATION_STYLE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualMailNotificationStyleName(
        		getPreferenceScreen().getSharedPreferences()
        		.getString(Constants.PREF_MAIL_NOTIFICATION_STYLE, null)));
        
        e = findPreference(Constants.PREF_MAIL_NOTIFICATION_SERVICE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualMailNotificationServiceName(
        		getPreferenceScreen().getSharedPreferences()
        		.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, null)));
        
        e = findPreference(Constants.PREF_ON_CLICK);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualOnClickName(getPreferenceScreen().getSharedPreferences()
        		.getString(Constants.PREF_ON_CLICK, null)));
        
//        e = findPreference(BrowserSettings.PREF_TEXT_SIZE);
//        e.setOnPreferenceChangeListener(this);
//        e.setSummary(getVisualTextSizeName(
//                getPreferenceScreen().getSharedPreferences()
//                .getString(BrowserSettings.PREF_TEXT_SIZE, null)) );
        
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	Common.loadRedditPreferences(this, mSettings, null);
    	setRequestedOrientation(mSettings.rotation);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        // sync the shared preferences back to BrowserSettings
//        BrowserSettings.getInstance().syncSharedPreferences(
//                getPreferenceScreen().getSharedPreferences());
        
        Common.saveRedditPreferences(this, mSettings);
    }

    public boolean onPreferenceChange(Preference pref, Object objValue) {
//        if (pref.getKey().equals(BrowserSettings.PREF_EXTRAS_RESET_DEFAULTS)) {
//            Boolean value = (Boolean) objValue;
//            if (value.booleanValue() == true) {
//                finish();
//            }
//        }
    	if (pref.getKey().equals(Constants.PREF_HOMEPAGE)) {
    		pref.setSummary((String) objValue);
            mSettings.setHomepage((String) objValue);
            return true;
    	} else if (pref.getKey().equals(Constants.PREF_THEME)) {
            pref.setSummary(getVisualThemeName((String) objValue));
            mSettings.setTheme(RedditSettings.Theme.valueOf((String) objValue));
            return true;
    	} else if (pref.getKey().equals(Constants.PREF_ROTATION)) {
            pref.setSummary(getVisualRotationName((String) objValue));
            mSettings.setRotation(RedditSettings.Rotation.valueOf((String) objValue));
            return true;
        } else if (pref.getKey().equals(Constants.PREF_MAIL_NOTIFICATION_STYLE)) {
            pref.setSummary(getVisualMailNotificationStyleName((String) objValue));
            mSettings.setMailNotificationStyle((String) objValue);
            Preference servicePref = findPreference(Constants.PREF_MAIL_NOTIFICATION_SERVICE);
            if (Constants.PREF_MAIL_NOTIFICATION_STYLE_OFF.equals(objValue)) {
            	// Remove any current notifications
            	NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        		notificationManager.cancel(Constants.NOTIFICATION_HAVE_MAIL);
        		// Disable the service too
        		servicePref.setEnabled(false);
                AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
                am.cancel(mAlarmSender);
                // Tell the user about what we did.
                Toast.makeText(this, R.string.mail_notification_unscheduled,
                        Toast.LENGTH_LONG).show();
            } else {
            	// Enable the service preference
            	if (!servicePref.isEnabled()) {
	            	servicePref.setEnabled(true);
	            	String servicePrefValue = getPreferenceScreen().getSharedPreferences()
	    				.getString(Constants.PREF_MAIL_NOTIFICATION_SERVICE, null);
	            	// Enable the service alarm if it's not set to off
	            	if (!Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF.equals(servicePrefValue))
	            		onPreferenceChange(servicePref, servicePrefValue);
            	}
            }
            return true;
        } else if (pref.getKey().equals(Constants.PREF_MAIL_NOTIFICATION_SERVICE)) {
        	pref.setSummary(getVisualMailNotificationServiceName((String) objValue));
        	mSettings.setMailNotificationService((String) objValue);
        	if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF.equals(objValue)) {
        		// Cancel the service
                AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
                am.cancel(mAlarmSender);
                // Tell the user about what we did.
                Toast.makeText(this, R.string.mail_notification_unscheduled,
                        Toast.LENGTH_LONG).show();
        	} else {
        		long durationMillis;
        		if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_5MIN.equals(objValue)) {
        			durationMillis = 5 * 60 * 1000;
        		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_30MIN.equals(objValue)) {
        			durationMillis = 30 * 60 * 1000;
        		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_1HOUR.equals(objValue)) {
        			durationMillis = 1 * 3600 * 1000;
        		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_6HOURS.equals(objValue)) {
        			durationMillis = 6 * 3600 * 1000;
        		} else /* if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_1DAY.equals(objValue)) */ {
        			durationMillis = 24 * 3600 * 1000;
        		}
                long firstTime = SystemClock.elapsedRealtime();
                // Schedule the alarm!
                AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
                am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                firstTime, durationMillis, mAlarmSender);
                // Tell the user about what we did.
                Toast.makeText(this, "Reddit mail will be checked: " +
                		getVisualMailNotificationServiceName((String) objValue),
                        Toast.LENGTH_LONG).show();
        	}
        	return true;
        } else if (pref.getKey().equals(Constants.PREF_ON_CLICK))
        {
        	pref.setSummary(getVisualOnClickName((String) objValue));
        	mSettings.setOnClickAction((String)objValue);
        	return true;
        }
        
        return false;
    }
    
    public boolean onPreferenceClick(Preference pref) {
//        if (pref.getKey().equals(BrowserSettings.PREF_GEARS_SETTINGS)) {
//            List<Plugin> loadedPlugins = WebView.getPluginList().getList();
//            for(Plugin p : loadedPlugins) {
//                if (p.getName().equals("gears")) {
//                    p.dispatchClickEvent(this);
//                    return true;
//                }
//            }
//            
//        }
        return true;
    }

    private CharSequence getVisualThemeName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_theme_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_theme_values);
        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }
        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }
        return "";
    }
    
    private CharSequence getVisualRotationName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_rotation_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_rotation_values);
        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }
        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }
        return "";
    }
    
    private CharSequence getVisualMailNotificationStyleName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_mail_notification_style_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_mail_notification_style_values);
        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }
        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }
        return "";
    }
    private CharSequence getVisualMailNotificationServiceName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_mail_notification_service_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_mail_notification_service_values);
        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }
        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }
        return "";
    }
    
    private CharSequence getVisualOnClickName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_click_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_click_values);
        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }
        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }
        return "";
    }
}
