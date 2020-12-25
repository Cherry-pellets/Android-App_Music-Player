package com.example.musicplayer

import android.os.IBinder
import android.os.Bundle
import android.widget.SeekBar
import android.app.Activity
import android.content.*
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.content.Intent
import android.os.Build
import android.annotation.TargetApi
import java.lang.Exception
import java.util.*

class MusicActivity : Activity(), View.OnClickListener{
    //    歌曲列表
    var  songList:List<Album>? = null
    //    播放界面的按钮组合
    var musicPlay: ImageView? = null
    var musicPrevious: ImageView? = null
    var musicNext: ImageView? = null
    var playMode: ImageView? = null
    var playMenu: ImageView? = null
    private val isOrderPlay = 0//顺序播放
    private val isListLoop = 1//表示列表循环
    private val isRepeatone = 2//单曲循环
    private val isRandomPlay = 3//随机
    private var playModeFlag = 0 // 默认顺序播放
    private var isPlaying = true
    private var isPause = false

    //    进度条和时间
    internal lateinit var seekBar: SeekBar
    var currentTimeImg:TextView? = null
    var totalTitmeImg:TextView? = null
    var position = 0
    var currentTime = 0
    var totalTime = 0
    //    需要显示的歌曲名和歌手名
    lateinit var musicTitle: TextView
    lateinit var musicArtist: TextView
    //    自定义的歌词布局
    lateinit var lrcView : LrcView

    private val TAG = "MusicActivity"
    private val NOTIFICATION_BTN_CHANGE = "button in notification has been clicked"
    val UPDATE_ACTION = "UPDATE_ACTION"  //更新动作
    val CTL_ACTION = "CTL_ACTION"        //控制动作
    val MUSIC_CURRENT = "MUSIC_CURRENT"  //音乐当前时间改变动作
    val MUSIC_DURATION = "MUSIC_DURATION"//音乐播放长度改变动作
    val MUSIC_PLAYING = "MUSIC_PLAYING"  //音乐正在播放动作
    val REPEAT_ACTION = "REPEAT_ACTION"  //音乐重复播放动作
    val SHUFFLE_ACTION = "RANDOM_ACTION"//音乐随机播放动作
    val PLAY_STATUE = "PLAY_STATUE"      //更新播放状态

    var mBound: Boolean? = false
    //    记录鼠标点击了几次
    var flag = false
    lateinit var mService: MusicService

    //   多线程，后台更新UI
    internal lateinit var myThread: Thread
    //   控制后台线程退出
    internal var playStatus = true
    //   0处理进度条更新、1处理通知栏发来的按钮点击信息
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
                999 -> {
                    handlePlayMusic()
                }
            }

        }
    }
    //   通过bindService与service通信
/*    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val myBinder = binder as MusicService.MyBinder
            //获取service
            mService = myBinder.service as MusicService
            //绑定成功
            mBound = true
            //开启线程，更新UI
            myThread.start()
            musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play))
            mService.play()
            flag = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }*/
//   接收广播，更新播放页面的播放状态
    private val musicReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                NOTIFICATION_BTN_CHANGE -> {
                    val msg = Message()
                    msg.what = 999
                    mHandler.sendMessage(msg)
                }
                MUSIC_CURRENT -> {
                    // 当前时间更新
                    currentTime = intent.getIntExtra("currentTime", -1)
                    seekBar.progress = currentTime
                    currentTimeImg?.text = formatDuring(currentTime.toLong())
                }
                MUSIC_DURATION -> {
                    //总时间更新
                    totalTime = intent.getIntExtra("duration", -1)
                    seekBar.max = totalTime
                    totalTitmeImg?.text = formatDuring(totalTime.toLong())
                }
                UPDATE_ACTION -> {
                    position = intent.getIntExtra("position", -1)
                    val url = songList!![position].url
                    musicTitle.text = songList!![position].title
                    musicArtist.text = songList!![position].artist
                    totalTitmeImg?.text = formatDuring( songList!![position].duration)
                }
                PLAY_STATUE -> {
                    val playstatue = intent.getBooleanExtra("playstatue", true)
                    if (playstatue) {
                        musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play))
                        isPlaying = true
                    } else {
                        musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.pause))
                        isPlaying = false
                    }
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)
        initView()

        Log.d(TAG, "music activity onCreate")
        position = intent.getIntExtra("position", -1)
        val albumList = intent.getSerializableExtra("songList") as AlbumList
        songList = albumList.albumList
        Log.d(TAG, songList!![0].artist)
        try {
            musicArtist.text = songList!![position].artist
            musicTitle.text = songList!![position].title
        } catch (e: Exception) {
            Log.d(TAG, "------ $e")
        }
//        注册广播
        val filter = IntentFilter()
        filter.addAction(NOTIFICATION_BTN_CHANGE)
        filter.addAction(UPDATE_ACTION)
        filter.addAction(MUSIC_CURRENT)
        filter.addAction(MUSIC_DURATION)
        filter.addAction(PLAY_STATUE)
        registerReceiver(musicReceiver, filter)

        //设置响应事件
        musicNext!!.setOnClickListener(this)
        musicPrevious!!.setOnClickListener(this)
        playMenu!!.setOnClickListener(this)
        playMode!!.setOnClickListener(this)
        musicPlay!!.setOnClickListener(this)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //手动调节进度
                //seekbar的拖动位置
                val dest = seekBar.progress
                changeProgress(dest)
                //seekbar的最大值
//                val max = seekBar.max
                //调用service调节播放进度
//                mService.setProgress(max, dest)
            }
            override fun onProgressChanged(arg0: SeekBar, arg1: Int, arg2: Boolean) {
            }
            override fun onStartTrackingTouch(arg0: SeekBar) {
            }
        })
        playMusic()
