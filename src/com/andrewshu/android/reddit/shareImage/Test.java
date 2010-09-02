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
import android.widget.Toast;

import com.andrewshu.android.reddit.R;

/**
 * Just a Test Activity showing how to use the system. 
 * 
 * @author hamiltont
 *
 */
public class Test extends Activity implements UploadProgressListener {
	private PreviewAsyncTask previewAsyncTask;
	private UploadAsyncTask uploadAsyncTask;
	private ProgressBar uploadProgress_;
	private TextView uploadProgressPercent_;

	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);

		setContentView(R.layout.imgur_upload);

		previewAsyncTask = new PreviewAsyncTask(this);
		uploadAsyncTask = new UploadAsyncTask(this);

		uploadProgress_ = (ProgressBar) findViewById(R.id.imgur_upload_progress);
		uploadProgressPercent_ = (TextView) findViewById(R.id.imgur_upload_progress_percent);
		uploadProgress_.setMax(100);
		uploadProgress_.setIndeterminate(false);

		Log.i("rf", "Started them both");
	}

	public void onPostUpload(String url) {
		Log.i("rf", "Upload returned");

		TextView tv = (TextView) findViewById(R.id.imgur_status);
		tv.setText(url);
		tv.invalidate();
	}

	public void onUploadStateProgress(String status) {
		TextView tv = (TextView) findViewById(R.id.imgur_status);
		tv.setText(status);
		tv.invalidate();
	}

	public void onPostPreview(Bitmap previewImage) {
		Log.i("rf", "Preview returned");

		ProgressBar pb = (ProgressBar) findViewById(R.id.imgur_preview_progress);
		pb.setEnabled(false);
		pb.setVisibility(View.INVISIBLE);
		pb.invalidate();

		ImageView iv = (ImageView) findViewById(R.id.imgur_preview);
		iv.setImageBitmap(previewImage);
		iv.invalidate();
	}

	@Override
	protected void onStart() {
		super.onStart();

		Uri uri = (Uri) getIntent().getExtras().get(Intent.EXTRA_STREAM);
		uploadAsyncTask.execute(uri);
		previewAsyncTask.execute(uri);
	}

	int i = 0;

	public void onUploadProgress(double progress) {
		uploadProgress_.setProgress((int) progress);
		final int i = (int) Math.ceil(progress);
		uploadProgressPercent_.setText(Integer.toString(i) + "%");
		uploadProgressPercent_.invalidate();

	}

	@Override
	protected void onPause() {
		super.onPause();

		uploadAsyncTask.cancel(true);
		previewAsyncTask.cancel(true);

		Toast.makeText(this, "Aborted image upload", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		uploadAsyncTask.cancel(true);
		previewAsyncTask.cancel(true);
	}

	public void onUploadError(String string) {
		Toast.makeText(this, string, Toast.LENGTH_LONG).show();
	}

	@Override
	public void onUploadComplete(String imageUrl) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUploadFatalError(String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUploadProgressChange(double uploadPercentage) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUploadStatusChange(String newStatus) {
		// TODO Auto-generated method stub
		
	}
}
