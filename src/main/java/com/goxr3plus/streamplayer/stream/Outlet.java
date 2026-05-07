package com.goxr3plus.streamplayer.stream;

import javax.sound.sampled.*;
import java.util.logging.Logger;

/**
 * Manages the SourceDataLine (audio output) and its associated controls (gain, pan, mute, balance).
 */
public class Outlet {

    private final Logger logger;
    private FloatControl balanceControl;
    private FloatControl gainControl;
    private BooleanControl muteControl;
    private FloatControl panControl;
    private SourceDataLine sourceDataLine;

    public Outlet(Logger logger) {
        this.logger = logger;
    }

    public FloatControl getBalanceControl() { return balanceControl; }
    public FloatControl getGainControl() { return gainControl; }
    public BooleanControl getMuteControl() { return muteControl; }
    public FloatControl getPanControl() { return panControl; }
    public SourceDataLine getSourceDataLine() { return sourceDataLine; }

    public void setBalanceControl(FloatControl balanceControl) { this.balanceControl = balanceControl; }
    public void setGainControl(FloatControl gainControl) { this.gainControl = gainControl; }
    public void setMuteControl(BooleanControl muteControl) { this.muteControl = muteControl; }
    public void setPanControl(FloatControl panControl) { this.panControl = panControl; }
    public void setSourceDataLine(SourceDataLine sourceDataLine) { this.sourceDataLine = sourceDataLine; }

    /**
     * Check if a control type is supported by the current SourceDataLine.
     */
    public boolean hasControl(final Control.Type control, final Control component) {
        return component != null && sourceDataLine != null && sourceDataLine.isControlSupported(control);
    }

    /**
     * Returns the current gain value (dB scale), or 0.0 if gain control is unavailable.
     */
    public float getGainValue() {
        return hasControl(FloatControl.Type.MASTER_GAIN, gainControl) ? gainControl.getValue() : 0.0F;
    }

    /**
     * Drains, stops, closes and nullifies the SourceDataLine.
     */
    void drainStopAndFreeDataLine() {
        if (sourceDataLine != null) {
            sourceDataLine.drain();
            sourceDataLine.stop();
            sourceDataLine.close();
            sourceDataLine = null;
        }
    }

    /**
     * Flushes, closes and nullifies the SourceDataLine.
     */
    void flushAndFreeDataLine() {
        if (sourceDataLine != null) {
            sourceDataLine.flush();
            sourceDataLine.close();
            sourceDataLine = null;
        }
    }

    /**
     * Flushes and stops the SourceDataLine if it's running.
     */
    void flushAndStop() {
        if (sourceDataLine != null && sourceDataLine.isRunning()) {
            sourceDataLine.flush();
            sourceDataLine.stop();
        }
    }

    /**
     * @return true if the SourceDataLine exists and is not running
     */
    boolean isStartable() {
        return sourceDataLine != null && !sourceDataLine.isRunning();
    }

    /**
     * Starts the SourceDataLine.
     */
    void start() {
        sourceDataLine.start();
    }

    /**
     * Opens the SourceDataLine with the given format and buffer size,
     * and initializes available controls (gain, pan, mute, balance).
     */
    void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (sourceDataLine == null) return;

        logger.info("Opening SourceDataLine...");
        sourceDataLine.open(format, bufferSize);

        if (sourceDataLine.isOpen()) {
            gainControl = sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)
                    ? (FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN)
                    : null;
            panControl = sourceDataLine.isControlSupported(FloatControl.Type.PAN)
                    ? (FloatControl) sourceDataLine.getControl(FloatControl.Type.PAN)
                    : null;
            muteControl = sourceDataLine.isControlSupported(BooleanControl.Type.MUTE)
                    ? (BooleanControl) sourceDataLine.getControl(BooleanControl.Type.MUTE)
                    : null;
            balanceControl = sourceDataLine.isControlSupported(FloatControl.Type.BALANCE)
                    ? (FloatControl) sourceDataLine.getControl(FloatControl.Type.BALANCE)
                    : null;
        }
        logger.info("SourceDataLine opened successfully");
    }

}
