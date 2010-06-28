package com.andrewshu.android.reddit.shareImage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.andrewshu.android.reddit.R;

public class Test extends Activity {
	private PrevewAsyncTask previewAsyncTask;
	private UploadAsyncTask uploadAsyncTask;
	
	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		
		setContentView(R.layout.test);
		
		previewAsyncTask = new PrevewAsyncTask(this);
		uploadAsyncTask = new UploadAsyncTask(this);
		
		Log.i("rf", "Started them both");
	}
	
	public void onPostUpload(String url) {
		Log.i("rf", "Upload returned");
		
		TextView tv = (TextView)findViewById(R.id.test_text);
		tv.setText(url);
		tv.invalidate();
	}
	
	public void onPostPreview(Bitmap previewImage) {
		Log.i("rf", "Preview returned");
		
		ImageView iv = (ImageView)findViewById(R.id.test_imageview);
		iv.setImageBitmap(previewImage);
		iv.invalidate();
	}
	
	@Override 
	protected void onStart() {
		super.onStart();
		
		Uri uri = (Uri)getIntent().getExtras().get(Intent.EXTRA_STREAM);
		uploadAsyncTask.execute(uri);
		previewAsyncTask.execute(uri);
		
	}
}
