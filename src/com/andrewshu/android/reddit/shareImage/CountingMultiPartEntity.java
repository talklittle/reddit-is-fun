package com.andrewshu.android.reddit.shareImage;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;

import android.util.Log;

public class CountingMultiPartEntity extends MultipartEntity {

	private UploadProgressListener listener_;
	private CountingOutputStream outputStream_;
	private OutputStream lastOutputStream_;

	private long totalBytes_;
	private int bytesPerPercent;
	private int notificationCounter = 0;

	public CountingMultiPartEntity(UploadProgressListener listener) {
		this(listener, -1);
	}

	public CountingMultiPartEntity(UploadProgressListener listener,
			long totalBytes) {
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

	private class CountingOutputStream extends FilterOutputStream {

		private long transferred = 0;

		public CountingOutputStream(final OutputStream out) {
			super(out);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			super.write(b, off, len);
			transferred += len;
			notificationCounter += len;

			if (notificationCounter >= bytesPerPercent) {
				notificationCounter = 0;
				final double percent = ((double) transferred / (double) totalBytes_) * 100.0;

				listener_.transferred(percent);
			}
		}

		public void write(int b) throws IOException {
			super.write(b);
			++transferred;
			++notificationCounter;

			if (notificationCounter >= bytesPerPercent) {
				notificationCounter = 0;
				final double percent = ((double) transferred / (double) totalBytes_) * 100.0;

				listener_.transferred(percent);
			}
		}
	}
}
