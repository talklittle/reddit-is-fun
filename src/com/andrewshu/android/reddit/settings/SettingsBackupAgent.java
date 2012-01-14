package com.andrewshu.android.reddit.settings;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

public class SettingsBackupAgent extends BackupAgentHelper {

	static final String PREFS = "com.andrewshu.android.reddit_preferences";
	
	static final String PREFS_BACKUP_KEY = "preferences";
	
	@Override
	public void onCreate() {
		SettingsBackupHelper helper = new SettingsBackupHelper(this, PREFS);
		addHelper(PREFS_BACKUP_KEY, helper);
	}
	
	private class SettingsBackupHelper extends SharedPreferencesBackupHelper {
		private Context context;
		
		@Override
		public void performBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			String username = sharedPreferences.getString("username", null);
			String sessionValue = sharedPreferences.getString("reddit_sessionValue", null);
			String sessionDomain = sharedPreferences.getString("reddit_sessionDomain", null);
			String sessionPath = sharedPreferences.getString("reddit_sessionPath", null);
			long sessionExpiryDate = sharedPreferences.getLong("reddit_sessionExpiryDate", -1);
			
			Editor editor = sharedPreferences.edit();
			
			try {	
				editor.remove("username").remove("reddit_sessionValue").remove("reddit_sessionDomain").remove("reddit_sessionPath").remove("reddit_sessionExpiryDate");
				editor.commit();
			
				super.performBackup(oldState, data, newState);
			}
			finally {
				if(username != null)
					editor.putString("username", username);
				if(sessionValue != null)
					editor.putString("reddit_sessionValue", sessionValue);
				if(sessionDomain != null)
					editor.putString("reddit_sessionDomain", sessionDomain);
				if(sessionPath != null)
					editor.putString("reddit_sessionPath", sessionPath);
				if(sessionExpiryDate != -1)
					editor.putLong("reddit_sessionExpiryDate", sessionExpiryDate);
				
				editor.commit();
			}
		}
		
		public SettingsBackupHelper(Context context, String... prefGroups) {
			super(context, prefGroups);
			
			this.context = context;
		}
	}
}
