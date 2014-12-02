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

package com.andrewshu.android.reddit.mail;

import org.apache.http.client.HttpClient;

import com.andrewshu.android.reddit.common.RedditIsFunHttpClientFactory;
import com.andrewshu.android.reddit.settings.RedditSettings;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

/**
 * This is an example of implementing an application service that will run in
 * response to an alarm, allowing us to move long duration work out of an
 * intent receiver.
 * 
 * @see AlarmService
 * @see AlarmService_Alarm
 */
public class EnvelopeService extends Service {
    NotificationManager mNM;
    private RedditSettings mSettings = new RedditSettings();
    private HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mSettings.loadRedditPreferences(this, mClient);
        new PeekEnvelopeServiceTask(this, mClient, mSettings.getMailNotificationStyle()).execute();
    }
    
    private class PeekEnvelopeServiceTask extends PeekEnvelopeTask {
    	public PeekEnvelopeServiceTask(Context context, HttpClient client, String mailNotificationStyle) {
    		super(context, client, mailNotificationStyle);
    	}
    	@Override
    	public void onPostExecute(Object count) {
    		super.onPostExecute(count);
    		EnvelopeService.this.stopSelf();
    	}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * This is the object that receives interactions from clients.  See RemoteService
     * for a more complete example.
     */
    private final IBinder mBinder = new Binder() {
        @Override
		protected boolean onTransact(int code, Parcel data, Parcel reply,
		        int flags) throws RemoteException {
            return super.onTransact(code, data, reply, flags);
        }
    };
    
    public static void resetAlarm(Context context, long interval) {
        // Create an IntentSender that will launch our service, to be scheduled
        // with the alarm manager.
        PendingIntent alarmSender = PendingIntent.getService(context, 0, new Intent(context, EnvelopeService.class), 0);
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        am.cancel(alarmSender);
        if (interval != 0)
        	am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), interval, alarmSender);
    }
}