//        musicPlay!!.setOnClickListener{
//            handlePlayMusic()
//        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onClick(v: View) {
        val intent = Intent(this, MusicService::class.java)
        val bundle = Bundle()
        bundle.putSerializable("songList",AlbumList(songList!!))
        intent.putExtras(bundle)
        intent.putExtra("position", position)
        when (v.id) {
            R.id.musicPlay -> {
                if (isPlaying) {
                    musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.pause))
                    intent.putExtra("MSG", PlayerMSG.MSG.PAUSE_MSG)
                    startService(intent)
                    isPlaying = false
                } else {
                    musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play))
                    intent.putExtra("MSG", PlayerMSG.MSG.CONTINUE_MSG)
                    startService(intent)
                    isPlaying = true
                }
            }
            R.id.playMode -> setPlayMOde()
            R.id.musicNext -> playNextMusic()
            R.id.musicPrevious -> playPreviousMusic()
            R.id.musicMenu -> {
            }
            else -> {
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun setPlayMOde() {
        playModeFlag++
        val intent = Intent(CTL_ACTION)
        intent.putExtra("control", playModeFlag)
        sendBroadcast(intent)
        when (playModeFlag) {
            isOrderPlay -> playMode!!.setImageDrawable(resources.getDrawable(R.drawable.orderplay))
            isListLoop -> playMode!!.setImageDrawable(resources.getDrawable(R.drawable.recyclemode))
            isRepeatone -> playMode!!.setImageDrawable(resources.getDrawable(R.drawable.singleplay))
            isRandomPlay -> {
                playMode!!.setImageDrawable(resources.getDrawable(R.drawable.randomplay))
                playModeFlag = -1
            }
            else -> {
            }
        }

    }
    //播放音乐
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun playMusic() {
        isPlaying = true
        musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play, null))
        // 开始播放的时候为顺序播放
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("url", songList!![position].url)
        intent.putExtra("position", position)
        intent.putExtra("MSG", PlayerMSG.MSG.PLAY_MSG)
//        MusicService().songList = songList!!
        val bundle = Bundle()
        bundle.putSerializable("songList",AlbumList(songList!!))
        intent.putExtras(bundle)
        startService(intent)
    }
    fun changeProgress(progress: Int) {
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("url", songList!![position].url)
        intent.putExtra("position", position)
        intent.putExtra("MSG", PlayerMSG.MSG.PROGRESS_CHANGE)
        intent.putExtra("progress", progress)
        val bundle = Bundle()
        bundle.putSerializable("songList",AlbumList(songList!!))
        intent.putExtras(bundle)
        intent.putExtra("position", position)
        startService(intent)
    }
    //播放上一首音乐
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun playPreviousMusic() {
        position -= 1
        //   musicPlay.setImageDrawable(getResources().getDrawable(R.drawable.musicpause,null));

        if (position < 0) {
            position = songList!!.size - 1
        }
        val mp3Info = songList!![position]
        musicTitle.text = mp3Info.title
        musicArtist.text = mp3Info.artist
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("url", mp3Info.url)
        intent.putExtra("position", position)
        intent.putExtra("MSG", PlayerMSG.MSG.PRIVIOUS_MSG)
        val bundle = Bundle()
        bundle.putSerializable("songList",AlbumList(songList!!))
        intent.putExtras(bundle)
        startService(intent)
        isPlaying = true
        musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play, null))
    }

    //播放下一首音乐
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun playNextMusic() {
        //判断是否是随机播放，因为随机播放设置后，playmodeflag变为-1了
        if (playModeFlag === -1) {
            val random = Random()
            position = random.nextInt(songList!!.size)
        } else
            position += 1
        if (position >= songList!!.size)
            position = 0

        val mp3Info = songList!![position]
        musicTitle.text = mp3Info.title
        musicArtist.text = mp3Info.artist
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("url", mp3Info.url)
        intent.putExtra("position", position)
        intent.putExtra("MSG", PlayerMSG.MSG.NEXT_MSG)
        val bundle = Bundle()
        bundle.putSerializable("songList",AlbumList(songList!!))
        intent.putExtras(bundle)
        startService(intent)
        musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play, null))
        isPlaying = true

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
        val intent = Intent(this, MusicService::class.java)
        val bundle = Bundle()
        bundle.putSerializable("songList",AlbumList(songList!!))
        intent.putExtras(bundle)
        intent.putExtra("position", position)
        try {
            if (isPlaying) {
                musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.pause))
                intent.putExtra("MSG", PlayerMSG.MSG.PAUSE_MSG)
                startService(intent)
                isPlaying = false
            } else {
                musicPlay!!.setImageDrawable(resources.getDrawable(R.drawable.play))
                intent.putExtra("MSG", PlayerMSG.MSG.CONTINUE_MSG)
                startService(intent)
                isPlaying = true
            }
        } catch (e:Exception) {
            Log.d(TAG, "--- handle music play --- $e")
        }
    }

    //   转换毫秒数为时间模式，一般都是分钟数，音乐文件
    fun formatDuring(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        return String.format("%02d", minutes) + ":" + String.format("%02d", seconds)
    }
    //初始化控件
    fun initView() {
        musicPlay = findViewById(R.id.musicPlay)
        musicNext = findViewById(R.id.musicNext)
        musicPrevious = findViewById(R.id.musicPrevious)
        playMenu = findViewById(R.id.musicMenu)
        playMode = findViewById(R.id.playMode)
        totalTitmeImg = findViewById(R.id.totalTime)
        currentTimeImg = findViewById(R.id.currentTime)
        seekBar = findViewById(R.id.MusicProgress)
        musicTitle = findViewById(R.id.music_title)
        musicArtist = findViewById(R.id.music_artist)
    }
}
