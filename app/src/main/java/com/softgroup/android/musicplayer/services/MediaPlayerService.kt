package com.softgroup.android.musicplayer.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.IOException

class MediaPlayerService : Service(), MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null

    private var mediaFile: String = ""

    private val iBinder = LocalBinder()

    private var resumePosition = 0

    private lateinit var audioManager: AudioManager


    private fun playMedia() {
        if (!mediaPlayer?.isPlaying!!) {
            mediaPlayer?.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer?.isPlaying!!) {
            mediaPlayer?.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer?.isPlaying!!) {
            mediaPlayer?.pause()
            resumePosition = mediaPlayer?.currentPosition!!
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer?.isPlaying!!) {
            mediaPlayer?.seekTo(resumePosition)
            mediaPlayer?.start()
        }
    }


    override fun onPrepared(mp: MediaPlayer?) {
        playMedia()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {

        when (what) {
            MediaPlayer.MEDIA_ERROR_IO -> Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra)
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra)
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra)
        }
        return false
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
    }

    override fun onAudioFocusChange(focusChange: Int) {

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (mediaPlayer == null) {
                    initMediaPlayer()
                } else if (!mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.start()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                if (mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.release()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.pause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.setVolume(0.1f, 0.1f)
                }
            }
        }
    }

    private fun registerAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return false
    }

    private fun removeAudioFocus() : Boolean{
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    override fun onCompletion(mp: MediaPlayer?) {
        stopMedia()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return iBinder
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService {
            return this@MediaPlayerService
        }
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()

        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)
        mediaPlayer?.setOnSeekCompleteListener(this)
        mediaPlayer?.setOnInfoListener(this)

        mediaPlayer?.reset()

        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
        try {
            mediaPlayer?.setDataSource(mediaFile)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

    }
}

