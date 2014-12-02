package com.andrewshu.android.reddit.browser;

import java.lang.reflect.Method;

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
    
    // WebSettings available on Android 2.1 (API level 7)
    private static Method mWebSettings_setDomStorageEnabled;
    private static Method mWebSettings_setLoadWithOverviewMode;
    
    static {
        initCompatibility();
    };

    private static void initCompatibility() {
        try {
        	mWebSettings_setDomStorageEnabled = WebSettings.class.getMethod("setDomStorageEnabled", new Class[] { Boolean.TYPE } );
        } catch (NoSuchMethodException nsme) {}
        try {
        	mWebSettings_setLoadWithOverviewMode = WebSettings.class.getMethod("setLoadWithOverviewMode", new Class[] { Boolean.TYPE } );
        } catch (NoSuchMethodException nsme) {}
    }
    
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
		trySetDomStorageEnabled(settings);
		trySetLoadWithOverviewMode(settings);
		
    	// HACK: set background color directly for android 2.0
        if (Util.isLightTheme(mSettings.getTheme()))
        	webview.setBackgroundResource(R.color.white);

		// use transparent background while loading
		webview.setBackgroundColor(0);
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
	
	private void trySetDomStorageEnabled(WebSettings settings) {
		if (mWebSettings_setDomStorageEnabled != null) {
			try {
				mWebSettings_setDomStorageEnabled.invoke(settings, true);
			} catch (Exception ex) {
				Log.e(TAG, "trySetDomStorageEnabled", ex);
			}
		}
	}
	
	private void trySetLoadWithOverviewMode(WebSettings settings) {
		if (mWebSettings_setLoadWithOverviewMode != null) {
			try {
				mWebSettings_setLoadWithOverviewMode.invoke(settings, true);
				return;
			} catch (Exception ex) {
				Log.e(TAG, "trySetLoadWithOverviewMode", ex);
			}
		}
		// if that method didn't work, do this instead for old devices
		webview.setInitialScale(50);
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
        switch (item.getItemId()) {
        
        case R.id.open_browser_menu_id:
    		if (mUri == null)
    			break;
    		Common.launchBrowser(this, mUri.toString(), null, false, true, true, false);
    		break;
        
        case R.id.close_browser_menu_id:
        	finish();
        	break;
        
        case R.id.view_comments_menu_id:
        	if (mThreadUrl == null)
        		break;
			Intent intent = new Intent(this, CommentsListActivity.class);
			intent.setData(Uri.parse(mThreadUrl));
			intent.putExtra(Constants.EXTRA_NUM_COMMENTS, Constants.DEFAULT_COMMENT_DOWNLOAD_LIMIT);
			startActivity(intent);
        	break;
        	
    	case android.R.id.home:
    		Common.goHome(this);
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
