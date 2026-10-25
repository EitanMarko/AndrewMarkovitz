package edu.yu.dbimpl.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.yu.dbimpl.config.DBConfiguration;

/**
 * FileMgr: the one class in PetiteDB that touches the disk.
 *
 * It owns the database directory and presents every file in it as a sequence
 * of fixed-size blocks.  Block n of a file is the byte range
 * [n * blockSize, (n + 1) * blockSize), and all I/O moves exactly one such
 * range between a Page and the file (lecture: "combination approach" — use
 * the OS file system, but only ever at logical block granularity).
 *
 * Rules from FileMgrBase / the file_module lecture:
 * - The DBConfiguration singleton says whether this is a brand-new database
 *   (wipe anything already in the directory) or a reopened one (touch
 *   nothing).  If it can't say, IllegalStateException propagates.
 * - Files are created on demand: naming a file that doesn't exist creates it.
 * - read/write/append each cost exactly one disk access.
 * - Reading a block that doesn't exist is allowed, creates nothing, and leaves
 *   the page content undefined.  Writing past the end of a file extends it,
 *   zero-filling any blocks in between.  A never-written file has length 0.
 * - No checked exceptions escape: IOExceptions become RuntimeExceptions.
 *
 * Implementation:
 * - One FileChannel per file, opened on first use and kept in a cache that
 *   holds at most MAX_OPEN_FILES channels.  The operating system lends a
 *   process only so many open files at once (about 10,000 on macOS), and a
 *   database may name far more files than that over its life, so the cache
 *   closes the least-recently-used channel when it needs room and simply
 *   reopens a file if it is named again.  A channel that a transfer is using
 *   right now is never closed (see OpenFile.inUse).  Channels are opened
 *   with SYNC (the lecture's "rws"): every write reaches the device before
 *   the call returns, which the write-ahead-logging layer will depend on.
 * - read/write use FileChannel's POSITIONAL read/write.  These are single
 *   calls and touch no shared file cursor, so transfers of different blocks
 *   never get in each other's way.
 * - Transfers of the SAME block are guarded by a read/write lock: a write
 *   excludes every other transfer of that block, reads share.  Without it,
 *   whether a read can see half of a concurrent write is up to the operating
 *   system (macOS happens to prevent it; Linux doesn't promise to).  The
 *   locks are "striped": a fixed array of them, with each block hashed to
 *   one, so unrelated blocks rarely share a lock and no per-block state
 *   accumulates.
 * - The Page's monitor is held during a transfer so no Page setter can run
 *   while its bytes are in flight (Page's methods synchronize on the same
 *   monitor).
 * - append and length take a per-file lock (striped by file NAME, not by
 *   channel, because a channel can be closed and reopened), so "find the
 *   end, then write there" is atomic per file.
 * - Lock order is always: file lock (append/length only), then the cache
 *   lock (held only for the lookup, never during any disk call), then page monitor,
 *   then block lock.  Every path takes them in that order, so no two calls
 *   can deadlock.
 *
 * Thread-safe.  Exactly one instance per database (the client's job).
 */
public class FileMgr extends FileMgrBase {

    private static final Logger logger = LogManager.getLogger(FileMgr.class);

    /** Root directory holding every database file. */
    private final File dbDirectory;

    /** Size of every block in every file managed by this instance. */
    private final int blockSize;

    /**
     * Most channels the cache keeps open at once.  Far below any operating
     * system's per-process limit, and far above the number of files a
     * database has in play at one moment, so the cache almost never has to
     * evict.  One constant to raise if a workload ever cycles through more
     * files than this.
     */
    static final int MAX_OPEN_FILES = 256;

    /**
     * An open file: its channel plus the number of transfers using it right
     * now.  The count is guarded by the openFiles lock; eviction skips any
     * entry whose count is above zero, so a channel is never closed while a
     * read/write/append/length is in the middle of using it.
     */
    private static final class OpenFile {
        final FileChannel channel;
        int inUse;

        OpenFile(FileChannel channel) {
            this.channel = channel;
        }
    }

