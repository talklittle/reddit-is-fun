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

import org.apache.http.impl.client.DefaultHttpClient;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * This is an example of implementing an application service that will run in
 * response to an alarm, allowing us to move long duration work out of an
 * intent receiver.
 * 
 * @see AlarmService
 * @see AlarmService_Alarm
 */
public class EnvelopeService extends Service {
    
	private Context mContext;
	
	NotificationManager mNM;
    private RedditSettings mSettings = new RedditSettings();
    private DefaultHttpClient mClient = Common.getGzipHttpClient();

    @Override
    public void onCreate() {
    	mContext = getApplicationContext();
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Common.loadRedditPreferences(mContext, mSettings, mClient);
        new PeekEnvelopeServiceTask(mContext, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    private class PeekEnvelopeServiceTask extends Common.PeekEnvelopeTask {
    	public PeekEnvelopeServiceTask(Context context, DefaultHttpClient client, String mailNotificationStyle) {
    		super(context, client, mailNotificationStyle);
    	}
    	@Override
    	public void onPostExecute(Integer count) {
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
}

