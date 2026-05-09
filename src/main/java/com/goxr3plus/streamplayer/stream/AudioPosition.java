package com.goxr3plus.streamplayer.stream;

/**
 * Represents a position in an audio stream, combining encoded byte position
 * and absolute time in milliseconds.
 */
public record AudioPosition(long encodedBytes, long milliseconds) {

    /** Zero position constant. */
    public static final AudioPosition ZERO = new AudioPosition(0, 0);
}
