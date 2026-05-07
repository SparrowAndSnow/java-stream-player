package com.goxr3plus.streamplayer.tools;

import com.goxr3plus.streamplayer.enums.AudioType;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp3.MP3File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

/**
 * Utility methods for audio time/duration calculations and formatting.
 */
public final class TimeTool {

    private TimeTool() {
    }

    /**
     * Returns time formatted as MM:SS.
     */
    public static String getTimeEditedOnHours(final int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Returns time formatted as:
     * <ul>
     *   <li>{@code XXs} if &lt; 60 seconds</li>
     *   <li>{@code XXm:XX} if &lt; 1 hour</li>
     *   <li>{@code XXh:XXm:XX} otherwise</li>
     * </ul>
     */
    public static String getTimeEdited(final int seconds) {
        if (seconds < 60) {
            return String.format("%02ds", seconds % 60);
        }
        int minutes = seconds / 60;
        if (minutes / 60 <= 0) {
            return String.format("%02dm:%02d", minutes % 60, seconds % 60);
        }
        return String.format("%02dh:%02dm:%02d", minutes / 60, minutes % 60, seconds % 60);
    }

    /**
     * Returns the fractional part of milliseconds as ".d".
     */
    public static String millisecondsToTime(final long ms) {
        return String.format(".%d", (int) ((ms % 1000) / 100));
    }

    /**
     * Returns the duration of audio in seconds, delegating to {@link #durationInMilliseconds}.
     */
    public static int durationInSeconds(final String name, final AudioType type) {
        long millis = durationInMilliseconds(name, type);
        return (int) ((millis <= 0) ? millis : millis / 1000);
    }

    /**
     * Returns the duration of audio in milliseconds.
     * Currently only FILE type is supported; returns -1 for other types.
     */
    public static long durationInMilliseconds(final String input, final AudioType audioType) {
        return audioType == AudioType.FILE ? fileDurationInMillis(new File(input)) : -1;
    }

    private static long fileDurationInMillis(final File file) {
        if (!file.exists() || file.length() == 0) return -1;

        return switch (IOInfo.getFileExtension(file.getName())) {
            case "mp3" -> mp3DurationInMillis(file);
            case "ogg", "wav" -> oggOrWavDurationInMillis(file);
            default -> audioSystemDurationInMillis(file);
        };
    }

    private static long mp3DurationInMillis(final File file) {
        try {
            var mp3 = new MP3File(file);
            var header = mp3.getMP3AudioHeader();

            // Method 1: track length in seconds
            int trackLen = header.getTrackLength();
            if (trackLen > 0) return trackLen * 1000L;

            // Method 2: calculate from frames
            int samplesPerFrame = switch (header.getMpegLayer()) {
                case "Layer 1" -> 384;
                case "Layer 2" -> 576;
                default -> 1152; // Layer 3 and others
            };
            double frameDurationMs = (double) samplesPerFrame / header.getSampleRateAsNumber() * 1000.0;
            long fromFrames = (long) (header.getNumberOfFrames() * frameDurationMs);
            if (fromFrames > 0) return fromFrames;

            // Method 3: fallback to AudioSystem
            return audioSystemDurationInMillis(file);
        } catch (Exception ex) {
            System.err.println("Error reading MP3 duration: " + file.getAbsolutePath());
            return -1;
        }
    }

    private static long oggOrWavDurationInMillis(final File file) {
        try (var ais = AudioSystem.getAudioInputStream(file)) {
            var format = ais.getFormat();
            long frames = ais.getFrameLength();
            if (frames > 0 && format.getFrameRate() > 0) {
                return (long) (((double) frames / format.getFrameRate()) * 1000.0);
            }
            long bytesPerSecond = (long) (format.getFrameSize() * format.getFrameRate());
            if (bytesPerSecond > 0) {
                return (file.length() * 1000L) / bytesPerSecond;
            }
        } catch (IOException | UnsupportedAudioFileException ex) {
            System.err.println("Error reading OGG/WAV duration: " + file.getAbsolutePath());
        }
        return -1;
    }

    private static long audioSystemDurationInMillis(final File file) {
        try {
            var aff = AudioSystem.getAudioFileFormat(file);
            var micros = (Long) aff.properties().get("duration");
            if (micros != null && micros > 0) return micros / 1000L;
        } catch (Exception ex) {
            System.err.println("Error reading audio duration: " + file.getAbsolutePath());
        }
        return -1;
    }
}
