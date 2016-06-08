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
        int var;
        if(numInodes % 16 == 0) {
            var = 1;
        } else {
            var = 2;
        }
        freeList = numInodes / 16 + var;

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
    Add a block to end of the freelist.
    Return true if success false otherwise.
     */
    public boolean returnBlock(int blockNum){
        byte[] buffer = new byte[Disk.blockSize];
        SysLib.short2bytes((short)freeList,buffer, 0);
        SysLib.rawwrite(blockNum, buffer);
        freeList = blockNum;
        return true;
    }

    /*
     * Returns first free block from free list
     */
    public int nextFreeBlock() {
        if (freeList > 0 && freeList < totalBlocks) {
            byte[] temp = new byte[Disk.blockSize];
            SysLib.rawread(freeList, temp);

            int tempVal = freeList;

            // update next free block
            freeList = SysLib.bytes2int(temp, 0);

            // return block location
            return tempVal;
        }
        return -1;
    }
}
