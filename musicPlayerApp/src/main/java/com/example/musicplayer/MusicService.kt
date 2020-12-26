package com.example.musicplayer

import android.app.*
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    //    歌曲列表
    var  songList:List<Album>? = null
    internal var path: String? = null // 歌曲的绝对路径
    internal var title: String? = null // 歌曲名
    internal var artist: String? = null // 歌手
    // 歌词信息和列表
    lateinit var lrcInfo:LrcInfo
    lateinit var lrcLists: ArrayList<LrcList>
    var index = 0  // 歌词行数下标
    var duration:Int = 0   // 歌曲时长
    private var currentTime = 0
    private var position = 0
    private var msg: Int = 0     //播放信息-与MusicActivity对应
    private var isPause: Boolean = false
    var palyflag = 0

    internal var musicBinder: IBinder = MyBinder()
    internal var mediaPlayer: MediaPlayer? = null //播放音乐的媒体类

    private lateinit var remoteView: RemoteViews
    private lateinit var notification: Notification
    private lateinit var pi: PendingIntent
    private val notifyId = 1

    private val TAG = "MusicService"
    //服务要发送的一些Action
    val UPDATE_ACTION = "UPDATE_ACTION"  //更新音乐播放曲目
    val CTL_ACTION = "CTL_ACTION"        //控制播放模式
    val MUSIC_CURRENT = "MUSIC_CURRENT"  //当前音乐播放时间更新
    val MUSIC_DURATION = "MUSIC_DURATION"//播放音乐长度更新
    val PLAY_STATUE = "PLAY_STATUE"      //更新播放状态
    private val STATUS_BAR_COVER_CLICK_ACTION = "click button in notification"
    private val NOTIFICATION_BTN_CHANGE = "button in notification has been clicked"
    private val INIT_LRC = "init Lrc"
    private  val  UPDATE_LRC = "update lrc"

    //返回播放进度:百分比
    val progress: Double
        get() {
            val position = mediaPlayer!!.currentPosition
            val time = mediaPlayer!!.duration
            return position.toDouble() / time.toDouble()
        }
    //Binder用来和Activity交互
    internal inner class MyBinder : Binder() {
        val service: Service
            get() = this@MusicService
    }

    private val notificationReceiver= object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "click button in notification")
//                    处理通知栏按钮被点击后的事情
            // 1、发送广播消息给MusicActivity,使其改变播放状态
            val notificationBtnReceiver = Intent()
            notificationBtnReceiver.action = NOTIFICATION_BTN_CHANGE
            sendBroadcast(notificationBtnReceiver)
            // 2、更新通知栏的按钮
            updateNotification()
        }
    }

    //handler用来接收消息，来发送广播更新播放时间
    internal var mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                1 -> {
                    if (mediaPlayer != null) {
                        currentTime = mediaPlayer!!.currentPosition
                        val intent = Intent()
                        intent.action = MUSIC_CURRENT
                        intent.putExtra("currentTime", currentTime)
                        sendBroadcast(intent) // 给MusicActivity发送广播
                        sendEmptyMessageDelayed(1, 1000)
                    }
                }
            }
        }
    }

    private var musicReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val control = intent?.getIntExtra("control", -1)
            when (control) {
                0 -> palyflag = 0 // 顺序播放
                1 -> palyflag = 1    //列表循环
                2 -> palyflag = 2    //单曲循环
                3 -> palyflag = 3  //随机
                else -> {
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        /**
         * 设置音乐播放完成时的监听器
         */
        mediaPlayer!!.setOnCompletionListener{
            val intent = Intent(PLAY_STATUE)
            // 发送播放完毕的信号，更新播放状态
            intent.putExtra("playstatue", false)
            sendBroadcast(intent)

            if (palyflag == 2) {
                val loopintent = Intent(PLAY_STATUE)
                // 发送播放完毕的信号，更新播放状态
                intent.putExtra("playstatue", true)
                sendBroadcast(loopintent)
                // 单曲循环
                mediaPlayer!!.start()

            } else if (palyflag == 1) {
                // 列表循环
                position++
                if (position > songList!!.size - 1) {
                    //变为第一首的位置继续播放
                    position = 0
                }
                val sendIntent = Intent(UPDATE_ACTION)
                sendIntent.putExtra("position", position)
                // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                sendBroadcast(sendIntent)
                path = songList!![position].url
                play(0)
            } else if (palyflag == 0) { // 顺序播放
                position++    //下一首位置
                if (position <= songList!!.size - 1) {
                    val sendIntent = Intent(UPDATE_ACTION)
                    sendIntent.putExtra("position", position)
                    // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                    sendBroadcast(sendIntent)
                    path = songList!![position].url
                    play(0)
                } else {
                    mediaPlayer!!.seekTo(0)
                    position = 0
                    val sendIntent = Intent(UPDATE_ACTION)
                    sendIntent.putExtra("position", position)
                    // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                    sendBroadcast(sendIntent)
                }
            } else if (palyflag == 3) {    //随机播放
                position = getRandomIndex(songList!!.size - 1)
                val sendIntent = Intent(UPDATE_ACTION)
                sendIntent.putExtra("position", position)
                // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                sendBroadcast(sendIntent)
                path = songList!![position].url
                play(0)
            }
        }
        // 声明要接收哪些广播，Action就是广播的标志
        val filter = IntentFilter()
        filter.addAction(CTL_ACTION)
        registerReceiver(musicReceiver, filter)
    }
    //每次程序执行时调用
/*    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)*/
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val albumList = intent.getSerializableExtra("songList") as AlbumList
        songList = albumList.albumList
        path = intent.getStringExtra("url")
        title = songList!![position].title
        artist = songList!![position].artist
        position = intent.getIntExtra("position", -1)
        msg = intent.getIntExtra("MSG", 0)
        if (msg == PlayerMSG.MSG.PLAY_MSG) {    //直接播放音乐
            play(0)
        } else if (msg == PlayerMSG.MSG.PAUSE_MSG) {    //暂停
            pause()
        } else if (msg == PlayerMSG.MSG.STOP_MSG) {        //停止
            stop()
        } else if (msg == PlayerMSG.MSG.CONTINUE_MSG) {    //继续播放
            resume()
        } else if (msg == PlayerMSG.MSG.PRIVIOUS_MSG) {    //上一首
            previous()
        } else if (msg == PlayerMSG.MSG.NEXT_MSG) {        //下一首
            next()
        } else if (msg == PlayerMSG.MSG.PROGRESS_CHANGE) {    //进度更新
            currentTime = intent.getIntExtra("progress", -1)
            play(currentTime)
        } else if (msg == PlayerMSG.MSG.PLAYING_MSG) {
            mHandler.sendEmptyMessage(1)
        }
//        在通知栏显示，并监听播放/停止按钮
        setNotification()
        updateNotification()

        return super.onStartCommand(intent, flags, startId)
    }

    //必须重载的方法
    override fun onBind(arg0: Intent): IBinder {
        //当绑定后，返回一个musicBinder
        return musicBinder
    }
    //初始化音乐播放
