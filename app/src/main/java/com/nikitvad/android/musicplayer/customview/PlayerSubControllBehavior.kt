package com.nikitvad.android.musicplayer.customview

import android.content.Context
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.Toolbar

class PlayerSubControllBehavior(context: Context?, attrs: AttributeSet?) : CoordinatorLayout.Behavior<ImageButton>(context, attrs) {

    private val TAG = "PlayerControllBehavior"

    private var initialHeight = -1
    private var prevDependencyBottom = 0

    override fun layoutDependsOn(parent: CoordinatorLayout?, child: ImageButton?, dependency: View?): Boolean {
        if (dependency is AppBarLayout) {
            if (prevDependencyBottom != dependency.bottom) {
                prevDependencyBottom = dependency.bottom
                return true
            }
        }
        return false
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout?, child: ImageButton?, dependency: View?): Boolean {
        updateView(child, dependency)
        return super.onDependentViewChanged(parent, child, dependency)
        //TODO modify
    }

    private fun updateView(imageButton: ImageButton?, view: View?) {

        if (initialHeight < 0) {
            initialHeight = imageButton?.measuredHeight!!
        }

        Log.d(TAG, "updateView: ${view?.bottom!!}")

//        if (view?.bottom!! < initialHeight) {
//            val layoutParams = imageButton?.layoutParams
//            layoutParams?.height = view.bottom
//            layoutParams?.width = view.bottom
//            imageButton?.layoutParams = layoutParams
//        } else {
//            val layoutParams = imageButton?.layoutParams
//            layoutParams?.height = initialHeight
//            layoutParams?.width = initialHeight
//            imageButton?.layoutParams = layoutParams
//        }

//        if (view.bottom < imageButton?.measuredHeight!!) {
//            val scale = view.bottom.toFloat() / initialHeight
//
//            imageButton.scaleY = scale
//            imageButton.scaleX = scale
//
//        } else if (initialHeight < view.bottom) {
//            imageButton.scaleY = 1f
//            imageButton.scaleX = 1f
//
//        }

        if (view.bottom < imageButton?.measuredHeight!!) {
            val layoutParams = imageButton.layoutParams
            layoutParams?.height = view.bottom
            imageButton.layoutParams = layoutParams
        } else if (view.bottom > imageButton.measuredHeight) {
            if (imageButton.measuredHeight < initialHeight) {
                val layoutParams = imageButton.layoutParams
                val newHeight = if (view.bottom < initialHeight) view.bottom else initialHeight
                layoutParams?.height = newHeight
                imageButton.layoutParams = layoutParams
            }
        }

    }
}