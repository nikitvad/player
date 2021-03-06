package com.nikitvad.android.musicplayer.ui

import android.content.*
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.bumptech.glide.Glide
import com.nikitvad.android.musicplayer.R
import com.nikitvad.android.musicplayer.data.Audio
import com.nikitvad.android.musicplayer.services.AudioInfoListener
import com.nikitvad.android.musicplayer.services.AudioProgressListener
import com.nikitvad.android.musicplayer.services.MediaPlayerService
import com.nikitvad.android.musicplayer.shortToast
import com.nikitvad.android.musicplayer.utils.StorageUtil
import kotlinx.android.synthetic.main.activity_main.*
import android.media.audiofx.Visualizer
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.SeekBar
import com.nikitvad.android.musicplayer.BR
import com.nikitvad.android.musicplayer.databinding.ItemAudioBinding
import com.nikitvad.android.musicplayer.utils.AppOnItemClickListener
import com.nikitvad.android.musicplayer.utils.AppRecyclerAdapter
import java.nio.file.Files.size


class MainActivity : AppCompatActivity(), AudioInfoListener {

    companion object {
        const val BROADCAST_PLAY_NEW_AUDIO = "com.nikitvad.android.musicplayer"
        const val BROADCAST_PLAY_PAUSE = "com.nikitvad.android.musicplayer.play_pause"
    }

    private val TAG = "MainActivity"
    private val CODE_PERMISSION_REQUEST = 200

    private var playerService: MediaPlayerService? = null
    private var serviceBounded = false

    private var audioList: ArrayList<Audio>? = null

    private var ignoreAudioProgress = false

    private val permissions = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.RECORD_AUDIO)


    private lateinit var mediaSession: MediaSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (isPermissionGranted()) {
            initPlayer()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions, CODE_PERMISSION_REQUEST)
            }
        }

    }

    private fun initPlayer() {
        loadAudio()
        mediaSession = MediaSession(applicationContext, "AudioPlayer")

        prev.setOnClickListener {
            playerService?.skipToPrevious()
        }

        next.setOnClickListener {
            playerService?.skipToNext()
        }

        play.setOnClickListener { playPause() }


        playAudio(0)

        val playerIntent = Intent(this, MediaPlayerService::class.java)
        startService(playerIntent)
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)


        playProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var seekTo: Int = -1
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekTo = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                ignoreAudioProgress = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekTo > -1) {
                    playerService?.seekTo(seekTo)
                }
                ignoreAudioProgress = false
            }
        })

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CODE_PERMISSION_REQUEST) {
            var granted = true
            grantResults.forEach {
                if (it != PackageManager.PERMISSION_GRANTED) {
                    granted = false
                }
            }

            if (granted) {
                initPlayer()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(permissions, requestCode)
                }
            }

        }
    }

    override fun onInfoChanged(audio: Audio) {
        soundName.text = audio.title
        performer.text = audio.artist

        Glide.with(this).load(audio.albumArt).into(image)

        adapter.selectItem(audio)
    }

    override fun onStart() {
        super.onStart()
        playerService?.progressListener = audioProgressListener
        playerService?.infoListener = this
    }

    override fun onStop() {
        super.onStop()
        playerService?.progressListener = null
        playerService?.infoListener = null
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            shortToast("Service Disconnected")
            Log.d(TAG, ": ")
            serviceBounded = false
            mVisualizer?.enabled = false
            mVisualizer?.release()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            playerService = binder.getService()
            serviceBounded = true
            playerService?.progressListener = audioProgressListener
            playerService?.infoListener = this@MainActivity

            setupVisualizerFxAndUI()

        }
    }


    private fun playAudio(audioPos: Int) {
        if (!serviceBounded) {

            val storageUtil = StorageUtil(this)
            storageUtil.storeAudio(audioList!!)
            storageUtil.storeAudioIndex(audioPos)

        } else {
            val storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(audioPos)

            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            val broadcastIntent = Intent(BROADCAST_PLAY_NEW_AUDIO)
            sendBroadcast(broadcastIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
//        playerService.stopSelf()

    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putBoolean("ServiceState", serviceBounded)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBounded = savedInstanceState?.getBoolean("ServiceState")!!
    }

    private fun loadAudio() {

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!=0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val audioCursor = contentResolver.query(uri, null, selection, null, sortOrder)

        if (audioCursor != null && audioCursor.count > 0) {
            audioList = ArrayList()

            while (audioCursor.moveToNext()) {
                val data = audioCursor.getString(audioCursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title = audioCursor.getString(audioCursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album = audioCursor.getString(audioCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist = audioCursor.getString(audioCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                val albumId = audioCursor.getString(audioCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                var albumArt: String? = ""


                val cursor = contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART),
                        MediaStore.Audio.Albums._ID + "=?",
                        arrayOf(albumId),
                        null)

                if (cursor.moveToFirst()) {
                    var columnIndex: Int? = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
                    albumArt = columnIndex?.let { cursor.getString(it) }
                    Log.d(TAG, "loadAudio: $albumArt")
                    // do whatever you need to do
                }

                cursor.close()
                audioList?.add(Audio(data, title, album, artist, albumId, albumArt))


            }

        }

        audioCursor?.close()

        adapter.setItems(audioList!!)
        musicList.adapter = adapter
        musicList.layoutManager = LinearLayoutManager(this)

    }

    private fun playPause() {
        val broadcastIntent = Intent(BROADCAST_PLAY_PAUSE)

        sendBroadcast(broadcastIntent)
    }

    private val audioProgressListener = object : AudioProgressListener {
        override fun onProgressChanged(audio: Audio, length: Int, progress: Int) {
            if (!ignoreAudioProgress) {
                playProgress.max = length - 10
                playProgress.progress = progress
            }

        }
    }

    private var mVisualizer: Visualizer? = null

    private fun setupVisualizerFxAndUI() {
        mVisualizer = Visualizer(playerService?.getMediaPlayer()?.audioSessionId!!)
        mVisualizer?.captureSize = Visualizer.getCaptureSizeRange()[1]
        mVisualizer?.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
        mVisualizer?.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer,
                                                       bytes: ByteArray, samplingRate: Int) {

                        this@MainActivity.visualizer.updateVisualizer(bytes)
                    }

                    override fun onFftDataCapture(visualizer: Visualizer,
                                                  bytes: ByteArray, samplingRate: Int) {

                    }
                }, (Visualizer.getMaxCaptureRate()), true, false)
        mVisualizer?.enabled = true
    }

    private fun isPermissionGranted(): Boolean {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    val adapter: AppRecyclerAdapter<ItemAudioBinding, Audio> = AppRecyclerAdapter<ItemAudioBinding, Audio>(R.layout.item_audio, BR.audio,
            object : AppOnItemClickListener<Audio> {
                override fun onItemClick(view: View, item: Audio) {
                    playAudio(item)
                }

            })

    fun playAudio(audio: Audio){

        val broadcastIntent = Intent(BROADCAST_PLAY_NEW_AUDIO)
        val pos: Int = audioList!!.indexOf(audio)

        broadcastIntent.putExtra(MediaPlayerService.ARG_AUDIO_POS, pos)
        sendBroadcast(broadcastIntent)


    }
}
