package com.andrewshu.android.reddit;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class RedditPreferencesPage extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, 
        Preference.OnPreferenceClickListener {
	
	private RedditSettings mSettings = new RedditSettings();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the XML preferences file
        addPreferencesFromResource(R.xml.reddit_preferences);

        Preference e;
        
        e = findPreference(Constants.PREF_THEME);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualThemeName(
        		getPreferenceScreen().getSharedPreferences()
                .getString(Constants.PREF_THEME, null)));
        
        e = findPreference(Constants.PREF_MAIL_NOTIFICATION_STYLE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualMailNotificationStyleName(
        		getPreferenceScreen().getSharedPreferences()
        		.getString(Constants.PREF_MAIL_NOTIFICATION_STYLE, null)));
        
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
        if (pref.getKey().equals(Constants.PREF_THEME)) {
            pref.setSummary(getVisualThemeName((String) objValue));
            mSettings.setTheme(RedditSettings.Theme.valueOf((String) objValue));
            return true;
        } else if (pref.getKey().equals(Constants.PREF_MAIL_NOTIFICATION_STYLE)) {
            pref.setSummary(getVisualMailNotificationStyleName((String) objValue));
            mSettings.setMailNotificationStyle(RedditSettings.MailNotificationStyle.valueOf((String) objValue));
            if (Constants.PREF_MAIL_NOTIFICATION_STYLE_OFF.equals(objValue)) {
            	// Remove any current notifications
            	NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        		notificationManager.cancel(Constants.NOTIFICATION_HAVE_MAIL);
            }
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
}
