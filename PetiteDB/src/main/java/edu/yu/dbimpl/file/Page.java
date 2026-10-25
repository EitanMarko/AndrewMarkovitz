package edu.yu.dbimpl.file;

import java.nio.ByteBuffer;

/**
 * Page: the main-memory image of one disk block, with typed getters/setters.
 *
 * A Page is just a fixed-size run of bytes plus the rules for laying values
 * out in it.  It keeps no record of what has been stored where: the client
 * stores a value at an offset and must read it back from the same offset with
 * the matching getter.  A Page is not tied to any particular block, and never
 * touches the disk itself: the FileMgr moves its bytes to and from disk.
 *
 * Layout rules (PageBase Javadoc / file_module lecture, "Mandatory Design For
 * Type Storage"):
 * - int:     exactly Integer.BYTES
 * - double:  exactly Double.BYTES
 * - boolean: exactly 1 byte
 * - byte[]:  the length stored as an int, then the bytes immediately after it
 * - String:  the byte[] of its PageBase.CHARSET encoding, laid out as above,
 *            so it occupies PageBase.maxLength(s.length()) bytes
 *
 * A value may never cross the end of the page.  Every getter/setter checks
 * that "offset + persisted length" fits and throws IllegalArgumentException
 * BEFORE touching the buffer, so a failed call leaves the page unchanged.
 *
 * Two constructors, for two owners:
 * - Page(int blocksize) allocates its own bytes; used by the FileMgr (and
 *   later the buffer manager) to move blocks to/from disk.  The buffer is
 *   direct (off-heap) so FileChannel I/O can use it without copying through
 *   an intermediate OS buffer ("double buffering", see lecture).
 * - Page(byte[] b) wraps the caller's array without copying, so writes through
 *   the Page show up in the array; used by the log manager to lay out records.
 *
 * Thread safety: all public operations are synchronized on the Page instance,
 * so a multi-step write (length prefix + bytes) can't interleave with a
 * concurrent read.  FileMgr synchronizes on the same monitor while doing I/O.
 */
public class Page extends PageBase {

    /**
     * The page's bytes.  Only absolute (index-based) ByteBuffer operations are
     * used by the getters/setters, so position/limit are never relied on.
     */
    private final ByteBuffer bb;

    /**
     * Allocate a page of {@code blocksize} bytes, all zero.
     *
     * @throws IllegalArgumentException if blocksize is not positive
     */
    public Page(int blocksize) {
        super(blocksize);
        if (blocksize <= 0) {
            throw new IllegalArgumentException("Block size must be positive: " + blocksize);
        }
        this.bb = ByteBuffer.allocateDirect(blocksize);
    }

    /**
     * Wrap the caller's array: the Page reads from and writes into {@code b}
     * directly, so the caller sees every change.
     *
     * @throws IllegalArgumentException if b is null
     */
    public Page(byte[] b) {
        super(b);
        if (b == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        this.bb = ByteBuffer.wrap(b);
    }

    // ---------------------------------------------------------------- ints

    @Override
    public synchronized int getInt(int offset) {
        checkRange(offset, Integer.BYTES);
        return bb.getInt(offset);
    }

    @Override
    public synchronized void setInt(int offset, int n) {
        checkRange(offset, Integer.BYTES);
        bb.putInt(offset, n);
    }

    // ------------------------------------------------------------- doubles

    @Override
    public synchronized double getDouble(int offset) {
        checkRange(offset, Double.BYTES);
        return bb.getDouble(offset);
    }

    @Override
    public synchronized void setDouble(int offset, double d) {
        checkRange(offset, Double.BYTES);
        bb.putDouble(offset, d);
    }

    // ------------------------------------------------------------ booleans

    /** A boolean is one byte: 0 is false, anything else is true. */
    @Override
    public synchronized boolean getBoolean(int offset) {
        checkRange(offset, 1);
        return bb.get(offset) != 0;
    }

    @Override
    public synchronized void setBoolean(int offset, boolean d) {
        checkRange(offset, 1);
        bb.put(offset, (byte) (d ? 1 : 0));
    }

    // --------------------------------------------------------------- blobs

    /**
     * Read a variable-length value: the int length, then that many bytes.
     * The length is validated against the remaining space, so reading from an
     * offset that doesn't hold a blob fails with IllegalArgumentException
     * rather than an out-of-bounds error or a huge allocation.
     */
    @Override
    public synchronized byte[] getBytes(int offset) {
        checkRange(offset, Integer.BYTES);
        final int length = bb.getInt(offset);
        if (length < 0) {
            throw new IllegalArgumentException(
                "No blob at offset " + offset + ": length prefix is negative (" + length + ")");
        }
        checkRange(offset + Integer.BYTES, length);
        final byte[] result = new byte[length];
        bb.get(offset + Integer.BYTES, result);
        return result;
    }

    /**
     * Write a variable-length value: the int length, then the bytes
     * immediately after it.
     *
     * @throws IllegalArgumentException if b is null or the length + bytes
     * don't fit between offset and the end of the page
     */
    @Override
    public synchronized void setBytes(int offset, byte[] b) {
        if (b == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        checkRange(offset, Integer.BYTES + b.length);
        bb.putInt(offset, b.length);
        bb.put(offset + Integer.BYTES, b);
    }

    // ------------------------------------------------------------- strings

    /** Read a blob and decode it with the PetiteDB charset. */
    @Override
    public synchronized String getString(int offset) {
        return new String(getBytes(offset), CHARSET);
    }

    /**
     * Encode the string with the PetiteDB charset and store it as a blob.
     * Space is checked against PageBase.maxLength so that the room a client
     * reserves via maxLength is always exactly what the page will accept.
     *
     * @throws IllegalArgumentException if s is null or doesn't fit
     */
    @Override
    public synchronized void setString(int offset, String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        checkRange(offset, maxLength(s.length()));
        setBytes(offset, s.getBytes(CHARSET));
    }

    // ------------------------------------------------------------ internal

    /**
     * Expose the raw buffer to the FileMgr for disk I/O, rewound to the start
     * with the limit at capacity so a channel read/write covers the whole
     * block.  Package-private on purpose: clients must go through the typed
     * API.  Callers must hold this Page's monitor while using the buffer.
     */
    ByteBuffer contents() {
        bb.clear(); // resets position/limit only; the bytes are untouched
        return bb;
    }

    /**
     * Verify that {@code length} bytes starting at {@code offset} lie entirely
     * inside the page.  Uses long arithmetic so a huge length can't overflow
     * int and sneak past the check.
     */
    private void checkRange(int offset, int length) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if ((long) offset + length > bb.capacity()) {
            throw new IllegalArgumentException(
                "Value of " + length + " bytes at offset " + offset
                + " exceeds page size " + bb.capacity());
        }
    }
}
