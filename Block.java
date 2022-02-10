import java.util.Arrays;

public class Block {
    private byte[] uncompressedData;
    private int uncompressedSize;

    private byte [] dictionary;

    private byte[] compressedData;
    private int compressedSize;

    private volatile boolean compressed = false;

    public volatile boolean IS_FINAL_BLOCK = false;
    public boolean IS_FIRST_BLOCK;
    public int blockID;

    public Block (int blockNum, byte[] input, int size) {
        uncompressedData = input;
        uncompressedSize = size;

        IS_FIRST_BLOCK = blockNum == 0;
        blockID = blockNum;

        if (uncompressedSize > Pigzj.DICT_SIZE) {
            dictionary = Arrays.copyOfRange(uncompressedData, uncompressedSize - Pigzj.DICT_SIZE, uncompressedSize);
        }
    }

    public Block getLastBlock() {
        return Pigzj.blocks.get(blockID - 1);
    }

    /* Had to get into the OOP mood with unnecessary get/sets */

    public byte[] getUncompressedData() {
        return uncompressedData;
    }

    public int getUncompressedSize() {
        return uncompressedSize;
    }

    public byte[] getCompressedData() {
        return compressedData;
    }

    public int getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedData(byte[] comp, int size) {
        compressedData = comp;
        compressedSize = size;
        compressed = true;
    }

    public synchronized boolean done () { 
        return compressed; 
    }

    public byte[] getDict() {
        return dictionary;
    }

    /* To know when to call Deflater.finish() */

    public void setFinalBlock () { IS_FINAL_BLOCK = true; }

    /* Debugging Purposes */

    public void printContents() {
        String s = "";
        for (int i = 0; i < uncompressedSize; i++) {
            s += (char)(uncompressedData[i]);
        }
        System.out.println(s);
    }

    public String toString() {
        return "{ BLOCK_ID: " + blockID + ", UNCOMPRESSED_SIZE: " + uncompressedSize + ", IS_FIRST: " + IS_FIRST_BLOCK + ", IS_LAST: " + IS_FINAL_BLOCK + ", COMPRESSED: " + compressed + (compressed ? ", COMPRESSED_SIZE: " + compressedSize : "") + "}";
    }

    /* These methods help reduce the maximum system space used at once, by releasing memory when no longer needed. */

    public void clearUncompressedData() {
        uncompressedData = null;
    }

    public void clearDictionary() {
        dictionary = null;
    }

    public void clearCompressedData() {
        compressedData = null;
    }

}
