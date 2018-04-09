package com.mcivicm.audiosample;

import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import omrecorder.AudioChunk;
import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;
import omrecorder.WriteAction;

/**
 * 格式转换
 */

public class Converter {
    /**
     * WAV转PCM文件
     *
     * @param wavFilePath wav文件路径
     * @param pcmFilePath pcm要保存的文件路径及文件名
     * @return
     */
    public static boolean mav2pcm(String wavFilePath, String pcmFilePath) {
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            fileInputStream = new FileInputStream(wavFilePath);
            fileOutputStream = new FileOutputStream(pcmFilePath);
            byte[] wavBytes = toByteArray(fileInputStream);
            byte[] pcmBytes = Arrays.copyOfRange(wavBytes, 44, wavBytes.length);
            fileOutputStream.write(pcmBytes);
        } catch (Exception e) {
            return false;
        } finally {
            IOUtils.closeQuietly(fileInputStream);
            IOUtils.closeQuietly(fileOutputStream);
        }
        return true;
    }

    /**
     * 输入流转byte二进制数据
     *
     * @param fis
     * @return
     * @throws IOException
     */
    private static byte[] toByteArray(FileInputStream fis) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        long size = fis.getChannel().size();
        byte[] buffer;
        if (size <= Integer.MAX_VALUE) {
            buffer = new byte[(int) size];
        } else {
            buffer = new byte[8];
            for (int ix = 0; ix < 8; ++ix) {
                int offset = 64 - (ix + 1) * 8;
                buffer[ix] = (byte) ((size >> offset) & 0xff);
            }
        }
        int len;
        while ((len = fis.read(buffer)) != -1) {
            byteStream.write(buffer, 0, len);
        }
        byte[] data = byteStream.toByteArray();
        IOUtils.closeQuietly(byteStream);
        return data;
    }


    public static void pcm2wav2(String src, String dst) {
        OmRecorder.wav(
                new PullTransport.Default(
                        new PullableSource.Default(
                                new AudioRecordConfig.Default(
                                        MediaRecorder.AudioSource.DEFAULT,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        44100
                                )
                        )
                ),
                new File(Environment.getExternalStorageDirectory(), "om_file.wav")
        );
    }

    public static void pcm2wav(String src, String target) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(target);
        //计算长度
        byte[] buf = new byte[1024 * 4];
        int size = fis.read(buf);
        int pcmSize = 0;
        while (size != -1) {
            pcmSize += size;
            size = fis.read(buf);
        }
        fis.close();
        //填入参数，比特率等等。这里用的是16位单声道 8000 hz
        WaveHeader header = new WaveHeader();
        //长度字段 = 内容的大小（PCMSize) + 头部字段的大小(不包括前面4字节的标识符RIFF以及fileLength本身的4字节)
        header.fileLength = pcmSize + (44 - 8);
        header.FmtHdrLength = 16;
        header.BitsPerSample = 16;
        header.Channels = 1;
        header.FormatTag = 0x0001;
        header.SamplesPerSec = 44100;
        header.BlockAlign = (short) (header.Channels * header.BitsPerSample / 8);
        header.AvgBytesPerSec = header.BlockAlign * header.SamplesPerSec;
        header.DataHdrLength = pcmSize;

        byte[] h = header.getHeader();

        //write header
        fos.write(h, 0, h.length);
        //write data stream
        fis = new FileInputStream(src);
        size = fis.read(buf);
        while (size != -1) {
            fos.write(buf, 0, size);
            size = fis.read(buf);
        }
        fis.close();
        fos.close();
        System.out.println("Convert OK!");
    }

    private static class WaveHeader {

        final char fileID[] = {'R', 'I', 'F', 'F'};
        int fileLength;
        char wavTag[] = {'W', 'A', 'V', 'E'};
        ;
        char FmtHdrID[] = {'f', 'm', 't', ' '};
        int FmtHdrLength;
        short FormatTag;
        short Channels;
        int SamplesPerSec;
        int AvgBytesPerSec;
        short BlockAlign;
        short BitsPerSample;
        char DataHdrID[] = {'d', 'a', 't', 'a'};
        int DataHdrLength;

        byte[] getHeader() throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            writeChar(bos, fileID);
            writeInt(bos, fileLength);
            writeChar(bos, wavTag);
            writeChar(bos, FmtHdrID);
            writeInt(bos, FmtHdrLength);
            writeShort(bos, FormatTag);
            writeShort(bos, Channels);
            writeInt(bos, SamplesPerSec);
            writeInt(bos, AvgBytesPerSec);
            writeShort(bos, BlockAlign);
            writeShort(bos, BitsPerSample);
            writeChar(bos, DataHdrID);
            writeInt(bos, DataHdrLength);
            bos.flush();
            byte[] r = bos.toByteArray();
            bos.close();
            return r;
        }

        private void writeShort(ByteArrayOutputStream bos, int s) throws IOException {
            byte[] myByte = new byte[2];
            myByte[1] = (byte) ((s << 16) >> 24);
            myByte[0] = (byte) ((s << 24) >> 24);
            bos.write(myByte);
        }


        private void writeInt(ByteArrayOutputStream bos, int n) throws IOException {
            byte[] buf = new byte[4];
            buf[3] = (byte) (n >> 24);
            buf[2] = (byte) ((n << 8) >> 24);
            buf[1] = (byte) ((n << 16) >> 24);
            buf[0] = (byte) ((n << 24) >> 24);
            bos.write(buf);
        }

        private void writeChar(ByteArrayOutputStream bos, char[] id) {
            for (char c : id) {
                bos.write(c);
            }
        }
    }
}
