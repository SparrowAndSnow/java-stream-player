package com.goxr3plus.streamplayer.stream;

/**
 * Holds seeking state: byte offset, base millisecond position, and pending seek target.
 */
public class SeekService {

    private volatile long seekOffset = 0;
    private volatile long baseMillisecondPosition = 0;
    private volatile long pendingSeekMilliseconds = -1;

    public long getSeekOffset() {
        return seekOffset;
    }

    public void setSeekOffset(long offset) {
        this.seekOffset = offset;
    }

    public long getBaseMillisecondPosition() {
        return baseMillisecondPosition;
    }

    public void setBaseMillisecondPosition(long ms) {
        this.baseMillisecondPosition = ms;
    }

    public long getPendingSeekMilliseconds() {
        return pendingSeekMilliseconds;
    }

    public void setPendingSeekMilliseconds(long ms) {
        this.pendingSeekMilliseconds = ms;
    }

    public void reset() {
        seekOffset = 0;
        baseMillisecondPosition = 0;
        pendingSeekMilliseconds = -1;
    }
}
