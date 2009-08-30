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
    NotificationManager mNM;
    private RedditSettings mSettings = new RedditSettings();
    private DefaultHttpClient mClient = Common.createGzipHttpClient();

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Common.loadRedditPreferences(this, mSettings, mClient);
        new PeekEnvelopeServiceTask(this, mClient, mSettings.mailNotificationStyle).execute();
    }
    
    private class PeekEnvelopeServiceTask extends Common.PeekEnvelopeTask {
    	public PeekEnvelopeServiceTask(Context context, DefaultHttpClient client, String mailNotificationStyle) {
    		super(context, client, mailNotificationStyle);
    	}
    	@Override
    	public void onPostExecute(Boolean hasMail) {
    		super.onPostExecute(hasMail);
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

