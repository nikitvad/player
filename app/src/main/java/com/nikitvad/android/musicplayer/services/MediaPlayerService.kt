package com.nikitvad.android.musicplayer.services

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
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.nikitvad.android.musicplayer.BuildConfig
import com.nikitvad.android.musicplayer.ui.MainActivity
import com.nikitvad.android.musicplayer.PlaybackStatus
import com.nikitvad.android.musicplayer.R
import com.nikitvad.android.musicplayer.data.Audio
import com.nikitvad.android.musicplayer.utils.StorageUtil
import java.util.*


class MediaPlayerService : Service(), MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {

    private val TAG = "MediaPlayerService"

    private val iBinder = LocalBinder()
    private var resumePosition = 0

    private var lostAudioFocus = false
    private var forceSkipped = false

    private var audioList: ArrayList<Audio>? = null
    private var audioIndex = -1
    private var activeAudio: Audio? = null

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var audioManager: AudioManager
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSession? = null
    private var transportControls: MediaController.TransportControls? = null

    var progressListener: AudioProgressListener? = null
    var infoListener: AudioInfoListener? = null
        set(value) {
            activeAudio?.let { value?.onInfoChanged(it) }
            field = value
        }

    private val timer = Timer()

    private val NOTIFICATION_ID = 101

    companion object {
        const val ACTION_PLAY = "${BuildConfig.APPLICATION_ID}.ACTION_PLAY"
        const val ACTION_PAUSE = "${BuildConfig.APPLICATION_ID}.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "${BuildConfig.APPLICATION_ID}.ACTION_PREVIOUS"
        const val ACTION_NEXT = "${BuildConfig.APPLICATION_ID}.ACTION_NEXT"
        const val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.ACTION_STOP"
        const val ACTION_PLAY_AUDIO = "${BuildConfig.APPLICATION_ID}.ACTION_PLAY_AUDIO"

        const val ARG_AUDIO_POS = "${BuildConfig.APPLICATION_ID}.ARG_AUDIO_POS"

    }

    override fun onCreate() {
        super.onCreate()

        registerPlayAudioReceiver()
        registerbecomingNoisyReceiver()
        registerPlayPauseReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {

            val storage = StorageUtil(applicationContext)
            audioList = storage.loadAudio()
            audioIndex = storage.loadAudioIndex()

            if (audioIndex > -1 && audioIndex < audioList?.size!!) {
                activeAudio = audioList?.get(audioIndex)
            } else {
                stopSelf()
            }
        } catch (e: NullPointerException) {
            stopSelf()
        }

        if (!requestAudioFocus()) {
            stopSelf()
        }

        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                mediaPlayer = initMediaPlayer()
                updateMediaPlayer(activeAudio?.data!!)
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            //TODO why is it here?
            buildNotification(PlaybackStatus.PLAYING)
        }

        //TODO crash here
        handleIncomingActions(intent!!)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaPlayer.stop()

        mediaPlayer.release()
        mediaSession?.release()
        timer.cancel()

        removeAudioFocus()
        removeNotification()

