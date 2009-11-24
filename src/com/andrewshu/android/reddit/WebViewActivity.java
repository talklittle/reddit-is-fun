package com.andrewshu.android.reddit;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewActivity extends Activity {
	private static final String TAG = "WebViewActivity";
	
	WebView webView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        
        // Pull URL info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
        	// Quit, because this Activity requires a URL.
        	if (Constants.LOGGING) Log.e(TAG, "Quitting because no URL data was passed into the Intent.");
        	finish();
        }
        
        setContentView(R.layout.webview);
    	
        webView = (WebView) findViewById(R.id.webview);
    	webView.setWebViewClient(new RedditWebViewClient());
    	webView.getSettings().setJavaScriptEnabled(true);
    	webView.getSettings().setPluginsEnabled(true);
        webView.loadUrl(extras.getString(Constants.EXTRA_URL));
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private class RedditWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }
}
