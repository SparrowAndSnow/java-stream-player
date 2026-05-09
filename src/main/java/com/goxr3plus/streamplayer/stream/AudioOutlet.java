package com.goxr3plus.streamplayer.stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Control;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Abstraction over the audio output line (SourceDataLine) and its controls.
 */
public interface AudioOutlet {

    SourceDataLine getSourceDataLine();

    void setSourceDataLine(SourceDataLine sourceDataLine);

    FloatControl getGainControl();

    void setGainControl(FloatControl gainControl);

    FloatControl getPanControl();

    void setPanControl(FloatControl panControl);

    BooleanControl getMuteControl();

    void setMuteControl(BooleanControl muteControl);

    FloatControl getBalanceControl();

    void setBalanceControl(FloatControl balanceControl);

    boolean hasControl(Control.Type type, Control component);

    float getGainValue();

    void open(AudioFormat format, int bufferSize) throws LineUnavailableException;

    void start();

    boolean isStartable();

    void flushAndFreeDataLine();

    void drainStopAndFreeDataLine();

    void flushAndStop();
}