    /**
     * Open channels, keyed by file name (relative to dbDirectory), in
     * least-recently-used order: the third constructor argument ("access
     * order") makes every get() move its entry to the end, so the first
     * entry is always the one used longest ago.  All access is under the
     * map's own monitor, which is held only for lookups, opens, and closes,
     * never while a block is being transferred.
     */
    private final LinkedHashMap<String, OpenFile> openFiles =
        new LinkedHashMap<>(2 * MAX_OPEN_FILES, 0.75f, true);

    /** Number of block locks and of file locks; a power of two so a hash maps to one by masking. */
    private static final int LOCK_STRIPES = 64;

    /**
     * The block locks.  A block always maps to the same lock (see
     * blockLock), so two transfers of one block always meet on it.  Two
     * different blocks may share a lock; that costs a little concurrency
     * but never correctness.
     */
    private final ReadWriteLock[] blockLocks = new ReadWriteLock[LOCK_STRIPES];

    /**
     * The file locks, for append and length.  A file always maps to the
     * same lock (see fileLock), keyed by its NAME so the lock survives the
     * file's channel being closed and reopened by the cache.
     */
    private final Lock[] fileLocks = new Lock[LOCK_STRIPES];

    /**
     * Create the file manager for {@code dbDirectory}, creating the directory
     * if needed and wiping its contents when DBConfiguration says this is a
     * brand-new database.
     *
     * @throws IllegalArgumentException if the directory is null or the block
     * size is not positive
     * @throws IllegalStateException if DBConfiguration has no "db.startup"
     * setting (propagated from DBConfiguration.isDBStartup)
     * @throws RuntimeException if the directory can't be created or cleaned
     */
    public FileMgr(File dbDirectory, int blocksize) {
        super(dbDirectory, blocksize); // required by Java: the base class has no no-arg constructor
        if (dbDirectory == null) {
            throw new IllegalArgumentException("Database directory cannot be null");
        }
        if (blocksize <= 0) {
            throw new IllegalArgumentException("Block size must be positive: " + blocksize);
        }
        // Ask the configuration BEFORE touching the disk: if it can't answer,
        // the IllegalStateException must escape with nothing created.
        final boolean isNew = DBConfiguration.INSTANCE.get().isDBStartup();

        this.dbDirectory = dbDirectory;
        this.blockSize = blocksize;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            blockLocks[i] = new ReentrantReadWriteLock();
            fileLocks[i] = new ReentrantLock();
        }

        // A regular file sitting where the directory should be can't be used.
        if (dbDirectory.exists() && !dbDirectory.isDirectory()) {
            throw new RuntimeException("Not a directory: " + dbDirectory);
        }

        int deleted = 0;
        // New database: a previous run's files must not be mistaken for live
        // data, so clear them.  Existing database: never touch them.
        if (isNew && dbDirectory.exists()) {
            deleted = deleteContents(dbDirectory.toPath());
        }
        // mkdirs (not mkdir) also creates missing parent directories.
        if (!dbDirectory.exists() && !dbDirectory.mkdirs()) {
            throw new RuntimeException("Cannot create database directory: " + dbDirectory);
        }

