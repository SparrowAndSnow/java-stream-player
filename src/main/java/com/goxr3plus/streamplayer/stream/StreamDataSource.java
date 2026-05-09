package com.goxr3plus.streamplayer.stream;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class StreamDataSource implements DataSource {

    private final InputStream source;
    private Long cachedDurationMillis = null;

    StreamDataSource(InputStream source) {
        this.source = source;
    }

    @Override
    public AudioFileFormat getAudioFileFormat() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioFileFormat(source);
    }

    @Override
    public AudioInputStream getAudioInputStream() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioInputStream(source);
    }

    @Override
    public int getDurationInSeconds() {
        long millis = getDurationInMilliseconds();
        return millis == -1 ? -1 : (int) (millis / 1000);
    }
    
    @Override
    public long getDurationInMilliseconds() {
        if (cachedDurationMillis != null) {
            return cachedDurationMillis;
        }
        
        try {
            AudioFileFormat format = getAudioFileFormat();
            AudioFormat audioFormat = format.getFormat();
            
            Object durationObj = format.properties().get("duration");
            if (durationObj instanceof Number durationNum && durationNum.longValue() > 0) {
                cachedDurationMillis = durationNum.longValue() / 1000L;
                return cachedDurationMillis;
            }

            float frameRate = audioFormat.getFrameRate();
            long frameLength = format.getFrameLength();
            
            if (frameRate > 0 && frameLength > 0) {
                cachedDurationMillis = (long) ((frameLength / frameRate) * 1000);
                return cachedDurationMillis;
            }
            
            Long bitrate = extractBitrate(format.properties(), audioFormat);
            long availableBytes = source.available();
            
            if (bitrate != null && bitrate > 0 && availableBytes > 0) {
                cachedDurationMillis = (availableBytes * 8 * 1000) / bitrate;
                return cachedDurationMillis;
            }
        } catch (Exception e) {
            System.err.println("Failed to calculate InputStream duration: " + e.getMessage());
        }
        
        return -1;
    }
    
    private Long extractBitrate(java.util.Map<?, ?> properties, AudioFormat audioFormat) {
        if (properties != null) {
            String[] bitrateKeys = {"bitrate", "audio.bitrate", "mp3.bitrate", "mp3.bitrate.nsb", "mp3.bitrate.nsynch"};
            for (String key : bitrateKeys) {
                Object value = properties.get(key);
                if (value instanceof Number number) {
                    long bps = number.longValue();
                    if (bps > 0) return bps < 10000 ? bps * 1000 : bps;
                }
            }
        }
        
        if (audioFormat != null) {
            float sampleRate = audioFormat.getSampleRate();
            int channels = audioFormat.getChannels();
            int sampleSizeInBits = audioFormat.getSampleSizeInBits();
            
            if (sampleRate > 0 && channels > 0 && sampleSizeInBits > 0) {
                return (long) (sampleRate * channels * sampleSizeInBits);
            }
        }
        
        return null;
    }
    
    @Override
    public Duration getDuration() {
        long millis = getDurationInMilliseconds();
        return millis == -1 ? null : Duration.ofMillis(millis);
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "StreamDataSource with " + source.toString();
    }

    @Override
    public boolean isFile() {
       return false;
   }
}