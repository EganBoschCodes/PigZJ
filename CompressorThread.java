import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

public class CompressorThread extends Thread {
    
    private Block block;

    public CompressorThread (Block b) {
        block = b;
    }

    public void run() {

        //Set up Deflater by adding in dictionary, input, and writing the file-end bytes if it's the last block.
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);

        if (!block.IS_FIRST_BLOCK) {
            deflater.setDictionary(block.getLastBlock().getDict());
        }

        deflater.setInput(block.getUncompressedData(), 0, block.getUncompressedSize());

        if (block.IS_FINAL_BLOCK) {
            deflater.finish();
        }

        //Read Data from Deflation Object
        byte[] deflationBuffer = new byte[Pigzj.BUFFER_SIZE * 2];
        ByteArrayOutputStream compressedByteStream = new ByteArrayOutputStream();
        int compressedDataSize = 0;

        int deflatedBytes;

        while ( (deflatedBytes = deflater.deflate(deflationBuffer, 0, Pigzj.BUFFER_SIZE * 2, Deflater.SYNC_FLUSH)) > 0 ) {
            compressedByteStream.write(deflationBuffer, 0, deflatedBytes);
            compressedDataSize += deflatedBytes;
        }

        block.setCompressedData(compressedByteStream.toByteArray(), compressedDataSize);

        //Release the memory being used by holding the uncompressed data in each block.
        block.clearUncompressedData();
        if (!block.IS_FIRST_BLOCK) {
            block.getLastBlock().clearDictionary();
        }

    }

}
