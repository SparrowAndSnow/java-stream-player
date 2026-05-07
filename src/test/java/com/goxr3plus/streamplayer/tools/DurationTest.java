package com.goxr3plus.streamplayer.tools;

import com.goxr3plus.streamplayer.enums.AudioType;

import java.io.File;

/**
 * 测试时长计算
 */
public class DurationTest {
    
    public static void main(String[] args) {
        System.out.println("=== 时长计算测试 ===\n");
        
        // 测试文件
        String[] testFiles = {
            "Logic - Ballin [Bass Boosted].mp3",
            "kick.mp3",
            "kick.wav"
        };
        
        for (String fileName : testFiles) {
            File file = new File(fileName);
            if (!file.exists()) {
                System.out.println("跳过: " + fileName + " (文件不存在)");
                continue;
            }
            
            System.out.println("文件: " + fileName);
            System.out.println("  文件大小: " + (file.length() / 1024) + " KB");
            
            // 使用 TimeTool 计算
            long durationMillis = TimeTool.durationInMilliseconds(file.getAbsolutePath(), AudioType.FILE);
            int durationSeconds = TimeTool.durationInSeconds(file.getAbsolutePath(), AudioType.FILE);
            
            System.out.println("  TimeTool 计算:");
            System.out.println("    毫秒: " + durationMillis);
            System.out.println("    秒: " + durationSeconds);
            System.out.println("    格式化: " + formatDuration(durationMillis));
            
            // 尝试用 AudioSystem 获取
            try {
                javax.sound.sampled.AudioFileFormat aff = javax.sound.sampled.AudioSystem.getAudioFileFormat(file);
                Long microseconds = (Long) aff.properties().get("duration");
                if (microseconds != null) {
                    long audioSystemMillis = microseconds / 1000;
                    System.out.println("  AudioSystem 计算:");
                    System.out.println("    微秒: " + microseconds);
                    System.out.println("    毫秒: " + audioSystemMillis);
                    System.out.println("    秒: " + (audioSystemMillis / 1000));
                    System.out.println("    格式化: " + formatDuration(audioSystemMillis));
                }
            } catch (Exception e) {
                System.out.println("  AudioSystem 失败: " + e.getMessage());
            }
            
            System.out.println();
        }
    }
    
    private static String formatDuration(long millis) {
        if (millis < 0) return "未知";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%d:%02d", minutes % 60, seconds % 60);
        }
    }
}
