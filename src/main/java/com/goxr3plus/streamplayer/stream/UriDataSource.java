package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.AudioType;
import com.goxr3plus.streamplayer.tools.TimeTool;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;

public final class UriDataSource implements DataSource {

    private final URI source;
    private Long cachedDurationMillis = null;
    private AudioFileFormat cachedAudioFileFormat = null;

    UriDataSource(URI source) {
        this.source = source;
    }

    @Override
    public AudioFileFormat getAudioFileFormat() throws UnsupportedAudioFileException, IOException {
        if (cachedAudioFileFormat != null) {
            return cachedAudioFileFormat;
        }

        AudioFileFormat format;
        if (source.getScheme() != null && source.getScheme().startsWith("file")) {
            format = AudioSystem.getAudioFileFormat(new File(source));
        } else {
            format = AudioSystem.getAudioFileFormat(source.toURL());
        }

        cachedAudioFileFormat = format;
        return format;
    }

    @Override
    public AudioInputStream getAudioInputStream() throws UnsupportedAudioFileException, IOException {
        if (source.getScheme() != null && source.getScheme().startsWith("file")) {
            return AudioSystem.getAudioInputStream(new File(source));
        } else {
            return AudioSystem.getAudioInputStream(source.toURL());
        }
    }

    @Override
    public int getDurationInSeconds() {
        long millis = getDurationInMilliseconds();
        return millis == -1 ? -1 : (int) (millis / 1000);
    }
    
    @Override
    public long getDurationInMilliseconds() {
        if (cachedDurationMillis != null) {
            return cachedDurationMillis;
        }
        
        try {
            if (source.getScheme() != null && source.getScheme().startsWith("file")) {
                // Use TimeTool for local files (same as FileDataSource), which supports
                // JAudioTagger for MP3 and AudioSystem for other formats
                cachedDurationMillis = TimeTool.durationInMilliseconds(
                        new File(source).getAbsolutePath(), AudioType.FILE);
                if (cachedDurationMillis > 0) {
                    return cachedDurationMillis;
                }
                // Fallback to AudioFileFormat-based calculation
                return getDurationFromLocalFile();
            } else {
                return getDurationFromNetworkResource();
            }
        } catch (Exception e) {
            System.err.println("Failed to get duration: " + e.getMessage());
        }
        
        return -1;
    }
    
    private long getDurationFromLocalFile() throws IOException, UnsupportedAudioFileException {
        AudioFileFormat format = getAudioFileFormat();
        
        // Safe type check for the "duration" property (can be Long, Integer, etc.)
        Object durationObj = format.properties().get("duration");
        if (durationObj instanceof Number durationNum && durationNum.longValue() > 0) {
            cachedDurationMillis = durationNum.longValue() / 1000L;
            return cachedDurationMillis;
        }
        
        AudioFormat audioFormat = format.getFormat();
        float frameRate = audioFormat.getFrameRate();
        long frameLength = format.getFrameLength();
        
        if (frameRate > 0 && frameLength > 0) {
            cachedDurationMillis = (long) ((frameLength / frameRate) * 1000);
            return cachedDurationMillis;
        }
        
        return -1;
    }
    
