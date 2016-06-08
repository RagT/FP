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
        // if file table entry is null
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

    public int read(FileTableEntry fte, byte[] buffer) {
        int seekPtr, length, block, offset, available, remaining, rLength, index;
        Inode iNode;
        byte[] data;
        // file table entry cannot be null
        if (fte == null)
            return ERROR;
        // mode must be read
        if (fte.mode == FileTableEntry.WRITEONLY
                || fte.mode == FileTableEntry.APPEND)
            return ERROR;
        // iNode cannot be null
        if ((iNode = fte.iNode) == null)
            return ERROR;
        // read up to buffer length
        length = buffer.length;

        // multiple threads cannot read at the same time
        synchronized (fte) {
            // start at position pointed to by iNode's seek pointer
            seekPtr = fte.seekPtr;
            data = new byte[Disk.blockSize];
            index = 0;
            while (index < length) {
                // byte offset-- 0 is a new block
                offset = seekPtr % Disk.blockSize;
                // bytes available
                available = Disk.blockSize - offset;
                // bytes remaining
                remaining = length - index;
                // bytes to read-- cannot be greater than available
                rLength = Math.min(available, remaining);

                // block must exist
                if ((block = iNode.findTargetBlock(offset)) == -1) {
                    // if ((block = iNode.findTargetBlock(seekPtr)) == ERROR) {
                    return -1;
                }

                if (block < 0 || block >= superBlock.totalBlocks) {
                    break;
                    // return ERROR;
                }

                if (offset == 0) {
                    data = new byte[Disk.blockSize];
                }

                // read block from disk to data
                SysLib.rawread(block, data);

                // copy data to buffer
                // source, source position, destination, destination position,
                // length to copy
                System.arraycopy(data, offset, buffer, index, rLength);

                index += rLength;
                seekPtr += rLength;
            }
            // set new seek pointer
            seek(fte, index, 1);
        }
        return index;
    }

    //Read the file from the FileTableEntry into a byte array buffer
//    public int read(FileTableEntry fte, byte[] buffer) {
//        //Check for invalid mode (Valid modes are read only and read and write)
//        if(fte.mode.equals("w") || fte.mode.equals("a")) {
//            return -1;
//        }
//        int buffSize  = buffer.length;   //size of data to read
//        int rBuff = 0;
//        int blockSize = 512;        //set block size to 512
//        int itr = 0;
//
//        synchronized(fte) {
//            while (fte.seekPtr < fsize(fte) && (buffSize > 0)) {
//                int currentBlock = fte.inode.findTargetBlock(fte.seekPtr);
//                if (currentBlock == -1) {
//                    break;
//                }
//                byte[] data = new byte[blockSize];
//                SysLib.rawread(currentBlock, data);
//
//                int dataOffset = fte.seekPtr % blockSize;
//                int blocksRemaining = blockSize - itr;
//                int fileLeft = fsize(fte) - fte.seekPtr;
//
//                if (blocksRemaining < fileLeft) {
//                    itr = blocksRemaining;
//                } else {
//                    itr = fileLeft;
//                }
//                if (itr > buffSize) {
//                    itr = buffSize;
//                }
//
//                System.arraycopy(data, dataOffset, buffer, rBuff, itr);
//                rBuff += itr;
//                fte.seekPtr += itr;
//                buffSize -= itr;
//            }
//            return rBuff;
//        }
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

            // update inode length if seekPtr larger
            if (fte.seekPtr > fte.inode.length) {
                fte.inode.length = fte.seekPtr;
            }
            fte.inode.toDisk(fte.iNumber);
            return bytesWritten;
        }

    }

    public synchronized int seek(FileTableEntry fte, int offset, int loc){
        int seekPtr;
        int eof;
        if (fte == null) {
            return -1;
        }
        synchronized (fte) {
            seekPtr = fte.seekPtr;
            eof = fsize(fte);
            switch (loc) {
                case 0 :
                    seekPtr = offset;
                    break;
                case 1 :
                    seekPtr += offset;
                    break;
                case 2 :
                    seekPtr = eof + offset;
                    break;
                default :
                    return -1;
            }
            if (seekPtr < 0) {
                seekPtr = 0;
            } else if (seekPtr > eof) {
                seekPtr = eof;
            }
            fte.seekPtr = seekPtr;
        }
        return seekPtr;
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