        unregisterReceiver(playNewAudioReceiver)
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(startPauseReceiver)

        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }

    private fun playMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            resumePosition = mediaPlayer.currentPosition
        }
        buildNotification(PlaybackStatus.PAUSED)
    }

    private fun resumeMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.seekTo(resumePosition)
            playMedia()
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        playMedia()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {

        when (what) {
            MediaPlayer.MEDIA_ERROR_IO -> Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra")
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED $extra")
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN $extra")
        }
        return false
    }

    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        Log.d(TAG, "onSeekComplete: ${mp?.currentPosition!!}")
    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {

        return false
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {

    }


    override fun onAudioFocusChange(focusChange: Int) {

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!mediaPlayer.isPlaying && lostAudioFocus) {
                    playMedia()
                    mediaPlayer.setVolume(1f, 1f)
                    lostAudioFocus = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.release()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    lostAudioFocus = true
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer.isPlaying) {
                    //TODO maybe come piece of code require here
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return true
    }

    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this)
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d(TAG, "onCompletion: ")


        if (!forceSkipped) {
            if (audioIndex == audioList?.size!! - 1) {
                audioIndex = 0
                activeAudio = audioList?.get(audioIndex)
            } else {
                activeAudio = audioList?.get(++audioIndex)
            }

            StorageUtil(applicationContext).storeAudioIndex(audioIndex)

            mediaPlayer.stop()

            updateMediaPlayer(activeAudio?.data!!)
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        } else {
            forceSkipped = false
        }
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

            if (mediaPlayer.isPlaying) {
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

    private fun initMediaPlayer(): MediaPlayer {
        val mediaPlayer = MediaPlayer()

        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnErrorListener(this)
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setOnBufferingUpdateListener(this)
        mediaPlayer.setOnSeekCompleteListener(this)
        mediaPlayer.setOnInfoListener(this)

        timer.schedule(object : TimerTask() {

            override fun run() {
                progressListener?.onProgressChanged(activeAudio!!, mediaPlayer.duration, mediaPlayer.currentPosition)
            }
        }, 0, 100)

        mediaPlayer.reset()

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        return mediaPlayer
    }

    private fun updateMediaPlayer(dataSource: String) {
        mediaPlayer.reset()
        try {
            mediaPlayer.setDataSource(dataSource)
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
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

            audioIndex = -1
            intent?.let { audioIndex = it.getIntExtra(ARG_AUDIO_POS, -1) }


            if (audioIndex < 0) {
                audioIndex = StorageUtil(applicationContext).loadAudioIndex()
            }

            if (audioIndex >= 0 && audioIndex < audioList?.size!!) {
                activeAudio = audioList?.get(audioIndex)

                Log.d("mPlayAudio", "playNewAudioReceiver: $audioIndex, ${activeAudio!!.title}")

            } else {
                stopSelf()
            }

            forceSkipped = true
            mediaPlayer.stop()
            updateMediaPlayer(activeAudio?.data!!)
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerPlayAudioReceiver() {
        val intentFilter = IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudioReceiver, intentFilter)
    }

    private fun initMediaSession() {
        if (mediaSessionManager != null) return

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSession = MediaSession(applicationContext, "AudioPlayer")

        transportControls = mediaSession?.controller?.transportControls
        mediaSession?.isActive = true

        updateMetaData()

        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
            }

            override fun onStop() {
                super.onStop()
                removeNotification()
                stopSelf()
            }

            override fun onSkipToNext() {
                skipToNext()
            }

            override fun onSkipToPrevious() {
                skipToPrevious()
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

        Log.d(TAG, "updateMetaData: ${activeAudio?.title}, ${infoListener?.toString()}")
        activeAudio?.let { infoListener?.onInfoChanged(it) }
    }

    fun skipToNext() {

        forceSkipped = true

        Log.d(TAG, "skipToNext: ")

        if (audioIndex == audioList?.size!! - 1) {
            audioIndex = 0
            activeAudio = audioList?.get(audioIndex)
        } else {
            activeAudio = audioList?.get(++audioIndex)
        }

        StorageUtil(applicationContext).storeAudioIndex(audioIndex)

        mediaPlayer.stop()

        updateMediaPlayer(activeAudio?.data!!)
        updateMetaData()
        buildNotification(PlaybackStatus.PLAYING)
    }

    fun skipToPrevious() {
        Log.d(TAG, "skipToPrevious: ");

        forceSkipped = true

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

        mediaPlayer.stop()
        updateMediaPlayer(activeAudio?.data!!)
        updateMetaData()
        buildNotification(PlaybackStatus.PLAYING)
    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {

        val notificationIntent = Intent(this, MainActivity::class.java)

        val intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0)

        val notificationBuilder = Notification.Builder(this)

        var notificationAction = android.R.drawable.ic_media_pause
        var playPauseAction: PendingIntent? = null

        Log.d(TAG, "buildNotification: $playbackStatus")

        if (playbackStatus == PlaybackStatus.PLAYING) {

            notificationAction = android.R.drawable.ic_media_pause
            notificationBuilder.setOngoing(true)
            playPauseAction = playbackAction(1)

        } else if (playbackStatus == PlaybackStatus.PAUSED) {

            notificationAction = android.R.drawable.ic_media_play
            notificationBuilder.setOngoing(false)
            playPauseAction = playbackAction(0)

        }

        var largeIcon = BitmapFactory.decodeResource(resources,
                R.drawable.ic_launcher_foreground) //replace with your own image

        // Create a new Notification

        notificationBuilder.setShowWhen(false)
                .setContentIntent(intent)
                .setStyle(Notification.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mediaSession?.sessionToken)
                        // Show our playback controls in the compact notification view.
                        .setShowActionsInCompactView(0, 1, 2))
                // Set the Notification color
                .setColor(resources.getColor(R.color.colorPrimary))
                // Set the large and small icons
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

    public fun getMediaPlayer(): MediaPlayer? {
        return mediaPlayer
    }
}

interface AudioProgressListener {
    fun onProgressChanged(audio: Audio, length: Int, progress: Int)
}

interface AudioInfoListener {
    fun onInfoChanged(audio: Audio)
}

