/**
 * Raghu Tirumala
 * CSS 430 Final Project
 *
 * Superblock.java
 */
public class SuperBlock {
    private static final int DEFAULT_INODE_BLOCKS = 64;
    public int totalBlocks; // the number of disk blocks
    public int totalInodes; // the number of Inodes
    public int freeList;    // the block number of the free list's head

    //Default constructor
    public SuperBlock() {
        this(DEFAULT_INODE_BLOCKS);
    }

    public SuperBlock(int diskSize) {
        //Allocate space for SuperBlock
        byte[] superBlock = new byte[Disk.blockSize];

        //Read SuperBlock from Disk
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        totalInodes = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if(totalBlocks == diskSize && totalInodes > 0 && freeList >= 2)
            return;
        else{
            totalBlocks = diskSize;
            format();
        }
    }

    public void format() {
        format(DEFAULT_INODE_BLOCKS);
    }

    public void format(int numInodes) {
        byte[] block = null;

        totalInodes = numInodes;

        for (int i = 0; i < totalInodes; i++) {
            Inode newNode = new Inode();
            newNode.toDisk((short) i);
        }

        // Setting free list based on number of Inodes

        freeList = numInodes / 16 + (numInodes % 16 == 0 ? 1 : 2);

        // create new free blocks and write it into Disk
        for (int i = totalBlocks - 2; i >= freeList; i--) {
            block = new byte[Disk.blockSize];
            for (int j = 0; j < Disk.blockSize; j++) {
                block[j] = (byte) 0;
            }
            SysLib.int2bytes(i + 1, block, 0);
            SysLib.rawwrite(i, block);
        }
        // Nullptr in last block
        SysLib.int2bytes(-1, block, 0);
        SysLib.rawwrite(totalBlocks - 1, block);
        sync();
    }

    /*
    Write back totalBlocks, totalInodes, and freeList to Disk
     */
    public void sync() {
        byte[] block = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, block, 0);
        SysLib.int2bytes(totalInodes, block, 4);
        SysLib.int2bytes(freeList, block, 8);
        SysLib.rawwrite(0, block);
    }

    /*
    Get the first block from the freelist and remove that
    block from the freelist.
     */
    public short getFreeBlock() {
        short freeBlock;
        byte[] block;

        // return -1 if there are no free blocks
        if (freeList < 0 || freeList > totalBlocks)
            return -1;

        // Get free block from free list
        freeBlock = (short) freeList;

        // create new empty block
        block = new byte[Disk.blockSize];

        // get content of freeList block
        SysLib.rawread(freeList, block);
        SysLib.int2bytes(0, block, 0);
        SysLib.rawwrite(freeList, block);

        // free list becomes free block
        freeList = SysLib.bytes2int(block, 0);
        return freeBlock;
    }


}
