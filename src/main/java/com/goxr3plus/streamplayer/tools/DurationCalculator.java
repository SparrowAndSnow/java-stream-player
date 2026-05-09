package com.goxr3plus.streamplayer.tools;

import com.goxr3plus.streamplayer.enums.AudioType;

/**
 * Strategy interface for audio duration calculation and time formatting.
 */
public interface DurationCalculator {

    long durationInMilliseconds(String input, AudioType audioType);

    int durationInSeconds(String name, AudioType type);

    String getTimeEditedOnHours(int seconds);

    String getTimeEdited(int seconds);

    String millisecondsToTime(long ms);
}
