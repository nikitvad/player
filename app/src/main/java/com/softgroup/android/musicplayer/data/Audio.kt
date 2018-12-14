package com.softgroup.android.musicplayer.data

import java.io.Serializable

data class Audio(val data: String = "",
                 val title: String = "",
                 val album: String = "",
                 val artist: String = "") : Serializable