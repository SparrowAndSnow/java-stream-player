/*
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>. Also(warning!): 1)You are not allowed to sell this product to third party. 2)You can't change license and made it
 * like you are the owner,author etc. 3)All redistributions of source code files must contain all copyright notices that are currently in this file,
 * and this list of conditions without modification.
 */

package com.goxr3plus.streamplayer.stream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;


import com.goxr3plus.streamplayer.enums.Status;
import com.goxr3plus.streamplayer.stream.StreamPlayerException.PlayerException;

import javazoom.spi.PropertiesContainer;

/**
 * StreamPlayer is a class based on JavaSound API. It has been successfully tested under Java 10
 *
 * @author GOXR3PLUS (www.goxr3plus.co.nf)
 * @author JavaZOOM (www.javazoom.net)
 */
public class StreamPlayer implements StreamPlayerInterface, Callable<Void> {

    /**
     * Class logger
     */
    private final Logger logger;

    // State management
    private final StateManager stateManager = new StateManager();

    // Stream lifecycle management
    private final AudioStreamManager streamManager;

    // Mixer and line management
    private final LineManager lineManager;

    /**
     * The data source
     */
    private DataSource source;

    // -------------------LOCKS---------------------

    /**
     * Synchronization lock for stream operations.
     */
    private final Object audioLock = new Object();

    /**
     * The audio playback engine (call() loop, pause/resume).
     */
    private final PlaybackEngine playbackEngine;

    // -------------------VARIABLES---------------------

    /**
     * State holder for seeking operations.
     */
    private final SeekService seekService;

    /**
     * Speed Factor of the Audio
     */
    private double speedFactor = 1;

    /**
     * The Constant SKIP_INACCURACY_SIZE.
     */
    // private static final int SKIP_INACCURACY_SIZE = 1200

    // -------------------CLASSES---------------------

    /**
     * This is starting a Thread for StreamPlayer to Run
     */
    private final ExecutorService streamPlayerExecutorService;
    private Future<Void> future;

    /**
     * Dispatches listener events (status async, progress sync).
     */
    private final EventDispatcher eventDispatcher;

    // Properties when the File/URL/InputStream is opened.
    Map<String, Object> audioProperties;

    /**
     * Responsible for the output SourceDataLine and the controls that depend on it.
     */
    private final AudioOutlet outlet;

    /**
     * Default parameter less Constructor. A default logger will be used.
     */
    public StreamPlayer() {
        this(Logger.getLogger(StreamPlayer.class.getName()));

    }

    /**
     * Constructor with a logger.
     *
     * @param logger The logger that will be used by the player
     */
    public StreamPlayer(Logger logger) {
        this(logger,
                Executors.newSingleThreadExecutor(new ThreadFactoryWithNamePrefix("StreamPlayer")),
                Executors.newSingleThreadExecutor(new ThreadFactoryWithNamePrefix("StreamPlayerEvent")));
    }

    /**
     * Constructor with settable logger and executor services.
     *
     * @param logger                      The logger that will be used by the player
     * @param streamPlayerExecutorService Executor service for the stream player
     * @param eventsExecutorService       Executor service for events.
     */
    public StreamPlayer(Logger logger, ExecutorService streamPlayerExecutorService, ExecutorService eventsExecutorService) {
        this.logger = logger;
        this.streamPlayerExecutorService = streamPlayerExecutorService;
        this.eventDispatcher = new EventDispatcher(logger, eventsExecutorService);
        this.streamManager = new AudioStreamManager(logger);
        this.outlet = new Outlet(logger);
        this.lineManager = new LineManager(logger, outlet);
        this.seekService = new SeekService();
        this.playbackEngine = new PlaybackEngine(logger, streamManager, outlet,
                eventDispatcher, stateManager, seekService, audioLock);
        reset();
    }

    /**
     * Freeing the resources.
     */
    @Override
    public void reset() {

        // Close the stream
        synchronized (audioLock) {
            streamManager.closeStream();
        }

        outlet.flushAndFreeDataLine();

        // AudioFile
        streamManager.reset(true);
        seekService.reset();

        // Controls
        outlet.setGainControl(null);
        outlet.setPanControl(null);
        outlet.setBalanceControl(null);

        // Notify the Status
        stateManager.setStatus(Status.NOT_SPECIFIED);
        eventDispatcher.fireStatus(this,Status.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, null);

    }

