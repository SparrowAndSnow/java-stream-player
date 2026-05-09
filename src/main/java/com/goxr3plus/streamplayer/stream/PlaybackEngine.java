package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.Status;
import javazoom.spi.PropertiesContainer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The audio playback loop. Reads PCM data from the AudioInputStream,
 * writes it to the SourceDataLine, and notifies progress listeners.
 * Runs as a Callable submitted to the stream player executor.
 */
public class PlaybackEngine implements Callable<Void> {

    private static final int EXTERNAL_BUFFER_SIZE = 4096;

    private final Logger logger;
    private final AudioStreamManager streamManager;
    private final AudioOutlet outlet;
    private final EventDispatcher eventDispatcher;
    private final StateManager stateManager;
    private final SeekService seekService;

    private final Object audioLock;
    private final Object pauseLock = new Object();

    public PlaybackEngine(Logger logger, AudioStreamManager streamManager, AudioOutlet outlet,
                          EventDispatcher eventDispatcher, StateManager stateManager, SeekService seekService,
                          Object audioLock) {
        this.logger = logger;
        this.streamManager = streamManager;
        this.outlet = outlet;
        this.eventDispatcher = eventDispatcher;
        this.stateManager = stateManager;
        this.seekService = seekService;
        this.audioLock = audioLock;
    }

    @Override
    public Void call() {
        int nBytesRead = 0;
        final int audioDataLength = EXTERNAL_BUFFER_SIZE;
        final ByteBuffer audioDataBuffer = ByteBuffer.allocate(audioDataLength);
        audioDataBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Lock stream while playing (prevents concurrent seek operations)
        synchronized (audioLock) {
            while ((nBytesRead != -1) && stateManager.getStatus() != Status.STOPPED
                    && stateManager.getStatus() != Status.NOT_SPECIFIED
                    && stateManager.getStatus() != Status.SEEKING) {

                try {
                    if (stateManager.getStatus() == Status.PLAYING) {

                        int toRead = audioDataLength;
                        int totalRead = 0;

                        for (; toRead > 0 && (nBytesRead = streamManager.getAudioInputStream()
                                .read(audioDataBuffer.array(), totalRead, toRead)) != -1;
                             toRead -= nBytesRead, totalRead += nBytesRead) {

                            if (outlet.getSourceDataLine().available() >= outlet.getSourceDataLine().getBufferSize())
                                logger.info(() -> "Underrun> Available=" + outlet.getSourceDataLine().available()
                                        + " , SourceDataLineBuffer=" + outlet.getSourceDataLine().getBufferSize());
                        }

                        if (totalRead > 0) {
                            var buffer = audioDataBuffer.array();
                            if (totalRead < buffer.length) {
                                buffer = new byte[totalRead];
                                System.arraycopy(audioDataBuffer.array(), 0, buffer, 0, totalRead);
                            }

                            outlet.getSourceDataLine().write(buffer, 0, totalRead);

                            // Encoded stream position
                            int nEncodedBytes = -1;
                            var eais = streamManager.getEncodedAudioInputStream();
                            if (eais != null) {
                                try {
                                    nEncodedBytes = streamManager.getEncodedAudioLength() - eais.available();
                                    nEncodedBytes = (int) (seekService.getSeekOffset() + nEncodedBytes);
                                } catch (IOException ignored) {
                                }
                            }

                            long positionInMilliseconds = seekService.getBaseMillisecondPosition()
                                    + outlet.getSourceDataLine().getMicrosecondPosition() / 1000;

                            final var properties = streamManager.getAudioInputStream() instanceof PropertiesContainer pc
                                    ? pc.properties()
                                    : eventDispatcher.getEmptyMap();

                            eventDispatcher.fireProgress(nEncodedBytes, positionInMilliseconds, buffer, properties);
                        }

                    } else if (stateManager.getStatus() == Status.PAUSED) {
                        outlet.flushAndStop();
                        waitWhilePaused();

                        if (stateManager.getStatus() == Status.PLAYING && outlet.isStartable()) {
                            outlet.start();
                        }
                    }
                } catch (final IOException ex) {
                    logger.log(Level.WARNING, "\"Decoder Exception: \" ", ex);
                    stateManager.setStatus(Status.STOPPED);
                }
            }
        } // synchronized (audioLock)

        // Free audio resources.
        outlet.drainStopAndFreeDataLine();
        streamManager.closeStream();

        if (nBytesRead == -1) {
            // EOM handled by caller
        }

        logger.info("Decoding thread completed");
        return null;
    }

    private void waitWhilePaused() {
        synchronized (pauseLock) {
            try {
                while (stateManager.getStatus() == Status.PAUSED) {
                    pauseLock.wait();
                }
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.warning(() -> "Thread interrupted while paused.\n" + ex);
            }
        }
    }

    /**
     * Notify the pause lock so the engine resumes.
     */
    public void notifyPauseResume() {
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }
}
