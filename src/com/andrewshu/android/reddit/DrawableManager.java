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
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

public class DrawableManager {
    private Map<String, SoftReference<Drawable>> drawableMap;
    
    public DrawableManager() {
    	drawableMap = new HashMap<String, SoftReference<Drawable>>();
    }

    public Drawable fetchDrawable(String urlString) {
    	SoftReference<Drawable> drawableRef = drawableMap.get(urlString);
    	if (drawableRef != null) {
    		Drawable drawable = drawableRef.get();
    		if (drawable != null)
    			return drawable;
    		// Reference has expired so remove the key from drawableMap
    		drawableMap.remove(urlString);
    	}

    	if (Constants.LOGGING) Log.d(this.getClass().getSimpleName(), "image url:" + urlString);
    	try {
    		InputStream is = fetch(urlString);
    		Drawable drawable = Drawable.createFromStream(is, "src");
    		drawableRef = new SoftReference<Drawable>(drawable);
    		drawableMap.put(urlString, drawableRef);
    		if (Constants.LOGGING) Log.d(this.getClass().getSimpleName(), "got a thumbnail drawable: " + drawable.getBounds() + ", "
    				+ drawable.getIntrinsicHeight() + "," + drawable.getIntrinsicWidth() + ", "
    				+ drawable.getMinimumHeight() + "," + drawable.getMinimumWidth());
    		return drawableRef.get();
    	} catch (MalformedURLException e) {
    		if (Constants.LOGGING) Log.e(this.getClass().getSimpleName(), "fetchDrawable failed", e);
    		return null;
    	} catch (IOException e) {
    		if (Constants.LOGGING) Log.e(this.getClass().getSimpleName(), "fetchDrawable failed", e);
    		return null;
    	}
    }

    public void fetchDrawableOnThread(final String urlString, final ImageView imageView) {
    	SoftReference<Drawable> drawableRef = drawableMap.get(urlString);
    	if (drawableRef != null) {
    		Drawable drawable = drawableRef.get();
    		if (drawable != null) {
	    		imageView.setImageDrawable(drawableRef.get());
	    		return;
    		}
    		// Reference has expired so remove the key from drawableMap
    		drawableMap.remove(urlString);
    	}

    	final Handler handler = new Handler() {
    		@Override
    		public void handleMessage(Message message) {
    			imageView.setImageDrawable((Drawable) message.obj);
    		}
    	};

    	Thread thread = new Thread() {
    		@Override
    		public void run() {
    			//TODO : set imageView to a "pending" image
    			Drawable drawable = fetchDrawable(urlString);
    			Message message = handler.obtainMessage(1, drawable);
    			handler.sendMessage(message);
    		}
    	};
    	thread.start();
    }

    private InputStream fetch(String urlString) throws MalformedURLException, IOException {
    	DefaultHttpClient httpClient = new DefaultHttpClient();
    	HttpGet request = new HttpGet(urlString);
    	HttpResponse response = httpClient.execute(request);
    	return response.getEntity().getContent();
    }

}
