package com.mcivicm.audiosample;

import android.media.MediaPlayer;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Mp3助手
 */

public class Mp3Helper {
//    /**
//     * MP3转换PCM文件方法
//     *
//     * @param mp3 原始文件路径
//     * @param pcm 转换文件的保存路径
//     * @return
//     * @throws Exception
//     */
//    public static boolean mp3FileToPcmFile(String mp3, String pcm) {
//        try {
//            //获取文件的音频流，pcm的格式
//            AudioInputStream pcmAudioInputStream = getPcmAudioInputStream(mp3);
//            //将音频转化为  pcm的格式保存下来
//            if (pcmAudioInputStream != null) {
//                AudioSystem.write(pcmAudioInputStream, AudioFileFormat.Type.WAVE, new File(pcm));
//                return true;
//            } else {
//                return false;
//            }
//        } catch (IOException e) {
//            return false;
//        }
//    }
//
//    /**
//     * mp3流转pcm流
//     *
//     * @param mp3Is
//     * @param pcmOs
//     * @return
//     */
//    public static boolean mp3StreamToPcmStream(InputStream mp3Is, OutputStream pcmOs) {
//        if (mp3Is == null || pcmOs == null) {
//            return false;
//        }
//        try {
//            //读取音频文件的类
//            MpegAudioFileReader mafr = new MpegAudioFileReader();
//            AudioInputStream in = mafr.getAudioInputStream(mp3Is);
//            AudioFormat sourceFormat = in.getFormat();
//            //设定输出格式为pcm格式的音频文件
//            AudioFormat targetFormat = new AudioFormat(
//                    AudioFormat.Encoding.PCM_SIGNED,
//                    sourceFormat.getSampleRate(),
//                    16,
//                    sourceFormat.getChannels(),
//                    sourceFormat.getChannels() * 2,
//                    sourceFormat.getSampleRate(),
//                    false
//            );
//            //输出到音频
//            AudioInputStream pcmIs = AudioSystem.getAudioInputStream(targetFormat, in);
//            AudioSystem.write(pcmIs, AudioFileFormat.Type.WAVE, pcmOs);
//            return true;
//        } catch (UnsupportedAudioFileException e) {
//            return false;
//        } catch (IOException e) {
//            return false;
//        }
//    }
//
//
//    /**
//     * 播放MP3方法
//     *
//     * @param mp3filepath
//     * @throws Exception
//     */
//    public static void playMp3(String mp3filepath) throws Exception {
//        //获取音频为pcm的格式
//        AudioInputStream pcm = getPcmAudioInputStream(mp3filepath);
//        // 播放
//        if (pcm == null) {
//            return;
//        }
//        //获取音频的格式
//        AudioFormat targetFormat = pcm.getFormat();
//        DataLine.Info dtInfo = new DataLine.Info(SourceDataLine.class, targetFormat, AudioSystem.NOT_SPECIFIED);
//        //输出设备
//        try {
//            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(dtInfo);
//            line.open(targetFormat);
//            line.start();
//            byte[] buffer = new byte[1024];
//            int len;
//            //读取音频文件
//            while (true) {
//                len = pcm.read(buffer);
//                if (len > 0) {
//                    line.write(buffer, 0, len);//输出音频文件
//                } else {
//                    break;
//                }
//            }
//            // Block等待临时数据被输出为空
//            line.drain();
//            //停止播放
//            line.stop();
//            line.close();
//            //关闭读取流
//            pcm.close();
//        } catch (Exception ex) {
//            //ignore
//        }
//    }
//
//    /**
//     * 机能概要:获取文件的音频流
//     *
//     * @param mp3filepath
//     * @return
//     */
//    private static AudioInputStream getPcmAudioInputStream(String mp3filepath) {
//        File mp3 = new File(mp3filepath);
//        if (!mp3.exists()) {
//            return null;
//        }
//        try {
//            //读取音频文件的类
//            MpegAudioFileReader mp = new MpegAudioFileReader();
//            AudioInputStream in = mp.getAudioInputStream(mp3);
//            AudioFormat baseFormat = in.getFormat();
//            //设定输出格式为pcm格式的音频文件
//            AudioFormat targetFormat = new AudioFormat(
//                    AudioFormat.Encoding.PCM_SIGNED,
//                    baseFormat.getSampleRate(),
//                    16,
//                    baseFormat.getChannels(),
//                    baseFormat.getChannels() * 2,
//                    baseFormat.getSampleRate(),
//                    false
//            );
//            //输出到音频
//            return AudioSystem.getAudioInputStream(targetFormat, in);
//        } catch (Exception e) {
//            return null;
//        }
//    }
}
