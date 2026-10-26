package edu.yu.dbimpl.buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.yu.dbimpl.config.DBConfiguration;
import edu.yu.dbimpl.file.BlockIdBase;
import edu.yu.dbimpl.file.FileMgrBase;
import edu.yu.dbimpl.log.LogMgrBase;

/**
 * BufferMgr: a fixed pool of Buffers and the pinning protocol over them
 * (buffer_module lecture, slides 7-16 and 43-53).  From here up, nothing in
 * PetiteDB reads or writes a user-data block except through this class.
 *
 * Rules from BufferMgrBase / the lecture / the milestone sheet:
 * - pin(blk) runs the four scenarios of slides 9-12, in order:
 *     I/II  the block is already in a buffer (pinned or not): pin that
 *           buffer again and return it, no disk access;
 *     III   the block is not in memory but some buffer is unpinned: choose
 *           one by the eviction policy, write its page if dirty (the Buffer
 *           flushes the log first), read the block into it, pin it;
 *     IV    nothing is unpinned: wait until an unpin, then try again from
 *           the top; after maxWaitTime without success, BufferAbortException.
 * - unpin never touches the disk.  When a pin count reaches zero every
 *   waiting pin() is woken (Javadoc: "inform all clients").
 * - Disk access only when necessary (slides 14-16, 53): a read only inside
 *   pin for a block that isn't resident; a write only inside pin, for a
 *   dirty page being replaced, or inside flushAll.
 * - Default policy NAIVE (slide 44); both NAIVE and CLOCK are required.
 * - The constructor must access the DBConfiguration singleton.  The pool
 *   holds no persistent state, so there is nothing to (re)initialize, but
 *   a missing setting surfaces as DBConfiguration's IllegalStateException.
 * - Thread-safe: every public method synchronizes on the manager, and the
 *   waiting is the consumer/producer pattern (wait/notifyAll) of slide 45.
 *
 * Eviction policies (both only ever consider UNPINNED buffers):
 * - NAIVE (slide 28): scan the pool from buffer 0, take the first unpinned.
 * - CLOCK (slide 41): a hand circles the pool, starting just after the
 *   previous replacement.  Each pin sets the buffer's reference bit.  The
 *   sweep skips a pinned buffer; clears the bit of an unpinned buffer whose
 *   bit is set (a second chance); and takes the first unpinned buffer whose
 *   bit is already clear.  A buffer that has never held a block counts as
 *   unpinned with its bit clear, so an empty pool fills 0, 1, 2, ... in
 *   order under either policy, as in the lecture's table.
 *
 * Worked example (slides 22-40), 4 buffers:
 *   pin 10, 20, 30, 40; unpin 20; pin 50; unpin 40, 10, 30, 50
 *   -> pool [10, 50, 30, 40], all unpinned; 50 replaced 20 in buffer 1
 *   then pin 60, pin 70:
 *   NAIVE: 60 -> buffer 0, 70 -> buffer 1              -> [60, 70, 30, 40]
 *   CLOCK: hand is at buffer 2 (after the replacement in buffer 1).
 *          Sweep: 2 and 3 have bits set (pinned earlier), clear them;
 *          0 and 1 likewise; back at 2 with a clear bit -> 60 -> buffer 2;
 *          hand now 3, clear -> 70 -> buffer 3           -> [10, 50, 60, 70]
 *
 * Memory: nBuffers pages, plus a map from block to buffer so a hit costs
 * one lookup rather than a scan.  The map is keyed by (file, block number)
 * rather than by the BlockIdBase object, so it works for any BlockIdBase
 * a caller passes.
 */
public class BufferMgr extends BufferMgrBase {

    private static final Logger logger = LogManager.getLogger(BufferMgr.class);

    private final EvictionPolicy policy;
    private final long maxWaitNanos;

    private final Buffer[] pool;

    /** Which buffer holds which block, for every buffer that holds one. */
    private final Map<Key, Buffer> resident = new HashMap<>();

    /** Number of buffers with pin count 0 (so available() is O(1)). */
    private int available;

    /** CLOCK: the buffer where the next sweep starts. */
    private int hand;

    /** A block's identity: file name and block number. */
    private record Key(String file, int number) {
        static Key of(BlockIdBase blk) {
            return new Key(blk.fileName(), blk.number());
        }
    }

    /**
     * A pool of {@code nBuffers} buffers using the NAIVE policy.
     *
     * @throws IllegalArgumentException if nBuffers or maxWaitTime is not
     * positive, or (via Buffer) if fileMgr or logMgr is null
     * @throws IllegalStateException if DBConfiguration has no startup setting
     */
    public BufferMgr(FileMgrBase fileMgr, LogMgrBase logMgr, int nBuffers, int maxWaitTime) {
        this(fileMgr, logMgr, nBuffers, maxWaitTime, EvictionPolicy.NAIVE);
    }

