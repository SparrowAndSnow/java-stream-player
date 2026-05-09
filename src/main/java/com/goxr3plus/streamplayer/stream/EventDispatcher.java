package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.Status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages listener registration and event notification.
 * Status events are fired asynchronously via an executor service;
 * progress events are fired synchronously (called from the audio thread).
 */
public class EventDispatcher {

    private final Logger logger;
    private final ExecutorService eventsExecutorService;
    private final List<StreamPlayerListener> listeners;
    private final Map<String, Object> emptyMap;

    public EventDispatcher(Logger logger, ExecutorService eventsExecutorService) {
        this.logger = logger;
        this.eventsExecutorService = eventsExecutorService;
        this.listeners = new CopyOnWriteArrayList<>();
        this.emptyMap = new HashMap<>();
    }

    // ---- Listener management ----

    public void addListener(StreamPlayerListener listener) {
        listeners.add(java.util.Objects.requireNonNull(listener,
                "null is not allowed as StreamPlayerListener value."));
    }

    public void removeListener(StreamPlayerListener listener) {
        listeners.remove(listener);
    }

    public List<StreamPlayerListener> getListeners() {
        return listeners;
    }

    // ---- Event firing ----

    /**
     * Fires a status event asynchronously on the event executor service.
     */
    public void fireStatus(StreamPlayer source, Status status, int encodedStreamPosition, Object description) {
        try {
            eventsExecutorService.submit(
                    new StreamPlayerEventLauncher(source, status, encodedStreamPosition, description, listeners));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Problem firing status event", ex);
        }
    }

    /**
     * Fires a progress event synchronously on all listeners.
     * Called from the audio playback loop — must not block.
     */
    public void fireProgress(int nEncodedBytes, long positionInMilliseconds,
                             byte[] pcmData, Map<String, Object> properties) {
        for (var listener : listeners) {
            try {
                listener.progress(nEncodedBytes, positionInMilliseconds, pcmData, properties);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error in listener progress callback", ex);
            }
        }
    }

    public Map<String, Object> getEmptyMap() {
        return emptyMap;
    }

    // ---- Lifecycle ----

    public void shutdown() {
        eventsExecutorService.shutdown();
    }
}
