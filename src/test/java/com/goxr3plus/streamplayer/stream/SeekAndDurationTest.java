package com.goxr3plus.streamplayer.stream;

import com.goxr3plus.streamplayer.enums.Status;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 测试 seekTo 功能和时长获取
 */
public class SeekAndDurationTest implements StreamPlayerListener {
    
    private static final String TEST_FILE = "Logic - Ballin [Bass Boosted].mp3";
    private StreamPlayer player;
    private CountDownLatch latch;
    
    public static void main(String[] args) throws Exception {
        new SeekAndDurationTest().runTest();
    }
    
    public void runTest() throws Exception {
        System.out.println("=== 开始测试 seekTo 和时长功能 ===\n");
        
        player = new StreamPlayer();
        player.addStreamPlayerListener(this);
        
        File audioFile = new File(TEST_FILE);
        if (!audioFile.exists()) {
            System.err.println("错误: 测试文件不存在: " + TEST_FILE);
            return;
        }
        
        // 测试1: 打开文件并检查时长
        System.out.println("测试1: 打开文件并获取时长");
        player.open(audioFile);
        
        int durationSeconds = player.getDurationInSeconds();
        long durationMillis = player.getDurationInMilliseconds();
        System.out.println("  时长: " + durationSeconds + " 秒 (" + durationMillis + " 毫秒)");
        
        if (durationSeconds <= 0) {
            System.err.println("  ❌ 失败: 时长不正确");
        } else {
            System.out.println("  ✅ 成功: 时长获取正确");
        }
        System.out.println();
        
        // 测试2: 开始播放
        System.out.println("测试2: 开始播放");
        player.play();
        System.out.println("  状态: " + player.getStatus());
        
        if (!player.isPlaying()) {
            System.err.println("  ❌ 失败: 播放器未处于播放状态");
        } else {
            System.out.println("  ✅ 成功: 播放器正在播放");
        }
        System.out.println();
        
        // 等待3秒让播放进行
        System.out.println("等待3秒...");
        Thread.sleep(3000);
        
        // 测试3: 检查当前位置
        System.out.println("\n测试3: 检查当前播放位置");
        int positionBytes = player.getEncodedStreamPosition();
        long positionMicros = player.getSourceDataLine().getMicrosecondPosition();
        System.out.println("  字节位置: " + positionBytes);
        System.out.println("  微秒位置: " + positionMicros / 1000000.0 + " 秒");
        
        if (positionBytes <= 0 || positionMicros <= 0) {
            System.err.println("  ⚠️  警告: 位置可能不正确");
        } else {
            System.out.println("  ✅ 成功: 位置信息正常");
        }
        System.out.println();
        
        // 测试4: seekTo 到 10 秒位置
        System.out.println("测试4: seekTo 到 10 秒位置");
        latch = new CountDownLatch(1);
        player.seekTo(10);
        
        // 等待 seek 完成
        if (latch.await(2, TimeUnit.SECONDS)) {
            System.out.println("  Seek 完成");
        }
        
        // 检查 seek 后的位置
        Thread.sleep(500); // 给一点时间让位置更新
        int afterSeekBytes = player.getEncodedStreamPosition();
        long afterSeekMicros = player.getSourceDataLine().getMicrosecondPosition();
        System.out.println("  seek 后字节位置: " + afterSeekBytes);
        System.out.println("  seek 后微秒位置: " + afterSeekMicros / 1000000.0 + " 秒");
        
        // 验证位置是否在 10 秒附近（允许±2秒误差）
        double seekedSeconds = afterSeekMicros / 1000000.0;
        if (seekedSeconds >= 8 && seekedSeconds <= 12) {
            System.out.println("  ✅ 成功: seekTo 位置正确 (约10秒)");
        } else {
            System.err.println("  ❌ 失败: seekTo 位置不正确，期望约10秒，实际: " + seekedSeconds + " 秒");
        }
        System.out.println();
        
        // 测试5: 验证时长在 seek 后仍然正确
        System.out.println("测试5: 验证 seek 后时长仍然正确");
        int durationAfterSeek = player.getDurationInSeconds();
        System.out.println("  seek 后时长: " + durationAfterSeek + " 秒");
        
        if (durationAfterSeek == durationSeconds) {
            System.out.println("  ✅ 成功: 时长保持不变");
        } else {
            System.err.println("  ❌ 失败: 时长发生变化 (原: " + durationSeconds + ", 现: " + durationAfterSeek + ")");
        }
        System.out.println();
        
        // 测试6: seekTo 到另一个位置
        System.out.println("测试6: seekTo 到 30 秒位置");
        latch = new CountDownLatch(1);
        player.seekTo(30);
        
        if (latch.await(2, TimeUnit.SECONDS)) {
            System.out.println("  Seek 完成");
        }
        
        Thread.sleep(500);
        long finalMicros = player.getSourceDataLine().getMicrosecondPosition();
        double finalSeconds = finalMicros / 1000000.0;
        System.out.println("  seek 后位置: " + finalSeconds + " 秒");
        
        if (finalSeconds >= 28 && finalSeconds <= 32) {
            System.out.println("  ✅ 成功: seekTo 30秒位置正确");
        } else {
            System.err.println("  ❌ 失败: seekTo 位置不正确，期望约30秒，实际: " + finalSeconds + " 秒");
        }
        System.out.println();
        
        // 清理
        System.out.println("停止播放并清理资源...");
        player.stop();
        player.reset();
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    @Override
    public void opened(Object dataSource, Map<String, Object> properties) {
        System.out.println("[事件] 音频已打开");
    }
    
    @Override
    public void progress(int nEncodedBytes, long microsecondPosition, byte[] pcmData, Map<String, Object> properties) {
        // 每10秒打印一次进度
        if (microsecondPosition % 10000000 < 100000) { // 每10秒
            System.out.println("[进度] " + (microsecondPosition / 1000000) + " 秒, 字节: " + nEncodedBytes);
        }
    }
    
    @Override
    public void statusUpdated(StreamPlayerEvent event) {
        Status status = event.playerStatus();
        System.out.println("[状态] " + status);
        
        // 当 seek 完成时，释放 latch
        if (status == Status.SEEKED && latch != null) {
            latch.countDown();
        }
    }
}
