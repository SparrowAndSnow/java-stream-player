package com.goxr3plus.streamplayer.stream;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.goxr3plus.streamplayer.enums.Status;

/**
 * Runnable task that notifies all registered StreamPlayerListeners of a status change.
 * Submitted to the events executor service for asynchronous execution.
 */
record StreamPlayerEventLauncher(StreamPlayer source, Status playerStatus, int encodedStreamPosition,
                                  Object description, List<StreamPlayerListener> listeners) implements Callable<String> {

    @Override
    public String call() {
        if (listeners != null) {
            var event = new StreamPlayerEvent(source, playerStatus, encodedStreamPosition, description);
            listeners.forEach(listener -> listener.statusUpdated(event));
        }
        source.getLogger().log(Level.INFO, "Stream player Status -> " + playerStatus);
        return "OK";
    }
}
