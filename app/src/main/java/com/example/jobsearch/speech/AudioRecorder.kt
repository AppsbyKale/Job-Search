package com.example.jobsearch.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class AudioRecorder(private val context: Context) {

    private val sampleRate = 16000

    @Volatile private var recording = false
    private var recordThread: Thread? = null

    val isRecording: Boolean get() = recording

    fun start(destination: File) {
        if (recording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission is not granted.")
        }
        recording = true
        recordThread = Thread { recordToWav(destination) }.apply { start() }
    }

    fun stop() {
        recording = false
        recordThread?.let {
            try {
                it.join(3000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        recordThread = null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordToWav(destination: File) {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            return
        }
        try {
            RandomAccessFile(destination, "rw").use { raf ->
                writeWavHeader(raf, 0)
                raf.seek(44)
                recorder.startRecording()
                val buffer = ByteArray(minBuffer)
                while (recording) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) raf.write(buffer, 0, read)
                }
                runCatching { recorder.stop() }
                val dataSize = raf.filePointer - 44
                writeWavHeader(raf, dataSize)
            }
        } catch (_: IOException) {
        } catch (_: IllegalStateException) {
        } finally {
            runCatching { recorder.release() }
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Long) {
        raf.seek(0)
        raf.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        writeIntLE(raf, (36 + dataSize).toInt())
        raf.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        raf.write(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        writeIntLE(raf, 16)
        writeShortLE(raf, 1)
        writeShortLE(raf, 1)
        writeIntLE(raf, sampleRate)
        writeIntLE(raf, sampleRate * 2)
        writeShortLE(raf, 2)
        writeShortLE(raf, 16)
        raf.write(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        writeIntLE(raf, dataSize.toInt())
        raf.seek(44 + dataSize)
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
        raf.write((value shr 16) and 0xFF)
        raf.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
    }
}
