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
import android.media.ImageReader
import android.graphics.ImageFormat
// Android / Camera2
import android.graphics.Bitmap
import android.media.Image

// MediaPipe
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.viewinterop.AndroidView




import android.renderscript.*


import com.example.mediapipehands.ui.theme.MediaPipeHandsTheme
import com.google.mediapipe.formats.proto.LandmarkProto

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
    val handConnections = listOf(
        // Thumb
        Pair(0, 1),
        Pair(1, 2),
        Pair(2, 3),
        Pair(3, 4),

        // Index Finger
        Pair(0, 5),
        Pair(5, 6),
        Pair(6, 7),
        Pair(7, 8),

        // Middle Finger
        Pair(0, 9),
        Pair(9, 10),
        Pair(10, 11),
        Pair(11, 12),

        // Ring Finger
        Pair(0, 13),
        Pair(13, 14),
        Pair(14, 15),
        Pair(15, 16),

        // Pinky
        Pair(0, 17),
        Pair(17, 18),
        Pair(18, 19),
        Pair(19, 20)
    )

    val context = LocalContext.current

    // Create SurfaceViews (camera + overlay)
    val cameraView = remember {
        SurfaceView(context)
    }

    val drawingSurfaceView = remember {
        SurfaceView(context).apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
        }
    }

    // Set up ImageReader for MediaPipe
    val imageReader = remember {
        ImageReader.newInstance(
            640, 480,
            ImageFormat.YUV_420_888,
            2
        )
    }

    // Place camera and overlay SurfaceViews in the view hierarchy
    Box(modifier = modifier) {
        AndroidView(factory = {
            cameraView.apply {
                holder.addCallback(object : SurfaceHolder.Callback {
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
                                        val previewSurface = holder.surface

                                        val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                            addTarget(previewSurface)
                                            addTarget(imageReader.surface)
                                        }

                                        val outputConfigPreview = OutputConfiguration(previewSurface)
                                        val outputConfigReader = OutputConfiguration(imageReader.surface)

                                        val sessionConfig = SessionConfiguration(
                                            SessionConfiguration.SESSION_REGULAR,
                                            listOf(outputConfigPreview, outputConfigReader),
                                            ContextCompat.getMainExecutor(context),
                                            object : CameraCaptureSession.StateCallback() {
                                                override fun onConfigured(session: CameraCaptureSession) {
                                                    session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                                                }

                                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                                    Log.e("Camera2Preview", "CaptureSession configuration failed")
                                                }
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
                    override fun surfaceDestroyed(holder: SurfaceHolder) {

                    }
                })
            }
        })

        AndroidView(factory = {
            drawingSurfaceView
        })
    }



    // Initialize MediaPipe HandLandmarker
    val handLandmarker = remember {
        HandLandmarker.createFromOptions(
            context,
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            imageReader.setOnImageAvailableListener(null, null)
            handLandmarker.close()
        }
    }

    // Process camera frames and draw landmarks
    imageReader.setOnImageAvailableListener({ reader ->
        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
        try {
            val bitmap = YuvToRgbConverter().yuv420ToBitmap(image)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker.detectForVideo(mpImage, System.currentTimeMillis())

            result?.landmarks()?.let { hands ->
                val canvas = drawingSurfaceView.holder.lockCanvas()
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                val paint = Paint().apply {
                    color = Color.GREEN
                    style = Paint.Style.FILL
                    strokeWidth = 8f
                }

                hands.forEach { hand ->
                    //Drawing Dots (landmarks)
                    hand.forEach { landmark ->
                        val x = landmark.x() * drawingSurfaceView.width
                        val y = landmark.y() * drawingSurfaceView.height
                        canvas.drawCircle(x, y, 10f, paint)
                    }
                    val linePaint = Paint().apply {
                        color = Color.YELLOW
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                    }
                    //Connecting Dots (Skeleton)
                    handConnections.forEach { (startIdx, endIdx) ->
                        val start = hand[startIdx]
                        val end = hand[endIdx]

                        val startX = start.x() * drawingSurfaceView.width
                        val startY = start.y() * drawingSurfaceView.height
                        val endX = end.x() * drawingSurfaceView.width
                        val endY = end.y() * drawingSurfaceView.height

                        canvas.drawLine(startX, startY, endX, endY, linePaint)
                    }
                }

                drawingSurfaceView.holder.unlockCanvasAndPost(canvas)
            }
        } finally {
            image.close()
        }
    }, Handler(Looper.getMainLooper()))


}


class YuvToRgbConverter {

    fun yuv420ToBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val jpegBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }
}

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@Composable
fun CameraScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Camera2Preview(modifier = Modifier.fillMaxSize())
    }
}




