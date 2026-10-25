package edu.yu.dbimpl.log;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.yu.dbimpl.config.DBConfiguration;
import edu.yu.dbimpl.file.BlockId;
import edu.yu.dbimpl.file.BlockIdBase;
import edu.yu.dbimpl.file.FileMgrBase;
import edu.yu.dbimpl.file.Page;

/**
 * LogMgr: the write-ahead log.  Keeps an append-only sequence of byte-array
 * records in one file, and hands them back newest first.
 *
 * The LogMgr doesn't understand the records (the recovery manager, higher
 * up the stack, does).  To the LogMgr a record is just bytes with an LSN
 * (log sequence number): 0 for the first record ever written to this
 * database, then 1, 2, 3, ... with no gaps, continuing across restarts.
 *
 * Rules from LogMgrBase / the log_module lecture / the milestone sheet:
 * - append never touches the disk unless the current page has no room for
 *   the record; then it writes that page once and continues in a new block.
 * - flush(lsn) puts that record on disk (with everything else on its page),
 *   is a no-op if it's already there or if lsn is negative (no such record),
 *   and rejects an LSN above the highest one issued.
 * - iterator() flushes first, then walks every record newest to oldest.
 * - After a restart the LSN continues from where the previous instance
 *   left off, and every persisted record is still readable.
 * - A record larger than a page is an IllegalArgumentException; metadata
 *   per record may not exceed 8 bytes.
 * - Thread-safe, except the iterator (recovery is single-threaded).
 *
 * Layout of one block (the lecture's suggestion; file_module rules apply
 * because every byte goes through Page):
 *
 *   offset 0        offset boundary                                 end
 *   +----------+----//----+----------------+-----//-----+-----------------+
 *   | boundary |   free   | newest record  |    ...     | oldest record   |
 *   +----------+----//----+----------------+-----//-----+-----------------+
 *
 * - boundary (an int at offset 0) is the offset where the newest record
 *   starts; blockSize when the block is empty.  Per-block metadata: 4 bytes.
 * - Each record is stored by Page.setBytes: a 4-byte length, then the bytes.
 *   Per-record metadata: 4 bytes.  So the largest record is
 *   blockSize - 8 bytes, exactly the Javadoc's "reasonable" limit.
 * - Records are placed RIGHT TO LEFT: each new one goes just below the
 *   previous one.  Reading a block left to right from the boundary then
 *   yields newest first, which is the only order anyone wants.
 *
 * Worked example, block size 32.  Append A (6 bytes), B (9 bytes):
 *   A needs 10 bytes -> offset 22, boundary 22
 *   B needs 13 bytes -> offset  9, boundary  9
 * Append C (3 bytes, needs 7): 9 - 7 = 2 < 4, it would overlap the header,
 * so the page is written to block 0 and C starts block 1 at offset 25.
 * Iterating: block 1 from 25 gives C; then block 0 from 9 gives B, then A.
 *
 * Memory: exactly one page, holding the last block of the file (lecture:
 * the log is sequential, so there's nothing else worth caching).  The LogMgr
 * remembers nextLsn (the LSN the next append will return) and
 * lastFlushedLsn (the highest LSN known to be on disk; everything at or
 * below it is in a block that was written).
 *
 * Restart: the constructor reads every block of the existing log once,
 * counts the records, and resumes numbering from the count.  That works
 * because LSNs are dense from 0 and only persisted records survive.  The
 * last block becomes the in-memory page.
 *
 * Disk access per operation: append costs 0 normally, 2 when a page fills
 * (write the full page, append a fresh block); flush costs 1 or 0; the
 * constructor on an existing log costs one read per block.
 */
public class LogMgr extends LogMgrBase {

    private static final Logger logger = LogManager.getLogger(LogMgr.class);

    /** Size of the per-block header: the boundary int at offset 0. */
    private static final int HEADER = Integer.BYTES;

    /** Bytes Page.setBytes adds in front of a record: its length. */
    private static final int LENGTH_PREFIX = Integer.BYTES;

    private final FileMgrBase fm;
    private final String logfile;
    private final int blockSize;

    /** Largest record the layout can hold: a block minus header and length prefix. */
    private final int maxRecordSize;

    /** The one page: always the in-memory copy of the last block of the log. */
    private final Page page;

