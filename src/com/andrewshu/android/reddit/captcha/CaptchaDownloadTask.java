package com.andrewshu.android.reddit.captcha;

import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import com.andrewshu.android.reddit.common.Constants;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;

public abstract class CaptchaDownloadTask extends AsyncTask<Void, Void, Drawable> {
	
	private static final String TAG = "CaptchaDownloadTask";
	
	private String _mCaptchaUrl;
	private HttpClient _mClient;
	
	public CaptchaDownloadTask(String captchaUrl, HttpClient client) {
		_mCaptchaUrl = captchaUrl;
		_mClient = client;
	}
	@Override
	public Drawable doInBackground(Void... voidz) {
		try {
			HttpGet request = new HttpGet(Constants.REDDIT_BASE_URL + "/" + _mCaptchaUrl);
			HttpResponse response = _mClient.execute(request);
    	
			InputStream in = response.getEntity().getContent();
			
			//get image as bitmap
			Bitmap captchaOrg  = BitmapFactory.decodeStream(in);

			// create matrix for the manipulation
			Matrix matrix = new Matrix();
			// resize the bit map
			matrix.postScale(2f, 2f);

			// recreate the new Bitmap
			Bitmap resizedBitmap = Bitmap.createScaledBitmap (captchaOrg,
					captchaOrg.getWidth() * 3, captchaOrg.getHeight() * 3, true);
		 
			BitmapDrawable bmd = new BitmapDrawable(resizedBitmap);
			
			return bmd;
		
		} catch (Exception e) {
			if (Constants.LOGGING) Log.e(TAG, "download captcha", e);
		}
		
		return null;
	}
	

}
