package com.andrewshu.android.reddit.shareImage;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;

public class CountingMultiPartEntity extends MultipartEntity {

	private UploadProgressListener listener_;
	private CountingOutputStream outputStream_;
	private OutputStream lastOutputStream_;
	
	private static final long DEFAULT_BYTES_BETWEEN_NOTIFY = 10;
	
	// TODO - use this w/o slowing down the transfer!
	private long notifyEveryXBytes_;

	public CountingMultiPartEntity(UploadProgressListener listener) {
		super(HttpMultipartMode.BROWSER_COMPATIBLE);
		listener_ = listener;
	}
	
	public CountingMultiPartEntity(UploadProgressListener listener, long notifyEveryXBytes) {
		super(HttpMultipartMode.BROWSER_COMPATIBLE);
		listener_ = listener;
		notifyEveryXBytes_ = notifyEveryXBytes;
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
			listener_.transferred(transferred);
		}

		public void write(int b) throws IOException {
			super.write(b);
			++transferred;
			listener_.transferred(transferred);
		}
	}
}