    /** The block that {@link #page} mirrors. */
    private BlockIdBase currentBlk;

    /** LSN the next append will return. */
    private int nextLsn;

    /** Highest LSN whose record is on disk; -1 when nothing is. */
    private int lastFlushedLsn;

    /**
     * Open (or create) the log file {@code logfile} through {@code fm}.
     *
     * @throws IllegalArgumentException if fm is null or logfile is null/blank
     * @throws IllegalStateException if DBConfiguration can't say whether this
     * is a new database (propagated from DBConfiguration.isDBStartup)
     */
    public LogMgr(FileMgrBase fm, String logfile) {
        super(fm, logfile); // required by Java: the base class has no no-arg constructor
        if (fm == null) {
            throw new IllegalArgumentException("File manager cannot be null");
        }
        if (logfile == null || logfile.trim().isEmpty()) {
            throw new IllegalArgumentException("Log file name cannot be null or blank");
        }
        // Required by the Javadoc.  The FileMgr has already wiped the
        // directory for a new database, so the value is only informational
        // here, but the IllegalStateException for a missing setting must escape.
        final boolean isNew = DBConfiguration.INSTANCE.get().isDBStartup();

        this.fm = fm;
        this.logfile = logfile;
        this.blockSize = fm.blockSize();
        this.maxRecordSize = blockSize - HEADER - LENGTH_PREFIX;
        if (maxRecordSize <= 0) {
            throw new IllegalArgumentException(
                "Block size " + blockSize + " is too small to hold any log record");
        }
        this.page = new Page(blockSize);

        final int blocks = fm.length(logfile);
        if (blocks == 0) {
            // No log yet: "created with an empty first block".  Writing block
            // 0 creates the file; the page is all zeros, so set the boundary
            // to make it a well-formed empty block on disk.
            currentBlk = new BlockId(logfile, 0);
            setBoundary(blockSize);
            fm.write(currentBlk, page);
            nextLsn = 0;
            lastFlushedLsn = -1;
        } else {
            // Existing log: count the records in every block to recover the
            // LSN, and keep the last block as the working page.
            int count = 0;
            for (int b = 0; b < blocks; b++) {
                currentBlk = new BlockId(logfile, b);
                fm.read(currentBlk, page);
                count += countRecords();
            }
            // The loop left the LAST block in the page.  A block appended but
            // never written has boundary 0 on disk; normalise it to "empty".
            if (boundary() == 0) {
                setBoundary(blockSize);
            }
            nextLsn = count;
            lastFlushedLsn = count - 1;
        }
        logger.info("LogMgr started: file={}, mode={}, blocks={}, nextLsn={}",
            logfile, isNew ? "new database" : "existing database", fm.length(logfile), nextLsn);
    }

    // ---------------------------------------------------------------- API

    /**
     * Append a record and return its LSN.  Goes to disk only when the current
     * page can't hold the record: then the page is written and a fresh block
     * is started.
     *
     * @throws IllegalArgumentException if logrec is null or larger than
     * blockSize - 8 bytes
     */
    @Override
    public synchronized int append(byte[] logrec) {
        if (logrec == null) {
            throw new IllegalArgumentException("Log record cannot be null");
        }
        if (logrec.length > maxRecordSize) {
            throw new IllegalArgumentException("Log record of " + logrec.length
                + " bytes exceeds the maximum of " + maxRecordSize
                + " (block size " + blockSize + " minus 8 bytes of metadata)");
        }
        final int needed = LENGTH_PREFIX + logrec.length;
        int boundary = boundary();
        if (boundary - needed < HEADER) {
            // No room below the newest record without overlapping the header:
            // persist this page and start a new block (lecture, algorithm 3).
            writePage();
            currentBlk = fm.append(logfile);
            boundary = blockSize;
            setBoundary(boundary);
            logger.debug("log page full; continuing in {}", currentBlk);
        }
        final int recpos = boundary - needed;
        page.setBytes(recpos, logrec); // writes the length prefix, then the bytes
        setBoundary(recpos);
        return nextLsn++;
    }

