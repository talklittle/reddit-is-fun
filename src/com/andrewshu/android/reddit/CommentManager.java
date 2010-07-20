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
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class CommentManager {
    
	/**
	 * @param bodyHtml escaped HTML (like in reddit Thing's body_html)
	 * @return
	 */
    public CharSequence createSpanned(String bodyHtml) {
    	try {
    		// get unescaped HTML
    		bodyHtml = Html.fromHtml(bodyHtml).toString();
    		// fromHtml doesn't support all HTML tags. convert <code> and <pre>
    		bodyHtml = Util.convertHtmlTags(bodyHtml);
    		
    		Spanned body = Html.fromHtml(bodyHtml);
    		// remove last 2 newline characters
    		if (body.length() > 2)
    			return body.subSequence(0, body.length()-2);
    		else
    			return Constants.EMPTY_STRING;
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(this.getClass().getSimpleName(), "createSpanned failed", e);
    		return null;
    	}
    }

    public void createSpannedOnThread(final String bodyHtml, final TextView textView, final ThingInfo thingInfo) {
    	createSpannedOnThread(bodyHtml, textView, thingInfo, null, null);
    }
    
    public void createSpannedOnThread(final String bodyHtml, final TextView textView, final ThingInfo thingInfo,
    		final ProgressBar indeterminateProgressBar, final Activity progressActivity) {

    	final Runnable progressBarShow = new Runnable() {
    		public void run() {
    			if (indeterminateProgressBar != null) {
    				textView.setVisibility(View.GONE);
    				indeterminateProgressBar.setVisibility(View.VISIBLE);
    			}
    		}
    	};
    	final Runnable progressBarHide = new Runnable() {
    		public void run() {
    			if (indeterminateProgressBar != null) {
    				indeterminateProgressBar.setVisibility(View.GONE);
    				textView.setVisibility(View.VISIBLE);
    			}
    		}
    	};

    	final Handler handler = new Handler() {
    		@Override
    		public void handleMessage(Message message) {
    			if (indeterminateProgressBar != null && progressActivity != null)
    				progressActivity.runOnUiThread(progressBarHide);
    			textView.setText((CharSequence) message.obj);
    			thingInfo.setSpannedBody((CharSequence) message.obj);
    		}
    	};

    	Thread thread = new Thread() {
    		@Override
    		public void run() {
    			if (indeterminateProgressBar != null && progressActivity != null)
    				progressActivity.runOnUiThread(progressBarShow);
    			CharSequence spanned = createSpanned(bodyHtml);
    			Message message = handler.obtainMessage(1, spanned);
    			handler.sendMessage(message);
    		}
    	};
    	thread.start();
    }
}
