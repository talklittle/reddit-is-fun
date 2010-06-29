package com.andrewshu.android.reddit.shareImage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.andrewshu.android.reddit.R;

public class Test extends Activity {
	private PreviewAsyncTask previewAsyncTask;
	private UploadAsyncTask uploadAsyncTask;
	
	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		
		setContentView(R.layout.imgur_upload);
		
		previewAsyncTask = new PreviewAsyncTask(this);
		uploadAsyncTask = new UploadAsyncTask(this);
		
		Log.i("rf", "Started them both");
	}
	
	public void onPostUpload(String url) {
		Log.i("rf", "Upload returned");
		
		TextView tv = (TextView)findViewById(R.id.imgur_status);
		tv.setText(url);
		tv.invalidate();
	}
	
	public void onUploadStateProgress(String status) {
		TextView tv = (TextView)findViewById(R.id.imgur_status);
		tv.setText(status);
		tv.invalidate();
	}
	
	public void onPostPreview(Bitmap previewImage) {
		Log.i("rf", "Preview returned");
		
		ProgressBar pb = (ProgressBar) findViewById(R.id.imgur_progress);
		pb.setEnabled(false);
		pb.setVisibility(View.INVISIBLE);
		pb.invalidate();
		
		ImageView iv = (ImageView)findViewById(R.id.imgur_preview);
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
