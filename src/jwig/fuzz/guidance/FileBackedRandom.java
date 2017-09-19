/*
 The MIT License

 Copyright (c) 2017 University of California, Berkeley

 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject to
 the following conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package jwig.fuzz.guidance;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;


/**
 * This class extends {@link Random} to act as a generator of
 * "random" values, which themselves are read from a static file.
 *
 * The file-backed random number generator can be used for tuning the
 * "random" choices made by various <tt>junit-quickcheck</tt>
 * generators using a mutation-based genetic algorithm, in order to
 * maximize some objective function that can be measured from the
 * execution of each trial, such as code coverage.
 *
 * An instance of this class is associated with exactly one backing
 * file, which is opened and closed by the {@link open()} and
 * {@link close()} methods. Once opened, the instance will maintain
 * a pointer that starts at the beginning of the file and advances
 * as each random value is requested. In particular, data is read from
 * the backing file in chunks of 4-bytes, regardless of the size of
 * the random value requested (i.e. any trailing bits are ignored).
 * If the end-of-file is reached at any point, all further values
 * are assumed to be zeros.
 *
 * The backing file may be re-opened after closing, and this is in fact
 * the intended usage of this class. A common pattern of usage would be:
 * <code>
 *     FileBackedRandom r = new FileBackedRandom(guided.inputFile());
 *     for(int i = 0; i < numTrials; i++) {
 *         guided.waitForInput();
 *         r.open();
 *         // Run the program under test using `r` as the source of
 *         //   randomness.
 *         r.close();ø
 *         guided.notifyEndOfRun();
 *     }
 * </code>
 *
 *
 */
public class FileBackedRandom extends Random implements AutoCloseable {
    private final File source;
    private final InputStream inputStream;
    private final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    /**
     * Constructs a file-backed random generator.
     *
     * Also sets the seed of the underlying pseudo-random number
     * generator deterministically to zero.
     *
     * @param source   the file containing the randomly generated bytes
     */
    public FileBackedRandom(File source) throws IOException {
        super(0x5DEECE66DL);
        // Open the backing file source as a buffered input stream
        this.source = source;
        this.inputStream = new BufferedInputStream(new FileInputStream(source));
        // Set encoding to little-endian (XXX: Maybe nativeOrder()?)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Generates upto 32 bits of random data for internal use by the Random
     * class.
     *
     * Always attempts to read 32-bits (4 bytes) of data from the backing file,
     * regardless of how many bits of random data are requested. If end-of-file
     * is reached before reading 4 bytes, the remaining bytes are assumed to be
     * zeros.
     *
     * If the backing file source has not yet been set, it defaults to
     * the pseudo-random number generation algorithm from
     * {@link Random}. This is still deterministic, as the seed
     * of the pseudo-random number generator is deterministically set to 0.
     *
     * @param bits   the number of random bits to retain (1 to 32 inclusive)
     * @return the integer value whose lower <tt>bits</tt> bits contain the
     *    next random data available in the backing source
     */
    @Override
    protected synchronized int next(int bits) {
        // Ensure that up to 32 bits are being requested
        if (bits < 0 || bits > 32) {
            throw new IllegalArgumentException("Must read 1-32 bits at a time");
        }

        // Zero out the byte buffer before reading from the source
        byteBuffer.putInt(0, 0);

        try {
            // Read up to 4 bytes from the backing source
            int bytesRead = inputStream.read(byteBuffer.array(), 0, 4);
        } catch (IOException e) {
            throw new GuidanceIOException(e);
        }

        // Interpret the bytes read as an integer
        int value = byteBuffer.getInt(0);

        // Return only the lower order bits as requested
        int mask = bits < 32 ? (1 << bits) - 1 : -1;
        return value & mask;

    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

}
