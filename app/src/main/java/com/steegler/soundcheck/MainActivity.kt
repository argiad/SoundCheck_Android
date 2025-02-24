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
import java.security.MessageDigest
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private var audioJob: Job? = null
    private var playbackJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            var broadcastID by remember { mutableStateOf("* BROADCAST ID ") }
            var serverUrl by remember { mutableStateOf("https://ptt.steegler.com/broadcast") }
            var authToken by remember { mutableStateOf("* BEARER TOKEN *") }
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
                var counter = 0
                while (isActive) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        counter ++
                        println("$counter -> $bytesRead bytes ${buffer.md5()}")
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()
                    }
                }

                audioRecord.stop()
                outputStream.close()
                connection.disconnect()
                Log.i("MainActivity", "Streaming stopped. $counter chunks sent")
            } catch (e: Exception) {
                Log.e("MainActivity", "Streaming error: ${e.message}")
            } finally {
                println("BFS -> $bufferSize")
            }
        }
    }

    private fun stopAudioStreaming() {
        audioJob?.cancel()
        audioJob = null
    }

    private fun startPlayback(broadcastID: String, serverUrl: String, authToken: String) {

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

        println("BFS -> $bufferSize")
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun createHttpConnection(url: String, authToken: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
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

    private val sampleRate = 44100
    private val bufferSize = max(
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
        AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    )
}
fun ByteArray.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return md.digest(this).joinToString("") { "%02x".format(it) }
}