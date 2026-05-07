package com.goxr3plus.streamplayer.application;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;

import java.io.File;

/**
 * 缓冲区长度的使用示例
 */
public class BufferSizeDemo {
    
    public static void main(String[] args) {
        StreamPlayer player = new StreamPlayer();
        
        try {
            System.out.println("=== 缓冲区大小演示 ===\n");
            
            // 1. 初始状态
            System.out.println("1. 初始化后:");
            System.out.println("   配置的缓冲区: " + player.getLineBufferSize() + " 字节");
            System.out.println("   当前缓冲区: " + player.getLineCurrentBufferSize() + " 字节");
            System.out.println();
            
            // 2. 打开音频文件
            System.out.println("2. 打开音频文件...");
            player.open(new File("Logic - Ballin [Bass Boosted].mp3"));
            
            System.out.println("   配置的缓冲区: " + player.getLineBufferSize() + " 字节");
            System.out.println("   当前缓冲区: " + player.getLineCurrentBufferSize() + " 字节");
            System.out.println();
            
            // 3. 设置自定义缓冲区大小
            System.out.println("3. 设置自定义缓冲区大小...");
            player.setLineBufferSize(32768); // 32KB
            System.out.println("   已设置为: 32768 字节");
            System.out.println("   配置的缓冲区: " + player.getLineBufferSize() + " 字节");
            System.out.println();
            
            // 4. 停止并重新打开以应用新设置
            System.out.println("4. 重新打开以应用新设置...");
            player.stop();
            player.open(new File("Logic - Ballin [Bass Boosted].mp3"));
            System.out.println("   当前缓冲区: " + player.getLineCurrentBufferSize() + " 字节");
            System.out.println();
            
            // 5. 使用最大缓冲区
            System.out.println("5. 使用最大缓冲区 (-1)...");
            player.setLineBufferSize(-1);
            player.stop();
            player.open(new File("Logic - Ballin [Bass Boosted].mp3"));
            System.out.println("   配置的缓冲区: " + player.getLineBufferSize() + " 字节");
            System.out.println("   当前缓冲区: " + player.getLineCurrentBufferSize() + " 字节");
            System.out.println();
            
            // 6. 开始播放并监控
            System.out.println("6. 开始播放...");
            player.play();
            System.out.println("   播放中的缓冲区: " + player.getLineCurrentBufferSize() + " 字节");
            System.out.println();
            
            // 等待几秒
            Thread.sleep(3000);
            
            // 7. 清理
            System.out.println("7. 停止播放");
            player.stop();
            
            System.out.println("\n=== 演示完成 ===");
            
        } catch (StreamPlayerException | InterruptedException e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            player.reset();
        }
    }
}
