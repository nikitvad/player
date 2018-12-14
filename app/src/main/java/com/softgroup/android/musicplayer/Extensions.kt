package com.softgroup.android.musicplayer

import android.content.Context
import android.widget.Toast

fun Context.shortToast(a:Any){
    Toast.makeText(this, a.toString(), Toast.LENGTH_SHORT).show()
}

fun Context.longToast(a:Any){
    Toast.makeText(this, a.toString(), Toast.LENGTH_LONG).show()
}