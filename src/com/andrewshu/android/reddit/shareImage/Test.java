package com.andrewshu.android.reddit.shareImage;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;

/**
 * 
 * 
 *@see <a href="http://code.google.com/p/imgur-api/wiki/ImageUploading">imgur
 *      upload API</a>
 * 
 * @author Hamilton Turner <hamiltont@gmail.com>
 */
public class Test extends Activity {

	private static final String IMGUR_API_KEY = "347ec991d0079db6ea067c8471b74348";
	private static final String IMGUR_POST_URI = "http://imgur.com/api/upload.json";

	@Override
	public void onCreate(Bundle shared) {
		super.onCreate(shared);

		setContentView(R.layout.test);
	}

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

	@Override
	protected void onStart() {
		super.onStart();

		final Bundle extras = getIntent().getExtras();
		final Uri uri = (Uri) extras.get(Intent.EXTRA_STREAM);

		// Note: don't use ImageView.setImageURI. See the
		// PREVIEW_DOWNSAMPLE_FACTOR for why
		final ImageView iv = (ImageView) findViewById(R.id.test_imageview);

		InputStream is;
		try {
			is = getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {
			Toast.makeText(this, "Could not load image", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
			return;
		}

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = PREVIEW_DOWNSAMPLE_FACTOR;
		Bitmap bmp = BitmapFactory.decodeStream(is, null, options);

		iv.setImageBitmap(bmp);
		iv.invalidate();

		try {
			is.close();
		} catch (IOException ioe) {
		}

		JSONObject jsonResponse = sendToImgur(uri);
		if (jsonResponse == null)
			return;

		// Now dig out the image data
		String imgurLink = getImgurLink(jsonResponse);
		if (imgurLink == null)
			return;

		Log.i("rf", imgurLink);

	}

	private String getImgurLink(JSONObject jsonResponse) {
		try {
			JSONObject rsp = jsonResponse.getJSONObject("rsp");
			String stat = rsp.getString("stat");
			if (stat.equalsIgnoreCase("ok"))
				return rsp.getJSONObject("image").getString("imgur_page");

			StringBuffer error = new StringBuffer("Imgur Error: ");
			String code = rsp.getString("error_code");
			String msg = rsp.getString("error_msg");
			error.append(code);
			error.append(" - ");
			error.append(msg);
			Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show();
		} catch (JSONException je) {
			Toast.makeText(this, "Unable to get Imgur link", Toast.LENGTH_LONG)
					.show();
		}

		return null;
	}

	/**
	 * Attempts to load the image from the given Uri and upload it to imgur.
	 * Informs the user when (and why, if possible) an error occurs in
	 * contacting the Imgur site. Note that this method simply contacts the
	 * site, attempts the upload, and reports the API's response. Any errors
	 * returned by the API should be handled elsewhere.
	 * 
	 * @param uri
	 *            The URI that points to the InputStream for the image
	 * @return The JSON response from Imgur, or null.
	 */
	private JSONObject sendToImgur(Uri uri) {

		// Load the image data
		InputStream is = null;
		try {
			is = getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {
			Toast.makeText(this, "Could not load image", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
			return null;
		}

		// Create the post
		HttpPost hp = new HttpPost(IMGUR_POST_URI);
		MultipartEntity en = new MultipartEntity(
				HttpMultipartMode.BROWSER_COMPATIBLE);

		HttpClient c = new DefaultHttpClient();
		HttpResponse r;
		String jsonResponse = null;
		try {
			// Note that the imgur api seems to need some filename to recognize
			// the image part as binary file. Passing a string versus null for
			// the filename changes the headers for the image part of the
			// multipart post slightly (adds "filename=something" to
			// Content-Disposition).
			// Inserting null seems to make imgur interpret everything sent
			// to it as a URL that it should attempt to query for an image file.
			// Returns error "1003 Invalid image type or URL" and recommends you
			// use the JPEG format in the returned error message.
			en.addPart("image", new InputStreamBody(is,
					"uploaded-from-reddit-is-fun"));
			en.addPart("key", new StringBody(IMGUR_API_KEY));
			hp.setEntity(en);

			r = c.execute(hp);
			// TODO Might be nice to show a better error message
			final int code = r.getStatusLine().getStatusCode();
			if (code != 200) {
				Toast.makeText(this, "Unable to contact Imgur",
						Toast.LENGTH_LONG).show();
				return null;
			}

			// Read in the response
			InputStream content = r.getEntity().getContent();
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			final int BUF_SIZE = 1 << 8; // 1KiB buffer
			byte[] buffer = new byte[BUF_SIZE];
			int bytesRead = -1;
			while ((bytesRead = content.read(buffer)) > -1) {
				response.write(buffer, 0, bytesRead);
			}
			content.close();

			jsonResponse = response.toString();

		} catch (ClientProtocolException e) {
			e.printStackTrace();
			Toast.makeText(this, "Could not save image", Toast.LENGTH_LONG)
					.show();
		} catch (IOException e) {
			Toast.makeText(this, "Could not save image", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
			}
		}

		Log.i("rf", "Imgur Response: ");
		Log.i("rf", jsonResponse);

		// Convert to JSON object
		JSONObject jo = null;
		try {
			jo = new JSONObject(jsonResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return jo;
	}
}
