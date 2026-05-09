package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.AudioType;
import com.goxr3plus.streamplayer.tools.TimeTool;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.time.Duration;

public final class FileDataSource implements DataSource, SeekableDataSource {

    private final File source;

    FileDataSource(File source) {
        this.source = source;
    }

    @Override
    public AudioInputStream openAtPosition(long bytePosition) throws IOException, UnsupportedAudioFileException {
        var fis = new java.io.FileInputStream(source);
        long skipped = fis.skip(bytePosition);
        if (skipped < bytePosition) {
            fis.close();
            throw new IOException("Failed to skip " + bytePosition + " bytes in " + source);
        }
        return AudioSystem.getAudioInputStream(new BufferedInputStream(fis));
    }

    @Override
    public long getContentLength() {
        return source.length();
    }

    @Override
    public AudioFileFormat getAudioFileFormat() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioFileFormat(this.source);
    }

    @Override
    public AudioInputStream getAudioInputStream() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioInputStream(source);
    }

    @Override
    public int getDurationInSeconds() {
        return TimeTool.durationInSeconds(source.getAbsolutePath(), AudioType.FILE);
    }
    
    @Override
    public long getDurationInMilliseconds() {
    	return TimeTool.durationInMilliseconds(source.getAbsolutePath(), AudioType.FILE);
    }
    
    @Override
    public Duration getDuration() {
        return Duration.ofMillis(getDurationInMilliseconds());
    }

    @Override
    public long getSize() {
        return source.length();
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "FileDataSource with " + source.toString();
    }

    @Override
    public boolean isFile() {
       return true;
   }
}
