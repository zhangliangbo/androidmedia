package com.mcivicm.media.camera2;

/**
 * 会话声明周期
 */

public enum SessionState {
    Ready, Active, CaptureQueueEmpty, Closed, SurfacePrepared, Configured, ConfiguredFailed
}
