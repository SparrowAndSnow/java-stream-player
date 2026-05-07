package com.goxr3plus.streamplayer.application;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;

import java.io.File;
import java.util.Scanner;

/**
 * 音量控制演示
 */
public class VolumeControlDemo {
    
    public static void main(String[] args) {
        StreamPlayer player = new StreamPlayer();
        
        try {
            System.out.println("=== 音量控制演示 ===\n");
            
            // 打开音频
            player.open(new File("Logic - Ballin [Bass Boosted].mp3"));
            
            // 显示音量信息
            System.out.println("最小增益: " + player.getMinimumGain() + " dB");
            System.out.println("最大增益: " + player.getMaximumGain() + " dB");
            System.out.println("当前增益: " + player.getGainValue() + " dB");
            System.out.println();
            
            // 开始播放
            System.out.println("开始播放...");
            player.play();
            
            // 演示不同音量
            System.out.println("\n1. 设置音量为 25%");
            player.setGain(0.25);
            Thread.sleep(3000);
            
            System.out.println("2. 设置音量为 50%");
            player.setGain(0.5);
            Thread.sleep(3000);
            
            System.out.println("3. 设置音量为 75%");
            player.setGain(0.75);
            Thread.sleep(3000);
            
            System.out.println("4. 设置音量为 100%");
            player.setGain(1.0);
            Thread.sleep(3000);
            
            System.out.println("5. 静音");
            player.setMute(true);
            Thread.sleep(2000);
            
            System.out.println("6. 取消静音");
            player.setMute(false);
            Thread.sleep(2000);
            
            player.stop();
            System.out.println("\n演示完成");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            player.reset();
        }
    }
}
