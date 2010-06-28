package com.andrewshu.android.reddit.shareImage;

import java.io.FileNotFoundException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

public class PrevewAsyncTask extends AsyncTask<Uri, Void, Bitmap> {
	/**
	 * Showing a full-resolution preview is a fast-track to an
	 * OutOfMemoryException. Therefore, we downsample the preview image. Android
	 * docs recommend using a power of 2 to downsample
	 * 
	 * @see <a
	 *      href="http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue/823966#823966">StackOverflow
	 *      post discussing OutOfMemoryException</a>
	 * @see <a
	 *      href="http://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inSampleSize">Android
	 *      docs explaining BitmapFactory.Options#inSampleSize</a>
	 * 
	 */
	private static final int PREVIEW_DOWNSAMPLE_FACTOR = 8;

	private Context context_;
	private Test test_;

	public PrevewAsyncTask(Test t) {
		context_ = t.getApplicationContext();
		test_ = t;
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		super.onPostExecute(result);

		test_.onPostPreview(result);
	}

	// TODO replace all null's with a default image
	@Override
	protected Bitmap doInBackground(Uri... params) {
		if (params.length != 1)
			throw new IllegalArgumentException(
					"Can only upload a single image to imgur at a time");

		final Uri uri = params[0];

		// Note: don't use ImageView.setImageURI. See the
		// PREVIEW_DOWNSAMPLE_FACTOR for why
		// final ImageView iv = (ImageView) findViewById(R.id.test_imageview);

		InputStream is;
		try {
			is = context_.getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {
			Toast.makeText(context_, "Could not load image", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
			return null;
		}

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = PREVIEW_DOWNSAMPLE_FACTOR;
		Bitmap bmp = null;
		try {
			BitmapFactory.decodeStream(is, null, options);
		} catch (OutOfMemoryError ome) {
			// TODO - return default image
		}

		return bmp;
	}
}
