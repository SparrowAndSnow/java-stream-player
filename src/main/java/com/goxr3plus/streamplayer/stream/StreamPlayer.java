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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
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
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.tritonus.share.sampled.TAudioFormat;
import org.tritonus.share.sampled.file.TAudioFileFormat;

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

    // -------------------AUDIO----------------,-----

    private volatile Status status = Status.NOT_SPECIFIED;

    /**
     * The data source
     */
    private DataSource source;

    // Cached audio settings — preserved across source changes
    private double cachedGain = -1;      // -1 means "not yet set"
    private boolean cachedMute = false;
    private double cachedPan = 0;
    private float cachedBalance = 0;

    /**
     * The audio input stream.
     */
    private volatile AudioInputStream audioInputStream;

    /**
     * The encoded audio input stream.
     */
    private AudioInputStream encodedAudioInputStream;

    /**
     * The audio file format.
     */
    private AudioFileFormat audioFileFormat;

    // -------------------LOCKS---------------------

    /**
     * It is used for synchronization in place of audioInputStream
     */
    private final Object audioLock = new Object();

    // -------------------VARIABLES---------------------
    /**
     * Name of the mixer to use
     */
    private String mixerName;

    /**
     * The current mixer
     */
    private Mixer mixer = null;

    /**
     * The current line buffer size.
     */
    private int currentLineBufferSize = -1;

    /**
     * The line buffer size.
     */
    private int lineBufferSize = -1;

    /**
     * The encoded audio length.
     */
    private int encodedAudioLength = -1;

    /**
     * Seek offset to track position after seeking
     */
    private volatile long seekOffset = 0;

    /**
     * Speed Factor of the Audio
     */
    private double speedFactor = 1;

    /**
     * The Constant EXTERNAL_BUFFER_SIZE.
     */
    private static final int EXTERNAL_BUFFER_SIZE = 4096;

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
     * This executor service is used in order the playerState events to be executed
     * in an order
     */
    private final ExecutorService eventsExecutorService;

    /**
     * Thread-safe list of listeners to be notified about Stream PlayerEvents
     */
    private final List<StreamPlayerListener> listeners;

    /**
     * The empty map.
     */
    private final Map<String, Object> emptyMap = new HashMap<>();

    // Properties when the File/URL/InputStream is opened.
    Map<String, Object> audioProperties;

    /**
     * Responsible for the output SourceDataLine and the controls that depend on it.
     */
    private final Outlet outlet;

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
        this.eventsExecutorService = eventsExecutorService;
        listeners = new CopyOnWriteArrayList<>();
        outlet = new Outlet(logger);
        reset();
    }

    /**
     * Freeing the resources.
     */
    @Override
    public void reset() {

        // Close the stream
        synchronized (audioLock) {
            closeStream();
        }

        outlet.flushAndFreeDataLine();

        // AudioFile
        audioInputStream = null;
        audioFileFormat = null;
        encodedAudioInputStream = null;
        encodedAudioLength = -1;
        seekOffset = 0;

        // Controls
        outlet.setGainControl(null);
        outlet.setPanControl(null);
        outlet.setBalanceControl(null);

        // Notify the Status
        status = Status.NOT_SPECIFIED;
        generateEvent(Status.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, null);

    }

    /**
     * Notify listeners about a StreamPlayerEvent.
     *
     * @param status                event code
     * @param encodedStreamPosition position in the encoded stream
     * @param description           optional description
     */
    private void generateEvent(final Status status, final int encodedStreamPosition, final Object description) {
        try {
            eventsExecutorService.submit(
                    new StreamPlayerEventLauncher(this, status, encodedStreamPosition, description, listeners));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Problem in generateEvent()", ex);
        }
    }

    /**
     * Add a listener to be notified.
     *
     * @param streamPlayerListener the listener
     */
    @Override
    public void addStreamPlayerListener(final StreamPlayerListener streamPlayerListener) {

        Objects.requireNonNull(streamPlayerListener,
                "null is not allowed as StreamPlayerListener value.");

        listeners.add(streamPlayerListener);
    }

    /**
     * Remove registered listener.
     *
     * @param streamPlayerListener the listener
     */
    @Override
    public void removeStreamPlayerListener(final StreamPlayerListener streamPlayerListener) {
        if (listeners != null)
            listeners.remove(streamPlayerListener);

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
                    closeStream();
                }
                outlet.flushAndFreeDataLine();
                audioInputStream = null;
                audioFileFormat = null;
                encodedAudioInputStream = null;
                // Keep encodedAudioLength and seekOffset
            }

            status = Status.OPENING;
            generateEvent(Status.OPENING, getEncodedStreamPosition(), source);

            if (source.isFile()) {
                audioFileFormat = source.getAudioFileFormat();
                audioInputStream = source.getAudioInputStream();
            } else {
                audioInputStream = source.getAudioInputStream();
            }

            createLine();
            determineProperties();

            status = Status.OPENED;
            generateEvent(Status.OPENED, getEncodedStreamPosition(), null);

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
        logger.info("Entered determineProperties()!");

        if (audioFileFormat == null) return;

        // Initialize properties from Tritonus SPI if available, or empty map
        audioProperties = audioFileFormat instanceof TAudioFileFormat taff
                ? new HashMap<>(taff.properties())
                : new HashMap<>();

        // Add JavaSound AudioFileFormat properties
        var aff = audioFileFormat;
        putIfPositive(audioProperties, "audio.length.bytes", aff.getByteLength());
        putIfPositive(audioProperties, "audio.length.frames", aff.getFrameLength());
        if (aff.getType() != null) {
            audioProperties.put("audio.type", aff.getType());
        }

        // AudioFormat properties
        final var af = aff.getFormat();
        putIfPositive(audioProperties, "audio.framerate.fps", (long) af.getFrameRate());
        putIfPositive(audioProperties, "audio.framesize.bytes", (long) af.getFrameSize());
        putIfPositive(audioProperties, "audio.samplerate.hz", (long) af.getSampleRate());
        putIfPositive(audioProperties, "audio.samplesize.bits", (long) af.getSampleSizeInBits());
        putIfPositive(audioProperties, "audio.channels", (long) af.getChannels());

        // Tritonus SPI audio format properties
        if (af instanceof TAudioFormat taf) {
            audioProperties.putAll(taf.properties());
        }

        audioProperties.put("basicplayer.sourcedataline", outlet.getSourceDataLine());

        // Notify listeners
        listeners.forEach(listener -> listener.opened(source.getSource(), audioProperties));

        logger.info("Exited determineProperties()!");
    }

    private static void putIfPositive(Map<String, Object> map, String key, long value) {
        if (value > 0) map.put(key, value);
    }

    /**
     * Initializes the audio output line, creating it if needed, and opening it with the correct format.
     *
     * @throws LineUnavailableException the line unavailable exception
     * @throws StreamPlayerException    if the line cannot be created
     */
    private void initLine() throws LineUnavailableException, StreamPlayerException {
        logger.info("Initializing the line...");

        if (outlet.getSourceDataLine() == null) {
            createLine();
        }

        if (!outlet.getSourceDataLine().isOpen()) {
            currentLineBufferSize = lineBufferSize >= 0 ? lineBufferSize : outlet.getSourceDataLine().getBufferSize();
            openLine(audioInputStream.getFormat(), currentLineBufferSize);
        } else {
            var currentFormat = audioInputStream != null ? audioInputStream.getFormat() : null;
            if (!outlet.getSourceDataLine().getFormat().equals(currentFormat)) {
                outlet.getSourceDataLine().close();
                currentLineBufferSize = lineBufferSize >= 0 ? lineBufferSize : outlet.getSourceDataLine().getBufferSize();
                openLine(audioInputStream.getFormat(), currentLineBufferSize);
            }
        }
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
        logger.info("Entered createLine()!");

        if (outlet.getSourceDataLine() != null) {
            logger.warning("Source DataLine is not null!");
            return;
        }

        final var sourceFormat = audioInputStream.getFormat();
        logger.info(() -> "Source format: " + sourceFormat);

        // Calculate sample size in bits — convert non-PCM/8-bit to 16-bit
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
        encodedAudioInputStream = audioInputStream;
        try {
            encodedAudioLength = encodedAudioInputStream.available();
        } catch (final IOException e) {
            logger.warning(() -> "Cannot get encodedAudioInputStream.available(): " + e);
        }

        // Create decoded PCM stream
        audioInputStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);
        final var lineInfo = new DataLine.Info(SourceDataLine.class, audioInputStream.getFormat(),
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
     * Open the line.
     *
     * @param audioFormat
     * @param currentLineBufferSize
     * @throws LineUnavailableException the line unavailable exception
     */
    private void openLine(AudioFormat audioFormat, int currentLineBufferSize) throws LineUnavailableException {
        outlet.open(audioFormat, currentLineBufferSize);
    }

    /**
     * Starts the play back.
     *
     * @throws StreamPlayerException the stream player exception
     */
    @Override
    public void play() throws StreamPlayerException {
        // If paused, resume instead
        if (status == Status.PAUSED) {
            resume();
            return;
        }

        if (status == Status.STOPPED)
            initAudioInputStream();
        if (status != Status.OPENED)
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
            status = Status.PLAYING;

            // Restore cached audio settings to the new line
            restoreAudioSettings();

            generateEvent(Status.PLAYING, getEncodedStreamPosition(), null);
        }
    }

    /**
     * Restores cached audio control settings (gain, mute, pan, balance)
     * to the current SourceDataLine. Called after opening a new source
     * so that volume/mute/pan/balance survive across source changes.
     */
    private void restoreAudioSettings() {
        // Restore gain (volume) — bypass the isPausedOrPlaying() check
        if (cachedGain >= 0 && outlet.hasControl(FloatControl.Type.MASTER_GAIN, outlet.getGainControl())) {
            outlet.getGainControl().setValue((float) (20 * Math.log10(cachedGain)));
        }
        // Restore mute
        if (outlet.hasControl(BooleanControl.Type.MUTE, outlet.getMuteControl())) {
            outlet.getMuteControl().setValue(cachedMute);
        }
        // Restore pan
        if (cachedPan >= -1.0 && cachedPan <= 1.0
                && outlet.hasControl(FloatControl.Type.PAN, outlet.getPanControl())) {
            outlet.getPanControl().setValue((float) cachedPan);
        }
        // Restore balance
        if (cachedBalance >= -1.0 && cachedBalance <= 1.0
                && outlet.hasControl(FloatControl.Type.BALANCE, outlet.getBalanceControl())) {
            outlet.getBalanceControl().setValue(cachedBalance);
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
        if (outlet.getSourceDataLine() == null || status != Status.PLAYING)
            return false;
        status = Status.PAUSED;
        logger.info("pausePlayback() completed");
        generateEvent(Status.PAUSED, getEncodedStreamPosition(), null);
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
        if (status == Status.STOPPED)
            return;
        if (isPlaying())
            pause();
        status = Status.STOPPED;
        // generateEvent(Status.STOPPED, getEncodedStreamPosition(), null);
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
        if (outlet.getSourceDataLine() == null || status != Status.PAUSED)
            return false;
        outlet.start();
        status = Status.PLAYING;
        generateEvent(Status.RESUMED, getEncodedStreamPosition(), null);
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
        if ((bytesLength <= 0) || (bytes >= bytesLength)) {
            generateEvent(Status.EOM, getEncodedStreamPosition(), null);
            return totalSkipped;
        }

        logger.info(() -> "Bytes to skip : " + bytes);
        final Status previousStatus = status;
        status = Status.SEEKING;

        try {
            synchronized (audioLock) {
                generateEvent(Status.SEEKING, AudioSystem.NOT_SPECIFIED, null);
                
                // Stop current playback
                if (outlet.getSourceDataLine() != null && outlet.getSourceDataLine().isRunning()) {
                    outlet.getSourceDataLine().stop();
                    outlet.getSourceDataLine().flush();
                }
                
                // For file sources, we can reinitialize and skip from beginning
                // For streams/URLs, we need to skip from current position
                if (source.isFile()) {
                    // Reinitialize audio stream for file-based seeking (preserve encodedAudioLength)
                    initAudioInputStream(false);
                    // Reset seekOffset since we're starting from the beginning of the reinitialized stream
                    seekOffset = 0;
                }
                
                if (audioInputStream != null) {

                    long skipped;
                    // Loop until bytes are really skipped.
                    while (totalSkipped < bytes) {
                        skipped = audioInputStream.skip(bytes - totalSkipped);
                        if (skipped == 0)
                            break;
                        totalSkipped += skipped;
                        logger.info("Skipped : " + totalSkipped + "/" + bytes);
                        if (totalSkipped == -1)
                            throw new StreamPlayerException(
                                    PlayerException.SKIP_NOT_SUPPORTED);

                        logger.info("Skipping:" + totalSkipped);
                    }
                }
            }
            
            // Set seek offset to track the absolute position
            // For file sources, this is the bytes we skipped from the beginning
            seekOffset = totalSkipped;
            
            generateEvent(Status.SEEKED, getEncodedStreamPosition(), null);
            status = Status.OPENED;
            
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
    //todo not finished needs more validations
    public long seekSeconds(int seconds) throws StreamPlayerException {
        int durationInSeconds = this.getDurationInSeconds();

        //Validate
        validateSeconds(seconds, durationInSeconds);

        //Calculate Bytes
        long totalBytes = getTotalBytes();
        double percentage = (seconds * 100) / durationInSeconds;
        long bytes = (long) (totalBytes * (percentage / 100));

        return seekBytes(this.getEncodedStreamPosition() + bytes);
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

        //Calculate Bytes
        long totalBytes = getTotalBytes();
        double percentage = (seconds * 100) / durationInSeconds;
        long bytes = (long) (totalBytes * (percentage / 100));

        return seekBytes(bytes);
    }


    private void validateSeconds(int seconds, int durationInSeconds) {
        if (seconds < 0) {
            throw new UnsupportedOperationException("Trying to skip negative seconds ");
        } else if (seconds >= durationInSeconds) {
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
        int nBytesRead = 0;
        final int audioDataLength = EXTERNAL_BUFFER_SIZE;
        final ByteBuffer audioDataBuffer = ByteBuffer.allocate(audioDataLength);
        audioDataBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Lock stream while playing.
        synchronized (audioLock) {
            // Main play/pause loop.
            while ((nBytesRead != -1) && status != Status.STOPPED && status != Status.NOT_SPECIFIED
                    && status != Status.SEEKING) {

                try {
                    // Playing?
                    if (status == Status.PLAYING) {

                        // System.out.println("Inside Stream Player Run method")
                        int toRead = audioDataLength;
                        int totalRead = 0;

                        // Reads up a specified maximum number of bytes from audio stream
                        // wtf i have written here omg //to fix! cause it is complicated
                        for (; toRead > 0 && (nBytesRead = audioInputStream.read(audioDataBuffer.array(), totalRead,
                                toRead)) != -1; toRead -= nBytesRead, totalRead += nBytesRead)

                            // Check for under run
                            if (outlet.getSourceDataLine().available() >= outlet.getSourceDataLine().getBufferSize())
                                logger.info(() -> "Underrun> Available=" + outlet.getSourceDataLine().available()
                                        + " , SourceDataLineBuffer=" + outlet.getSourceDataLine().getBufferSize());

                        // Check if anything has been read
                        if (totalRead > 0) {
                            var buffer = audioDataBuffer.array();
                            if (totalRead < buffer.length) {
                                buffer = new byte[totalRead];
                                System.arraycopy(audioDataBuffer.array(), 0, buffer, 0, totalRead);
                            }

                            // Writes audio data to the mixer via this source data line
                            outlet.getSourceDataLine().write(buffer, 0, totalRead);

                            // Compute position in bytes in encoded stream.
                            final int nEncodedBytes = getEncodedStreamPosition();
                            final long microsecondPosition = outlet.getSourceDataLine().getMicrosecondPosition();
                            final var properties = audioInputStream instanceof PropertiesContainer pc
                                ? pc.properties()
                                : emptyMap;

                            // Notify all registered Listeners
                            for (var listener : listeners) {
                                try {
                                    listener.progress(nEncodedBytes, microsecondPosition, buffer, properties);
                                } catch (Exception ex) {
                                    logger.log(Level.WARNING, "Error in listener progress callback", ex);
                                }
                            }

                        }

                    } else if (status == Status.PAUSED) {
                        // Flush and stop the source data line
                        outlet.flushAndStop();
                        waitWhilePaused();

                        // [FIX] Race condition: resume() may have been called during
                        // flushAndStop(), restarting the line. But flushAndStop() could
                        // have run AFTER resume(), stopping the line again.
                        // Ensure the line is actually running before continuing playback.
                        if (status == Status.PLAYING && outlet.isStartable()) {
                            outlet.start();
                        }
                    }
                } catch (final IOException ex) {
                    logger.log(Level.WARNING, "\"Decoder Exception: \" ", ex);
                    status = Status.STOPPED;
                    generateEvent(Status.STOPPED, getEncodedStreamPosition(), null);
                }
            }
            // Free audio resources.
            outlet.drainStopAndFreeDataLine();

            // Close stream.
            closeStream();

            // Notification of "End Of Media"
            if (nBytesRead == -1)
                generateEvent(Status.EOM, AudioSystem.NOT_SPECIFIED, null);

        }
        // Generate Event
        status = Status.STOPPED;
        generateEvent(Status.STOPPED, AudioSystem.NOT_SPECIFIED, null);

        // Log
        logger.info("Decoding thread completed");

        return null;
    }

    private void waitWhilePaused() {
        try {
            while (status == Status.PAUSED) {
                Thread.sleep(50);
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warning(() -> "Thread interrupted while paused.\n" + ex);
        }
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
        if (encodedAudioInputStream != null) {
            try {
                // Calculate position based on available bytes
                int currentPosition = encodedAudioLength - encodedAudioInputStream.available();
                // Add seek offset to get actual position
                position = (int) (seekOffset + currentPosition);
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
        try {
            if (audioInputStream != null) {
                audioInputStream.close();
                logger.info("Stream closed");
            }
        } catch (final IOException ex) {
            logger.warning("Cannot close stream\n" + ex);
        }
    }

    /**
     * Return SourceDataLine buffer size.
     *
     * @return -1 maximum buffer size.
     */
    @Override
    public int getLineBufferSize() {
        return lineBufferSize;
    }

    /**
     * Return SourceDataLine current buffer size.
     *
     * @return The current line buffer size
     */
    @Override
    public int getLineCurrentBufferSize() {
        return currentLineBufferSize;
    }

    /**
     * Returns all available mixers that support SourceDataLine.
     *
     * @return A List of available mixer names
     */
    @Override
    public List<String> getMixers() {
        final var lineInfo = new Line.Info(SourceDataLine.class);
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(info -> AudioSystem.getMixer(info).isLineSupported(lineInfo))
                .map(Mixer.Info::getName)
                .toList();
    }

    /**
     * Returns the mixer with the given name, or null if not found.
     *
     * @param name the mixer name
     * @return The Mixer with that name, or null
     */
    private Mixer getMixer(final String name) {
        if (name == null) return null;
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(info -> info.getName().equals(name))
                .findFirst()
                .map(AudioSystem::getMixer)
                .orElse(null);
    }

    /**
     * Set the name of the mixer to use. This should be called before opening a Line.
     *
     * @param mixerName the name
     */
    public void setMixerName(String mixerName) {
        this.mixerName = mixerName;
    }

    /**
     * Returns the name of the mixer
     *
     * @return the name of the mixer
     */
    public String getMixerName() {
        return mixerName;
    }

    /**
     * Returns the mixer that is currently being used, if there is no line created this will return null
     *
     * @return The Mixer being used
     */
    public Mixer getCurrentMixer() {
        return mixer;
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
        return encodedAudioLength;
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

    /**
     * The source data line.
     */
    public Outlet getOutlet() {
        return outlet;
    }

    /**
     * This method will return the status of the player
     *
     * @return The Player Status
     */
    @Override
    public Status getStatus() {
        return status;
    }

    /**
     * Set SourceDataLine buffer size. It affects audio latency. (the delay between
     * line.write(data) and real sound). Minimum value should be over 10000 bytes.
     *
     * @param size -1 means maximum buffer size available.
     */
    @Override
    public void setLineBufferSize(final int size) {
        lineBufferSize = size;
    }

    /**
     * Sets Pan value. Line should be opened before calling this method.
     * Linear scale: -1.0 ... +1.0
     */
    @Override
    public void setPan(final double fPan) {
        if (fPan < -1.0 || fPan > 1.0) return;
        cachedPan = fPan;
        if (outlet.hasControl(FloatControl.Type.PAN, outlet.getPanControl())) {
            logger.info(() -> "Pan: " + fPan);
            outlet.getPanControl().setValue((float) fPan);
            generateEvent(Status.PAN, getEncodedStreamPosition(), null);
        }
    }

    /**
     * Sets Gain value (linear scale 0.0 ... 1.0).
     * Internally converted to logarithmic dB scale.
     */
    @Override
    public void setGain(final double fGain) {
        cachedGain = fGain;
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
        cachedMute = mute;
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
        cachedBalance = fBalance;
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
        if (!isPausedOrPlaying() || !(audioInputStream instanceof PropertiesContainer))
            return;
        // Map<?, ?> map = ((PropertiesContainer) audioInputStream).properties()
        final float[] equalizer = (float[]) ((PropertiesContainer) audioInputStream).properties().get("mp3.equalizer");
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
        if (!isPausedOrPlaying() || !(audioInputStream instanceof PropertiesContainer))
            return;
        // Map<?, ?> map = ((PropertiesContainer) audioInputStream).properties()
        final float[] equalizer = (float[]) ((PropertiesContainer) audioInputStream).properties().get("mp3.equalizer");
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
        return status == Status.NOT_SPECIFIED;
    }

    /**
     * Checks if is playing.
     *
     * @return <b>true</b> if player is playing ,<b>false</b> if not.
     */
    @Override
    public boolean isPlaying() {
        return status == Status.PLAYING;
    }

    /**
     * Checks if is paused.
     *
     * @return <b>true</b> if player is paused ,<b>false</b> if not.
     */
    @Override
    public boolean isPaused() {
        return status == Status.PAUSED;
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
        return status == Status.STOPPED;
    }

    /**
     * Checks if is opened.
     *
     * @return <b>true</b> if player is opened ,<b>false</b> if not
     */
    @Override
    public boolean isOpened() {
        return status == Status.OPENED;
    }

    /**
     * Checks if is seeking.
     *
     * @return <b>true</b> if player is seeking ,<b>false</b> if not
     */
    @Override
    public boolean isSeeking() {
        return status == Status.SEEKING;
    }

    Logger getLogger() {
        return logger;
    }

    @Override
    public SourceDataLine getSourceDataLine() {
        return outlet.getSourceDataLine();
    }
}
