package com.andrewshu.android.reddit.settings;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class SettingsBackupAgent extends BackupAgentHelper {

	static final String PREFS = "com.andrewshu.android.reddit_preferences";
	
	static final String PREFS_BACKUP_KEY = "preferences";
	
	@Override
	public void onCreate() {
		SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, PREFS);
		addHelper(PREFS_BACKUP_KEY, helper);
	}
}
