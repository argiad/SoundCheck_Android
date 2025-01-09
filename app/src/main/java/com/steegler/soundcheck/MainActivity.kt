package com.steegler.soundcheck

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private var audioJob: Job? = null
    private var playbackJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            var broadcastID by remember { mutableStateOf("01JBBBJT4SS9K04K3B7X8JG4TE") }
            var serverUrl by remember { mutableStateOf("http://192.168.1.22:9080/broadcast") }
            var authToken by remember { mutableStateOf("eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhdXRoLXNlcnZpY2UiLCJ1c2VySWQiOiIwMUpBUFlUVEtSRE44Qjc3M1k4NlM3VFBKQiIsInVzZXJuYW1lIjoibWUiLCJleHAiOjE3MzE1NTYzMzZ9.xjTi0W23JLRuPX5ADMBZO3idpV59oNdwoqLpphsRw8XDBj05Gkw2FTEt_0ozQ-hAq7WrxujjDp9FeyGD-p3NgA") }
            var isStreaming by remember { mutableStateOf(false) }
            var isPlaying by remember { mutableStateOf(false) }

            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = broadcastID,
                    onValueChange = { broadcastID = it },
                    label = { Text("Broadcast ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Authorization Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isStreaming) {
                            stopAudioStreaming()
                            isStreaming = false
                        } else {
                            startAudioStreaming(broadcastID, serverUrl, authToken)
                            isStreaming = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isPlaying) {
                            stopPlayback()
                            isPlaying = false
                        } else {
                            startPlayback(broadcastID, serverUrl, authToken)
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isPlaying) "Stop Playback" else "Start Playback")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        cleanupTheBroadcast(broadcastID, serverUrl, authToken)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cleanup the broadcast chunks")
                }
            }
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1)
        }
    }

    private fun cleanupTheBroadcast(broadcastID: String, serverUrl: String, authToken: String) {
        val url = "$serverUrl/$broadcastID"
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = createHttpConnectionForCleanup(url, authToken)

                connection.connect()

                val inputStream = connection.inputStream
                val responseText = inputStream.bufferedReader().use { it.readText() }

                println(responseText)

            } catch (e: Exception) {
                println(e.message)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioStreaming(broadcastID: String, serverUrl: String, authToken: String) {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val url = "$serverUrl/$broadcastID"
        audioJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = createHttpConnection(url, authToken)
                val outputStream = connection.outputStream
                val buffer = ByteArray(bufferSize)

                audioRecord.startRecording()
                Log.i("MainActivity", "Streaming started.")

                while (isActive) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()
                    }
                }

                audioRecord.stop()
                outputStream.close()
                connection.disconnect()
                Log.i("MainActivity", "Streaming stopped.")
            } catch (e: Exception) {
                Log.e("MainActivity", "Streaming error: ${e.message}")
            }
        }
    }

    private fun stopAudioStreaming() {
        audioJob?.cancel()
        audioJob = null
    }

    private fun startPlayback(broadcastID: String, serverUrl: String, authToken: String) {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        val url = "$serverUrl/$broadcastID"
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = createHttpConnectionForPlayback(url, authToken)
                val inputStream = connection.inputStream
                val buffer = ByteArray(bufferSize)

                audioTrack.play()
                Log.i("MainActivity", "Playback started.")

                var bytesRead: Int = 0
                while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    audioTrack.write(buffer, 0, bytesRead)
                }

                audioTrack.stop()
                audioTrack.release()
                inputStream.close()
                connection.disconnect()
                Log.i("MainActivity", "Playback stopped.")
            } catch (e: Exception) {
                Log.e("MainActivity", "Playback error: ${e.message}")
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun createHttpConnection(url: String, authToken: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $authToken")
            setRequestProperty("Content-Type", "application/octet-stream")
            setChunkedStreamingMode(4096)
        }
    }

    private fun createHttpConnectionForPlayback(url: String, authToken: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            doInput = true
            setRequestProperty("Authorization", "Bearer $authToken")
        }
    }

    private fun createHttpConnectionForCleanup(url: String, authToken: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            doInput = true
            setRequestProperty("Authorization", "Bearer $authToken")
        }
    }
}
