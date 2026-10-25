package edu.yu.dbimpl.buffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.yu.dbimpl.file.BlockIdBase;
import edu.yu.dbimpl.file.FileMgrBase;
import edu.yu.dbimpl.file.Page;
import edu.yu.dbimpl.file.PageBase;
import edu.yu.dbimpl.log.LogMgr;
import edu.yu.dbimpl.log.LogMgrBase;

/**
 * Buffer: one slot in the BufferMgr's pool.  A Page plus what the manager
 * needs to know about it:
 *
 * - block:    the disk block the page holds (null until first assigned)
 * - pins:     how many clients are using it right now (0 = unpinned)
 * - txnum:    the transaction that modified the page since it was last
 *             written, or -1 if the page matches its disk block ("clean")
 * - lsn:      the newest log record describing those modifications, or -1
 *             if none was supplied
 * - referenced: the CLOCK policy's reference bit (lecture, slide 41), set
 *             on every pin and cleared by the manager's sweep
 *
 * Rules from BufferBase / the buffer_module lecture / the milestone sheet:
 * - The buffer writes its own page (slide 13), but only when the manager
 *   says so: when the buffer is reused for another block, or at flushAll.
 *   Unpinning never writes (slides 14-16, 53).
 * - Write-ahead: before the page goes to disk, the log record named by lsn
 *   must be on disk, so flush() calls logMgr.flush(lsn) first (slide 11).
 * - setModified(txnum, lsn) rejects a negative txnum or lsn, and an lsn
 *   above the newest one the LogMgr has issued.  LogMgrBase has no getter
 *   for that, so the check uses our LogMgr.latestLsn(); with any other
 *   LogMgrBase only the negative check applies.
 * - setModified(txnum) means "modified, but no log record": the page is
 *   still dirty, and an lsn from an earlier modification is kept, since
 *   that record must still reach disk before the page does.
 *
 * Worked example (block size 400, newest LSN issued so far 7):
 *   assignToBlock(students:3)  reads block 3          txnum -1, lsn -1
 *   client changes bytes, setModified(5, 7)           txnum  5, lsn  7
 *   client changes more, setModified(5)               txnum  5, lsn  7
 *   flush()   logMgr.flush(7), then write block 3     txnum -1, lsn -1
 *   flush()   clean: does nothing
 *
 * A block past the end of its file has no bytes to read, and
 * FileMgr.read leaves the page as it was, which would show the previous
 * block's bytes.  Such a block gets a fresh, zeroed Page instead.
 *
 * Thread safety: BufferBase says the BufferMgr controls access, so a
 * Buffer needn't be thread-safe.  The fields are still guarded by this
 * object's monitor, because a client may call setModified on one thread
 * while the manager's flushAll looks at the same buffer on another.  Lock
 * order is always BufferMgr, then Buffer, then LogMgr/Page, so there is no
 * deadlock.
 */
public class Buffer extends BufferBase {

    private static final Logger logger = LogManager.getLogger(Buffer.class);

    private final FileMgrBase fm;
    private final LogMgrBase lm;
    private final int blockSize;

    /** Replaced only while unpinned, for a block past the end of its file. */
    private Page page;

    private BlockIdBase blk;
    private int pins;

    /** Modifying transaction since the last write; -1 when clean. */
    private int txnum = -1;

    /** Newest log record for those modifications; -1 when there is none. */
    private int lsn = -1;

    private boolean referenced;

    /**
     * An unassigned buffer: no block, unpinned, clean.  Normally only the
     * BufferMgr creates these.
     *
     * @throws IllegalArgumentException if fileMgr or logMgr is null
     */
    public Buffer(FileMgrBase fileMgr, LogMgrBase logMgr) {
        super(fileMgr, logMgr); // required by Java: the base class has no no-arg constructor
        if (fileMgr == null) {
            throw new IllegalArgumentException("File manager cannot be null");
        }
        if (logMgr == null) {
            throw new IllegalArgumentException("Log manager cannot be null");
        }
        this.fm = fileMgr;
        this.lm = logMgr;
        this.blockSize = fileMgr.blockSize();
        this.page = new Page(blockSize);
    }