    /**
     * Add a listener to be notified.
     *
     * @param streamPlayerListener the listener
     */
    @Override
    public void addStreamPlayerListener(final StreamPlayerListener streamPlayerListener) {
        eventDispatcher.addListener(streamPlayerListener);
    }

    /**
     * Remove registered listener.
     *
     * @param streamPlayerListener the listener
     */
    @Override
    public void removeStreamPlayerListener(final StreamPlayerListener streamPlayerListener) {
        eventDispatcher.removeListener(streamPlayerListener);
    }

    /**
     * Open the specified file for playback.
     *
     * @param file the file to be played
     * @throws StreamPlayerException the stream player exception
     */
    @Override
    public void open(File file) throws StreamPlayerException {

        logger.info(() -> "open(" + file + ")\n");
        source = new FileDataSource(file);
        initAudioInputStream();
    }

    /**
     * Open the specified location for playback.
     *
     * @param uri the location / network to be played
     * @throws StreamPlayerException the stream player exception
     */
    @Override
    public void open(URI uri) throws StreamPlayerException {
        logger.info(() -> "open(" + uri + ")\n");
        source = new UriDataSource(uri);
        initAudioInputStream();
    }

    /**
     * Open the specified stream for playback.
     *
     * @param stream the stream to be played
     * @throws StreamPlayerException the stream player exception
     */
    @Override
    public void open(InputStream stream) throws StreamPlayerException {
        logger.info(() -> "open(" + stream + ")\n");
        source = new StreamDataSource(stream);
        initAudioInputStream();
    }

    /**
     * Create AudioInputStream and AudioFileFormat from the data source.
     *
     * @throws StreamPlayerException the stream player exception
     */
    private void initAudioInputStream() throws StreamPlayerException {
        initAudioInputStream(true);
    }

    /**
     * Create AudioInputStream and AudioFileFormat from the data source.
     *
     * @param resetSeekOffset whether to reset seek offset
     * @throws StreamPlayerException the stream player exception
     */
    private void initAudioInputStream(boolean resetSeekOffset) throws StreamPlayerException {
        try {
            logger.info("Entered initAudioInputStream");

            if (resetSeekOffset) {
                reset();
            } else {
                // Only close stream, don't reset seekOffset
                synchronized (audioLock) {
                    streamManager.closeStream();
                }
                outlet.flushAndFreeDataLine();
                streamManager.reset(false);
                // Keep encodedAudioLength and seekOffset
            }

            stateManager.setStatus(Status.OPENING);
            eventDispatcher.fireStatus(this,Status.OPENING, getEncodedStreamPosition(), source);

            AudioFileFormat aff;
            AudioInputStream ais;
            if (source.isFile()) {
                aff = source.getAudioFileFormat();
                ais = source.getAudioInputStream();
            } else {
                aff = null;
                ais = source.getAudioInputStream();
            }

            streamManager.setAudioFileFormat(aff);
            streamManager.setAudioInputStream(ais);
            streamManager.setEncodedAudioInputStream(ais);

            // Set encodedAudioLength from available bytes
            try {
                streamManager.setEncodedAudioLength(ais.available());
            } catch (IOException e) {
                logger.warning(() -> "Cannot get streamManager.getAudioInputStream().available(): " + e);
            }

            createLine();
            var props = streamManager.determineProperties(outlet, source, audioProperties, eventDispatcher);
            if (props != null) audioProperties = props;

            stateManager.setStatus(Status.OPENED);
            eventDispatcher.fireStatus(this,Status.OPENED, getEncodedStreamPosition(), null);

        } catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
            logger.log(Level.INFO, e.getMessage(), e);
            throw new StreamPlayerException(e);
        }

