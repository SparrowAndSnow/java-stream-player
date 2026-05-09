package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.Status;
import com.goxr3plus.streamplayer.stream.StreamPlayerException.PlayerException;
import org.tritonus.share.sampled.TAudioFormat;
import org.tritonus.share.sampled.file.TAudioFileFormat;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages AudioInputStream lifecycle: opening, closing, and property extraction.
 */
public class AudioStreamManager {

    private final Logger logger;

    private volatile AudioInputStream audioInputStream;
    private AudioInputStream encodedAudioInputStream;
    private AudioFileFormat audioFileFormat;
    private int encodedAudioLength = -1;

    public AudioStreamManager(Logger logger) {
        this.logger = logger;
    }

    // ---- Getters ----

    public AudioInputStream getAudioInputStream() {
        return audioInputStream;
    }

    public void setAudioInputStream(AudioInputStream stream) {
        this.audioInputStream = stream;
    }

    public AudioInputStream getEncodedAudioInputStream() {
        return encodedAudioInputStream;
    }

    public void setEncodedAudioInputStream(AudioInputStream stream) {
        this.encodedAudioInputStream = stream;
    }

    public AudioFileFormat getAudioFileFormat() {
        return audioFileFormat;
    }

    public void setAudioFileFormat(AudioFileFormat format) {
        this.audioFileFormat = format;
    }

    public int getEncodedAudioLength() {
        return encodedAudioLength;
    }

    public void setEncodedAudioLength(int length) {
        this.encodedAudioLength = length;
    }

    // ---- Stream lifecycle ----

    /**
     * Close the audio input stream.
     */
    public void closeStream() {
        try {
            if (audioInputStream != null) {
                audioInputStream.close();
                logger.info("Stream closed");
            }
        } catch (final IOException ex) {
            logger.warning("Cannot close stream\n" + ex);
        }
    }

    // ---- Property extraction ----

    /**
     * Determines audio properties from AudioFileFormat and notifies listeners.
     */
    public Map<String, Object> determineProperties(AudioOutlet outlet, DataSource source,
                                                   Map<String, Object> audioProperties,
                                                   EventDispatcher eventDispatcher) {
        logger.info("Entered determineProperties()!");

        if (audioFileFormat == null) return new HashMap<>();

        // Initialize properties from Tritonus SPI if available, or empty map
        Map<String, Object> props = audioFileFormat instanceof TAudioFileFormat taff
                ? new HashMap<>(taff.properties())
                : new HashMap<>();

        // Add JavaSound AudioFileFormat properties
        putIfPositive(props, "audio.length.bytes", audioFileFormat.getByteLength());
        putIfPositive(props, "audio.length.frames", audioFileFormat.getFrameLength());
        if (audioFileFormat.getType() != null) {
            props.put("audio.type", audioFileFormat.getType());
        }

        // AudioFormat properties
        final var af = audioFileFormat.getFormat();
        putIfPositive(props, "audio.framerate.fps", (long) af.getFrameRate());
        putIfPositive(props, "audio.framesize.bytes", (long) af.getFrameSize());
        putIfPositive(props, "audio.samplerate.hz", (long) af.getSampleRate());
        putIfPositive(props, "audio.samplesize.bits", (long) af.getSampleSizeInBits());
        putIfPositive(props, "audio.channels", (long) af.getChannels());

        // Tritonus SPI audio format properties
        if (af instanceof TAudioFormat taf) {
            props.putAll(taf.properties());
        }

        props.put("basicplayer.sourcedataline", outlet.getSourceDataLine());

        // Notify listeners
        eventDispatcher.getListeners().forEach(listener -> listener.opened(source.getSource(), props));

        logger.info("Exited determineProperties()!");
        return props;
    }

    private static void putIfPositive(Map<String, Object> map, String key, long value) {
        if (value > 0) map.put(key, value);
    }

    // ---- Reset ----

    public void reset(boolean full) {
        if (full) {
            encodedAudioLength = -1;
        }
        audioInputStream = null;
        audioFileFormat = null;
        encodedAudioInputStream = null;
    }
}
