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
import kotlin.math.acos
import kotlin.math.sqrt
import android.renderscript.*
import java.net.Socket
import java.io.OutputStream
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.Color as cully
import com.example.mediapipehands.ui.theme.MediaPipeHandsTheme
import com.google.mediapipe.formats.proto.LandmarkProto
import kotlin.math.atan2

@androidx.camera.camera2.interop.ExperimentalCamera2Interop


class MainActivity : ComponentActivity() {
    // tcpServerInformation
    var tcpServerAddress: String = ""
    var tcpServerPort: Int = 0
    var tcpPassword: String = ""
    var isConnectedToServer by mutableStateOf(false)


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

                NavHost(navController = navController, startDestination = "connect") {
                    composable("connect") {
                        ConnectScreen { address, port, password ->
                            // Save or pass these values
                            tcpServerAddress = address
                            tcpServerPort = port
                            tcpPassword = password

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    tcpSocket = Socket(tcpServerAddress, tcpServerPort)
                                    tcpOutputStream = tcpSocket?.getOutputStream()

                                    // Optionally send the password first
                                    if (tcpPassword.isNotEmpty()) {
                                        tcpOutputStream?.write((tcpPassword + "\n").toByteArray())
                                    }

                                    isConnectedToServer = true //connection success
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isConnectedToServer = false //connection failed
                                }
                            }


                            navController.navigate("home")
                        }
                    }
                    composable("home") {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isConnectedToServer) {
                                Text("Connected to Server!", color = cully(0xFF00FF00))
                            } else {
                                Text("Not Connected", color = cully(0xFFFF0000))
                            }

                            Spacer(modifier = Modifier.height(96.dp))


                        }

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

var tcpSocket: Socket? = null
var tcpOutputStream: OutputStream? = null


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

                        val thumbAngles = listOf(
                            calculateAngle(hand[1].x(), hand[1].y(), hand[2].x(), hand[2].y(), hand[3].x(), hand[3].y()),
                            calculateAngle(hand[2].x(), hand[2].y(), hand[3].x(), hand[3].y(), hand[4].x(), hand[4].y())
                        )

                        val indexAngles = listOf(
                            calculateAngle(hand[5].x(), hand[5].y(), hand[6].x(), hand[6].y(), hand[7].x(), hand[7].y()),
                            calculateAngle(hand[6].x(), hand[6].y(), hand[7].x(), hand[7].y(), hand[8].x(), hand[8].y())
                        )

                        val middleAngles = listOf(
                            calculateAngle(hand[9].x(), hand[9].y(), hand[10].x(), hand[10].y(), hand[11].x(), hand[11].y()),
                            calculateAngle(hand[10].x(), hand[10].y(), hand[11].x(), hand[11].y(), hand[12].x(), hand[12].y())
                        )

                        val ringAngles = listOf(
                            calculateAngle(hand[13].x(), hand[13].y(), hand[14].x(), hand[14].y(), hand[15].x(), hand[15].y()),
                            calculateAngle(hand[14].x(), hand[14].y(), hand[15].x(), hand[15].y(), hand[16].x(), hand[16].y())
                        )

                        val pinkyAngles = listOf(
                            calculateAngle(hand[17].x(), hand[17].y(), hand[18].x(), hand[18].y(), hand[19].x(), hand[19].y()),
                            calculateAngle(hand[18].x(), hand[18].y(), hand[19].x(), hand[19].y(), hand[20].x(), hand[20].y())
                        )

                        val avgThumb = thumbAngles.average().toFloat()
                        val avgIndex = indexAngles.average().toFloat()
                        val avgMiddle = middleAngles.average().toFloat()
                        val avgRing = ringAngles.average().toFloat()
                        val avgPinky = pinkyAngles.average().toFloat()



                        val wrist = hand[0]
                        val thumbBase = hand[1]

                        val wristPronationAngle = calculatePronationAngle(wrist.x(), wrist.y(), thumbBase.x(), thumbBase.y())
                        val normalizedPronationAngle = (wristPronationAngle + 360f) % 360f


                        val dataString = "${avgThumb.toInt()},${avgIndex.toInt()},${avgMiddle.toInt()},${avgRing.toInt()},${avgPinky.toInt()},${normalizedPronationAngle.toInt()}\n"
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                tcpOutputStream?.write(dataString.toByteArray())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val thumbPaint = Paint().apply {
                            color = angleToColor(avgThumb)
                            textSize = 40f
                            style = Paint.Style.FILL
                        }

                        val indexPaint = Paint().apply {
                            color = angleToColor(avgIndex)
                            textSize = 40f
                            style = Paint.Style.FILL
                        }

                        val middlePaint = Paint().apply {
                            color = angleToColor(avgMiddle)
                            textSize = 40f
                            style = Paint.Style.FILL
                        }

                        val ringPaint = Paint().apply {
                            color = angleToColor(avgRing)
                            textSize = 40f
                            style = Paint.Style.FILL
                        }

                        val pinkyPaint = Paint().apply {
                            color = angleToColor(avgPinky)
                            textSize = 40f
                            style = Paint.Style.FILL
                        }


                        val textPaint = Paint().apply {
                            color = Color.WHITE
                            textSize = 40f
                            style = Paint.Style.FILL
                        }

                        canvas.drawText("Thumb: ${avgThumb.toInt()}°", 20f, 50f, thumbPaint)
                        canvas.drawText("Index: ${avgIndex.toInt()}°", 20f, 100f, indexPaint)
                        canvas.drawText("Middle: ${avgMiddle.toInt()}°", 20f, 150f, middlePaint)
                        canvas.drawText("Ring: ${avgRing.toInt()}°", 20f, 200f, ringPaint)
                        canvas.drawText("Pinky: ${avgPinky.toInt()}°", 20f, 250f, pinkyPaint)
                        canvas.drawText("Wrist: ${normalizedPronationAngle.toInt()}°", 20f, 300f, textPaint)
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

fun calculateAngle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
    val abX = ax - bx
    val abY = ay - by
    val cbX = cx - bx
    val cbY = cy - by

    val dot = (abX * cbX + abY * cbY)
    val magAB = sqrt(abX * abX + abY * abY)
    val magCB = sqrt(cbX * cbX + cbY * cbY)

    val cosAngle = dot / (magAB * magCB + 1e-6f) // small value to avoid division by zero
    val angle = Math.toDegrees(acos(cosAngle).toDouble()).toFloat()
    return 180f - angle

}
fun angleToColor(angle: Float): Int {
    return when {
        angle < 30f -> Color.GREEN
        angle < 60f -> Color.YELLOW
        else -> Color.RED
    }
}
@Composable
fun ConnectScreen(
    onConnect: (String, Int, String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Server Address") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            val portNumber = port.toIntOrNull()
            if (portNumber != null && address.isNotBlank()) {
                onConnect(address, portNumber, password)
            }
        }) {
            Text("Connect")
        }
    }
}

fun calculatePronationAngle(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val angleRad = atan2(dy, dx) // Radians
    val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

    return angleDeg
}





