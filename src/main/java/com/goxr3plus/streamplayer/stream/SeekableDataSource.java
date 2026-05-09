package com.goxr3plus.streamplayer.stream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

/**
 * Optional capability for {@link DataSource} implementations that support
 * reopening at a specific byte position (e.g. local files via RandomAccessFile,
 * network streams via HTTP Range headers).
 */
public interface SeekableDataSource {

    /**
     * Opens an {@link AudioInputStream} starting at the given byte offset
     * from the beginning of the source. The stream is positioned as close as
     * possible to the requested offset (exact for local files, approximate for
     * network streams due to HTTP Range granularity).
     */
    AudioInputStream openAtPosition(long bytePosition)
            throws IOException, UnsupportedAudioFileException;

    /**
     * @return total content length in bytes, or -1 if unknown
     */
    long getContentLength();

    /** @return true if this source supports reopenable seeking */
    default boolean isSeekable() { return true; }
}