    private long getDurationFromNetworkResource() {
        try {
            URL url = source.toURL();
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            long contentLength = connection.getContentLengthLong();
            
            // Try to get AudioFileFormat (cached if already retrieved)
            try {
                AudioFileFormat format = getAudioFileFormat();
                AudioFormat audioFormat = format.getFormat();
                
                // 1) Try "duration" property from audio file metadata (most accurate)
                // The value is in microseconds; safely handle any numeric type
                Object durationObj = format.properties().get("duration");
                if (durationObj instanceof Number durationNum && durationNum.longValue() > 0) {
                    cachedDurationMillis = durationNum.longValue() / 1000L;
                    return cachedDurationMillis;
                }
                
                // 2) Try frame length / frame rate calculation
                float frameRate = audioFormat.getFrameRate();
                long frameLength = format.getFrameLength();
                if (frameRate > 0 && frameLength > 0) {
                    cachedDurationMillis = (long) ((frameLength / frameRate) * 1000);
                    return cachedDurationMillis;
                }
                
                // 3) Try content-length + bitrate from AudioFileFormat properties
                if (contentLength > 0) {
                    Long bitrate = extractBitrateFromProperties(format.properties());
                    if (bitrate != null && bitrate > 0) {
                        cachedDurationMillis = (contentLength * 8L * 1000L) / bitrate;
                        return cachedDurationMillis;
                    }
                }
                
                // 4) Try AudioInputStream frame length (works for some URL connections)
                try (var ais = AudioSystem.getAudioInputStream(url)) {
                    long streamFrameLength = ais.getFrameLength();
                    if (streamFrameLength > 0 && audioFormat.getFrameRate() > 0) {
                        cachedDurationMillis = (long) ((streamFrameLength / audioFormat.getFrameRate()) * 1000);
                        return cachedDurationMillis;
                    }
                } catch (Exception ignored) {
                    // AudioInputStream from URL not available
                }
                
            } catch (Exception e) {
                // AudioFileFormat unavailable
            }
            
            // 5) Last resort: URL-based format detection + content-length estimate
            // Only use this when we have a content-length and can identify the format
            if (contentLength > 0) {
                Long urlBitrate = estimateBitrateFromUrl(url);
                if (urlBitrate != null && urlBitrate > 0) {
                    cachedDurationMillis = (contentLength * 8L * 1000L) / urlBitrate;
                    return cachedDurationMillis;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Failed to get duration from network: " + e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * Extract bitrate (in bps) from AudioFileFormat properties.
     * Returns null if no bitrate property is found.
     */
    private Long extractBitrateFromProperties(java.util.Map<?, ?> properties) {
        if (properties == null) return null;
        
        // Standard bitrate property keys used by various SPI implementations
        String[] bitrateKeys = {"bitrate", "audio.bitrate", "mp3.bitrate",
                "mp3.bitrate.nsb", "mp3.bitrate.nsynch", "ogg.bitrate"};
        for (String key : bitrateKeys) {
            Object value = properties.get(key);
            if (value instanceof Number number) {
                long bps = number.longValue();
                if (bps > 0) {
                    // SPI often stores bitrate in kbps (e.g. 128 for 128kbps)
                    return bps < 10000 ? bps * 1000 : bps;
                }
            }
            // Some SPI implementations store bitrate as string (e.g. "128000")
            if (value instanceof String str && !str.isBlank()) {
                try {
                    long bps = Long.parseLong(str.trim());
                    if (bps > 0) return bps < 10000 ? bps * 1000 : bps;
                } catch (NumberFormatException ignored) {
                    // not a numeric string
                }
            }
        }
        return null;
    }
    
    /**
     * Estimate bitrate from the URL and content-type.
     * Checks for explicit bitrate patterns in the URL (e.g. "128k", "bitrate=192")
     * and falls back to format-specific defaults.
     */
    private Long estimateBitrateFromUrl(URL url) {
        String urlString = url.toString().toLowerCase();
        
        // Detect format from URL path
        String format = detectAudioFormat(urlString);
        
        // If not detected from path, try content-type
        if (format == null) {
            try {
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                format = detectFormatFromContentType(conn.getContentType());
            } catch (Exception ignored) {
                // content-type detection failed
            }
        }
        
        if (format == null) return null;
        
        // Check for explicit bitrate patterns in URL: _128k, -192kbps, bitrate=320, br=256, 128kbps
        Long explicitBitrate = extractExplicitBitrateFromUrl(urlString);
        if (explicitBitrate != null) return explicitBitrate;
        
        // Return format-specific default bitrate
        return switch (format) {
            case "mp3" -> 128000L;
            case "ogg", "opus" -> 128000L;
            case "wav" -> 1411200L;
            case "flac" -> 800000L;
            case "aac", "m4a" -> 192000L;
            case "wma" -> 128000L;
            default -> null;
        };
    }
    
    /**
     * Detect audio format from URL path.
     */
    private String detectAudioFormat(String url) {
        if (url.contains(".mp3") || url.contains("mp3-preview")
                || url.contains("mp3/") || url.contains("mp3?")) return "mp3";
        if (url.contains(".ogg")) return "ogg";
        if (url.contains(".opus")) return "opus";
        if (url.contains(".wav")) return "wav";
        if (url.contains(".flac")) return "flac";
        if (url.contains(".aac")) return "aac";
        if (url.contains(".m4a")) return "m4a";
        if (url.contains(".wma")) return "wma";
        return null;
    }
    
    /**
     * Detect audio format from HTTP content-type.
     */
    private String detectFormatFromContentType(String contentType) {
        if (contentType == null) return null;
        String ct = contentType.toLowerCase();
        if (ct.contains("mpeg") || ct.contains("mp3")) return "mp3";
        if (ct.contains("ogg")) return "ogg";
        if (ct.contains("opus")) return "opus";
        if (ct.contains("wav") || ct.contains("wave")) return "wav";
        if (ct.contains("flac")) return "flac";
        if (ct.contains("aac")) return "aac";
        if (ct.contains("mp4")) return "m4a";
        if (ct.contains("wma")) return "wma";
        return null;
    }
    
    /**
     * Extract explicit bitrate patterns from URL string.
     * Looks for patterns like "128k", "192kbps", "bitrate=320", "br=256", "_320_".
     * Only matches when the number is clearly a bitrate, not an arbitrary ID.
     */
    private Long extractExplicitBitrateFromUrl(String url) {
        // Pattern: keyword=number, numberk(bps), or number surrounded by separators
        // where the number is a valid audio bitrate (32-320)
        var pattern = java.util.regex.Pattern.compile(
                "(?:(?:bitrate|br|quality)=)(\\d{2,3})" +       // bitrate=128, br=192
                "|(?<![\\d])(\\d{2,3})k(?:bps)?" +              // 128k, 192kbps
                "|(?:[_-])(\\d{3})(?:[_-])",                     // _128_, -320-
                java.util.regex.Pattern.CASE_INSENSITIVE);
        var matcher = pattern.matcher(url);
        
        while (matcher.find()) {
            String numStr = matcher.group(1) != null ? matcher.group(1)
                    : matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3);
            if (numStr == null) continue;
            
            try {
                int value = Integer.parseInt(numStr);
                // Valid audio bitrates: 32, 40, 48, 56, 64, 80, 96, 112, 128,
                // 160, 192, 224, 256, 320
                if ((value >= 32 && value <= 320) && value % 8 == 0) {
                    return (long) value * 1000;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return null;
    }
    
    @Override
    public Duration getDuration() {
        long millis = getDurationInMilliseconds();
        return millis == -1 ? null : Duration.ofMillis(millis);
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "UrlDataSource with " + source.toString();
    }

    @Override
    public boolean isFile() {
       return source.getScheme() != null && source.getScheme().startsWith("file");
   }
}