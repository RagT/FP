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
        FileTableEntry fte;
        Inode iNode;
        if (filename == "" || mode == "") {
            return null;
        }
        int m = FileTable.getEntryMode(mode);

        // if file table entry is null or mode is invalid
        if ((fte = fileTable.falloc(filename, mode)) == null || m == -1
                || (iNode = fte.inode) == null || iNode.flag == 4) {
            fileTable.ffree(fte);
            return null;
        }
        synchronized (fte) {
            if (fte.mode.equals("w") && !deallocBlocks(fte)) {
                fileTable.ffree(fte);
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

    public int read(FileTableEntry entry, byte[] buffer){
        //entry is index of file in process open-file table
        //this accesses system wide open file table
        //data blocks accessed, file control block returned

        //check write or append status
        if ((entry.mode == "w") || (entry.mode == "a"))
            return -1;

        int size  = buffer.length;   //set total size of data to read
        int rBuffer = 0;            //track data read
        int rError = -1;            //track error on read
        int blockSize = 512;        //set block size
        int itrSize = 0;            //track how much is left to read
//        fileLength = fsize(entry);
//        int fLeft = 0;

        //cast the entry as synchronized
        //loop to read chunks of data

        synchronized(entry)
        {
            while (entry.seekPtr < fsize(entry) && (size > 0))
            {
                int currentBlock = entry.inode.findTargetBlock(entry.seekPtr);
                if (currentBlock == rError)
                {

                    break;
                }
                byte[] data = new byte[blockSize];
                SysLib.rawread(currentBlock, data);

                int dataOffset = entry.seekPtr % blockSize;
                int blocksLeft = blockSize - itrSize;
                int fileLeft = fsize(entry) - entry.seekPtr;

                if (blocksLeft < fileLeft)
                    itrSize = blocksLeft;
                else
                    itrSize = fileLeft;

                if (itrSize > size)
                    itrSize = size;

                System.arraycopy(data, dataOffset, buffer, rBuffer, itrSize);
                rBuffer += itrSize;
                entry.seekPtr += itrSize;
                size -= itrSize;
            }
            return rBuffer;
        }
    }

//    public int read(FileTableEntry fte, byte[] buffer) {
//        int seekPtr, length, block, offset, available, remaining, rLength, index;
//        Inode iNode;
//        byte[] data;
//        if (fte == null)
//            return -1;
//
//        if (fte.mode.equals("w") || fte.mode.equals("a")) {
//            return -1;
//        }
//
//        if ((iNode = fte.inode) == null) {
//            return -1;
//        }
//
//        length = buffer.length;
//        synchronized (fte) {
//
//            seekPtr = fte.seekPtr;
//            data = new byte[Disk.blockSize];
//            index = 0;
//            while (index < length) {
//
//                offset = seekPtr % Disk.blockSize;
//                available = Disk.blockSize - offset;
//                remaining = length - index;
//                rLength = Math.min(available, remaining);
//
//                if ((block = iNode.findTargetBlock(offset)) == -1) {
//                    return -1;
//                }
//
//                if (block < 0 || block >= superBlock.totalBlocks) {
//                    break;
//                }
//
//                if (offset == 0) {
//                    data = new byte[Disk.blockSize];
//                }
//
//                SysLib.rawread(block, data);
//                System.arraycopy(data, offset, buffer, index, rLength);
//                index += rLength;
//                seekPtr += rLength;
//            }
//            seek(fte, index, 1);
//        }
//        return index;
//    }

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

            if (fte.seekPtr > fte.inode.length) {
                fte.inode.length = fte.seekPtr;
            }
            fte.inode.toDisk(fte.iNumber);
            return bytesWritten;
        }

    }

    public synchronized int seek(FileTableEntry fte, int offset, int loc){
        int eof;
        if (fte == null) {
            return -1;
        }
        synchronized (fte) {
            eof = fsize(fte);
            switch (loc) {
                case 0 :
                    fte.seekPtr = offset;
                    break;
                case 1 :
                    fte.seekPtr += offset;
                    break;
                case 2 :
                    fte.seekPtr = eof + offset;
                    break;
                default :
                    return -1;
            }
            if (fte.seekPtr < 0) {
                fte.seekPtr = 0;
            } else if (fte.seekPtr > eof) {
                fte.seekPtr = eof;
            }
        }
        return fte.seekPtr;
    }

    //Deletes file with given filename from file table
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

    private boolean deallocBlocks(FileTableEntry fte){
        if (fte.inode.count != 1) {
            SysLib.cerr("Null Pointer");
            return false;
        }
        for (short bId = 0; bId < fte.inode.directSize; bId++) {
            if (fte.inode.direct[bId] != -1)
            {
                superBlock.returnBlock(bId);
                fte.inode.direct[bId] = -1;
            }
        }
        byte [] data = fte.inode.freeIndirectBlock();
        if (data != null) {
            short blockId;
            while((blockId = SysLib.bytes2short(data, 0)) != -1)
            {
                superBlock.returnBlock(blockId);
            }
        }
        fte.inode.toDisk(fte.iNumber);
        return true;
    }


}
