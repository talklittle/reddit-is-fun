package com.andrewshu.android.reddit.shareImage;

import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.andrewshu.android.reddit.R;

public class Test extends Activity {

	@Override
	public void onCreate(Bundle shared) {
		super.onCreate(shared);
		
		setContentView(R.layout.test);
	}

	@Override
	protected void onStart() {
		super.onStart();

		final Bundle extras = getIntent().getExtras();
		final Uri uri = (Uri)extras.get(Intent.EXTRA_STREAM);
		final ImageView iv = (ImageView)findViewById(R.id.test_imageview);
		iv.setImageURI(uri);
		iv.invalidate();

	}

}