    /**
     * A pool of {@code nBuffers} buffers using the given policy.
     *
     * @throws IllegalArgumentException if nBuffers or maxWaitTime is not
     * positive, policy is null, or (via Buffer) fileMgr or logMgr is null
     * @throws IllegalStateException if DBConfiguration has no startup setting
     */
    public BufferMgr(FileMgrBase fileMgr, LogMgrBase logMgr, int nBuffers, int maxWaitTime,
                     EvictionPolicy policy) {
        super(fileMgr, logMgr, nBuffers, maxWaitTime, policy); // required by Java: no no-arg base constructor
        if (nBuffers <= 0) {
            throw new IllegalArgumentException("nBuffers must be greater than 0: " + nBuffers);
        }
        if (maxWaitTime <= 0) {
            throw new IllegalArgumentException("maxWaitTime must be greater than 0: " + maxWaitTime);
        }
        if (policy == null) {
            throw new IllegalArgumentException("Eviction policy cannot be null");
        }
        // Required by the Javadoc.  The pool is purely in-memory, so the
        // value is informational, but the IllegalStateException for a
        // missing setting must escape.
        final boolean isNew = DBConfiguration.INSTANCE.get().isDBStartup();

        this.policy = policy;
        this.maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(maxWaitTime);
        this.pool = new Buffer[nBuffers];
        for (int i = 0; i < nBuffers; i++) {
            pool[i] = new Buffer(fileMgr, logMgr);
        }
        this.available = nBuffers;
        logger.info("BufferMgr started: buffers={}, maxWaitTime={} ms, policy={}, mode={}",
            nBuffers, maxWaitTime, policy, isNew ? "new database" : "existing database");
    }

    // ---------------------------------------------------------------- API

    @Override
    public synchronized int available() {
        return available;
    }

    /**
     * Write every dirty page modified by {@code txnum} (each one flushes the
     * log first) and forget the association.
     *
     * @throws IllegalArgumentException if txnum is negative
     */
    @Override
    public synchronized void flushAll(int txnum) {
        if (txnum < 0) {
            throw new IllegalArgumentException("Transaction number cannot be negative: " + txnum);
        }
        for (Buffer buf : pool) {
            if (buf.modifyingTx() == txnum) {
                buf.flush();
            }
        }
    }

    /**
     * Drop one pin.  If the buffer becomes unpinned, it is available for
     * reuse and every thread waiting in pin() is woken.  Never writes.
     *
     * @throws IllegalArgumentException if buffer is null or not pinned
     */
    @Override
    public synchronized void unpin(BufferBase buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        if (!buffer.isPinned()) {
            throw new IllegalArgumentException("Buffer is not pinned: " + buffer);
        }
        final Buffer buf = (Buffer) buffer;
        buf.unpin();
        if (!buf.isPinned()) {
            available++;
            notifyAll(); // "inform all clients currently blocked invoking pin()"
        }
    }

    /**
     * Pin a buffer to {@code blk}, waiting up to maxWaitTime for a buffer to
     * become available.
     *
     * @throws IllegalArgumentException if blk is null
     * @throws BufferAbortException if no buffer became available in time
     */
    @Override
    public synchronized BufferBase pin(BlockIdBase blk) {
        if (blk == null) {
            throw new IllegalArgumentException("BlockId cannot be null");
        }
        final long deadline = System.nanoTime() + maxWaitNanos;
        while (true) {
            final Buffer buf = tryToPin(blk);
            if (buf != null) {
                return buf;
            }
            // Scenario IV: everything is pinned.  Wait for an unpin, then
            // start over (the block may have been read by someone else
            // meanwhile, or another waiter may have taken the freed buffer).
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new BufferAbortException("No buffer became available for " + blk
                    + " within " + TimeUnit.NANOSECONDS.toMillis(maxWaitNanos) + " ms");
            }
            try {
                wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // leave the flag for the caller
                throw new BufferAbortException("Interrupted while waiting for a buffer for " + blk);
            }
        }
    }

    @Override
    public EvictionPolicy getEvictionPolicy() {
        return policy;
    }

    // ------------------------------------------------------------ helpers

    /**
     * Scenarios I-III: return the buffer now pinned to {@code blk}, or null
     * if the block isn't resident and nothing is unpinned (scenario IV).
     */
    private Buffer tryToPin(BlockIdBase blk) {
        final Key key = Key.of(blk);
        Buffer buf = resident.get(key);
        if (buf == null) {
            buf = chooseUnpinnedBuffer();
            if (buf == null) {
                return null;
            }
            final BlockIdBase old = buf.block();
            if (old != null) {
                resident.remove(Key.of(old));
            }
            // Writes the old page if dirty (log first), then reads blk.  If
            // the read fails the buffer is left unassigned, unpinned and
            // out of the map, and the exception reaches the client.
            buf.assignToBlock(blk);
            resident.put(key, buf);
            logger.debug("{} now holds {}{}", buf, blk, old == null ? "" : " (replaced " + old + ")");
        }
        if (!buf.isPinned()) {
            available--;
        }
        buf.pin();
        return buf;
    }

    /** The eviction policy's choice among the unpinned buffers, or null if there are none. */
    private Buffer chooseUnpinnedBuffer() {
        if (available == 0) {
            return null;
        }
        switch (policy) {
            case NAIVE:
                for (Buffer buf : pool) {
                    if (!buf.isPinned()) {
                        return buf;
                    }
                }
                return null; // unreachable: available > 0
            case CLOCK:
                // Terminates: at least one buffer is unpinned, and one full
                // sweep clears every unpinned bit, so the second finds it.
                while (true) {
                    final Buffer buf = pool[hand];
                    hand = (hand + 1) % pool.length;
                    if (buf.isPinned()) {
                        continue;
                    }
                    if (buf.isReferenced()) {
                        buf.clearReferenced(); // second chance
                        continue;
                    }
                    return buf; // hand already points past this replacement
                }
            default:
                throw new IllegalStateException("Unknown eviction policy: " + policy);
        }
    }
}
