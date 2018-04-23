package com.mcivicm.media.camera2;

/**
 * 会话声明周期
 */

public enum SessionState {
    Ready, Active, CaptureQueueEmpty, Close, SurfacePrepared, Configured, ConfiguredFailed
}
