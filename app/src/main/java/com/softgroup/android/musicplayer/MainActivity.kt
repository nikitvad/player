package com.softgroup.android.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import com.softgroup.android.musicplayer.data.Audio
import com.softgroup.android.musicplayer.services.MediaPlayerService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var playerService: MediaPlayerService
    private var serviceBounded = false

    private var audioList: ArrayList<Audio>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadAudio()

        if(audioList?.size!! > 0){

            Log.d("sdfsdf", audioList?.toString())

            playAudio(audioList?.get(0)?.data!!)
        }

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

    private fun playAudio(media: String) {
        if (!serviceBounded) {
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            playerIntent.putExtra("media", media)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        playerService.stopSelf()

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

        if(cursor!=null && cursor.count > 0){
            audioList = ArrayList()

            while (cursor.moveToNext()){
                val data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))

                audioList?.add(Audio(data, title, album, artist))

            }

        }

        cursor?.close()

    }
}
