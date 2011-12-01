package com.andrewshu.android.reddit.threads;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.app.ListActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.common.util.StringUtils;
import com.andrewshu.android.reddit.things.ThingInfo;
import com.andrewshu.android.reddit.threads.ShowThumbnailsTask.ThumbnailLoadAction;

public class ShowThumbnailsTask extends AsyncTask<ThumbnailLoadAction, Integer, Void> {
	
	private ListActivity mActivity;
	private HttpClient mClient;
	
	private static final String TAG = "ShowThumbnailsTask";

	public static class ThumbnailLoadAction {
    	public ThingInfo thingInfo;
    	public int threadIndex;
    	public ThumbnailLoadAction(ThingInfo thingInfo, int threadIndex) {
    		this.thingInfo = thingInfo;
    		this.threadIndex = threadIndex;
    	}
    }
	
	public ShowThumbnailsTask(ListActivity activity, HttpClient client) {
		this.mActivity = activity;
		this.mClient = client;
	}
	
	@Override
	protected Void doInBackground(ThumbnailLoadAction... thumbnailLoadActions) {
		for (ThumbnailLoadAction thumbnailLoadAction : thumbnailLoadActions) {
			loadThumbnail(thumbnailLoadAction.thingInfo);
			publishProgress(thumbnailLoadAction.threadIndex);
		}
		return null;
	}
	
	// TODO use external storage cache if present
	private void loadThumbnail(ThingInfo thingInfo) {
		if (!StringUtils.isEmpty(thingInfo.getThumbnail()))
			thingInfo.setThumbnailBitmap(readBitmapFromNetwork(thingInfo.getThumbnail()));
	}
	
    private InputStream fetch(String urlString) throws MalformedURLException, IOException {
    	HttpGet request = new HttpGet(urlString);
    	HttpResponse response = mClient.execute(request);
    	return response.getEntity().getContent();
    }
    
	private Bitmap readBitmapFromNetwork( String url ) {
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
	
	@Override
	protected void onProgressUpdate(Integer... threadIndices) {
		for (Integer threadIndex : threadIndices)
			refreshThumbnailIfVisible(threadIndex);
	}
	
	private void refreshThumbnailIfVisible(int threadIndex) {
		if (isPositionVisibleUI(threadIndex))
			refreshThumbnailUI(threadIndex);
	}

	// TODO factor out from here and CommentsListActivity
	private boolean isPositionVisibleUI(int position) {
		return position <= mActivity.getListView().getLastVisiblePosition() &&
				position >= mActivity.getListView().getFirstVisiblePosition();
	}
	
	private void refreshThumbnailUI(int position) {
		View v = mActivity.getListView().getChildAt(position);
		if (v != null) {
			View thumbnailImageView = v.findViewById(R.id.thumbnail_view);
			if (thumbnailImageView != null) {
				ThingInfo thingInfo = (ThingInfo) mActivity.getListView().getItemAtPosition(position);
				((ImageView) thumbnailImageView).setImageBitmap(thingInfo.getThumbnailBitmap());
			}
		}
	}
	
}

