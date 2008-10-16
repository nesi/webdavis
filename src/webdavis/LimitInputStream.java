package webdavis;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Limits the amount of data that can be read from the underlying stream
 * to a predefined value.  This is used to prevent an XML DOS attack, in which
 * the client sents a very large XML document in a PROPFIND or LOCK request.
 *
 * @author Eric Glass
 */ 
public class LimitInputStream extends FilterInputStream {

    private final long limit;

    private long amountRead;

    private long markRead;

    /**
     * Creates a <code>LimitInputStream</code> from the specified underlying
     * stream, using the provided limit.  Attempts to read after the limit
     * has been reached will result in an <code>EOFException</code>.
     *
     * @param in The underlying input stream.
     * @param limit The maximum number of bytes that can be read from this
     * stream.
     */
    public LimitInputStream(InputStream in, long limit) {
        super(in);
        this.limit = limit;
    }

    /**
     * Reads a byte of data from the stream.
     *
     * @return An <code>int</code> containing the next byte of data, or -1
     * if the end of the stream is reached.
     * @throws IOException If an IO error occurs.
     * @throws EOFException If the limit has already been read.
     */
    public int read() throws IOException {
        if (amountRead >= limit) {
            Log.log(Log.INFORMATION,
                    "Blocked attempt to read past limit; request too big.");
            throw new EOFException(DavisUtilities.getResource(
                    LimitInputStream.class, "limitReached", null, null));
        }
        int val = super.read();
        ++amountRead;
        return val;
    }

    /**
     * Reads bytes from the stream into the specified buffer.
     *
     * @param b The buffer that will receive the data.
     * @return An <code>int</code> indicating the number of bytes read, or
     * -1 if the end of the stream is reached.
     * @throws IOException If an IO error occurs.
     * @throws EOFException If the limit has already been read.
     */ 
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads up to the given number of bytes from the stream into the
     * specified buffer starting at the specified offset in the buffer.
     *
     * @param b The buffer that will receive the data.
     * @param offset The offset into the buffer where the data is to be
     * written.
     * @param length The maximum number of bytes to read. 
     * @return An <code>int</code> indicating the number of bytes read, or
     * -1 if the end of the stream is reached.
     * @throws IOException If an IO error occurs.
     * @throws EOFException If the limit has already been read.
     */ 
    public int read(byte[] b, int offset, int length) throws IOException {
        int remaining = (int) (limit - amountRead);
        if (remaining == 0 && length > 0) {
            Log.log(Log.INFORMATION,
                    "Blocked attempt to read past limit; request too big.");
            throw new EOFException(DavisUtilities.getResource(
                    LimitInputStream.class, "limitReached", null, null));
        }
        // if remaining is negative, should indicate an overflow -- plenty left.
        length = (remaining > 0) ? Math.min(length, remaining) : length;
        int count = super.read(b, offset, length);
        amountRead += count;
        return count;
    }

    /**
     * Marks the current position in the stream.  This implementation also
     * marks the amount read, to allow a rollback (if marks are supported
     * by the underlying stream).
     *
     * @param readLimit The amount that can be read before the mark is
     * invalidated.
     */
    public void mark(int readLimit) {
        super.mark(readLimit);
        markRead = amountRead;
    }

    /**
     * Resets the stream to the previously marked position.  This
     * implementation also resets the amount read.
     *
     * @throws IOException If the stream has not been marked, or the mark
     * has been invalidated.
     */
    public void reset() throws IOException {
        super.reset();
        amountRead = markRead;
    }

}
