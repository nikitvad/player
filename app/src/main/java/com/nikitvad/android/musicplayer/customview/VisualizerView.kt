package com.nikitvad.android.musicplayer.customview

import android.content.Context
import android.graphics.*
import android.opengl.ETC1.getHeight
import android.opengl.ETC1.getWidth
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.util.*


class VisualizerView : View {

    private val TAG = VisualizerView::class.java.simpleName

    private var mBytes: ByteArray? = null
    private var mPoints: FloatArray? = null
    private val mForePaint = Paint()
    private val path = Path()


    private var radius: Float = 30f

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        mBytes = null
        mForePaint.strokeWidth = 2f
        mForePaint.strokeCap = Paint.Cap.ROUND
        mForePaint.isAntiAlias = true
        mForePaint.style = Paint.Style.STROKE
        mForePaint.color = Color.rgb(207, 111, 85)

//        path.fillType = Path.FillType.INVERSE_WINDING
    }

    fun updateVisualizer(bytes: ByteArray) {
        mBytes = bytes

        invalidate()
    }

//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//        if (mBytes == null) {
//            return
//        }
//        if (mPoints == null || mPoints!!.size < mBytes!!.size * 4) {
//            mPoints = FloatArray(mBytes!!.size * 4)
//        }
//        mRect.set(0, 0, getWidth(), getHeight())
//
//        for (i in 0 until mBytes!!.size - 4 ) {
//
//
//            mPoints!![i * 4] = (mRect.width() * i / (mBytes!!.size - 1)).toFloat()
//            mPoints!![i * 4 + 1] = (mRect.height() / 2 + (mBytes!![i]) * (mRect.height() / 2) / 128).toFloat()
//
//            mPoints!![i * 4 + 2] = (mRect.width() * (i + 1) / (mBytes!!.size - 1)).toFloat()
//            mPoints!![i * 4 + 3] = (mRect.height() / 2 + (mBytes!![i + 1]) * (mRect.height() / 2) / 128).toFloat()
//        }
//        canvas.drawLines(mPoints, mForePaint)
//    }

    //    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//        if (mBytes == null) {
//            return
//        }
//        if (mPoints == null || mPoints!!.size < mBytes!!.size * 2) {
//            mPoints = FloatArray(mBytes!!.size * 4)
//        }
//
//        val angelOffset = (mBytes!!.size / 6.28319)/2
//
//        for (i in 0 until mBytes!!.size - 1) {
//
//            val value = Math.abs(mBytes!![i].toInt())
//
//            val x = Math.sin(angelOffset*i) * (30 + (128 -value) * 0.9)
//            val y = Math.cos(angelOffset*i) * (30 + (128 -value) * 0.9)
//
//            mPoints!![i*2] = x.toFloat() + width/2
//            mPoints!![i*2 + 1] = y.toFloat() + height/2
//
//        }
//        canvas.drawPoints(mPoints, mForePaint)
//    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBytes == null) {
            return
        }
        if (mPoints == null || mPoints!!.size < mBytes!!.size * 2) {
            mPoints = FloatArray(mBytes!!.size * 4)
        }

        val angelOffset = ((mBytes!!.size / 10) / 6.28319) / 10

        path.reset()
        path.moveTo(width / 2.toFloat(), height / 2.toFloat() + 30)
        for (i in 0 until mBytes!!.size / 10 - 2 step 2) {

            val value = Math.abs(mBytes!![i].toInt())

            val x = Math.sin(angelOffset * i) * (30 + (128 - value) * 0.9)
            val y = Math.cos(angelOffset * i) * (30 + (128 - value) * 0.9)

            val value2 = Math.abs(mBytes!![i+1].toInt())

            val x2 = Math.sin(angelOffset * i+1) * (30 + (128 - value2) * 0.9)
            val y2 = Math.cos(angelOffset * i+1) * (30 + (128 - value2) * 0.9)


            path.quadTo(x.toFloat() + width / 2, y.toFloat() + height / 2,x2.toFloat() + width / 2, y2.toFloat() + height / 2)


//            mPoints!![i * 2] = x.toFloat() + width / 2
//            mPoints!![i * 2 + 1] = y.toFloat() + height / 2


        }
//        canvas.drawPoints(mPoints, mForePaint)
        canvas.drawPath(path, mForePaint)
    }
}