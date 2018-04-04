package com.mcivicm.audiosample;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

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
            byte[] wavByte = toByteArray(fileInputStream);
            byte[] pcmByte = Arrays.copyOfRange(wavByte, 44, wavByte.length);
            fileOutputStream.write(pcmByte);
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
}
