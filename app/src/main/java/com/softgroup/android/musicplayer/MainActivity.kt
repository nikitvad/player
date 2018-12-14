package com.softgroup.android.musicplayer

import android.content.*
import android.media.session.MediaSession
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import com.softgroup.android.musicplayer.data.Audio
import com.softgroup.android.musicplayer.services.MediaPlayerService
import com.softgroup.android.musicplayer.utils.StorageUtil
import kotlinx.android.synthetic.main.activity_main.*;


class MainActivity : AppCompatActivity() {

    companion object {
        const val BROADCAST_PLAY_NEW_AUDIO = "com.softgroup.android.musicplayer"
        const val BROADCAST_PLAY_PAUSE = "com.softgroup.android.musicplayer.play_pause"
    }

    private lateinit var playerService: MediaPlayerService
    private var serviceBounded = false

    private var audioList: ArrayList<Audio>? = null

    private lateinit var mediaSession: MediaSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadAudio()

        mediaSession = MediaSession(applicationContext, "AudioPlayer")


        prev.setOnClickListener {
            val currentPos = StorageUtil(this).loadAudioIndex()
            playAudio(currentPos - 1)
        }

        next.setOnClickListener {
            val currentPos = StorageUtil(this).loadAudioIndex()
            playAudio(currentPos + 1)
        }

        pause_play.setOnClickListener { playPause() }

        playAudio(0)


    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            shortToast("Service Disconnected")
            serviceBounded = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            playerService = binder.getService()
            serviceBounded = true

            shortToast("Service Bounded")

        }
    }

    private fun playAudio(audioPos: Int) {
        if (!serviceBounded) {

            val storageUtil = StorageUtil(this)
            storageUtil.storeAudio(audioList!!)
            storageUtil.storeAudioIndex(audioPos)

            val playerIntent = Intent(this, MediaPlayerService::class.java)
//            playerIntent.putExtra("media", media)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
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
//        unbindService(serviceConnection)
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
        val cursor = contentResolver.query(uri, null, selection, null, sortOrder)

        if (cursor != null && cursor.count > 0) {
            audioList = ArrayList()

            while (cursor.moveToNext()) {
                val data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))

                audioList?.add(Audio(data, title, album, artist))

            }

        }

        cursor?.close()

    }

    private fun playPause() {
        val broadcastIntent = Intent(BROADCAST_PLAY_PAUSE)

        sendBroadcast(broadcastIntent)
    }
}
