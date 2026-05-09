package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.stream.StreamPlayerException.PlayerException;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages mixer selection and SourceDataLine creation/lifecycle.
 */
public class LineManager {

    private final Logger logger;
    private final AudioOutlet outlet;

    private String mixerName;
    private Mixer mixer = null;
    private int currentLineBufferSize = -1;
    private int lineBufferSize = -1;

    public LineManager(Logger logger, AudioOutlet outlet) {
        this.logger = logger;
        this.outlet = outlet;
    }

    // ---- Mixer management ----

    public List<String> getMixers() {
        final var lineInfo = new Line.Info(SourceDataLine.class);
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(info -> AudioSystem.getMixer(info).isLineSupported(lineInfo))
                .map(Mixer.Info::getName)
                .toList();
    }

    private Mixer getMixer(String name) {
        if (name == null) return null;
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(info -> info.getName().equals(name))
                .findFirst()
                .map(AudioSystem::getMixer)
                .orElse(null);
    }

    public void setMixerName(String name) {
        this.mixerName = name;
    }

    public String getMixerName() {
        return mixerName;
    }

    public Mixer getCurrentMixer() {
        return mixer;
    }

    // ---- Buffer size ----

    public int getLineBufferSize() {
        return lineBufferSize;
    }

    public void setLineBufferSize(int size) {
        this.lineBufferSize = size;
    }

    public int getCurrentLineBufferSize() {
        return currentLineBufferSize;
    }

    // ---- Line creation ----

    /**
     * Creates the audio output line from the given AudioInputStream format.
     * Handles PCM conversion, mixer selection, and SourceDataLine setup.
     */
    public void createLine(AudioStreamManager streamManager, double speedFactor)
            throws LineUnavailableException, StreamPlayerException {
        logger.info("Entered createLine()!");

        if (outlet.getSourceDataLine() != null) {
            logger.warning("Source DataLine is not null!");
            return;
        }

        final var sourceFormat = streamManager.getAudioInputStream().getFormat();
        logger.info(() -> "Source format: " + sourceFormat);

        int sampleBits = sourceFormat.getSampleSizeInBits();
        if (sourceFormat.getEncoding() == AudioFormat.Encoding.ULAW
                || sourceFormat.getEncoding() == AudioFormat.Encoding.ALAW
                || sampleBits != 8) {
            sampleBits = 16;
        }

        final var targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                (float) (sourceFormat.getSampleRate() * speedFactor),
                sampleBits,
                sourceFormat.getChannels(),
                sampleBits / 8 * sourceFormat.getChannels(),
                sourceFormat.getSampleRate(),
                false);

        logger.info(() -> "Target format: " + targetFormat);

        // Keep reference on encoded stream for progress notification
        AudioInputStream originalStream = streamManager.getAudioInputStream();
        streamManager.setEncodedAudioInputStream(originalStream);
        try {
            streamManager.setEncodedAudioLength(originalStream.available());
        } catch (final IOException e) {
            logger.warning(() -> "Cannot get encodedAudioInputStream.available(): " + e);
        }

        // Create decoded PCM stream
        AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, originalStream);
        streamManager.setAudioInputStream(pcmStream);

        final var lineInfo = new DataLine.Info(SourceDataLine.class,
                streamManager.getAudioInputStream().getFormat(),
                AudioSystem.NOT_SPECIFIED);
        if (!AudioSystem.isLineSupported(lineInfo)) {
            throw new StreamPlayerException(PlayerException.LINE_NOT_SUPPORTED);
        }

        // Resolve mixer
        if (mixerName == null) {
            mixerName = getMixers().getFirst();
        }
        mixer = getMixer(mixerName);
        if (mixer == null) {
            outlet.setSourceDataLine((SourceDataLine) AudioSystem.getLine(lineInfo));
            mixerName = null;
        } else {
            logger.info(() -> "Mixer: " + mixer.getMixerInfo());
            outlet.setSourceDataLine((SourceDataLine) mixer.getLine(lineInfo));
        }

        logger.info(() -> "Line: " + outlet.getSourceDataLine());
        logger.info("Exited createLine()!");
    }

    /**
     * Initializes the audio output line, creating it if needed.
     */
    public void initLine(AudioStreamManager streamManager, double speedFactor)
            throws LineUnavailableException, StreamPlayerException {
        logger.info("Initializing the line...");

        if (outlet.getSourceDataLine() == null) {
            createLine(streamManager, speedFactor);
        }

        if (!outlet.getSourceDataLine().isOpen()) {
            currentLineBufferSize = lineBufferSize >= 0
                    ? lineBufferSize
                    : outlet.getSourceDataLine().getBufferSize();
            openLine(streamManager.getAudioInputStream().getFormat(), currentLineBufferSize);
        } else {
            var currentFormat = streamManager.getAudioInputStream() != null
                    ? streamManager.getAudioInputStream().getFormat()
                    : null;
            if (!outlet.getSourceDataLine().getFormat().equals(currentFormat)) {
                outlet.getSourceDataLine().close();
                currentLineBufferSize = lineBufferSize >= 0
                        ? lineBufferSize
                        : outlet.getSourceDataLine().getBufferSize();
                openLine(streamManager.getAudioInputStream().getFormat(), currentLineBufferSize);
            }
        }
    }

    private void openLine(AudioFormat format, int bufferSize) throws LineUnavailableException {
        outlet.open(format, bufferSize);
    }

    // ---- Reset ----

    public void reset() {
        mixerName = null;
        mixer = null;
        currentLineBufferSize = -1;
    }
}
