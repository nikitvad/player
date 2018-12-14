package com.softgroup.android.musicplayer.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.softgroup.android.musicplayer.BuildConfig
import com.softgroup.android.musicplayer.MainActivity
import com.softgroup.android.musicplayer.PlaybackStatus
import com.softgroup.android.musicplayer.R
import com.softgroup.android.musicplayer.data.Audio
import com.softgroup.android.musicplayer.utils.StorageUtil
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

    private var ongoingCall = false
    private lateinit var phoneStateListener: PhoneStateListener
    private lateinit var telephonyManager: TelephonyManager

    private var audioList: ArrayList<Audio>? = null
    private var audioIndex = -1
    private var activeAudio: Audio? = null

    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSession? = null
    private var transportControls: MediaController.TransportControls? = null

    //AudioPlayer notification ID
    private val NOTIFICATION_ID = 101

    companion object {
        const val ACTION_PLAY = "${BuildConfig.APPLICATION_ID}.ACTION_PLAY"
        const val ACTION_PAUSE = "${BuildConfig.APPLICATION_ID}.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "${BuildConfig.APPLICATION_ID}.ACTION_PREVIOUS"
        const val ACTION_NEXT = "${BuildConfig.APPLICATION_ID}.ACTION_NEXT"
        const val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()

        callStateListener()
        registerPlayAudioReseiver()
        registerbecomingNoisyReceiver()
        registerPlayPauseReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {

            val storage = StorageUtil(applicationContext)
            audioList = storage.loadAudio()
            audioIndex = storage.loadAudioIndex()

            if (audioIndex != -1 && audioIndex < audioList?.size!!) {

                activeAudio = audioList?.get(audioIndex)
            } else {
                stopSelf()
            }
        } catch (e: NullPointerException) {
            stopSelf()
        }

        //Request audio focus
        if (!requestAudioFocus()) {
            stopSelf()
        }

        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }

        handleIncomingActions(intent!!)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun callStateListener() {

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                when (state) {


                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> if (mediaPlayer != null) {
                        pauseMedia()
                        ongoingCall = true
                    }
                    TelephonyManager.CALL_STATE_IDLE ->

                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                }
            }
        }


        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMedia()
        mediaPlayer?.release()
        removeAudioFocus()

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)

        removeNotification()

        unregisterReceiver(playNewAudioReceiver)
        unregisterReceiver(becomingNoisyReceiver)

        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }

    private fun playMedia() {
        Log.d("sdasdfad", "playMedia")

        if (!mediaPlayer?.isPlaying!!) {
            mediaPlayer?.start()
            Log.d("sdasdfad", "playMedia.start")

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
        Log.d("sdasdfad", "onPrepared")

    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.d("sdasdfad", "onError")

        when (what) {
            MediaPlayer.MEDIA_ERROR_IO -> Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra")
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED $extra")
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN $extra")
        }
        return false
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        Log.d("sdasdfad", "onSeekComplete")

    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.d("sdasdfad", "onInfo")

        return false
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
        Log.d("sdasdfad", "onBufferingUpdate")

    }


    override fun onAudioFocusChange(focusChange: Int) {
        Log.d("sdasdfad", "onAudioFocusChange")

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

    private fun requestAudioFocus(): Boolean {
        Log.d("sdasdfad", "requestAudioFocus")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return true
    }

    private fun removeAudioFocus(): Boolean {
        Log.d("sdasdfad", "removeAudioFocus")
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this)
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d("sdasdfad", "removeAudioFocus")

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

    private val startPauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (mediaPlayer?.isPlaying!!) {
                pauseMedia()
            } else {
                playMedia()
            }
        }

}

private fun registerPlayPauseReceiver() {
    val intentFilter = IntentFilter(MainActivity.BROADCAST_PLAY_PAUSE)
    registerReceiver(startPauseReceiver, intentFilter)
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
        mediaPlayer?.setDataSource(activeAudio?.data)

    } catch (e: IOException) {
        e.printStackTrace()
        stopSelf()
    }

    mediaPlayer?.prepareAsync()
}

private fun registerbecomingNoisyReceiver() {
    val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    registerReceiver(becomingNoisyReceiver, intentFilter)
}

private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        pauseMedia()
        buildNotification(PlaybackStatus.PAUSED)
    }
}

private val playNewAudioReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        audioIndex = StorageUtil(applicationContext).loadAudioIndex()

        if (audioIndex >= 0 && audioIndex < audioList?.size!!) {
            activeAudio = audioList?.get(audioIndex)
        } else {
            stopSelf()
        }

        stopMedia()
        mediaPlayer?.reset()
        initMediaPlayer()
        updateMetaData()
        buildNotification(PlaybackStatus.PLAYING)

    }
}

private fun registerPlayAudioReseiver() {
    val intentFilter = IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO)
    registerReceiver(playNewAudioReceiver, intentFilter)
}

