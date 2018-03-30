package com.mcivicm.audiosample;

import android.hardware.Camera;

/**
 * 摄像头助手
 */

public class CameraHelper {
    /**
     * @return 摄像头的总数
     */
    public static int cameraNumber() {
        return Camera.getNumberOfCameras();
    }

    /**
     * 获取摄像头的信息
     *
     * @param id
     * @return
     */
    public static Camera.CameraInfo getInfo(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        return info;
    }

    /**
     * 打开编号为id的摄像头
     *
     * @param id
     * @return
     */
    public static Camera open(int id) {
        return Camera.open(id);
    }

    /**
     * 设置默认的照相参数
     *
     * @param parameter
     */
    public static Camera.Parameters defaultParameter(Camera.Parameters parameter) {
        return parameter;
    }


}
