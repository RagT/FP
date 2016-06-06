/**
 * Raghu Tirumala
 * CSS430 Final Project
 *
 * FileSystem class
 */
public class FileSystem {
    private SuperBlock superBlock;
    private FileTable fileTable;
    private Directory directory;

    public FileSystem(int blocks) {
        //Initialize superBlock, fileTable, and directory
        superBlock = new SuperBlock(blocks);
        directory = new Directory(superBlock.totalInodes);
        fileTable = new FileTable(directory);
        byte[] dirData;

        FileTableEntry dirEntry = open("/", "r");
        int dirSize = fsize(dirEntry);
        if (dirSize > 0) {
            // the directory has some data
            dirData = new byte[dirSize];
            read(dirEntry, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEntry);
    }

    public void sync() {
        FileTableEntry fte = open("/", "w");
        byte[] data = directory.directory2bytes();
        write(fte, data);
        close(fte);
        superBlock.sync();
    }

    public FileTableEntry open(String filename, String mode) {
        FileTableEntry fte = fileTable.falloc(filename, mode);
        if (mode.equals("w")) {
            if ( !deallocBlocks( fte)) {
                return null;
            }
        }
        return fte;
    }

    public boolean close(FileTableEntry fte) {
        synchronized(fte) {
            // decrement count of #threads using the fte
            fte.count--;

            //If no threads are using fte free the fte
            if (fte.count == 0) {
                return fileTable.ffree(fte);
            }
            return true;
        }
    }

    public boolean format(int files) {
        superBlock.format(files);
        directory = new Directory(superBlock.totalInodes);
        fileTable = new FileTable(directory);
        return true;
    }

    //Return size of file (in bytes)
    public int fsize(FileTableEntry fte) {
        //Invalid fte
        if (fte == null || fte.inode == null) {
            return -1;
        }
        return fte.inode.length;
    }

    //Read the file from the FileTableEntry into a byte array buffer
    public int read(FileTableEntry fte, byte[] buffer) {
        //Check for invalid mode (Valid modes are read only and read and write)
        if(fte.mode.equals("w") || fte.mode.equals("a")) {
            return -1;
        }
        int size  = buffer.length;   //set total size of data to read
        int rBuffer = 0;            //track data read
        int rError = -1;            //track error on read
        int blockSize = 512;        //set block size
        int itrSize = 0;            //track how much is left to read

        synchronized(fte) {
            while (fte.seekPtr < fsize(fte) && (size > 0)) {
                int currentBlock = fte.inode.findTargetBlock(fte.seekPtr);
                if (currentBlock == rError) {
                    break;
                }
                byte[] data = new byte[blockSize];
                SysLib.rawread(currentBlock, data);

                int dataOffset = fte.seekPtr % blockSize;
                int blocksLeft = blockSize - itrSize;
                int fileLeft = fsize(fte) - fte.seekPtr;

                if (blocksLeft < fileLeft) {
                    itrSize = blocksLeft;
                } else {
                    itrSize = fileLeft;
                }
                if (itrSize > size) {
                    itrSize = size;
                }

                System.arraycopy(data, dataOffset, buffer, rBuffer, itrSize);
                rBuffer += itrSize;
                fte.seekPtr += itrSize;
                size -= itrSize;
            }
            return rBuffer;
        }
    }

    public int write(FileTableEntry fte, byte[] data) {

    }

    private boolean deallocBlocks(FileTableEntry fileTableEntry){
        short invalid = -1;
        if (fileTableEntry.inode.count != 1)
        {
            SysLib.cerr("Null Pointer");
            return false;
        }

        for (short blockId = 0; blockId < fileTableEntry.inode.directSize; blockId++)
        {
            if (fileTableEntry.inode.direct[blockId] != invalid)
            {
                superBlock.returnBlock(blockId);
                fileTableEntry.inode.direct[blockId] = invalid;
            }
        }

        byte [] data = fileTableEntry.inode.freeIndirectBlock();

        if (data != null)
        {
            short blockId;
            while((blockId = SysLib.bytes2short(data, 0)) != invalid)
            {
                superBlock.returnBlock(blockId);
            }
        }
        fileTableEntry.inode.toDisk(fileTableEntry.iNumber);
        return true;
    }
}
