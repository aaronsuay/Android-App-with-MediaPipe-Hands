@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

import androidx.camera.core.CameraInfo
import androidx.camera.camera2.interop.Camera2CameraInfo

fun getId(info: CameraInfo): String {
    return Camera2CameraInfo.from(info).getCameraId()
}
