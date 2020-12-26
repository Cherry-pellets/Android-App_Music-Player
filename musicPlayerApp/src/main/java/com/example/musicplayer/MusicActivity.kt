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
import android.graphics.Paint
import android.view.animation.AnimationUtils
import java.lang.Exception
import java.util.*

class MusicActivity : Activity(), View.OnClickListener{
    //    歌曲列表
    var  songList:List<Album>? = null
    var path: String = ""
    var lrcIndex:Int = 0
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
    lateinit var lrcInfo:LrcInfo
    lateinit var lrcLists: ArrayList<LrcList>
    lateinit var lrcView : LrcView

    private val TAG = "MusicActivity"
    private val NOTIFICATION_BTN_CHANGE = "button in notification has been clicked"
    private val UPDATE_ACTION = "UPDATE_ACTION"  //更新动作
    private val CTL_ACTION = "CTL_ACTION"        //控制动作
    private val MUSIC_CURRENT = "MUSIC_CURRENT"  //音乐当前时间改变动作
    private val MUSIC_DURATION = "MUSIC_DURATION"//音乐播放长度改变动作
    private val MUSIC_PLAYING = "MUSIC_PLAYING"  //音乐正在播放动作
    private val REPEAT_ACTION = "REPEAT_ACTION"  //音乐重复播放动作
    private val SHUFFLE_ACTION = "RANDOM_ACTION"//音乐随机播放动作
    private val PLAY_STATUE = "PLAY_STATUE"      //更新播放状态
    private val INIT_LRC = "init Lrc"
    private  val  UPDATE_LRC = "update lrc"

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
                    val progress = msg.data.getDouble("progress")

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
                INIT_LRC -> {
                    path = intent.getStringExtra("path")!!
                    //建立歌词对象
                    val lrcParser = LrcParse("$path")
                    //读歌词，并将数据传给歌词信息类
                    lrcInfo = lrcParser.readLrc()
                    //获得歌词中的结点
                    lrcLists = lrcInfo.lrcLists
                    lrcView.setmLrcList(lrcInfo)
//                    drawLrc()
                }
                UPDATE_LRC -> {
                    lrcIndex = intent.getIntExtra("lrcIndex", 0)
                    lrcView.setIndex(lrcIndex)
                    lrcView.invalidate()
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)
        initView()

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
        filter.addAction(INIT_LRC)
        filter.addAction(UPDATE_LRC)
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
            }
            override fun onProgressChanged(arg0: SeekBar, arg1: Int, arg2: Boolean) {
            }
            override fun onStartTrackingTouch(arg0: SeekBar) {
            }
        })
        playMusic()
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
        lrcView = findViewById(R.id.lrcView)
    }

    fun drawLrc() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.lrc_anim)
        lrcView.startAnimation(animation)
    }
}
