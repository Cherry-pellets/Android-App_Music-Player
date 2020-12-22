package com.example.musicplayer

import android.os.IBinder
import android.os.Bundle
import android.widget.SeekBar
import android.app.Activity
import android.content.*
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.ImageView
import android.widget.TextView

class MusicActivity : Activity() {
     var MusicPlay: ImageView? = null
    lateinit var lrcView : LrcView

    private val TAG = "MusicActivity"
    private val NOTIFICATION_BTN_CHANGE = "button in notification has been clicked"

    var mBound: Boolean? = false
    //记录鼠标点击了几次
    var flag = false
    lateinit var mService: MusicService
    internal lateinit var seekBar: SeekBar
    //多线程，后台更新UI
    internal lateinit var myThread: Thread
    //控制后台线程退出
    internal var playStatus = true
    //处理进度条更新
    internal var mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                0 -> {
                    //从bundle中获取进度，是double类型，播放的百分比
                    val progress = msg.getData().getDouble("progress")

                    //根据播放百分比，计算seekbar的实际位置
                    val max = seekBar.max
                    val position = (max * progress).toInt()
                    //设置seekbar的实际位置
                    seekBar.progress = position
                }
                1 -> {
                    handlePlayMusic()
                }
            }

        }
    }
//    通过bindService与service通信
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val myBinder = binder as MusicService.MyBinder
            //获取service
            mService = myBinder.service as MusicService
            //绑定成功
            mBound = true
            //开启线程，更新UI
            myThread.start()
            MusicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play))
            mService.play()
            flag = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val musicReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                NOTIFICATION_BTN_CHANGE -> {
                    val msg = Message()
                    msg.what = 1
                    mHandler.sendMessage(msg)
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)

        Log.d(TAG, "musicActivity onCreate")
//        播放按钮
        MusicPlay = findViewById(R.id.Musicplay)
//        获取歌词布局
        lrcView = findViewById(R.id.lrcView)
        Log.d(TAG, "init lrcView ---- ${lrcView.text}")
//        显示歌手和歌曲名
        val musicTilte: TextView = findViewById(R.id.music_title)
        val musicArtist: TextView = findViewById(R.id.music_artist)
        musicTilte.text = intent.getStringExtra("title")
        musicArtist.text = intent.getStringExtra("artist")

        //定义一个新线程，用来发送消息，通知更新UI
        myThread = Thread(UpdateProgress())
        //绑定service;
        val serviceIntent = Intent(this@MusicActivity, MusicService::class.java)
        //如果未绑定，则进行绑定,第三个参数是一个标志，它表明绑定中的操作．它一般应是BIND_AUTO_CREATE，这样就会在service不存在时创建一个
        if ((!mBound!!)!!) {
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE)
        }
        seekBar = findViewById(R.id.MusicProgress) as SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //手动调节进度
                // TODO Auto-generated method stub
                //seekbar的拖动位置
                val dest = seekBar.progress
                //seekbar的最大值
                val max = seekBar.max
                //调用service调节播放进度
                mService.setProgress(max, dest)
            }
            override fun onProgressChanged(arg0: SeekBar, arg1: Int, arg2: Boolean) {
                // TODO Auto-generated method stub

            }
            override fun onStartTrackingTouch(arg0: SeekBar) {
                // TODO Auto-generated method stub
            }
        })

        MusicPlay!!.setOnClickListener{
            handlePlayMusic()
        }

//        接收Service中关于通知栏按钮点击的广播
        val filter = IntentFilter()
        filter.addAction(NOTIFICATION_BTN_CHANGE)
        registerReceiver(musicReceiver, filter)
    }

    //实现runnable接口，多线程实时更新进度条
    inner class UpdateProgress : Runnable {
        //通知UI更新的消息
        //用来向UI线程传递进度的值
        internal var data = Bundle()
        //更新UI间隔时间
        internal var milliseconds = 100
        internal var progress: Double = 0.toDouble()
        override fun run() {
            // TODO Auto-generated method stub
            //用来标识是否还在播放状态，用来控制线程退出
            while (playStatus) {
                try {
                    //绑定成功才能开始更新UI
                    if (mBound!!) {
                        //发送消息，要求更新UI
                        val msg = Message()
                        data.clear()
                        progress = mService.progress
                        msg.what = 0
                        data.putDouble("progress", progress)
                        msg.setData(data)
                        mHandler.sendMessage(msg)
                    }
                    Thread.sleep(milliseconds.toLong())
                    //Thread.currentThread().sleep(milliseconds);
                    //每隔100ms更新一次UI
                } catch (e: InterruptedException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

            }
        }
    }

//    fun onCreateOptionsMenu(menu: Menu): Boolean {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        //      getMenuInflater().inflate(R.menu.main, menu);
//        return true
//    }

    public override fun onDestroy() {
        //销毁activity时，要记得销毁线程
        playStatus = false
        unregisterReceiver(musicReceiver)
        super.onDestroy()
    }
     fun handlePlayMusic() {
        Log.d(TAG, "handle music start")
        if (mBound!! && flag) {
            MusicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.pause))
            mService.pause()
            flag = false
        } else {
            MusicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play))
            mService.play()
            flag = true
        }
        mService.updateNotification()
        Log.d(TAG, "handle music end")
    }
}
