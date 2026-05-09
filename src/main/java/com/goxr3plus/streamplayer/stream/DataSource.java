package com.goxr3plus.streamplayer.stream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.time.Duration;

/**
 * Represents an abstract audio data source.
 * Implementations are sealed to {@link FileDataSource}, {@link UriDataSource}, and {@link StreamDataSource}.
 */
public sealed interface DataSource permits FileDataSource, UriDataSource, StreamDataSource {

    /**
     * @return the underlying source object (File, URI, or InputStream)
     */
    Object getSource();

    /**
     * @return the format of the source data
     * @throws UnsupportedAudioFileException if the file type is unsupported
     * @throws IOException                   if there is a runtime problem with IO
     */
    AudioFileFormat getAudioFileFormat() throws UnsupportedAudioFileException, IOException;

    /**
     * @return a stream representing the input data, regardless of source
     * @throws UnsupportedAudioFileException if the file type is unsupported
     * @throws IOException                   if there is a runtime problem with IO
     */
    AudioInputStream getAudioInputStream() throws UnsupportedAudioFileException, IOException;

    /**
     * @return The duration of the source data in seconds, or -1 if duration is unavailable
     */
    int getDurationInSeconds();

    /**
     * @return The duration of the source data in milliseconds, or -1 if duration is unavailable
     */
    long getDurationInMilliseconds();

    /**
     * @return The duration of the source data as a {@link Duration}, or null if unavailable
     */
    Duration getDuration();

    /**
     * @return true if the DataSource is backed by a File
     */
    boolean isFile();

    /**
     * @return total size of the data source in bytes, or -1 if unknown
     */
    default long getSize() { return -1; }
}
