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
 * Default implementation of {@link DurationCalculator} using JAudioTagger for MP3
 * and JavaSound for other formats.
 */
public final class TimeToolDurationCalculator implements DurationCalculator {

    @Override
    public long durationInMilliseconds(String input, AudioType audioType) {
        return audioType == AudioType.FILE ? fileDurationInMillis(new File(input)) : -1;
    }

    @Override
    public int durationInSeconds(String name, AudioType type) {
        long millis = durationInMilliseconds(name, type);
        return (int) ((millis <= 0) ? millis : millis / 1000);
    }

    @Override
    public String getTimeEditedOnHours(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public String getTimeEdited(int seconds) {
        if (seconds < 60) {
            return String.format("%02ds", seconds % 60);
        }
        int minutes = seconds / 60;
        if (minutes / 60 <= 0) {
            return String.format("%02dm:%02d", minutes % 60, seconds % 60);
        }
        return String.format("%02dh:%02dm:%02d", minutes / 60, minutes % 60, seconds % 60);
    }

    @Override
    public String millisecondsToTime(long ms) {
        return String.format(".%d", (int) ((ms % 1000) / 100));
    }

    private long fileDurationInMillis(File file) {
        if (!file.exists() || file.length() == 0) return -1;

        return switch (IOInfo.getFileExtension(file.getName())) {
            case "mp3" -> mp3DurationInMillis(file);
            case "ogg", "wav" -> oggOrWavDurationInMillis(file);
            default -> audioSystemDurationInMillis(file);
        };
    }

    private long mp3DurationInMillis(File file) {
        try {
            var mp3 = new MP3File(file);
            var header = mp3.getMP3AudioHeader();

            int trackLen = header.getTrackLength();
            if (trackLen > 0) return trackLen * 1000L;

            int samplesPerFrame = switch (header.getMpegLayer()) {
                case "Layer 1" -> 384;
                case "Layer 2" -> 576;
                default -> 1152;
            };
            double frameDurationMs = (double) samplesPerFrame / header.getSampleRateAsNumber() * 1000.0;
            long fromFrames = (long) (header.getNumberOfFrames() * frameDurationMs);
            if (fromFrames > 0) return fromFrames;

            return audioSystemDurationInMillis(file);
        } catch (Exception ex) {
            System.err.println("Error reading MP3 duration: " + file.getAbsolutePath());
            return -1;
        }
    }

    private long oggOrWavDurationInMillis(File file) {
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

    private long audioSystemDurationInMillis(File file) {
        try {
            var aff = AudioSystem.getAudioFileFormat(file);
            var durationObj = aff.properties().get("duration");
            if (durationObj instanceof Number durationNum && durationNum.longValue() > 0)
                return durationNum.longValue() / 1000L;
        } catch (Exception ex) {
            System.err.println("Error reading audio duration: " + file.getAbsolutePath());
        }
        return -1;
    }
}
