package com.andrewshu.android.reddit.shareImage;

import android.graphics.Bitmap;

public interface PreviewProgressListener {

	/**
	 * Called once the preview thumbnail is prepared. Currently there is no
	 * method of requesting a specific size - this will simply return a preview
	 * image that is 8 times smaller in width and height than the original
	 * image, or will return a default 'no preview available' image (if the
	 * smaller image is still too large to fit into memory)
	 * 
	 * @param result
	 */
	public void onPostPreview(Bitmap result);

}
