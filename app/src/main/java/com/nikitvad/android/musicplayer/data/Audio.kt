package com.nikitvad.android.musicplayer.data

import java.io.Serializable

data class Audio(val data: String = "",
                 val title: String = "",
                 val album: String = "",
                 val artist: String = "",
                 val albumId: String = "",
                 val albumArt: String? = "") : Serializable {

    override fun equals(other: Any?): Boolean {
        if (other == null) return false

        if (other is Audio) {
            return data.equals(other.data)
        } else {
            return false
        }

    }

    override fun hashCode(): Int {
        return data.hashCode()
    }
}