    // ---------------------------------------------------------------- API

    @Override
    public synchronized PageBase contents() {
        return page;
    }

    /** The block this buffer holds, or null if it has never been assigned one. */
    @Override
    public synchronized BlockIdBase block() {
        return blk;
    }

    /**
     * Record that transaction {@code txnum} modified the page, described by
     * log record {@code lsn}.
     *
     * @throws IllegalArgumentException if txnum or lsn is negative, or lsn is
     * greater than the newest LSN the LogMgr has issued
     */
    @Override
    public synchronized void setModified(int txnum, int lsn) {
        checkTxnum(txnum);
        if (lsn < 0) {
            throw new IllegalArgumentException("LSN cannot be negative: " + lsn);
        }
        if (lm instanceof LogMgr) {
            final int latest = ((LogMgr) lm).latestLsn();
            if (lsn > latest) {
                throw new IllegalArgumentException("LSN " + lsn + " was never issued"
                    + (latest < 0 ? " (the log is empty)" : " (highest so far: " + latest + ")"));
            }
        }
        this.txnum = txnum;
        // Keep the newest: flushing the log through it covers every older
        // record of this page too.
        this.lsn = Math.max(this.lsn, lsn);
    }

    /**
     * Record that transaction {@code txnum} modified the page without
     * writing a log record.
     *
     * @throws IllegalArgumentException if txnum is negative
     */
    @Override
    public synchronized void setModified(int txnum) {
        checkTxnum(txnum);
        this.txnum = txnum;
    }

    @Override
    public synchronized boolean isPinned() {
        return pins > 0;
    }

    @Override
    public synchronized String toString() {
        return "Buffer[" + blk + ", pins=" + pins + ", txnum=" + txnum + ", lsn=" + lsn + "]";
    }

    // ------------------------------------------- for the BufferMgr only

    /** The transaction that dirtied the page, or -1 if it is clean. */
    synchronized int modifyingTx() {
        return txnum;
    }

    synchronized void pin() {
        pins++;
        referenced = true;
    }

    /** The caller (BufferMgr) has already checked that the buffer is pinned. */
    synchronized void unpin() {
        pins--;
    }

    synchronized boolean isReferenced() {
        return referenced;
    }

    synchronized void clearReferenced() {
        referenced = false;
    }

    /**
     * If the page is dirty, flush the log through its LSN and then write the
     * page to its block.  A clean page costs nothing.
     */
    synchronized void flush() {
        if (txnum < 0) {
            return;
        }
        if (blk != null) { // setModified on a never-assigned buffer has nothing to write
            if (lsn >= 0) {
                lm.flush(lsn); // write-ahead: the log record first
            }
            fm.write(blk, page);
            logger.debug("wrote {} (txnum {}, lsn {})", blk, txnum, lsn);
        }
        txnum = -1;
        lsn = -1;
    }

    /**
     * Make this unpinned buffer hold {@code newBlk}: write the old page if
     * it is dirty, then read the new block (or start from zeros if the block
     * is past the end of its file).  If the read fails the buffer is left
     * holding no block, so the manager never maps a half-loaded page.
     */
    synchronized void assignToBlock(BlockIdBase newBlk) {
        flush();
        blk = null;
        referenced = false;
        if (newBlk.number() >= fm.length(newBlk.fileName())) {
            page = new Page(blockSize);
        } else {
            fm.read(newBlk, page);
        }
        blk = newBlk;
    }

    // ------------------------------------------------------------ helpers

    private static void checkTxnum(int txnum) {
        if (txnum < 0) {
            throw new IllegalArgumentException("Transaction number cannot be negative: " + txnum);
        }
    }
}
