package com.andrewshu.android.reddit;
/* package com.wilson.android.library;
 * by James A Wilson, stackoverflow
 * http://stackoverflow.com/questions/541966/android-how-do-i-do-a-lazy-load-of-images-in-listview
 */

/*
 Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.    
*/
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class BitmapManager {
	
	private static final String TAG = "BitmapManager";
	
    private Map<String, SoftReference<Bitmap>> mCache;
    private DefaultHttpClient mClient = Common.getGzipHttpClient();
    
    public BitmapManager() {
    	mCache = new HashMap<String, SoftReference<Bitmap>>();
    }

    public Bitmap fetchBitmap(String urlString) {
    	SoftReference<Bitmap> ref = mCache.get(urlString);
    	if (ref != null && ref.get() != null) {
    		return ref.get();
    	}

    	if (Constants.LOGGING) Log.d(TAG, "image url:" + urlString);
    	
    	try {
    		Bitmap bitmap = readBitmapFromNetwork(urlString);
    		mCache.put(urlString, new SoftReference<Bitmap>(bitmap));
//    		if (Constants.LOGGING) Log.d(this.getClass().getSimpleName(), "got a thumbnail drawable: " + drawable.getBounds() + ", "
//    				+ drawable.getIntrinsicHeight() + "," + drawable.getIntrinsicWidth() + ", "
//    				+ drawable.getMinimumHeight() + "," + drawable.getMinimumWidth());
    		return bitmap;
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, "fetchBitmap failed", e);
    		return null;
    	}
    }

    public void fetchBitmapOnThread(final String urlString, final ImageView imageView) {
    	fetchBitmapOnThread(urlString, imageView, null, null);
    }
    
    public void fetchBitmapOnThread(final String urlString, final ImageView imageView, final ProgressBar indeterminateProgressBar, final Activity act) {
    	SoftReference<Bitmap> ref = mCache.get(urlString);
    	if (ref != null && ref.get() != null) {
    		imageView.setImageBitmap(ref.get());
	    	return;
    	}

    	final Runnable progressBarShow = new Runnable() {
    		public void run() {
    			if (indeterminateProgressBar != null) {
    				imageView.setVisibility(View.GONE);
    				indeterminateProgressBar.setVisibility(View.VISIBLE);
    			}
    		}
    	};
    	final Runnable progressBarHide = new Runnable() {
    		public void run() {
    			if (indeterminateProgressBar != null) {
    				indeterminateProgressBar.setVisibility(View.GONE);
    				imageView.setVisibility(View.VISIBLE);
    			}
    		}
    	};

    	final Handler handler = new Handler() {
    		@Override
    		public void handleMessage(Message message) {
    			if (indeterminateProgressBar != null && act != null)
    				act.runOnUiThread(progressBarHide);
    			imageView.setImageBitmap((Bitmap) message.obj);
    		}
    	};

    	Thread thread = new Thread() {
    		@Override
    		public void run() {
    			if (indeterminateProgressBar != null && act != null)
    				act.runOnUiThread(progressBarShow);
    			Bitmap bitmap = fetchBitmap(urlString);
    			Message message = handler.obtainMessage(1, bitmap);
    			handler.sendMessage(message);
    		}
    	};
    	thread.start();
    }

    private InputStream fetch(String urlString) throws MalformedURLException, IOException {
    	HttpGet request = new HttpGet(urlString);
    	HttpResponse response = mClient.execute(request);
    	return response.getEntity().getContent();
    }
    
	/**
	 * http://ballardhack.wordpress.com/2010/04/10/loading-images-over-http-on-a-separate-thread-on-android/
	 * Convenience method to retrieve a bitmap image from
	 * a URL over the network. The built-in methods do
	 * not seem to work, as they return a FileNotFound
	 * exception.
	 *
	 * Note that this does not perform any threading --
	 * it blocks the call while retrieving the data.
	 *
	 * @param url The URL to read the bitmap from.
	 * @return A Bitmap image or null if an error occurs.
	 */
	public Bitmap readBitmapFromNetwork( String url ) {
		InputStream is = null;
		BufferedInputStream bis = null;
		Bitmap bmp = null;
		try {
			is = fetch(url);
			bis = new BufferedInputStream(is);
			bmp = BitmapFactory.decodeStream(bis);
		} catch (MalformedURLException e) {
			Log.e(TAG, "Bad ad URL", e);
		} catch (IOException e) {
			Log.e(TAG, "Could not get remote ad image", e);
		} finally {
			try {
				if( is != null )
					is.close();
				if( bis != null )
					bis.close();
			} catch (IOException e) {
				Log.w(TAG, "Error closing stream.");
			}
		}
		return bmp;
	}

	public void clearCache() {
		mCache.clear();
	}
}
