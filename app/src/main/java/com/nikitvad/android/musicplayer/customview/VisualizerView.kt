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

    private lateinit var mBytes: ByteArray
    private var mPoints: FloatArray? = null
    private val mForePaint = Paint()
    private val whitePaint = Paint()
    private val path = Path()
    private val timer = Timer()

    private var canvasRotation = 0

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
        mForePaint.strokeWidth = 8f
        mForePaint.strokeCap = Paint.Cap.ROUND
        mForePaint.isAntiAlias = true
        mForePaint.style = Paint.Style.STROKE
        mForePaint.color = Color.rgb(207, 111, 85)
        mBytes = ByteArray(512) { 0.toByte() }

        whitePaint.style = Paint.Style.FILL
        whitePaint.color = Color.rgb(255, 255, 255)

        timer.scheduleAtFixedRate(timerTask, 0, 33)

//        path.fillType = Path.FillType.INVERSE_WINDING
    }

    fun updateVisualizer(bytes: ByteArray) {
        mergeVisualizationData(bytes)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mPoints == null || mPoints!!.size < mBytes.size * 2) {
            mPoints = FloatArray(mBytes.size * 4)
        }

        val angelOffset = (mBytes.size / 6.28319)

        for (i in 0 until mBytes.size - 1) {

            val value = mBytes[i].toInt()

            val x = Math.sin(angelOffset * i) * ((128 - value) * 1.7)
            val y = Math.cos(angelOffset * i) * ((128 - value) * 1.7)

            mPoints!![i * 2] = x.toFloat() + width / 2
            mPoints!![i * 2 + 1] = y.toFloat() + height / 2

        }
        canvas.rotate(canvasRotation.toFloat(), width / 2.toFloat(), height / 2.toFloat())
        canvas.drawPoints(mPoints, mForePaint)
    }

    private val timerTask =

            object : TimerTask() {
                override fun run() {
                    updateVisualizationData()
                    this@VisualizerView.post { invalidate() }
                }
            }

    @Synchronized
    private fun updateVisualizationData() {
        for (i in 0 until mBytes.size - 1) {

            val b = Math.abs(mBytes[i].toInt())

            if (b < 127) {
                val dec = ((mBytes[i] * 1.02) + 3).toInt()
                mBytes[i] = if (dec in 0..127) dec.toByte() else 127
            }
        }

        if (canvasRotation < 359) {
            canvasRotation+=2
        } else {
            canvasRotation = 0
        }
    }


    @Synchronized
    private fun mergeVisualizationData(bytes: ByteArray) {
        val minCount = if (bytes.size < mBytes.size) bytes.size else mBytes.size
        for (i in 0 until minCount) {
            val b = Math.abs(bytes[i].toInt())
            if (mBytes[i] > b) mBytes[i] = b.toByte()
        }


    }
}