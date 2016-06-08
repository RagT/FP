import java.util.Vector;
/**
 * Raghu Tirumala
 * CSS 430 Final Project
 */
public class FileTable {
    private Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory

    public FileTable( Directory directory ) { // constructor
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    // major public methods

    // allocate a new file (structure) table fte for this file name
    // allocate/retrieve and register the corresponding inode using dir
    // increment this inode's count
    // immediately write back this inode to the disk
    // return a reference to this file (structure) table fte
    public synchronized FileTableEntry falloc(String filename, String mode) {
        short iNumber = -1; // inode number
        Inode inode = null; // holds inode

        while (true) {
            // get the inumber form the inode for given file name
            iNumber = (filename.equals("/") ? (short) 0 : dir.namei(filename));

            // if the inode for the given file exist
            if (iNumber >= 0) {
                inode = new Inode(iNumber);

                // if requesting a read
                if (mode.equals("r")) {

                    //if flag is read used or unused
                    if (inode.flag == 2 || inode.flag == 1 || inode.flag == 0) {
                        // change the flag of the node to read
                        inode.flag = 2;
                        break;

                        // if the file is already written wait
                    } else if (inode.flag == 3) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                    }

                    //Read and write or append
                } else {
                    // if flsg is used/unused change to write
                    if (inode.flag == 1 || inode.flag == 0) {
                        inode.flag = 3;
                        break;
                        // if flag is read or write
                    } else {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            } else if (!mode.equals("r")) {
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = 3;
                break;
            } else {
                return null;
            }
        }
        inode.count++;
        inode.toDisk(iNumber);
        FileTableEntry fte = new FileTableEntry(inode, iNumber, mode);
        table.addElement(fte);
        return fte;
    }

    // receive a file table fte reference
    // save the corresponding inode to the disk
    // free this file table fte.
    // return true if this file table fte found in my table
    public synchronized boolean ffree(FileTableEntry e) {
        if (e == null) {
            return true;
        }

        if (!table.removeElement(e)) {
            return false;
        }

        Inode fileNode = e.inode;
        short iNumber = e.iNumber;

        if (fileNode.count > 0) {
            fileNode.count--;
        }

        if (fileNode.count == 0) {
            fileNode.flag = 0;
        }

        fileNode.toDisk(iNumber);

        if (fileNode.flag == 0 || fileNode.flag == 1) {
            notify();
        }
        e = null;
        return true;
    }

    public synchronized boolean fempty( ) {
        return table.isEmpty();  // return if table is empty
    }                            // should be called before starting a format

    //Returns mode of FileTableEntry given its mode field
    public static short getEntryMode(String mode) {
        if (mode.equalsIgnoreCase("r")) { //read only
            return 0;
        } else if (mode.equalsIgnoreCase("w")) { //write only
            return 1;
        } else if (mode.equalsIgnoreCase("w+")) { //read and write
            return 2;
        } else if (mode.equalsIgnoreCase("a")) { //append
            return 3;
        }
        return -1; //Invalid mode
    }
}
