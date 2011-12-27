package com.andrewshu.android.reddit.browser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.comments.CommentsListActivity;
import com.andrewshu.android.reddit.common.Common;
import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.util.Util;
import com.andrewshu.android.reddit.settings.RedditSettings;

public class BrowserActivity extends Activity {
	
	private static final String TAG = "BrowserActivity";

	private WebView webview;
	private Uri mUri = null;
	private String mThreadUrl = null;
	private String mTitle = null;
	
    // Common settings are stored here
    private final RedditSettings mSettings = new RedditSettings();
    
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		CookieSyncManager.createInstance(getApplicationContext());
		
        mSettings.loadRedditPreferences(this, null);
        setRequestedOrientation(mSettings.getRotation());
		requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        resetUI();
		WebSettings settings = webview.getSettings();
		settings.setBuiltInZoomControls(true);
		settings.setPluginsEnabled(true);
		settings.setJavaScriptEnabled(true);
		settings.setUseWideViewPort(true);
		settings.setDomStorageEnabled(true);
		
    	// HACK: set background color directly for android 2.0
        if (Util.isLightTheme(mSettings.getTheme()))
        	webview.setBackgroundResource(R.color.white);

		// use transparent background while loading
		webview.setBackgroundColor(0);
		webview.setInitialScale(50);
		webview.setWebViewClient(new WebViewClient() {
		    @Override
		    public boolean shouldOverrideUrlLoading(WebView view, String url) {
		        view.loadUrl(url);
		        return true;
		    }
		    
		    @Override
		    public void onPageFinished(WebView view, String url) {
		    	CookieSyncManager.getInstance().sync();
		    	// restore default white background, no matter the theme
		    	view.setBackgroundResource(R.color.white);

		    	String host = Uri.parse(url).getHost();
		    	if (host != null && mTitle != null) {
		    		setTitle(host + " : " + mTitle);
		    	} else if (host != null) {
		    		setTitle(host);
		    	} else if (mTitle != null) {
		    		setTitle(mTitle);
		    	}
		    }
		});
		final Activity activity = this;
		webview.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int progress) {
		    	// Activities and WebViews measure progress with different scales.
		    	// The progress meter will automatically disappear when we reach 100%
		    	activity.setProgress(progress * 100);
			}
		    
		    @Override
		    public void onReceivedTitle(WebView view, String title) {
		    	mTitle = title;
		    	setTitle(title);
		    }
		});
		
		mUri = getIntent().getData();
		mThreadUrl = getIntent().getStringExtra(Constants.EXTRA_THREAD_URL);
		
		if (savedInstanceState != null) {
			if (Constants.LOGGING) Log.d(TAG, "Restoring previous WebView state");
			webview.restoreState(savedInstanceState);
		} else {
			if (Constants.LOGGING) Log.d(TAG, "Loading url " + mUri.toString());
			webview.loadUrl(mUri.toString());
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		CookieSyncManager.getInstance().startSync();
    	mSettings.loadRedditPreferences(this, null);
    	setRequestedOrientation(mSettings.getRotation());
    	int previousTheme = mSettings.getTheme();
    	if (mSettings.getTheme() != previousTheme) {
    		resetUI();
    	}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		CookieSyncManager.getInstance().stopSync();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Must remove the WebView from the view system before destroying.
		webview.setVisibility(View.GONE);
		webview.destroy();
		webview = null;
	}
	
	private void resetUI() {
		setTheme(mSettings.getTheme());
        setContentView(R.layout.browser);
        webview = (WebViewFixed) findViewById(R.id.webview);
    	// HACK: set background color directly for android 2.0
        if (Util.isLightTheme(mSettings.getTheme()))
        	webview.setBackgroundResource(R.color.white);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ((keyCode == KeyEvent.KEYCODE_BACK) && webview.canGoBack()) {
	        webview.goBack();
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.browser, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	super.onPrepareOptionsMenu(menu);
    	
    	if (mThreadUrl == null)
    		menu.findItem(R.id.view_comments_menu_id).setVisible(false);
    	else
    		menu.findItem(R.id.view_comments_menu_id).setVisible(true);
    	
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        if (!mCanChord) {
//            // The user has already fired a shortcut with this hold down of the
//            // menu key.
//            return false;
//        }
        
        switch (item.getItemId()) {
        
        case R.id.open_browser_menu_id:
    		if (mUri == null)
    			break;
    		Common.launchBrowser(this, mUri.toString(), null, false, true, true);
    		break;
        
        case R.id.view_comments_menu_id:
        	if (mThreadUrl == null)
        		break;
			Intent intent = new Intent(this, CommentsListActivity.class);
			intent.setData(Uri.parse(mThreadUrl));
			intent.putExtra(Constants.EXTRA_NUM_COMMENTS, Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			startActivity(intent);
        	break;
        
        default:
    		throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
    	}
    	
        return true;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	webview.saveState(outState);
    }
}