private fun initMediaSession() {
    if (mediaSessionManager != null) return

    mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    mediaSession = MediaSession(applicationContext, "AudioPlayer")

    transportControls = mediaSession?.controller?.transportControls
    mediaSession?.isActive = true

    mediaSession?.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

    updateMetaData()

    mediaSession?.setCallback(object : MediaSession.Callback() {
        override fun onPlay() {
            super.onPlay()
            resumeMedia()
            buildNotification(PlaybackStatus.PLAYING)

        }

        override fun onPause() {
            super.onPause()
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }

        override fun onStop() {
            super.onStop()
            removeNotification()
            stopSelf()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            skipToNext()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING);
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            skipToPrevious()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING);
        }


    })

}

private fun updateMetaData() {
    val albumArt = BitmapFactory.decodeResource(resources,
            R.drawable.ic_launcher_foreground) //replace with medias albumArt
    // Update the current metadata
    mediaSession?.setMetadata(MediaMetadata.Builder()
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, activeAudio?.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio?.album)
            .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio?.title)
            .build())
}

private fun skipToNext() {

    if (audioIndex == audioList?.size!! - 1) {
        //if last in playlist
        audioIndex = 0
        activeAudio = audioList?.get(audioIndex)
    } else {
        //get next in playlist
        activeAudio = audioList?.get(++audioIndex)
    }

    //Update stored index
    StorageUtil(applicationContext).storeAudioIndex(audioIndex)

    stopMedia()
    //reset mediaPlayer
    mediaPlayer?.reset()
    initMediaPlayer()
}

private fun skipToPrevious() {

    if (audioIndex == 0) {
        //if first in playlist
        //set index to the last of audioList
        audioIndex = audioList?.size!! - 1
        activeAudio = audioList?.get(audioIndex)
    } else {
        //get previous in playlist
        activeAudio = audioList?.get(--audioIndex)
    }

    //Update stored index
    StorageUtil(applicationContext).storeAudioIndex(audioIndex)

    stopMedia()
    //reset mediaPlayer
    mediaPlayer?.reset()
    initMediaPlayer()
}

private fun buildNotification(playbackStatus: PlaybackStatus) {

    var notificationAction = android.R.drawable.ic_media_pause
    //needs to be initialized
    var playPauseAction: PendingIntent? = null

    //Build a new notification according to the current state of the MediaPlayer
    if (playbackStatus == PlaybackStatus.PLAYING) {
        notificationAction = android.R.drawable.ic_media_pause
        //create the pause action
        playPauseAction = playbackAction(1)
    } else if (playbackStatus == PlaybackStatus.PAUSED) {
        notificationAction = android.R.drawable.ic_media_play
        //create the play action
        playPauseAction = playbackAction(0)
    }

    var largeIcon = BitmapFactory.decodeResource(resources,
            R.drawable.ic_launcher_foreground) //replace with your own image

    // Create a new Notification
    val notificationBuilder = Notification.Builder(this)
            .setShowWhen(false)
            .setStyle(Notification.MediaStyle()
                    // Attach our MediaSession token
                    .setMediaSession(mediaSession?.sessionToken)
                    // Show our playback controls in the compact notification view.
                    .setShowActionsInCompactView(0, 1, 2))
            // Set the Notification color
            .setColor(resources.getColor(R.color.colorPrimary))
            // Set the large and small icons
            .setOngoing(true)
            .setLargeIcon(largeIcon)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            // Set Notification content information
            .setContentText(activeAudio?.artist)
            .setContentTitle(activeAudio?.album)
            .setContentInfo(activeAudio?.title)
            // Add playback actions
            .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
            .addAction(notificationAction, "pause", playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2))

    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notificationBuilder.build())
}

private fun removeNotification() {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(NOTIFICATION_ID)
}

private fun playbackAction(actionNumber: Int): PendingIntent? {
    val playbackAction = Intent(this, MediaPlayerService::class.java)
    when (actionNumber) {
        0 -> {
            playbackAction.action = ACTION_PLAY
            return PendingIntent.getService(this, actionNumber, playbackAction, 0)
        }

        1 -> {
            playbackAction.action = ACTION_PAUSE
            return PendingIntent.getService(this, actionNumber, playbackAction, 0)
        }

        2 -> {
            playbackAction.action = ACTION_NEXT
            return PendingIntent.getService(this, actionNumber, playbackAction, 0)
        }

        3 -> {
            // Previous track
            playbackAction.action = ACTION_PREVIOUS
            return PendingIntent.getService(this, actionNumber, playbackAction, 0)
        }

    }
    return null
}

private fun handleIncomingActions(playbackAction: Intent) {
    Log.d("sdfasdf", "handleIncomingActions")
    if (playbackAction.action == null) return

    val actionString = playbackAction.action
    when {
        actionString.equals(ACTION_PLAY, true) -> transportControls?.play()
        actionString.equals(ACTION_PAUSE, true) -> transportControls?.pause()
        actionString.equals(ACTION_NEXT, true) -> transportControls?.skipToNext()
        actionString.equals(ACTION_PREVIOUS, true) -> transportControls?.skipToPrevious()
        actionString.equals(ACTION_STOP, true) -> transportControls?.stop()
    }
}

}