/*    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)*/
    internal fun init() {
        //进入Idle
        mediaPlayer = MediaPlayer()
        try {
            //初始化
            mediaPlayer!!.reset()
            mediaPlayer = null
            mediaPlayer = MediaPlayer()
            mediaPlayer!!.setDataSource(path)
            mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            // prepare 通过异步的方式装载媒体资源
            mediaPlayer!!.prepareAsync()
            mediaPlayer!!.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    protected fun getRandomIndex(end: Int): Int {
        return (Math.random() * end).toInt()
    }
    private fun play(currentTime: Int) {
        try {
            mediaPlayer!!.reset()// 把各项参数恢复到初始状态
            mediaPlayer!!.setDataSource(path)
            mediaPlayer!!.prepare() // 进行缓冲
            mediaPlayer!!.setOnPreparedListener(PreparedListener(currentTime))// 注册一个监听器

            initLrc()
            //更新播放状态
            val intent = Intent(PLAY_STATUE)
            // 发送播放完毕的信号，更新播放状态
            intent.putExtra("playstatue", true)
            sendBroadcast(intent)
            mHandler.sendEmptyMessage(1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    //继续播放
    private fun resume() {
        if (isPause) {
            mediaPlayer!!.start()
            isPause = false
        }
    }
    // 上一首
    private fun previous() {
        val sendIntent = Intent(UPDATE_ACTION)
        sendIntent.putExtra("position", position)
        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
        sendBroadcast(sendIntent)
        play(0)
    }
    // 下一首
    private operator fun next() {
        val sendIntent = Intent(UPDATE_ACTION)
        sendIntent.putExtra("position", position)
        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
        sendBroadcast(sendIntent)
        play(0)
    }
    // 暂停
    private fun pause() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying()) {
            mediaPlayer!!.pause()
            isPause = true
        }
    }
    // 停止
    private fun stop() {
        if (mediaPlayer != null) {
            mediaPlayer!!.stop()
            try {
                // 在调用stop后如果需要再次通过start进行播放,需要之前调用prepare函数
                mediaPlayer!!.prepare()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    // 实现一个OnPrepareLister接口,当音乐准备好的时候开始播放
    private inner class PreparedListener(private val currentTime: Int) :
        MediaPlayer.OnPreparedListener {
        override fun onPrepared(mp: MediaPlayer) {
            mediaPlayer!!.start() // 开始播放
            if (mediaPlayer!!.isPlaying) {
                Log.d(TAG, "is playing")
            }
            updateNotification()
            if (currentTime > 0) { // 如果音乐不是从头播放
                mediaPlayer!!.seekTo(currentTime)
            }
            val intent = Intent()
            intent.action = MUSIC_DURATION
            duration = mediaPlayer!!.duration
            intent.putExtra("duration", duration)    //通过Intent来传递歌曲的总长度
            sendBroadcast(intent)
        }
    }
    //service销毁时，停止播放音乐，释放资源
    override fun onDestroy() {
        // 在activity结束的时候回收资源
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            mediaPlayer = null
        }
        unregisterReceiver(notificationReceiver)
        super.onDestroy()
    }
    //        在通知栏显示，并监听播放/停止按钮
    fun setNotification() {
        try {
//            1、创建通知渠道
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            android 8 以后才有NotificationChannel，所以进行版本判断
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel("musicPlayer", "丸子音乐", IMPORTANCE_DEFAULT)
                manager.createNotificationChannel(channel)
            }

//           2、自定义通知栏布局，按钮点击事件
            remoteView = RemoteViews(packageName, R.layout.notification)
            val intentPause = Intent(STATUS_BAR_COVER_CLICK_ACTION)
            val pi2 = PendingIntent.getBroadcast(this, 2, intentPause, PendingIntent.FLAG_UPDATE_CURRENT)
            title = songList!![position].title
            artist = songList!![position].artist
            remoteView.setTextViewText(R.id.notification_title, "$title - $artist")
            remoteView.setOnClickPendingIntent(R.id.notification_play, pi2)

            val filter = IntentFilter()
            filter.addAction(STATUS_BAR_COVER_CLICK_ACTION)
            registerReceiver(notificationReceiver, filter)

//            3、创建通知栏点击时的跳转意图
            val intent = Intent(this, MusicService::class.java)
            pi = PendingIntent.getActivity(this, 0, intent, 0)
//             用Builder构造器创建Notification

            notification = NotificationCompat.Builder(this, "musicPlayer")
                .setContent(remoteView)   // 自定义的布局视图
                .setContentTitle("$title")
                .setProgress(100,0,false)
                .setSmallIcon(R.drawable.small_icon) // 要用alpha图标
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.large_icon))
                .setContentIntent(pi) // 点击通知栏跳转到播放页面
                .build()
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR  // 让通知不被清除
            manager.notify(notifyId, notification)
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "exp3------$e")
        }
    }

    fun updateNotification() {
        Log.d(TAG, "updating notification")
        title = songList!![position].title
        artist = songList!![position].artist
        remoteView.setTextViewText(R.id.notification_title, "$title - $artist")
        try {
            if (mediaPlayer!!.isPlaying) {
                remoteView.setImageViewResource(R.id.notification_play, R.drawable.play)
            } else {
                remoteView.setImageViewResource(R.id.notification_play, R.drawable.pause)
            }
            notification.contentView = remoteView
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notifyId, notification)
//            Log.d(TAG, "test")
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "---updating notification---$e")
        }
    }
    //    歌词
    fun initLrc() {
        //建立歌词对象
        val lrcParser = LrcParse("$path")
        //读歌词，并将数据传给歌词信息类
        lrcInfo = lrcParser.readLrc()
        //获得歌词中的结点
        lrcLists = lrcInfo.lrcLists
//        发送广播，初始化歌词
        val intent = Intent(INIT_LRC)
        intent.putExtra("path", path)
        sendBroadcast(intent)

        mHandler.post(mRunnable)
    }

    var mRunnable: Runnable = object : Runnable {
        override fun run() {
            val intent = Intent(UPDATE_LRC)
            intent.putExtra("lrcIndex", lrcIndex())
            sendBroadcast(intent)
            mHandler.postDelayed(this, 100)
        }
    }

    fun lrcIndex(): Int {
        if (mediaPlayer!!.isPlaying) {
            currentTime = mediaPlayer!!.currentPosition
            duration = mediaPlayer!!.duration
        }
        if (currentTime < duration) {
            for (i in 0 until lrcLists.size) {
                if (i < lrcLists.size - 1) {
                    if (currentTime < lrcLists[i].currentTime && i == 0) {
                        index = i
                    }
                    if (currentTime > lrcLists[i].currentTime && currentTime < lrcLists[i + 1].currentTime) {
                        index = i
                    }
                }
                if (i == lrcLists.size - 1 && currentTime > lrcLists[i].currentTime) {
                    index = i
                }
            }
        }
        return index
    }
}