package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.Status;

/**
 * Manages player state: status transitions and cached audio control values.
 */
public class StateManager {

    private volatile Status status = Status.NOT_SPECIFIED;

    // Cached audio settings — preserved across source changes
    private double cachedGain = -1;
    private boolean cachedMute = false;
    private double cachedPan = 0;
    private float cachedBalance = 0;

    // ---- Status ----

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status newStatus) {
        this.status = newStatus;
    }

    public boolean isUnknown() {
        return status == Status.NOT_SPECIFIED;
    }

    public boolean isPlaying() {
        return status == Status.PLAYING;
    }

    public boolean isPaused() {
        return status == Status.PAUSED;
    }

    public boolean isPausedOrPlaying() {
        return isPlaying() || isPaused();
    }

    public boolean isStopped() {
        return status == Status.STOPPED;
    }

    public boolean isOpened() {
        return status == Status.OPENED;
    }

    public boolean isSeeking() {
        return status == Status.SEEKING;
    }

    // ---- Cached audio settings ----

    public double getCachedGain() {
        return cachedGain;
    }

    public void setCachedGain(double gain) {
        this.cachedGain = gain;
    }

    public boolean isCachedMute() {
        return cachedMute;
    }

    public void setCachedMute(boolean mute) {
        this.cachedMute = mute;
    }

    public double getCachedPan() {
        return cachedPan;
    }

    public void setCachedPan(double pan) {
        this.cachedPan = pan;
    }

    public float getCachedBalance() {
        return cachedBalance;
    }

    public void setCachedBalance(float balance) {
        this.cachedBalance = balance;
    }

    // ---- Reset ----

    /** Reset state to defaults. */
    public void reset() {
        status = Status.NOT_SPECIFIED;
        cachedGain = -1;
        cachedMute = false;
        cachedPan = 0;
        cachedBalance = 0;
    }
}