        // One field per line, so long paths don't bury the other values.
        logger.info("FileMgr started: directory={}\n(absolute={}),\nblockSize={},\nmode={},\nfilesDeleted={}",
            dbDirectory, dbDirectory.getAbsolutePath(), blocksize,
            isNew ? "new database" : "existing database", deleted);
    }

    // ---------------------------------------------------------------- API

    /**
     * Read block {@code blk} into {@code p} with a single positional read.
     * If the block doesn't exist yet, nothing is created and the page's
     * content is left undefined (lecture, slide 49).
     */
    @Override
    public void read(BlockIdBase blk, PageBase p) {
        final Page page = checkArgs(blk, p);
        final OpenFile file = acquire(blk.fileName()); // opens/creates the file on first use
        try {
            final long position = position(blk);         // byte offset of the block in the file
            // Hold the page's lock for the whole transfer: Page's setters lock on
            // the same monitor, so none can change the bytes while they're in flight.
            synchronized (page) {
                final ByteBuffer buf = page.contents(); // position 0, limit = block size
                // Shared lock: other reads of this block may run alongside, but a
                // write of it can't, so we never see a half-written block.
                final Lock lock = blockLock(blk.fileName(), blk.number()).readLock();
                lock.lock();
                try {
                    int n;
                    // A regular file normally fills the buffer in one call; the
                    // loop only guards against the rare short read.
                    // channel.read(buf, pos) fills buf from file position pos and
                    // advances buf.position() by the bytes read, so the next
                    // iteration (if any) continues at position + buf.position().
                    // It returns -1 at end of file: a block that doesn't exist
                    // leaves buf as it was, which is the "undefined" result.
                    while (buf.hasRemaining() && (n = file.channel.read(buf, position + buf.position())) >= 0) {
                        if (n == 0) {
                            break; // nothing more is coming; avoid spinning forever
                        }
                    }
                } catch (IOException e) {
                    logger.error("read failed for {}", blk, e);
                    throw new RuntimeException("Cannot read block " + blk, e);
                } finally {
                    lock.unlock();
                }
            }
        } finally {
            release(file);
        }
        logger.debug("read {}", blk);
    }

    /**
     * Write {@code p} to block {@code blk} with a single positional write.
     * Writing beyond the current end of the file extends it; the OS zero-fills
     * any blocks in between (lecture, slide 49).
     */
    @Override
    public void write(BlockIdBase blk, PageBase p) {
        final Page page = checkArgs(blk, p);
        final OpenFile file = acquire(blk.fileName());
        try {
            final long position = position(blk);
            synchronized (page) {
                final ByteBuffer buf = page.contents();
                // Exclusive lock: no other read or write of this block can
                // overlap with this one.
                final Lock lock = blockLock(blk.fileName(), blk.number()).writeLock();
                lock.lock();
                try {
                    // channel.write(buf, pos) writes buf's remaining bytes at file
                    // position pos and advances buf.position().  One call normally
                    // writes the whole block; the loop finishes a partial write.
                    // Because the channel was opened with SYNC, the bytes are on
                    // the device when write() returns.
                    while (buf.hasRemaining()) {
                        file.channel.write(buf, position + buf.position());
                    }
                } catch (IOException e) {
                    logger.error("write failed for {}", blk, e);
                    throw new RuntimeException("Cannot write block " + blk, e);
                } finally {
                    lock.unlock();
                }
            }
        } finally {
            release(file);
        }
        logger.debug("wrote {}", blk);
    }

    /**
     * Allocate a new zero-filled block at the end of {@code filename} and
     * write it to disk.  "Find the end" and "write there" happen under the
     * file's lock so two concurrent appends can't claim the same block.
     */
    @Override
    public BlockIdBase append(String filename) {
        checkName(filename);
        final BlockId blk;
        // The file lock makes "measure the file, then write past its end"
        // atomic per file: length() takes the same lock.
        final Lock fileLock = fileLock(filename);
        fileLock.lock();
        try {
            final OpenFile file = acquire(filename);
            try {
                // Integer division: the file is always a whole number of
                // blocks, so this is the number of the first block past the end.
                final int blknum = (int) (file.channel.size() / blockSize);
                blk = new BlockId(filename, blknum);
                // A fresh ByteBuffer is all zeros, so this writes a zeroed block.
                final ByteBuffer zeros = ByteBuffer.allocate(blockSize);
                final long position = position(blk);
                // The new block is a block like any other: a client may be
                // writing this same block number "past the end" right now,
                // so take its lock for the write.
                final Lock lock = blockLock(filename, blknum).writeLock();
                lock.lock();
                try {
                    while (zeros.hasRemaining()) {
                        file.channel.write(zeros, position + zeros.position());
                    }
                } finally {
                    lock.unlock();
                }
            } catch (IOException e) {
                logger.error("append failed for file {}", filename, e);
                throw new RuntimeException("Cannot append to file " + filename, e);
            } finally {
                release(file);
            }
        } finally {
            fileLock.unlock();
        }
        logger.debug("appended {}", blk);
        return blk;
    }

    /**
     * Number of blocks in {@code filename}: 0 if it was never written to.
     * Naming the file creates it (empty) if it doesn't exist.
     */
    @Override
    public int length(String filename) {
        checkName(filename);
        final Lock fileLock = fileLock(filename);
        fileLock.lock(); // same lock as append, so the size can't change mid-call
        try {
            final OpenFile file = acquire(filename);
            try {
                return (int) (file.channel.size() / blockSize); // size is in bytes; 0 bytes -> 0 blocks
            } catch (IOException e) {
                logger.error("length failed for file {}", filename, e);
                throw new RuntimeException("Cannot determine length of file " + filename, e);
            } finally {
                release(file);
            }
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public int blockSize() {
        return blockSize;
    }

    /**
     * Close every open file, releasing the OS file handles.  Package-private
     * on purpose: it is not part of the FileMgrBase API, which has no notion
     * of shutting down.  Tests need it because Windows refuses to delete a
     * file (or the directory holding it) while a handle to it is open, so a
     * test's temp directory can't be cleaned up until the manager lets go.
     *
     * Not an end-of-life: a later read/write/append/length simply reopens
     * the file on demand.  Must not run concurrently with other operations
     * (a transfer in flight on a closed channel fails with
     * ClosedChannelException, wrapped in a RuntimeException).
     */
    void close() {
        synchronized (openFiles) {
            final Iterator<Map.Entry<String, OpenFile>> it = openFiles.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<String, OpenFile> entry = it.next();
                it.remove(); // drop the entry even if the close below fails, so a retry won't repeat it
                closeChannel(entry.getKey(), entry.getValue().channel);
            }
        }
    }

    /**
     * Number of files the cache currently holds open.  Package-private, for
     * tests only (like close()): it lets a test check that the cache never
     * grows past MAX_OPEN_FILES.
     */
    int openFileCount() {
        synchronized (openFiles) {
            return openFiles.size();
        }
    }

    /**
     * Whether {@code filename} is open right now.  Package-private, for
     * tests only: it lets a test check which file the cache chose to evict.
     * Deliberately does NOT use get(), which would count as a use and
     * change the eviction order.
     */
    boolean isOpen(String filename) {
        synchronized (openFiles) {
            return openFiles.containsKey(filename);
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * Return the open file for {@code filename}, opening (and creating) it on
     * first use or after the cache evicted it, and mark it in use so the
     * cache won't close it until the matching release().  Every caller must
     * release() in a finally block.
     *
     * The disk calls (open, and the close of an evicted file) run OUTSIDE
     * the cache lock, so twenty threads opening twenty different files do so
     * in parallel rather than one at a time.  The price is a possible race:
     * two threads may open the same uncached file at once.  The second to
     * reach the lock sees the first one's entry, closes its own duplicate
     * channel, and uses the cached one.
     */
    private OpenFile acquire(String filename) {
        checkName(filename);
        synchronized (openFiles) {
            // get() on an access-ordered LinkedHashMap also moves the entry to
            // the "most recently used" end.
            final OpenFile cached = openFiles.get(filename);
            if (cached != null) {
                cached.inUse++;
                return cached;
            }
        }
        // Not cached: open it without holding the lock (a disk access).
        final FileChannel opened = open(filename);
        OpenFile file;
        FileChannel duplicate = null;        // our channel, if another thread beat us to it
        Map.Entry<String, OpenFile> evicted = null; // whatever we pushed out to make room
        synchronized (openFiles) {
            file = openFiles.get(filename);
            if (file != null) {
                duplicate = opened; // lost the race; close ours below, use theirs
            } else {
                if (openFiles.size() >= MAX_OPEN_FILES) {
                    evicted = evictOne();
                }
                file = new OpenFile(opened);
                openFiles.put(filename, file);
            }
            file.inUse++;
        }
        // Closes are disk accesses too: do them after the lock is released.
        if (duplicate != null) {
            closeChannel(filename, duplicate);
        }
        if (evicted != null) {
            closeChannel(evicted.getKey(), evicted.getValue().channel);
            logger.debug("closed file {} to make room in the cache", evicted.getKey());
        }
        return file;
    }

    /** Undo acquire(): the transfer is over, so the cache may close this file if it needs room. */
    private void release(OpenFile file) {
        synchronized (openFiles) {
            file.inUse--;
        }
    }

    /**
     * Remove and return the least-recently-used file that no transfer is
     * using, or null if every cached file is in use at this instant (only
     * possible with more than MAX_OPEN_FILES concurrent transfers; then the
     * cache briefly exceeds its cap, which is harmless).  Caller holds the
     * openFiles lock and closes the returned channel after releasing it.
     */
    private Map.Entry<String, OpenFile> evictOne() {
        final Iterator<Map.Entry<String, OpenFile>> it = openFiles.entrySet().iterator();
        while (it.hasNext()) { // iterates oldest first
            final Map.Entry<String, OpenFile> entry = it.next();
            if (entry.getValue().inUse == 0) {
                // Copy the pair first: a LinkedHashMap entry is not safe to use after removal.
                final Map.Entry<String, OpenFile> victim = Map.entry(entry.getKey(), entry.getValue());
                it.remove();
                return victim;
            }
        }
        logger.debug("every cached file is in use; cache temporarily holds {} files", openFiles.size());
        return null;
    }

    /** Open (creating if needed) {@code filename} under dbDirectory. */
    private FileChannel open(String filename) {
        final Path path = new File(dbDirectory, filename).toPath(); // dbDirectory/filename
        try {
            // READ+WRITE: random access in both directions.
            // CREATE: make the file if it's missing ("files on demand").
            // SYNC: every write reaches the device before returning (the
            //       lecture's "rws" mode), so the log layer can rely on it.
            final FileChannel ch = FileChannel.open(path,
                StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.SYNC);
            logger.info("opened file: {}\nblocks={}", filename, ch.size() / blockSize);
            return ch;
        } catch (IOException e) {
            logger.error("cannot open file {}", path, e);
            throw new RuntimeException("Cannot open file " + path, e);
        }
    }

    private void closeChannel(String filename, FileChannel channel) {
        try {
            channel.close();
            logger.debug("closed file: {}", filename);
        } catch (IOException e) {
            logger.error("cannot close file {}", filename, e);
            throw new RuntimeException("Cannot close file " + filename, e);
        }
    }

    private static void checkName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or blank");
        }
    }

    /**
     * Validate a read/write call and return the page as our Page type, which
     * is the only implementation whose bytes the FileMgr can reach.
     */
    private Page checkArgs(BlockIdBase blk, PageBase p) {
        if (blk == null) {
            throw new IllegalArgumentException("BlockId cannot be null");
        }
        if (p == null) {
            throw new IllegalArgumentException("Page cannot be null");
        }
        // The API takes any PageBase, but only our Page exposes contents();
        // a foreign implementation gives us no way to reach its bytes.
        if (!(p instanceof Page)) {
            throw new IllegalArgumentException("Unsupported PageBase implementation: " + p.getClass());
        }
        final Page page = (Page) p;
        final int capacity;
        synchronized (page) { // contents() must be called under the page's lock
            capacity = page.contents().capacity();
        }
        // A page of the wrong size would read/write a partial or oversized block.
        if (capacity != blockSize) {
            throw new IllegalArgumentException(
                "Page size " + capacity + " does not match block size " + blockSize);
        }
        return page;
    }

    /**
     * The lock guarding transfers of one block.  Computed from the file name
     * and block number only, so every caller naming the same block gets the
     * same lock.
     */
    private ReadWriteLock blockLock(String fileName, int blknum) {
        int h = 31 * fileName.hashCode() + blknum;
        h ^= (h >>> 16); // fold the high bits in, so the mask below sees them
        return blockLocks[h & (LOCK_STRIPES - 1)];
    }

    /** The lock serializing append and length on one file, chosen by the file's name. */
    private Lock fileLock(String fileName) {
        int h = fileName.hashCode();
        h ^= (h >>> 16);
        return fileLocks[h & (LOCK_STRIPES - 1)];
    }

    /** Byte offset of a block within its file; long so large files don't overflow. */
    private long position(BlockIdBase blk) {
        // Cast BEFORE multiplying: int * int would overflow past 2 GB.
        return (long) blk.number() * blockSize;
    }

    /**
     * Delete everything inside {@code dir} (but not dir itself), deepest
     * entries first.  Returns the number of entries deleted.
     */
    private static int deleteContents(Path dir) {
        int count = 0;
        // Files.walk lists dir and everything under it, parents before children.
        // Reverse order puts children first, because a directory can only be
        // deleted once it's empty.  try-with-resources closes the stream.
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (p.equals(dir)) {
                    continue; // the walk includes dir itself; keep it
                }
                Files.delete(p);
                count++;
                logger.debug("deleted {}", p);
            }
        } catch (IOException e) {
            logger.error("cannot clean database directory {}", dir, e);
            throw new RuntimeException("Cannot clean database directory " + dir, e);
        }
        return count;
    }
}
