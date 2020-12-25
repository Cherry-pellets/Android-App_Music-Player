package com.example.musicplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView

class LrcView : TextView {
    private var width: Float = 0.toFloat()                   //歌词视图宽度
    private var height: Float = 0.toFloat()                 //歌词视图高度
    private var currentPaint: Paint? = null          //当前画笔对象
    private var notCurrentPaint: Paint? = null      //非当前画笔对象
    private val textHeight = 65f      //文本高度
    private val textMaxSize = 50f
    private val fontSize = 40f        //文本大小
    private var index = 0              //list集合下标
    private var infos: LrcInfo? = null              //歌词信息
    private val TAG = "LrcView"

    fun setmLrcList(infos: LrcInfo) {
        this.infos = infos
    }
    constructor(context: Context) : super(context) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        isFocusable = true     //设置可对焦
        //显示歌词部分
        currentPaint = Paint()
        currentPaint!!.isAntiAlias = true    //设置抗锯齿，让文字美观饱满
        currentPaint!!.textAlign = Paint.Align.CENTER//设置文本对齐方式
        //非高亮部分
        notCurrentPaint = Paint()
        notCurrentPaint!!.isAntiAlias = true
        notCurrentPaint!!.textAlign = Paint.Align.CENTER
    }

    //    绘画歌词
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) {
            return
        }

        currentPaint!!.color = Color.argb(210, 251, 248, 29)
        notCurrentPaint!!.color = Color.argb(140, 255, 255, 255)

        currentPaint!!.textSize = textMaxSize
        currentPaint!!.typeface = Typeface.SERIF

        notCurrentPaint!!.textSize = fontSize
        notCurrentPaint!!.typeface = Typeface.DEFAULT

        try {
            text = ""
            canvas.drawText(
                infos!!.lrcLists[index].content!!,
                width / 2,
                height / 2,
                currentPaint!!
            )
            var tempY = height / 2
            //画出本句之前的句子
            for (i in index - 1 downTo 0) {
                //向上推移
                tempY = tempY - textHeight
                canvas.drawText(infos!!.lrcLists[i].content!!, width / 2, tempY, notCurrentPaint!!)
            }
            tempY = height / 2
            //画出本句之后的句子
            for (i in index + 1 until infos!!.lrcLists.size) {
                //往下推移
                tempY = tempY + textHeight
                canvas.drawText(infos!!.lrcLists[i].content!!, width / 2, tempY, notCurrentPaint!!)
            }
        } catch (e: Exception) {
            text = "暂时没有歌词......"
            Log.d(TAG, "--------- $e")
        }
    }

    //    当view大小改变时
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.width = w.toFloat()
        this.height = h.toFloat()
    }

    fun setIndex(index: Int) {
        this.index = index
    }
}