        logger.info("Exited initAudioInputStream");
    }


    /**
     * Determines Properties when the File/URL/InputStream is opened.
     */
    private void determineProperties() {
        audioProperties = streamManager.determineProperties(outlet, source, audioProperties, eventDispatcher);
    }

    /**
     * Initializes the audio output line, creating it if needed, and opening it with the correct format.
     *
     * @throws LineUnavailableException the line unavailable exception
     * @throws StreamPlayerException    if the line cannot be created
     */
    private void initLine() throws LineUnavailableException, StreamPlayerException {
        lineManager.initLine(streamManager, speedFactor);
    }

    /**
     * Change the Speed Rate of the Audio , this variable affects the Sample Rate ,
     * for example 1.0 is normal , 0.5 is half the speed and 2.0 is double the speed
     * Note that you have to restart the audio for this to take effect
     *
     * @param speedFactor speedFactor
     */
    @Override
    public void setSpeedFactor(final double speedFactor) {
        this.speedFactor = speedFactor;

    }

    /**
     * Creates the audio output line (SourceDataLine) from the AudioInputStream format.
     * Handles PCM conversion, mixer selection, and decoded stream setup.
     *
     * @throws LineUnavailableException the line unavailable exception
     * @throws StreamPlayerException    if the line format is not supported
     */
    private void createLine() throws LineUnavailableException, StreamPlayerException {
        lineManager.createLine(streamManager, speedFactor);
    }

    /**
     * Open the line.
     *
     * @param audioFormat
     * @param bufferSize
     * @throws LineUnavailableException the line unavailable exception
     */
    private void openLine(AudioFormat audioFormat, int bufferSize) throws LineUnavailableException {
        outlet.open(audioFormat, bufferSize);
    }

    /**
     * Starts the play back.
     *
     * @throws StreamPlayerException the stream player exception
     */
    @Override
    public void play() throws StreamPlayerException {
        // If paused, resume instead
        if (stateManager.getStatus() == Status.PAUSED) {
            resume();
            return;
        }

        if (stateManager.getStatus() == Status.STOPPED)
            initAudioInputStream();
        if (stateManager.getStatus() != Status.OPENED)
            return;

        // Shutdown previous Thread Running
        awaitTermination();

        // Open SourceDataLine.
        try {
            initLine();
        } catch (final LineUnavailableException ex) {
            throw new StreamPlayerException(PlayerException.CAN_NOT_INIT_LINE, ex);
        }

        // Open the sourceDataLine
        if (outlet.isStartable()) {
            outlet.start();

            // Proceed only if we have not problems
            logger.info("Submitting new StreamPlayer Thread");
            streamPlayerExecutorService.submit(this);

            // Update the status
            stateManager.setStatus(Status.PLAYING);

            // Restore cached audio settings to the new line
            restoreAudioSettings();

            eventDispatcher.fireStatus(this,Status.PLAYING, getEncodedStreamPosition(), null);
        }
    }

    /**
     * Restores cached audio control settings (gain, mute, pan, balance)
     * to the current SourceDataLine. Called after opening a new source
     * so that volume/mute/pan/balance survive across source changes.
     */
    private void restoreAudioSettings() {
        // Restore gain (volume) — bypass the isPausedOrPlaying() check
        if (stateManager.getCachedGain() >= 0 && outlet.hasControl(FloatControl.Type.MASTER_GAIN, outlet.getGainControl())) {
            outlet.getGainControl().setValue((float) (20 * Math.log10(stateManager.getCachedGain())));
        }
        // Restore mute
        if (outlet.hasControl(BooleanControl.Type.MUTE, outlet.getMuteControl())) {
            outlet.getMuteControl().setValue(stateManager.isCachedMute());
        }
        // Restore pan
        if (stateManager.getCachedPan() >= -1.0 && stateManager.getCachedPan() <= 1.0
                && outlet.hasControl(FloatControl.Type.PAN, outlet.getPanControl())) {
            outlet.getPanControl().setValue((float) stateManager.getCachedPan());
        }
        // Restore balance
        if (stateManager.getCachedBalance() >= -1.0 && stateManager.getCachedBalance() <= 1.0
                && outlet.hasControl(FloatControl.Type.BALANCE, outlet.getBalanceControl())) {
            outlet.getBalanceControl().setValue(stateManager.getCachedBalance());
        }
    }

    /**
     * Pauses the play back.<br>
     * <p>
     * Player Status = PAUSED. * @return False if failed(so simple...)
     *
     * @return true, if successful
     */
    @Override
    public boolean pause() {
        if (outlet.getSourceDataLine() == null || stateManager.getStatus() != Status.PLAYING)
            return false;
        stateManager.setStatus(Status.PAUSED);
        logger.info("pausePlayback() completed");
        eventDispatcher.fireStatus(this,Status.PAUSED, getEncodedStreamPosition(), null);
        return true;
    }

    /**
     * Stops the play back.<br>
     * <p>
     * Player Status = STOPPED.<br>
     * Thread should free Audio resources.
     */
    @Override
    public void stop() {
        if (stateManager.getStatus() == Status.STOPPED)
            return;
        if (isPlaying())
            pause();
        stateManager.setStatus(Status.STOPPED);
        // eventDispatcher.fireStatus(this,Status.STOPPED, getEncodedStreamPosition(), null);
        logger.info("StreamPlayer stopPlayback() completed");
    }

    /**
     * Resumes the play back.<br>
     * <p>
     * Player Status = PLAYING*
     *
     * @return False if failed(so simple...)
     */
    @Override
    public boolean resume() {
        if (outlet.getSourceDataLine() == null || stateManager.getStatus() != Status.PAUSED)
            return false;
        outlet.start();
        stateManager.setStatus(Status.PLAYING);
        playbackEngine.notifyPauseResume();
        eventDispatcher.fireStatus(this,Status.RESUMED, getEncodedStreamPosition(), null);
        logger.info("resumePlayback() completed");
        return true;

    }

    /**
     * Wait for the StreamPlayer executor task to finish, then cancel it if still running.
     * Uses a non-blocking polling approach with a timeout to avoid hanging indefinitely.
     */
    private void awaitTermination() {
        if (future == null || future.isDone()) return;

        try {
            // Wait up to ~1 second for graceful completion, then cancel
            future.get(1000, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException ex) {
            logger.log(Level.WARNING, "StreamPlayer task failed", ex.getCause());
        } catch (final CancellationException ex) {
            logger.log(Level.INFO, "StreamPlayer task was already cancelled");
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "StreamPlayer await interrupted", ex);
        } catch (final java.util.concurrent.TimeoutException ex) {
            logger.log(Level.INFO, "StreamPlayer task did not finish in time, cancelling");
        } finally {
            future.cancel(true); // interrupt if still running
        }
    }

    /**
     * Skip bytes in the File input stream. It will skip N frames matching to bytes,
     * so it will never skip given bytes len
     *
     * @param bytes the bytes
     * @return value bigger than 0 for File and value = 0 for URL and InputStream
     * @throws StreamPlayerException the stream player exception
     */
    @Override
    public long seekBytes(final long bytes) throws StreamPlayerException {
        long totalSkipped = 0;

        // Check if the requested bytes are more than totalBytes of Audio
        final long bytesLength = getTotalBytes();
        logger.log(Level.INFO, "Bytes: " + bytes + " BytesLength: " + bytesLength);

        // Only use bytesLength for EOM check if it's a reliable file size (> 100KB).
        // URL streams return buffer size (typically < 64KB) from available(),
        // which is not the actual file size. Without this guard, PCM-based byte
        // estimates for network seek would falsely trigger EOM.
        boolean bytesLengthReliable = bytesLength > 1024 * 100;
        if (bytesLengthReliable && bytes >= bytesLength) {
            eventDispatcher.fireStatus(this,Status.EOM, getEncodedStreamPosition(), null);
            return totalSkipped;
        }

        // If total bytes is unknown or unreliable (e.g. network stream), log warning and proceed
        if (!bytesLengthReliable) {
            logger.warning(() -> "Total bytes unreliable (" + bytesLength + "), attempting seek anyway (bytes=" + bytes + ")");
        }

        logger.info(() -> "Bytes to skip : " + bytes);
        final Status previousStatus = stateManager.getStatus();
        stateManager.setStatus(Status.SEEKING);

        try {
            synchronized (audioLock) {
                eventDispatcher.fireStatus(this,Status.SEEKING, AudioSystem.NOT_SPECIFIED, null);
                
                // Stop current playback
                if (outlet.getSourceDataLine() != null && outlet.getSourceDataLine().isRunning()) {
                    outlet.getSourceDataLine().stop();
                    outlet.getSourceDataLine().flush();
                }
                
                // For file and URI sources, reinitialize stream for reliable seeking
                // StreamDataSource (raw InputStream) cannot be reinitialized
                if (source instanceof SeekableDataSource) {
                    // Reinitialize audio stream (preserve encodedAudioLength and seekOffset)
                    initAudioInputStream(false);
                    // Reset seekOffset since we're starting from the beginning of the reinitialized stream
                    seekService.setSeekOffset(0);
                }
                
                if (streamManager.getAudioInputStream() != null) {

                    long skipped;
                    // Loop until bytes are really skipped.
                    while (totalSkipped < bytes) {
                        skipped = streamManager.getAudioInputStream().skip(bytes - totalSkipped);
                        if (skipped == 0) {
                            // skip() not supported (e.g. network stream).
                            // Fall back to reading and discarding PCM data.
                            byte[] discardBuf = new byte[4096];
                            int n = streamManager.getAudioInputStream().read(discardBuf, 0,
                                    (int) Math.min(discardBuf.length, bytes - totalSkipped));
                            if (n == -1)
                                break;
                            skipped = n;
                        }
                        if (skipped == -1)
                            throw new StreamPlayerException(
                                    PlayerException.SKIP_NOT_SUPPORTED);
                        totalSkipped += skipped;
                        logger.info("Skipped : " + totalSkipped + "/" + bytes);
                    }
                }
            }
            
            // Set seek offset to track the absolute position
            // For file sources, this is the bytes we skipped from the beginning
            seekService.setSeekOffset(totalSkipped);

            // Update base position in milliseconds so progress() reports correct
            // absolute position even after SourceDataLine is recreated.
            // When seekTo() is used, pendingSeekMilliseconds holds the exact target;
            // for direct seekBytes() calls, estimate from PCM bytes skipped.
            if (seekService.getPendingSeekMilliseconds() >= 0) {
                seekService.setBaseMillisecondPosition(seekService.getPendingSeekMilliseconds());
                seekService.setPendingSeekMilliseconds(-1);
            } else if (totalSkipped > 0 && streamManager.getAudioInputStream() != null) {
                var fmt = streamManager.getAudioInputStream().getFormat();
                float frameRate = fmt.getFrameRate();
                int frameSize = fmt.getFrameSize();
                if (frameRate > 0 && frameSize > 0) {
                    float pcmBytesPerSecond = frameRate * frameSize;
                    seekService.setBaseMillisecondPosition((long) (totalSkipped / pcmBytesPerSecond * 1_000L));
                }
            } else {
                seekService.setBaseMillisecondPosition(0);
            }

            eventDispatcher.fireStatus(this,Status.SEEKED, getEncodedStreamPosition(), null);
            stateManager.setStatus(Status.OPENED);
            
            // Resume playback based on previous status
            if (previousStatus == Status.PLAYING) {
                play();
            } else if (previousStatus == Status.PAUSED) {
                play();
                pause();
            }

        } catch (final IOException ex) {
            logger.log(Level.WARNING, ex.getMessage(), ex);
        }
        
        return totalSkipped;
    }

    /**
     * Skip x seconds of audio
     * See  {@link #seekBytes(long)}
     *
     * @param seconds Seconds to Skip
     */
    @Override
    public long seekSeconds(int seconds) throws StreamPlayerException {
        int durationInSeconds = this.getDurationInSeconds();

        //Validate
        validateSeconds(seconds, durationInSeconds);

        //Calculate byte increment from current position
        long totalBytes = getTotalBytes();
        long byteIncrement = totalBytes * seconds / durationInSeconds;

        return seekBytes(this.getEncodedStreamPosition() + byteIncrement);
    }

