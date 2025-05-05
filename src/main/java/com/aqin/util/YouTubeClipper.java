package com.aqin.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class YouTubeClipper {
    // 工具路径配置（根据实际安装位置修改）
    private static final String YT_DLP_PATH = "/opt/homebrew/bin/yt-dlp"; // macOS/Linux
    // private static final String YT_DLP_PATH = "C:\\tools\\yt-dlp.exe"; // Windows
    private static final String FFMPEG_PATH = "/opt/homebrew/bin/ffmpeg";
    private static final int TIMEOUT_SECONDS = 300; // 5分钟超时

    // 字幕样式配置
    private static class SubtitleStyle {
        String fontName = "PingFang SC";  // 使用系统默认中文字体
        int fontSize = 28;                // 增大字体大小
        String fontColor = "white";       // 白色字体
        String backgroundColor = "black"; // 黑色背景
        int backgroundOpacity = 60;      // 背景半透明
        String alignment = "center";      // 居中对齐
        int marginV = 25;                 // 垂直边距
        int lineSpacing = -2;             // 行间距
        int marginH = 10;                 // 水平边距
    }

    public static void main(String[] args) {
        String videoUrl = "视频地址";
        String startTime = "起始时间"; // 格式 HH:mm:ss
        String endTime = "结束时间"; // 格式 HH:mm:ss
        String outputFile = "Result.mp4";
        String subtitleFile = "Result.srt";

        try {
            // Step 1: 下载字幕
            System.out.println("开始下载字幕...");
            ProcessBuilder subtitlePb = new ProcessBuilder(
                    YT_DLP_PATH,
                    "--write-subs",
                    "--write-auto-subs",
                    "--sub-lang", "en,zh-Hans,zh-Hant",
                    "--skip-download",
                    "--socket-timeout", "30",
                    "--retries", "5",
                    "-o", subtitleFile.replace(".srt", ""),
                    videoUrl
            );
            executeCommandWithTimeout(subtitlePb);

            // Step 2: 转换字幕格式
            System.out.println("开始转换字幕格式...");
            String[] languages = {"en", "zh-Hans", "zh-Hant"};
            for (String lang : languages) {
                String vttFile = "Result." + lang + ".vtt";
                String assFile = "Result." + lang + ".ass";

                if (new File(vttFile).exists()) {
                    // 如果ASS文件已存在，先删除
                    if (new File(assFile).exists()) {
                        new File(assFile).delete();
                    }

                    // 创建ASS字幕文件
                    try (FileWriter writer = new FileWriter(assFile)) {
                        // 写入ASS文件头
                        writer.write("[Script Info]\n");
                        writer.write("ScriptType: v4.00+\n");
                        writer.write("PlayResX: 1920\n");
                        writer.write("PlayResY: 1080\n");
                        writer.write("WrapStyle: 0\n\n");

                        // 写入样式
                        writer.write("[V4+ Styles]\n");
                        writer.write("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
                        writer.write("Style: Default,PingFang SC,28,&H00FFFFFF,&H000000FF,&H00000000,&H60000000,0,0,0,0,100,100,0,0,1,2,1,2,10,10,25,1\n\n");

                        // 写入事件
                        writer.write("[Events]\n");
                        writer.write("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
                    }

                    // 转换VTT到ASS
                    ProcessBuilder convertPb = new ProcessBuilder(
                            FFMPEG_PATH,
                            "-y",
                            "-i", vttFile,
                            "-f", "ass",
                            assFile
                    );
                    executeCommandWithTimeout(convertPb);

                    // 删除原始的VTT文件
                    new File(vttFile).delete();
                    System.out.println("已转换字幕: " + assFile);
                }
            }

            // Step 3: 使用 yt-dlp 下载视频
            System.out.println("开始下载视频...");
            ProcessBuilder downloadPb = new ProcessBuilder(
                    YT_DLP_PATH,
                    "-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]", // 使用最高1080p的视频+音频组合
                    "--merge-output-format", "mp4",
                    "--retries", "5",
                    "--socket-timeout", "30",
                    "--no-check-certificates",
                    "-o", "temp.mp4",
                    videoUrl
            );
            executeCommandWithTimeout(downloadPb);

            // Step 4: 剪辑视频
            System.out.println("开始剪辑视频...");
            if (new File(outputFile).exists()) {
                new File(outputFile).delete();
            }

            // 使用ffmpeg剪辑视频并嵌入字幕
            ProcessBuilder clipPb = new ProcessBuilder(
                    FFMPEG_PATH,
                    "-y",
                    "-ss", startTime,
                    "-to", endTime,
                    "-i", "temp.mp4",
                    "-vf", String.format(
                    "ass=%s",
                    "Result.zh-Hans.ass"
            ),
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    outputFile
            );
            executeCommandWithTimeout(clipPb);

            // Step 5: 清理临时文件
            System.out.println("视频剪辑完成: " + outputFile);
            System.out.println("字幕文件已生成: Result.en.srt, Result.zh-Hans.srt, Result.zh-Hant.srt");

        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void executeCommandWithTimeout(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 创建超时线程
        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_SECONDS * 1000);
                process.destroy();
            } catch (InterruptedException e) {
                // 正常完成，不需要处理中断
            }
        });
        timeoutThread.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 过滤进度信息，避免日志过多
                if (!line.contains("ETA") && !line.contains("frame=")) {
                    System.out.println(line);
                }
            }
        }

        int exitCode = process.waitFor();
        timeoutThread.interrupt();

        if (exitCode != 0) {
            throw new RuntimeException("命令执行失败，退出码: " + exitCode);
        }
    }
}