    /**
     * Make sure record {@code lsn} is on disk.  No-op if it already is.
     *
     * A negative LSN is also a no-op: flush means "put every record with LSN
     * <= lsn on disk" (lecture, slide 32), and no record has a negative LSN,
     * so there is nothing to do.  The textbook buffer manager relies on this
     * by passing -1 for a page it modified without writing a log record.
     * Only an LSN above the highest issued is an error: it could not have
     * come from this log manager.
     *
     * @throws IllegalArgumentException if lsn is greater than the highest
     * LSN issued so far
     */
    @Override
    public synchronized void flush(int lsn) {
        if (lsn > nextLsn - 1) {
            throw new IllegalArgumentException("LSN " + lsn + " was never issued"
                + (nextLsn == 0 ? " (the log is empty)" : " (highest so far: " + (nextLsn - 1) + ")"));
        }
        if (lsn <= lastFlushedLsn) {
            return; // already on disk (or negative: nothing to flush): the Javadoc's required no-op
        }
        writePage();
    }

    /**
     * Flush, then return an iterator over the whole log, newest record
     * first.  The iterator reads from disk with its own page, so appends may
     * continue while it runs; it is not itself thread-safe.
     */
    @Override
    public synchronized Iterator<byte[]> iterator() {
        if (lastFlushedLsn < nextLsn - 1) {
            writePage(); // only if something is still in memory
        }
        return new LogIterator(currentBlk);
    }

    // ------------------------------------------------------------ helpers

    /** Write the page to its block; everything issued so far is then on disk. */
    private void writePage() {
        fm.write(currentBlk, page);
        lastFlushedLsn = nextLsn - 1;
        logger.debug("wrote log page {} (LSNs through {})", currentBlk, lastFlushedLsn);
    }

    private int boundary() {
        return page.getInt(0);
    }

    private void setBoundary(int boundary) {
        page.setInt(0, boundary);
    }

    /**
     * Number of records in the block currently in the page, by walking from
     * the boundary to the end.  Boundary 0 means a block that was appended
     * (all zeros) but never written: no records.
     *
     * @throws IllegalStateException if the block's layout is not one this
     * class could have produced
     */
    private int countRecords() {
        final int boundary = boundary();
        if (boundary == 0) {
            return 0;
        }
        if (boundary < HEADER || boundary > blockSize) {
            throw new IllegalStateException("Corrupt log block " + currentBlk + ": boundary " + boundary);
        }
        int count = 0;
        int pos = boundary;
        while (pos < blockSize) {
            final int length = page.getInt(pos);
            if (length < 0 || pos + LENGTH_PREFIX + length > blockSize) {
                throw new IllegalStateException(
                    "Corrupt log block " + currentBlk + ": record length " + length + " at offset " + pos);
            }
            pos += LENGTH_PREFIX + length;
            count++;
        }
        return count;
    }

    // ----------------------------------------------------------- iterator

    /**
     * Walks the log newest record first.  Holds one page: the block the
     * current record is in.  Within a block it moves left to right from the
     * boundary (newest to oldest, because records were placed right to
     * left); at the end of a block it steps back to the previous block.
     *
     * Private on purpose (lecture, slide 36): clients only ever see an
     * Iterator<byte[]>.
     */
    private final class LogIterator implements Iterator<byte[]> {

        private final Page buf = new Page(blockSize);
        private BlockIdBase blk;

        /** Offset of the next record to return; blockSize when this block is used up. */
        private int pos;

        LogIterator(BlockIdBase last) {
            moveTo(last);
        }

        private void moveTo(BlockIdBase target) {
            blk = target;
            fm.read(blk, buf);
            final int boundary = buf.getInt(0);
            pos = (boundary == 0) ? blockSize : boundary; // 0 = appended-but-unwritten block: empty
        }

        /**
         * True while a record remains in this block or any earlier one.
         * Steps back over empty trailing blocks (only possible after a crash
         * between appending a block and writing it) so that next() never
         * lands on one.
         */
        @Override
        public boolean hasNext() {
            while (pos >= blockSize && blk.number() > 0) {
                moveTo(new BlockId(blk.fileName(), blk.number() - 1));
            }
            return pos < blockSize;
        }

        @Override
        public byte[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No earlier log record");
            }
            final byte[] rec = buf.getBytes(pos); // the client's bytes only; the length prefix stays behind
            pos += LENGTH_PREFIX + rec.length;
            return rec;
        }
    }
}
