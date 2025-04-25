package com.example.mediapipehands

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import android.util.Log
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import android.content.Intent



import com.example.mediapipehands.ui.theme.MediaPipeHandsTheme
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
class MainActivity : ComponentActivity() {

    private lateinit var permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var permissionsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionsGranted = hasPermissions()

        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissionsGranted = permissions.all { it.value == true }
        }

        setContent {
            MediaPipeHandsTheme {
                val permissionsState by remember { derivedStateOf { permissionsGranted } }
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {

                        CameraUI(
                            permissionsGranted = permissionsState,
                            onRequestPermissions = {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                )
                            },
                            onStartCamera = {
                                navController.navigate("camera")
                            }
                        )
                    }
                    composable("camera") {
                        CameraScreen()
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                audioPermission == PackageManager.PERMISSION_GRANTED
    }
}


@Composable
fun CameraUI(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartCamera: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current
        Button(onClick = {
            if (!permissionsGranted) {
                onRequestPermissions()
            } else {
                context.startActivity(Intent(context, CameraActivity::class.java))
            }
        }) {
            Text(if (permissionsGranted) "Start Camera" else "Grant Permissions")
        }
    }
}
@Composable
fun Camera2Preview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraView = remember { SurfaceView(context) }

    AndroidView(
        factory = { cameraView },
        modifier = modifier
    ) { surfaceView ->
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

                var widestFOV = 0f
                var bestCameraId: String? = null

                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing != CameraCharacteristics.LENS_FACING_BACK) continue

                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

                    if (focalLengths != null && sensorSize != null && focalLengths.isNotEmpty()) {
                        val fov = sensorSize.width / focalLengths[0]
                        if (fov > widestFOV) {
                            widestFOV = fov
                            bestCameraId = cameraId
                        }
                    }
                }

                bestCameraId?.let { cameraId ->
                    try {
                        val cameraDeviceCallback = object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                val surface = holder.surface


                                val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(surface)
                                }

                                val outputConfig = OutputConfiguration(surface)
                                val sessionConfig = SessionConfiguration(
                                    SessionConfiguration.SESSION_REGULAR,
                                    listOf(outputConfig),
                                    ContextCompat.getMainExecutor(context),
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(session: CameraCaptureSession) {
                                            session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                                        }

                                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                                    }
                                )

                                camera.createCaptureSession(sessionConfig)
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                camera.close()
                            }

                            override fun onError(camera: CameraDevice, error: Int) {
                                camera.close()
                            }
                        }

                        cameraManager.openCamera(cameraId, cameraDeviceCallback, null)

                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }
}

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@Composable
fun CameraScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Camera2Preview(modifier = Modifier.fillMaxSize())
    }
}




