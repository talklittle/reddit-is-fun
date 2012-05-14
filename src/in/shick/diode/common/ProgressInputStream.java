/**
 * Code taken from stackoverflow user Adamski:
 * http://stackoverflow.com/questions/1339437/inputstream-or-reader-wrapper-for-progress-reporting/1339589#1339589
 */

package in.shick.diode.common;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProgressInputStream extends FilterInputStream {
    private final PropertyChangeSupport propertyChangeSupport;
    private final long maxNumBytes;
    private volatile long totalNumBytesRead;

    public ProgressInputStream(InputStream in, long maxNumBytes) {
        super(in);
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.maxNumBytes = maxNumBytes;
    }

    public long getMaxNumBytes() {
        return maxNumBytes;
    }

    public long getTotalNumBytesRead() {
        return totalNumBytesRead;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }

    @Override
    public int read() throws IOException {
        return (int)updateProgress(super.read());
    }

    @Override
    public int read(byte[] b) throws IOException {
        return (int)updateProgress(super.read(b));
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return (int)updateProgress(super.read(b, off, len));
    }

    @Override
    public long skip(long n) throws IOException {
        return updateProgress(super.skip(n));
    }

    @Override
    public void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private long updateProgress(long numBytesRead) {
        if (numBytesRead > 0) {
            long oldTotalNumBytesRead = this.totalNumBytesRead;
            this.totalNumBytesRead += numBytesRead;
            propertyChangeSupport.firePropertyChange("totalNumBytesRead", oldTotalNumBytesRead, this.totalNumBytesRead);
        }

        return numBytesRead;
    }
}
