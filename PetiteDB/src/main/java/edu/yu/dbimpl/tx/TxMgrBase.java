package edu.yu.dbimpl.tx;

/** Specifies the public API for the TxMgr implementation by requiring all
 * TxMgr implementations to extend this class.
 *
 * Students MAY NOT modify this class in any way, they must suppport EXACTLY
 * the constructor signatures specified in the base class (and NO OTHER
 * signatures).
 *
 * A TxMgr is a factory for producing Transactions.
 *
 * The DBMS has exactly one TxMgr instance (singleton pattern), which is
 * created during system startup.
 *
 * Important design/implmentation considerations: a TxMgr is a factory for
 * producing transactions.  Transactions are NOT associated one-to-one with a
 * client thread: i.e., one client thread can create multiple transactions.
 * Whether or not the transactions execute concurrently depends on whether
 * there are concurrent execution threads; but there is no intrinsic
 * association between a client thread and the transactions that it creates.
 *
 * Important: in addition to (internally) designing and implementing log
 * records to support the recovery algorithm (e.g., to recover from
 * TxBase.setInt invocations), PetiteDB MUST support creation and usage of
 * checkpoint records to improve performance (see lecture and textbook for
 * details).
 * 
 * Design note: it is recommended, but not required, for the TxMgr to delegate
 * all read/write function of its files to the lower-level "managers" supplied
 * in the constructor.
 *
 * @author Avraham Leff
 */

import edu.yu.dbimpl.buffer.BufferMgrBase;
import edu.yu.dbimpl.file.FileMgrBase;
import edu.yu.dbimpl.log.LogMgrBase;

public abstract class TxMgrBase {

  /* Creates a TxMgr with the specified max waiting time, also supplying
   * managers for the lower-level DBMS modules.  The client is responsible for
   * ensuring that the file, log, and buffer managers are fully instantiated
   * before invoking this constructor.
   *
   * On DBMS startup, the TxMgr is responsible for TRANSPARENTLY rolling back
   * transactions that (per log record state) were neither committed or rolled
   * back when the DBMS last closed down.  Per lecture and textbook, the
   * persisted state associated with such transactions must be rolled back to
   * their pre-tx state: i.e., no state modifications made by these txs may be
   * visible when the DBMS finished the recovery process and is "open for
   * business".
   *
   * If the TxMgr implementation is in ANY way dependent on knowledge of
   * whether or not the DBMS is in "startup" mode, it MUST access the
   * DBConfiguration singleton to determine if implementation specific actions
   * must be taken to (re)initialize the necessary state.
   *
   * @param fileMgr file manager singleton
   * @param logMgr log manager singleton
   * @param bufferMgr buffer manager singleton
   * @param maxWaitTimeInMillis maximum amount of time that a tx will wait to
   * acquire a lock (whether slock or xlock) before the database throws a
   * LockAbortException.  Must be greater than 0, and is specified in ms.
   */
  public TxMgrBase(FileMgrBase fm, LogMgrBase lm, BufferMgrBase bm, long maxWaitTimeInMillis) {
    // base class constructor is a no-op
  }
    
  /** Returns the maxWaitTime value
   */
  public abstract long getMaxWaitTimeInMillis();

  /** Returns a new transaction instance.
   */
  public abstract TxBase newTx();

  /** Resets global lock-related state to "initial" state.  The TxMgr is
   * conceptually a DBMS singleton (as are the other module managers) and is
   * associated with a single DBMS lock table.  Therefore, invoking this method
   * on ANY TxMgr instance in a given JVM will reset the lock related state for
   * ALL TxMgr instances.
   *
   * This method is needed to prevent errors in one test from cascading test to
   * subsequent tests: whatever locks and state that were held by the previous
   * tx are reset so that subsequent txs can start with a clean state.
   *
   * @fixme: this method implicitly assumes that the DBMS is using a lock-based
   * concurrency control implementation.  This restriction should be cleaned up
   * in subsequent iterations.
   *
   * @protip you really want this method to be bug-free!
   */
  public abstract void resetAllLockState();

  /** Flushes all modified buffers, then traverse the log, rolling back all
   * uncommitted transactions.  Finally, writes a quiescent "checkpoint record"
   * to the log.  This method MUST be called by the DBMS during system startup,
   * before processing user transactions so as to set the system to a
   * consistent state.  The method MAY also be called by clients at any time,
   * but the client is implicitly guaranteeing that no other client processing
   * is currently occurring or will occur until the method returns (i.e., the
   * DBMS will remain quiescent).
   */
  public abstract void recover();

} // abstract base class