//	/**
//	 * Skip seconds of audio based on the pattern
//	 * See  {@link #seek(long)}
//	 *
//	 * @param pattern A string in the format (HH:MM:SS) WHERE h = HOURS , M = minutes , S = seconds
//	 */
//	public void seek(String pattern) throws StreamPlayerException {
//		long bytes = 0;
//
//		seek(bytes);
//	}

    /**
     * Go to X time of the Audio
     * See  {@link #seekBytes(long)}
     *
     * @param seconds Seconds to Skip
     */
    @Override
    public long seekTo(int seconds) throws StreamPlayerException {
        int durationInSeconds = this.getDurationInSeconds();

        //Validate
        validateSeconds(seconds, durationInSeconds);

        //Always set pending time before seekBytes, even when duration is unknown
        long targetMs = seconds * 1000L;
        seekService.setPendingSeekMilliseconds(targetMs);

        //When duration and totalBytes are known, convert to byte position for seek
        if (durationInSeconds > 0) {
            long totalBytes = getTotalBytes();
            // totalBytes > 100KB threshold: only use if it's actual file size,
            // not the buffer size from URL stream's available() which is <64KB.
            if (totalBytes > 1024 * 100) {
                long bytes = totalBytes * seconds / durationInSeconds;
                return seekBytes(bytes);
            }
            // UriDataSource with HTTP Range header seeking (most reliable for MP3 streams)
            if (source instanceof SeekableDataSource seekable && seekable.getContentLength() > 0) {
                long encodedPosition = seekable.getContentLength() * seconds / durationInSeconds;
                // Use setSeekPosition on UriDataSource for the next getAudioInputStream() call
                if (source instanceof UriDataSource uriSource) {
                    uriSource.setSeekPosition(encodedPosition);
                }
                try {
                    // Call seekBytes(0) to trigger reinit; Range header positions the stream
                    return seekBytes(0);
                } catch (Exception e) {
                    // Range header not supported or format not detectable at position
                    // (e.g. WAV, OGG). Fall through to PCM byte estimate below.
                    logger.warning(() -> "Range seek failed, falling back to PCM read/discard: " + e.getMessage());
                }
            }
            // PCM byte estimate fallback (works with read-and-discard for non-Range streams)
            if (streamManager.getAudioInputStream() != null) {
                var fmt = streamManager.getAudioInputStream().getFormat();
                float frameRate = fmt.getFrameRate();
                int frameSize = fmt.getFrameSize();
                if (frameRate > 0 && frameSize > 0) {
                    long bytes = (long) (seconds * frameRate * frameSize);
                    return seekBytes(bytes);
                }
            }
        }

        //Unknown duration or totalBytes: can only set display position via pendingSeekMilliseconds
        return 0;
    }


    private void validateSeconds(int seconds, int durationInSeconds) {
        if (seconds < 0) {
            throw new UnsupportedOperationException("Trying to skip negative seconds ");
        }
        if (durationInSeconds > 0 && seconds >= durationInSeconds) {
            throw new UnsupportedOperationException("Trying to skip with seconds {" + seconds + "} > maximum {" + durationInSeconds + "}");
        }
    }


    /**
     * @return The duration of the source data in seconds, or -1 if duration is unavailable.
     */
    @Override
    public int getDurationInSeconds() {
        return source.getDurationInSeconds();
    }

    /**
     * @return The duration of the source data in milliseconds, or -1 if duration is unavailable.
     */
    @Override
    public long getDurationInMilliseconds() {
        return source.getDurationInMilliseconds();
    }

    /**
     * @return The duration of the source data in a {@code java.time.Duration} instance, or null if unavailable
     */
    @Override
    public Duration getDuration() {
        return source.getDuration();
    }

    /**
     * Main loop.
     * <p>
     * Player Status == STOPPED || SEEKING = End of Thread + Freeing Audio
     * Resources.<br>
     * Player Status == PLAYING = Audio stream data sent to Audio line.<br>
     * Player Status == PAUSED = Waiting for another status.
     */
    @Override
    public Void call() {
        return playbackEngine.call();
    }

    /**
     * Calculates the current position of the encoded audio based on <br>
     * <b>nEncodedBytes = encodedAudioLength -
     * encodedAudioInputStream.available();</b>
     *
     * @return The Position of the encoded stream in term of bytes
     */
    @Override
    public int getEncodedStreamPosition() {
        int position = -1;
        var eais = streamManager.getEncodedAudioInputStream();
        if (eais != null) {
            try {
                int currentPosition = streamManager.getEncodedAudioLength() - eais.available();
                position = (int) (seekService.getSeekOffset() + currentPosition);
            } catch (final IOException ex) {
                logger.log(Level.WARNING, "Cannot get encodedAudioInputStream.available()", ex);
                stop();
            }
        }
        return position;
    }

    /**
     * Close stream.
     */
    private void closeStream() {
        streamManager.closeStream();
    }

    /**
     * Return SourceDataLine buffer size.
     *
     * @return -1 maximum buffer size.
     */
    @Override
    public int getLineBufferSize() {
        return lineManager.getLineBufferSize();
    }

    @Override
    public int getLineCurrentBufferSize() {
        return lineManager.getCurrentLineBufferSize();
    }

    @Override
    public List<String> getMixers() {
        return lineManager.getMixers();
    }

    public void setMixerName(String name) {
        lineManager.setMixerName(name);
    }

    public String getMixerName() {
        return lineManager.getMixerName();
    }

    /**
     * Returns the mixer that is currently being used, if there is no line created this will return null
     *
     * @return The Mixer being used
     */
    public Mixer getCurrentMixer() {
        return lineManager.getCurrentMixer();
    }

    /**
     * Returns Gain value.
     *
     * @return The Gain Value
     */
    @Override
    public float getGainValue() {
        return outlet.getGainValue();
    }

    /**
     * Returns maximum Gain value.
     */
    @Override
    public float getMaximumGain() {
        return outlet.hasControl(FloatControl.Type.MASTER_GAIN, outlet.getGainControl())
                ? outlet.getGainControl().getMaximum()
                : 0.0F;
    }

    /**
     * Returns minimum Gain value.
     */
    @Override
    public float getMinimumGain() {
        return outlet.hasControl(FloatControl.Type.MASTER_GAIN, outlet.getGainControl())
                ? outlet.getGainControl().getMinimum()
                : 0.0F;
    }

    /**
     * Returns Pan precision (resolution/granularity of the pan control).
     */
    @Override
    public float getPrecision() {
        return outlet.hasControl(FloatControl.Type.PAN, outlet.getPanControl())
                ? outlet.getPanControl().getPrecision()
                : 0.0F;
    }

    /**
     * Returns Pan value.
     */
    @Override
    public float getPan() {
        return outlet.hasControl(FloatControl.Type.PAN, outlet.getPanControl())
                ? outlet.getPanControl().getValue()
                : 0.0F;
    }

    /**
     * Return the mute Value (true if muted).
     */
    @Override
    public boolean getMute() {
        return outlet.hasControl(BooleanControl.Type.MUTE, outlet.getMuteControl())
                && outlet.getMuteControl().getValue();
    }

    /**
     * Return the balance Value.
     */
    @Override
    public float getBalance() {
        return outlet.hasControl(FloatControl.Type.BALANCE, outlet.getBalanceControl())
                ? outlet.getBalanceControl().getValue()
                : 0.0F;
    }

    /****
     * Return the total size of this file in bytes.
     *
     * @return encodedAudioLength
     */
    @Override
    public long getTotalBytes() {
        return streamManager.getEncodedAudioLength();
    }

    /**
     * @return BytePosition
     */
    @Override
    public int getPositionByte() {
        final int positionByte = AudioSystem.NOT_SPECIFIED;
        if (audioProperties != null) {
            if (audioProperties.containsKey("mp3.position.byte"))
                return (Integer) audioProperties.get("mp3.position.byte");
            if (audioProperties.containsKey("ogg.position.byte"))
                return (Integer) audioProperties.get("ogg.position.byte");
        }
        return positionByte;
    }

    @Override
    public long getSourceSize() {
        return source != null ? source.getSize() : -1;
    }

    @Override
    public int getBufferedPercentage() {
        long total = getSourceSize();
        if (total <= 0) return -1;
        long read = getEncodedStreamPosition();
        if (read < 0) return 0;
        return (int) (read * 100 / total);
    }

    /**
     * The source data line.
     */
    public AudioOutlet getOutlet() {
        return outlet;
    }

    /**
     * This method will return the status of the player
     *
     * @return The Player Status
     */
    @Override
    public Status getStatus() {
        return stateManager.getStatus();
    }

    /**
     * Set SourceDataLine buffer size. It affects audio latency. (the delay between
     * line.write(data) and real sound). Minimum value should be over 10000 bytes.
     *
     * @param size -1 means maximum buffer size available.
     */
    @Override
    public void setLineBufferSize(final int size) {
        lineManager.setLineBufferSize(size);
    }

    /**
     * Sets Pan value. Line should be opened before calling this method.
     * Linear scale: -1.0 ... +1.0
     */
    @Override
    public void setPan(final double fPan) {
        if (fPan < -1.0 || fPan > 1.0) return;
        stateManager.setCachedPan(fPan);
        if (outlet.hasControl(FloatControl.Type.PAN, outlet.getPanControl())) {
            logger.info(() -> "Pan: " + fPan);
            outlet.getPanControl().setValue((float) fPan);
            eventDispatcher.fireStatus(this,Status.PAN, getEncodedStreamPosition(), null);
        }
    }

    /**
     * Sets Gain value (linear scale 0.0 ... 1.0).
     * Internally converted to logarithmic dB scale.
     */
    @Override
    public void setGain(final double fGain) {
        stateManager.setCachedGain(fGain);
        if (isPausedOrPlaying() && outlet.hasControl(FloatControl.Type.MASTER_GAIN, outlet.getGainControl())) {
            outlet.getGainControl().setValue((float) (20 * Math.log10(fGain)));
        }
    }

    @Override
    public void setLogScaleGain(final double logScaleGain) {
        if (isPausedOrPlaying() && outlet.hasControl(FloatControl.Type.MASTER_GAIN, outlet.getGainControl())) {
            outlet.getGainControl().setValue((float) logScaleGain);
        }
    }

    /**
     * Set the mute of the Line. Note that mute status does not affect gain.
     */
    @Override
    public void setMute(final boolean mute) {
        stateManager.setCachedMute(mute);
        if (outlet.hasControl(BooleanControl.Type.MUTE, outlet.getMuteControl())
                && outlet.getMuteControl().getValue() != mute) {
            outlet.getMuteControl().setValue(mute);
        }
    }

    /**
     * Sets the balance of a stereo signal between two speakers.
     * Valid range: -1.0 (left) to 1.0 (right), 0.0 is centered.
     */
    @Override
    public void setBalance(final float fBalance) {
        stateManager.setCachedBalance(fBalance);
        if (outlet.hasControl(FloatControl.Type.BALANCE, outlet.getBalanceControl())
                && fBalance >= -1.0 && fBalance <= 1.0) {
            outlet.getBalanceControl().setValue(fBalance);
        } else {
            logger.warning(() -> "Balance control not supported or value " + fBalance + " out of range");
        }
    }

    /**
     * Changes specific values from equalizer.
     *
     * @param array the array
     * @param stop  the stop
     */
    @Override
    public void setEqualizer(final float[] array, final int stop) {
        if (!isPausedOrPlaying() || !(streamManager.getAudioInputStream() instanceof PropertiesContainer))
            return;
        // Map<?, ?> map = ((PropertiesContainer) streamManager.getAudioInputStream()).properties()
        final float[] equalizer = (float[]) ((PropertiesContainer) streamManager.getAudioInputStream()).properties().get("mp3.equalizer");
        if (stop >= 0) System.arraycopy(array, 0, equalizer, 0, stop);

    }

    /**
     * Changes a value from equalizer.
     *
     * @param value the value
     * @param key   the key
     */
    @Override
    public void setEqualizerKey(final float value, final int key) {
        if (!isPausedOrPlaying() || !(streamManager.getAudioInputStream() instanceof PropertiesContainer))
            return;
        // Map<?, ?> map = ((PropertiesContainer) streamManager.getAudioInputStream()).properties()
        final float[] equalizer = (float[]) ((PropertiesContainer) streamManager.getAudioInputStream()).properties().get("mp3.equalizer");
        equalizer[key] = value;

    }

    /**
     * @return The Speech Factor of the Audio
     */
    @Override
    public double getSpeedFactor() {
        return this.speedFactor;
    }

    /**
     * Checks if is unknown.
     *
     * @return If Status==STATUS.UNKNOWN.
     */
    @Override
    public boolean isUnknown() {
        return stateManager.getStatus() == Status.NOT_SPECIFIED;
    }

    /**
     * Checks if is playing.
     *
     * @return <b>true</b> if player is playing ,<b>false</b> if not.
     */
    @Override
    public boolean isPlaying() {
        return stateManager.getStatus() == Status.PLAYING;
    }

    /**
     * Checks if is paused.
     *
     * @return <b>true</b> if player is paused ,<b>false</b> if not.
     */
    @Override
    public boolean isPaused() {
        return stateManager.getStatus() == Status.PAUSED;
    }

    /**
     * Checks if is paused or playing.
     *
     * @return <b>true</b> if player is paused/playing,<b>false</b> if not
     */
    @Override
    public boolean isPausedOrPlaying() {
        return isPlaying() || isPaused();
    }

    /**
     * Checks if is stopped.
     *
     * @return <b>true</b> if player is stopped ,<b>false</b> if not
     */
    @Override
    public boolean isStopped() {
        return stateManager.getStatus() == Status.STOPPED;
    }

    /**
     * Checks if is opened.
     *
     * @return <b>true</b> if player is opened ,<b>false</b> if not
     */
    @Override
    public boolean isOpened() {
        return stateManager.getStatus() == Status.OPENED;
    }

    /**
     * Checks if is seeking.
     *
     * @return <b>true</b> if player is seeking ,<b>false</b> if not
     */
    @Override
    public boolean isSeeking() {
        return stateManager.getStatus() == Status.SEEKING;
    }

    Logger getLogger() {
        return logger;
    }

    @Override
    public SourceDataLine getSourceDataLine() {
        return outlet.getSourceDataLine();
    }
}
