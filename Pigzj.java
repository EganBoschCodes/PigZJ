import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public class Pigzj {

    public final static int BUFFER_SIZE = 128 * 1024;
    public final static int DICT_SIZE = BUFFER_SIZE / 4;
    private static int NUM_PROCESSORS;

    public static volatile ArrayList<Block> blocks = new ArrayList<Block>();

    public static volatile int BLOCK_COUNT = 0;

    public static long inputLength = 0;

    public static CRC32 crc = new CRC32();
    private static ThreadPoolExecutor threadExecutor;
  

    public static void main(String[] args) throws Exception {

        //Check for write permissions after writing the Header
        System.out.write( new byte[] { (byte)0x8b1f, (byte)(0x8b1f >> 8), Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0 } );
        if (System.out.checkError()) {
            System.err.println("Error writing to Standard Output!");
            System.exit(1);
        }

        //Just parse the args input
        handleInput(args);

        //Initialize ThreadPoolExecutor to handle all of our compression threads.
        threadExecutor = new ThreadPoolExecutor(NUM_PROCESSORS, NUM_PROCESSORS, Long.MAX_VALUE, TimeUnit.NANOSECONDS, new LinkedBlockingQueue<Runnable>());

        //Systematically read chunks of data from System.in until it is fully consumed and, whenever having read a large enough chunk of data, initialize a new thread
        readBlocks();

        //Notify ThreadPoolExecutor we're done adding new threads
        threadExecutor.shutdown();

        //Sleep makes threads wait, so while the compression threads are going, use main thread to begin reading out compressed data and free memory once written.
        for (Block b : blocks) {
            while ( !b.done() ) {
                Thread.sleep(1);
            }
            System.out.write(b.getCompressedData(), 0, b.getCompressedSize());
            b.clearCompressedData();
        }

        //Write the trailer once all blocks have been written.
        byte[] trailer = new byte[8];
        writeTrailer(inputLength, trailer, 0);
        System.out.write(trailer);

        System.exit(0);

    }

    //Just parse the input.
    private static void handleInput(String[] args) {
        
        switch (args.length) {
            case 0:
                NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();
                break;
            case 2:
                if (args[0].equals("-p")) {
                    try {
                        int requestedProcessors = Integer.parseInt(args[1]);
                        NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();

                        if (requestedProcessors > NUM_PROCESSORS * 4) {
                            System.err.println("Too many processors requested! Only up to " + (4*NUM_PROCESSORS) + " available.");
                            System.exit(1);
                        }
                        NUM_PROCESSORS = requestedProcessors;
                    }
                    catch (Exception E) {
                        System.err.println("Enter an Integer Number of Processors (Ex: 1, 2, 8): You wrote \"" + args[1] + "\".");
                        System.exit(1);
                    }
                }
                else {
                    System.err.println("Incorrect arguments (either no arguments, or -p [num])");
                    System.exit(1);
                }
                break;
            default:
                System.err.println("Incorrect arguments (either no arguments, or -p [num])");
                System.exit(1);
        }

    }

    //Lets our blocks know what their ID is.
    public static int getNextBlock() {
        BLOCK_COUNT += 1;
        return BLOCK_COUNT - 1;
    }
    
    public static void readBlocks () throws IOException {

        crc.reset();

        byte[] inputBuffer = new byte[BUFFER_SIZE];
        int bytesRead = 0;

        //Buffer in the input until fully consumed, as it often takes multiple reads per block.
        ByteArrayOutputStream systemInput = new ByteArrayOutputStream();
        int blockSize = 0;
        
        //Runs until all input is consumed from System.in
        while ((bytesRead = System.in.read(inputBuffer)) > 0) {

            inputLength += bytesRead;

            //Runs when we've gathered enough data to send out a block.
            if (blockSize + bytesRead >= BUFFER_SIZE) {

                systemInput.write(inputBuffer, 0, BUFFER_SIZE - blockSize);

                //System.err.println("Creating Block #" + blocks.size());
                Block block = new Block(blocks.size(), systemInput.toByteArray(), BUFFER_SIZE);
                updateCRC(block);
                blocks.add(block);
                threadExecutor.execute(new CompressorThread(block));

                systemInput.reset();
                blockSize = (blockSize + bytesRead) - BUFFER_SIZE;
                systemInput.write(inputBuffer, BUFFER_SIZE - blockSize, blockSize);

            }

            //Just track how much data has been read and append data to buffer for later block creation.
            else {
                blockSize += bytesRead;
                systemInput.write(inputBuffer, 0, bytesRead);
            }
        }

        //Create a final block containing any remaining data in the systemInput.
        if (blockSize > 0) {

            Block finalBlock = new Block(blocks.size(), systemInput.toByteArray(), blockSize);
            finalBlock.setFinalBlock();
            blocks.add(finalBlock);
            updateCRC(finalBlock);
            threadExecutor.execute(new CompressorThread(finalBlock));

        }
        else if (blocks.size() > 0) {
            blocks.get(blocks.size() -  1).setFinalBlock();
        }

        //Makes sure we aren't compressing an empty file
        if (blocks.size() == 0) {
            System.err.println("No input passed in!");
            System.exit(1);
        }
    }

    public static synchronized void updateCRC(Block b) {
        crc.update(b.getUncompressedData(), 0, b.getUncompressedSize());
    }


    private static void writeTrailer(long totalBytes, byte[] buf, int offset) throws IOException {
        writeInt((int)crc.getValue(), buf, offset);
        writeInt((int)totalBytes, buf, offset + 4);
    }

    private static void writeInt(int i, byte[] buf, int offset) throws IOException {
        writeShort(i & 0xffff, buf, offset);
        writeShort((i >> 16) & 0xffff, buf, offset + 2);
    }

    private static void writeShort(int s, byte[] buf, int offset) throws IOException {
        buf[offset] = (byte)(s & 0xff);
        buf[offset + 1] = (byte)((s >> 8) & 0xff);
    }

}
