package com.andrewshu.android.reddit.shareImage;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;

/**
 * <p>
 * An extension of {@link MultipartEntity} that can roughly count the number of
 * bytes transmitted, and inform an observer when the percentage of the transfer
 * changes by more than 0.5% to 1.0%. End result is that this class will inform
 * a listener approximately every half-percent how far along the transfer is.
 * </p>
 * 
 * <p>
 * Intended to assist in monitoring the upload progress of an image uploaded
 * with an {@link HttpPost} and {@link HttpClient}. If the length of the
 * {@link OutputStream} cannot be determined, it is recommended to use a
 * standard {@link MultipartEntity} rather than this class
 * </p>
 * 
 * <p>
 * Currently accepts an instance of {@link UploadAsyncTask} as the observer, and
 * calls {@link UploadAsyncTask#transferred(double)} to inform of a percentage
 * change. It would be relatively simple to extract some interface and just have
 * this class accept any object that implements that interface, if there is a
 * need for uploading to other image sites
 * </p>
 * 
 * @author Hamilton Turner
 * 
 */
public class CountingMultiPartEntity extends MultipartEntity {

	private UploadAsyncTask listener_;
	private CountingOutputStream outputStream_;
	private OutputStream lastOutputStream_;

	private long totalBytes_;
	private int bytesPerPercent;
	private int notificationCounter = 0;

	public CountingMultiPartEntity(UploadAsyncTask listener, long totalBytes) {
		super(HttpMultipartMode.BROWSER_COMPATIBLE);
		listener_ = listener;
		totalBytes_ = totalBytes;

		double averageBytesPerPercent = totalBytes / 100.0;
		bytesPerPercent = (int) Math.floor(averageBytesPerPercent);

	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		// If we have yet to create the CountingOutputStream, or the
		// OutputStream being passed in is different from the OutputStream used
		// to create the current CountingOutputStream
		if ((lastOutputStream_ == null) || (lastOutputStream_ != out)) {
			lastOutputStream_ = out;
			outputStream_ = new CountingOutputStream(out);
		}

		super.writeTo(outputStream_);
	}

	/**
	 * This is a class that attempts to roughly count the outgoing bytes.
	 * <p>
	 * Note that I can't figure out an efficient way to accurately count the
	 * bytes sent in every situation. The issue is discussed in detail at
	 * http://stackoverflow.com/questions
	 * /3163131/how-to-properly-extend-java-filteroutputstream-class , but
	 * suffice to say that this class may occasionally be unable to work
	 * properly in some situations. In the cases that it fails to work properly,
	 * it is a graceful failure. The progress listener will not be notified of
	 * some of the bytes transferred, and might not be at 100% when the upload
	 * completes. The worst thing that might happen is a user may think a
	 * particular upload is going very slow when it is in fact not (aka, they
	 * might be presented with information saying that the upload is 20%
	 * complete when it is really 80% complete).
	 * </p>
	 * <p>
	 * For practical purposes, I think that this implementation will work 99% of
	 * the time, but there are edge cases. However, protecting against the 1%
	 * edge cases would result in decreased efficiency for almost all other
	 * cases, so it's just not worth it IMO. For what it's worth, capturing all
	 * cases would involve calling super.write(byte[],int,int) and performing
	 * all counting operations in the write(int) method. The Java documentation
	 * recommends against using this (default) behavior for efficiency reasons.
	 * Again, I think doing things this way is a bad idea as it would highly
	 * penalize almost all other cases
	 * </p>
	 * 
	 * @author Hamilton Turner
	 * 
	 */
	private class CountingOutputStream extends FilterOutputStream {

		private long transferred = 0;
		private OutputStream wrappedOutputStream_;

		public CountingOutputStream(final OutputStream out) {
			super(out);
			wrappedOutputStream_ = out;
		}

		public void write(byte[] buffer, int offset, int length)
				throws IOException {
			transferred += length;
			notificationCounter += length;

			if (notificationCounter >= bytesPerPercent) {
				notificationCounter = 0;
				double percent = ((double) transferred / (double) totalBytes_) * 100.0;

				// This class is only intended to work on a rough scale
				if (percent > 100.0)
					percent = 100.0;

				listener_.transferred(percent);
			}

			wrappedOutputStream_.write(buffer, offset, length);
		}

		public void write(int b) throws IOException {
			super.write(b);
		}
	}
}
