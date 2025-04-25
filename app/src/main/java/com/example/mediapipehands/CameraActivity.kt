package com.example.mediapipehands
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import com.example.mediapipehands.Camera2Preview
class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Camera2Preview(modifier = Modifier.fillMaxSize())
        }
    }
}
