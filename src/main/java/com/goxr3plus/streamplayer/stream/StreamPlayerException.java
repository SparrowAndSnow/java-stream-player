package com.goxr3plus.streamplayer.stream;

/**
 * Exception thrown by StreamPlayer for player-specific errors.
 */
public class StreamPlayerException extends Exception {

    public enum PlayerException {
        GAIN_CONTROL_NOT_SUPPORTED,
        PAN_CONTROL_NOT_SUPPORTED,
        MUTE_CONTROL_NOT_SUPPORTED,
        BALANCE_CONTROL_NOT_SUPPORTED,
        WAIT_ERROR,
        CAN_NOT_INIT_LINE,
        LINE_NOT_SUPPORTED,
        SKIP_NOT_SUPPORTED,
    }

    private final Throwable cause;

    public StreamPlayerException(PlayerException error) {
        super(error.toString());
        this.cause = null;
    }

    public StreamPlayerException(Throwable cause) {
        this.cause = cause;
    }

    public StreamPlayerException(PlayerException error, Throwable cause) {
        super(error.toString());
        this.cause = cause;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String getMessage() {
        var msg = super.getMessage();
        return msg != null ? msg : (cause != null ? cause.toString() : null);
    }
}
