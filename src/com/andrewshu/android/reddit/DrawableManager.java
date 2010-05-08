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
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class DrawableManager {
    private Map<String, Drawable> drawableMap;
    private DefaultHttpClient mClient = Common.getGzipHttpClient();
    
    public DrawableManager() {
    	drawableMap = new HashMap<String, Drawable>();
    }

    public Drawable fetchDrawable(String urlString) {
    	if (drawableMap.containsKey(urlString)) {
    		return drawableMap.get(urlString);
    	}

    	if (Constants.LOGGING) Log.d(this.getClass().getSimpleName(), "image url:" + urlString);
    	try {
    		InputStream is = fetch(urlString);
    		Drawable drawable = Drawable.createFromStream(is, "src");
    		drawableMap.put(urlString, drawable);
    		if (Constants.LOGGING) Log.d(this.getClass().getSimpleName(), "got a thumbnail drawable: " + drawable.getBounds() + ", "
    				+ drawable.getIntrinsicHeight() + "," + drawable.getIntrinsicWidth() + ", "
    				+ drawable.getMinimumHeight() + "," + drawable.getMinimumWidth());
    		return drawable;
    	} catch (MalformedURLException e) {
    		if (Constants.LOGGING) Log.e(this.getClass().getSimpleName(), "fetchDrawable failed", e);
    		return null;
    	} catch (IOException e) {
    		if (Constants.LOGGING) Log.e(this.getClass().getSimpleName(), "fetchDrawable failed", e);
    		return null;
    	}
    }

    public void fetchDrawableOnThread(final String urlString, final ImageView imageView) {
    	fetchDrawableOnThread(urlString, imageView, null, null);
    }
    
    public void fetchDrawableOnThread(final String urlString, final ImageView imageView, final ProgressBar indeterminateProgressBar, final Activity act) {
    	if (drawableMap.containsKey(urlString)) {
    		imageView.setImageDrawable(drawableMap.get(urlString));
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
    			imageView.setImageDrawable((Drawable) message.obj);
    		}
    	};

    	Thread thread = new Thread() {
    		@Override
    		public void run() {
    			if (indeterminateProgressBar != null && act != null)
    				act.runOnUiThread(progressBarShow);
    			Drawable drawable = fetchDrawable(urlString);
    			Message message = handler.obtainMessage(1, drawable);
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

}
