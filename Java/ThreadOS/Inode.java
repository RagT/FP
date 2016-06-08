/**
 * Raghu Tirumala
 * CSS430 Final Project
 *
 *Inode Class
 */
public class Inode {
    public final static int iNodeSize = 32;       // fix to 32 bytes
    public final static int directSize = 11;      // # direct pointers

    public int length;                             // file size in bytes
    public short count;                            // # file-table entries pointing to this
    public short flag;                             // 0 = unused, 1 = used, 2 = read, 3 = write, 4 = delete
    public short direct[] = new short[directSize]; // direct pointers
    public short indirect;                         // a indirect pointer

    public Inode( ) {                                     // a default constructor
        length = 0;
        count = 0;
        flag = 1; //Initialized to unused
        for ( int i = 0; i < directSize; i++ )
            direct[i] = -1;
        indirect = -1;
    }

    //Reads Inode from the disk
    public Inode(short iNumber) {
        int blockNum = 1 + iNumber / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNum, data);
        int offset = (iNumber % 16) * iNodeSize;

        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data, offset);
        offset += 2;

        for(int i = 0; i < directSize; i++){
            direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset)
    }


    public int toDisk(short iNumber) {                  // save to disk as the i-th inode
        int offset;
        int block;
        byte[] data;
        if (iNumber < 0) {
            return -1;
        }

        block = offset(iNumber);
        offset = (iNumber % 16) * iNodeSize;
        data = new byte[Disk.blockSize];

        SysLib.int2bytes(length, data, offset);
        offset += 4;
        SysLib.short2bytes(count, data, offset);
        offset += 2;
        SysLib.short2bytes(flag, data, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++, offset += 2) {
            SysLib.short2bytes(direct[i], data, offset);
        }

        SysLib.short2bytes(indirect, data, offset);
        offset += 2;

        // write back to the Disk
        SysLib.rawwrite(block, data);
        return 0; //Success
    }

    //Get the offset of the block
    private int offset(short iNumber){
        return (iNumber / 16) + 1;
    }

    public short findTargetBlock(int offset) {
        int block, iBlock; // offset block, indirect offset
        byte[] data;
        block = offset / Disk.blockSize;

        // target block is in one of the direct blocks
        // return the block
        if (block < directSize) {
            return direct[block];
        }

        // this block is not registered
        if (indirect == -1) {
            return -1;
        }

        data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);

        // get the target block in indirect block and return
        iBlock = block - directSize;
        return SysLib.bytes2short(data, iBlock * 2);
    }

    public byte[] freeIndirectBlock(){
        if (indirect >= 0) {
            byte[] data = new byte[512];
            SysLib.rawread(indirect, data);
            indirect = -1;
            return data;
        } else {
            return null;
        }
    }

    public boolean setIndexBlock(short blockNumber){
        for (int i = 0; i < directSize; i++){
            if (direct[i] == -1){
                return false;
            }
        }
        if (indirect != -1){
            return false;
        }
        indirect = blockNumber;
        byte[ ] data = new byte[512];
        for(int i = 0; i < 256; i++){
            SysLib.short2bytes((short) -1, data, i * 2);
        }
        SysLib.rawwrite(blockNumber, data);
        return true;
    }

    int getIndexNumber(int entry, short offset){
        int target = entry / 512;

        if (target < directSize){
            if(direct[target] >= 0){
                return -1;
            }
            if ((target > 0 ) && (direct[target - 1 ] == -1)){
                return -2;
            }
            direct[target] = offset;
            return 0;
        }

        if (indirect < 0){
            return -3;
        } else{
            byte[] data = new byte[512];
            SysLib.rawread(indirect,data);

            int blockSpace = (target - directSize) * 2;
            if ( SysLib.bytes2short(data, blockSpace) > 0){
                return -1;
            }
            else
            {
                SysLib.short2bytes(offset, data, blockSpace);
                SysLib.rawwrite(indirect, data);
            }
        }
        return 0;
    }
}
