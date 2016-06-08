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
        if (!fileTable.fempty()) {
            return false;
        }
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
        int buffSize  = buffer.length;   //size of data to read
        int rBuff = 0;
        int blockSize = 512;        //set block size to 512
        int itr = 0;

        synchronized(fte) {
            while (fte.seekPtr < fsize(fte) && (buffSize > 0)) {
                int currentBlock = fte.inode.findTargetBlock(fte.seekPtr);
                if (currentBlock == -1) {
                    break;
                }
                byte[] data = new byte[blockSize];
                SysLib.rawread(currentBlock, data);

                int dataOffset = fte.seekPtr % blockSize;
                int blocksRemaining = blockSize - itr;
                int fileLeft = fsize(fte) - fte.seekPtr;

                if (blocksRemaining < fileLeft) {
                    itr = blocksRemaining;
                } else {
                    itr = fileLeft;
                }
                if (itr > buffSize) {
                    itr = buffSize;
                }

                System.arraycopy(data, dataOffset, buffer, rBuff, itr);
                rBuff += itr;
                fte.seekPtr += itr;
                buffSize -= itr;
            }
            return rBuff;
        }
    }

    //Write to file in the FileTableEntry from the data passed in
    public int write(FileTableEntry fte, byte[] data) {
        //Check if fte is in correct mode to write to
        if(fte.mode.equals("r") || fte == null) {
            return -1;
        }
        int bytesWritten = 0;
        int dataSize = data.length;
        int blockSize = 512;

        synchronized (fte) {
            while (dataSize > 0) {
                int location = fte.inode.findTargetBlock(fte.seekPtr);

                // if current block is null
                if (location == -1) {
                    short newLocation = (short) superBlock.nextFreeBlock();

                    int testPtr = fte.inode.getIndexNumber(fte.seekPtr, newLocation);

                    if (testPtr == -3)
                    {
                        short freeBlock = (short) superBlock.nextFreeBlock();
                        // indirect pointer is empty
                        if (!fte.inode.setIndexBlock(freeBlock)) {
                            return -1;
                        }
                        // check block pointer
                        if (fte.inode.getIndexNumber(fte.seekPtr, newLocation) != 0) {
                            return -1;
                        }
                    }
                    else if (testPtr == -2 || testPtr == -1) {
                        return -1;
                    }
                    location = newLocation;
                }

                byte [] tempBuff = new byte[blockSize];
                SysLib.rawread(location, tempBuff);

                int tempPtr = fte.seekPtr % blockSize;
                int diff = blockSize - tempPtr;

                if (diff > dataSize) {
                    System.arraycopy(data, bytesWritten, tempBuff, tempPtr, dataSize);
                    SysLib.rawwrite(location, tempBuff);

                    fte.seekPtr += dataSize;
                    bytesWritten += dataSize;
                    dataSize = 0;
                } else {
                    System.arraycopy(data, bytesWritten, tempBuff, tempPtr, diff);
                    SysLib.rawwrite(location, tempBuff);

                    fte.seekPtr += diff;
                    bytesWritten += diff;
                    dataSize -= diff;
                }
            }

            // update inode length if seekPtr larger

            if (fte.seekPtr > fte.inode.length) {
                fte.inode.length = fte.seekPtr;
            }
            fte.inode.toDisk(fte.iNumber);
            return bytesWritten;
        }

    }

    public synchronized int seek(FileTableEntry fte, int offset, int loc){
        if(loc < 1){
            fte.seekPtr = 0 + offset;
        }else if(loc == 1){
            fte.seekPtr += offset;
        }else if(loc == 2){
            fte.seekPtr = fte.inode.length + offset;
        }else{
            fte.seekPtr = fte.inode.length;
        }
        if (fte.seekPtr > fte.inode.length) {
            fte.seekPtr = fte.inode.length;
        }
        if (fte.seekPtr < 0) {
            fte.seekPtr = 0;
        }
        return fte.seekPtr;
    }


    public boolean delete(String fileName) {
        short inumber;
        //If blank file name cannot delete
        if (fileName == "") {
            return false;
        }
        //get inumber of file with given filename if invalid inumber, return false
        if ((inumber = directory.namei(fileName)) == -1) {
            return false;
        }
        //delete file
        return directory.ifree(inumber);
    }

    private boolean deallocBlocks(FileTableEntry fileTableEntry){
        if (fileTableEntry.inode.count != 1) {
            SysLib.cerr("Null Pointer");
            return false;
        }
        for (short bId = 0; bId < fileTableEntry.inode.directSize; bId++) {
            if (fileTableEntry.inode.direct[bId] != -1)
            {
                superBlock.returnBlock(bId);
                fileTableEntry.inode.direct[bId] = -1;
            }
        }
        byte [] data = fileTableEntry.inode.freeIndirectBlock();
        if (data != null) {
            short blockId;
            while((blockId = SysLib.bytes2short(data, 0)) != -1)
            {
                superBlock.returnBlock(blockId);
            }
        }
        fileTableEntry.inode.toDisk(fileTableEntry.iNumber);
        return true;
    }


}
