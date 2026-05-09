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

    private static final DurationCalculator CALCULATOR = new TimeToolDurationCalculator();

    private TimeTool() {
    }

    /**
     * Returns time formatted as MM:SS.
     */
    public static String getTimeEditedOnHours(final int seconds) {
        return CALCULATOR.getTimeEditedOnHours(seconds);
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
        return CALCULATOR.getTimeEdited(seconds);
    }

    /**
     * Returns the fractional part of milliseconds as ".d".
     */
    public static String millisecondsToTime(final long ms) {
        return CALCULATOR.millisecondsToTime(ms);
    }

    /**
     * Returns the duration of audio in seconds, delegating to {@link #durationInMilliseconds}.
     */
    public static int durationInSeconds(final String name, final AudioType type) {
        return CALCULATOR.durationInSeconds(name, type);
    }

    /**
     * Returns the duration of audio in milliseconds.
     * Currently only FILE type is supported; returns -1 for other types.
     */
    public static long durationInMilliseconds(final String input, final AudioType audioType) {
        return CALCULATOR.durationInMilliseconds(input, audioType);
    }
}
