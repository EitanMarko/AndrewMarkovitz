package edu.yu.dbimpl.file;

/**
 * BlockId: the address of one block on disk, i.e. a file name plus a logical
 * block number within that file.
 *
 * Example:
 *   new BlockId("students.tbl", 2)
 * names the third block of students.tbl.  With a block size of 4096, the
 * FileMgr will find its bytes at file position 2 * 4096.
 *
 * Rules from BlockIdBase:
 * - The file name must have a trimmed length greater than 0.
 * - The block number must be non-negative.
 * - A BlockId is an immutable value class: its state never changes after
 *   construction, and two BlockIds with the same file name and block number
 *   are equal.  Higher layers (buffer manager, lock table) rely on this to use
 *   BlockIds as hash map keys.
 *
 * Immutability also makes instances safe to share between threads.
 */
public final class BlockId extends BlockIdBase {

    /** File name, relative to the FileMgr's database directory. */
    private final String fileName;

    /** Logical block number within the file, starting at 0. */
    private final int blknum;

    /**
     * Create the address of block {@code blknum} of file {@code filename}.
     *
     * @throws IllegalArgumentException if the file name is null or blank, or
     * if the block number is negative
     */
    public BlockId(String filename, int blknum) {
        super(filename, blknum);
        if (filename == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }
        if (filename.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty or blank");
        }
        if (blknum < 0) {
            throw new IllegalArgumentException("Block number cannot be negative: " + blknum);
        }
        // Stored exactly as supplied: the API only constrains the trimmed length.
        this.fileName = filename;
        this.blknum = blknum;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public int number() {
        return blknum;
    }

    /**
     * Value equality: same file name and same block number.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BlockId)) { // this is an implicit null check as well
            return false;
        }
        BlockId that = (BlockId) obj;
        return blknum == that.blknum && fileName.equals(that.fileName);
    }

    /**
     * Consistent with equals, so equal BlockIds land in the same hash bucket.
     */
    @Override
    public int hashCode() {
        return 31 * fileName.hashCode() + blknum;
    }

    @Override
    public String toString() {
        return "[file " + fileName + ", block " + blknum + "]";
    }
}
