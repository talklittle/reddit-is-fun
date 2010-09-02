package com.andrewshu.android.reddit.shareImage;

public interface UploadProgressListener {

	/**
	 * Called when the image upload is finalized
	 * 
	 * @param imageUrl
	 *            the URL to be shared with reddit
	 */
	public void onUploadComplete(String imageUrl);

	/**
	 * Called when there is a string status update to report to the user
	 * interface
	 * 
	 * @param newStatus
	 *            the string status update. Typically something such as
	 *            "Creating post", "Adding image to post", "Uploading", etc
	 */
	public void onUploadStatusChange(String newStatus);

	/**
	 * Called when there is a fatal error and the image can no longer be
	 * uploaded
	 * 
	 * @param message
	 *            A detailed description of the problem
	 */
	public void onUploadFatalError(String message);

	/**
	 * Called when the percentage of the uploaded image has changed. Note that
	 * this could theoretically be called every few bytes, but that would slow
	 * any device down significantly. This is typically only called
	 * approximately every 1%, or every 0.5% at least.
	 * 
	 * @param uploadPercentage
	 *            a double between 0.0 and 100.0
	 */
	public void onUploadProgressChange(double uploadPercentage);

}