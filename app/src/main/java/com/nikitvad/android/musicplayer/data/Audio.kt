package com.nikitvad.android.musicplayer.data

import java.io.Serializable

data class Audio(val data: String = "",
                 val title: String = "",
                 val album: String = "",
                 val artist: String = "",
                 val albumId: String = "",
                 val albumArt: String = "https://images.unian.net/photos/2018_09/1535787337-4187.jpg?0.7018774183585941") : Serializable