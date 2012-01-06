package com.andrewshu.android.reddit.threads;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.util.HashMap;

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

public class ShowThumbnailsTask extends AsyncTask<ThumbnailLoadAction, ThumbnailLoadAction, Void> {
	
	private ListActivity mActivity;
	private HttpClient mClient;
	private Integer mDefaultThumbnailResource;
	
	private static HashMap<String, SoftReference<Bitmap>> cache = new HashMap<String, SoftReference<Bitmap>>();
	
	private static final String TAG = "ShowThumbnailsTask";

	public ShowThumbnailsTask(ListActivity activity, HttpClient client, Integer defaultThumbnailResource) {
		this.mActivity = activity;
		this.mClient = client;
		this.mDefaultThumbnailResource = defaultThumbnailResource;
	}
	
	public static class ThumbnailLoadAction {
    	public ThingInfo thingInfo;
    	public ImageView imageView;  // prefer imageView; if it's null, use threadIndex
    	public int threadIndex;
    	public ThumbnailLoadAction(ThingInfo thingInfo, ImageView imageView, int threadIndex) {
    		this.thingInfo = thingInfo;
    		this.imageView = imageView;
    		this.threadIndex = threadIndex;
    	}
    }
	
	@Override
	protected Void doInBackground(ThumbnailLoadAction... thumbnailLoadActions) {
		for (ThumbnailLoadAction thumbnailLoadAction : thumbnailLoadActions) {
			loadThumbnail(thumbnailLoadAction.thingInfo);
			publishProgress(thumbnailLoadAction);
		}
		return null;
	}
	
	// TODO use external storage cache if present
	private void loadThumbnail(ThingInfo thingInfo) {
		if ("default".equals(thingInfo.getThumbnail()) || "self".equals(thingInfo.getThumbnail()) || StringUtils.isEmpty(thingInfo.getThumbnail())) {
			thingInfo.setThumbnailResource(mDefaultThumbnailResource);
		}
		else {
			SoftReference<Bitmap> ref;
			Bitmap bitmap;
			
			ref = cache.get(thingInfo.getThumbnail());
			if (ref != null) {
				bitmap = ref.get();
				if (bitmap != null) {
					thingInfo.setThumbnailBitmap(bitmap);
					return;
				}
			}
			
			bitmap = readBitmapFromNetwork(thingInfo.getThumbnail());
			
			ref = new SoftReference<Bitmap>(bitmap);
			cache.put(thingInfo.getThumbnail(), ref);
			thingInfo.setThumbnailBitmap(ref.get());
		}
	}
	
    private InputStream fetch(String urlString) throws MalformedURLException, IOException {
    	HttpGet request = new HttpGet(urlString);
    	HttpResponse response = mClient.execute(request);
    	return response.getEntity().getContent();
    }
    
	private Bitmap readBitmapFromNetwork( String url ) {
		if (url == null)
			return null;
		
		InputStream is = null;
		BufferedInputStream bis = null;
		Bitmap bmp = null;
		try {
			// http://blog.donnfelker.com/2010/04/29/android-odd-error-in-defaulthttpclient/
			if (!url.startsWith("http://") && !url.startsWith("https://"))
				url = "http://" + url;
			
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
	protected void onProgressUpdate(ThumbnailLoadAction... thumbnailLoadActions) {
		for (ThumbnailLoadAction thumbnailLoadAction : thumbnailLoadActions)
			refreshThumbnailUI(thumbnailLoadAction);
	}
	
	private void refreshThumbnailUI(ThumbnailLoadAction thumbnailLoadAction) {
		ImageView imageView = null;
		if (thumbnailLoadAction.imageView != null) {
			imageView = thumbnailLoadAction.imageView;
		}
		else {
			if (isCurrentlyOnScreenUI(thumbnailLoadAction.threadIndex)) {
				int positionOnScreen = thumbnailLoadAction.threadIndex - mActivity.getListView().getFirstVisiblePosition();
				View v = mActivity.getListView().getChildAt(positionOnScreen);
				if (v != null) {
					View thumbnailImageView = v.findViewById(R.id.thumbnail);
					if (thumbnailImageView != null) {
						imageView = (ImageView) thumbnailImageView;
					}
				}
			}
		}
		if (imageView != null) {
			ThingInfo thingInfo = thumbnailLoadAction.thingInfo;
			if (thingInfo.getThumbnailBitmap() != null)
				imageView.setImageBitmap(thingInfo.getThumbnailBitmap());
			else if (thingInfo.getThumbnailResource() != null)
				imageView.setImageResource(thingInfo.getThumbnailResource());
		}
	}
	
	private boolean isCurrentlyOnScreenUI(int position) {
		return position >= mActivity.getListView().getFirstVisiblePosition() &&
				position <= mActivity.getListView().getLastVisiblePosition();
	}
	
}

