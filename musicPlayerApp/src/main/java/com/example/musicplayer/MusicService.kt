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
import android.view.animation.AnimationUtils
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    internal var path: String? = null // 歌曲的绝对路径
    internal var title: String? = null // 歌曲名
    internal var artist: String? = null // 歌手

    internal var musicBinder: IBinder = MyBinder()
    internal var mediaPlayer: MediaPlayer? = null //播放音乐的媒体类

    lateinit var lrcInfo:LrcInfo
    lateinit var lrcLists: ArrayList<LrcList>
    var index = 0
    var duration:Int = 0
    var currentTime = 0

    private val TAG = "MusicService"
    private val STATUS_BAR_COVER_CLICK_ACTION = "click button in notification"
    private val NOTIFICATION_BTN_CHANGE = "button in notification has been clicked"
    private val MUSIC_CURRENT = "current time "
    private lateinit var remoteView: RemoteViews
    private lateinit var notification: Notification
    private lateinit var pi: PendingIntent
    private val notifyId = 1

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
                        intent.setAction(MUSIC_CURRENT)
                        intent.putExtra("currentTime", currentTime)
                        sendBroadcast(intent) // 给MusicActivity发送广播
                        sendEmptyMessageDelayed(2, 1000)
                    }
                }
            }
        }
    }

    //每次程序执行时调用
/*    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)*/
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        path = intent.getStringExtra("url")
        title = intent.getStringExtra("title")
        artist = intent.getStringExtra("artist")
        Log.d(TAG, "" + path)
        init()
        mediaPlayer!!.start()
        if (mediaPlayer!!.isPlaying) {
            pause()
        }
//        在通知栏显示，并监听播放/停止按钮
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
            manager.notify(notifyId, notification)
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "exp3------$e")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    //必须重载的方法
    override fun onBind(arg0: Intent): IBinder {
        // TODO Auto-generated method stub
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
    //通过activity调节播放进度
    fun setProgress(max: Int, dest: Int) {
        val time = mediaPlayer!!.duration
        mediaPlayer!!.seekTo(time * dest / max)
    }
    //测试播放音乐
    fun play() {
        if (mediaPlayer != null) {
            mediaPlayer!!.start()
        }
//        initLrc()
    }
    //暂停音乐
    fun pause() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
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

    fun updateNotification() {
        Log.d(TAG, "updating notification")
        try {
            if (mediaPlayer!!.isPlaying) {
                remoteView.setImageViewResource(R.id.notification_play, R.drawable.play)
            } else {
                remoteView.setImageViewResource(R.id.notification_play, R.drawable.pause)
            }
            notification.contentView = remoteView
//            notification.contentIntent = pi
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notifyId, notification)
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "$e")
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
        //在musicActivity里面设置静态来共享数据
        MusicActivity().lrcView.setmLrcList(lrcInfo)
        //切换带动画显示歌词
        MusicActivity().lrcView.setAnimation(
            AnimationUtils.loadAnimation(
            this@MusicService, R.anim.lrc_anim))
        mHandler.post(mRunnable)
    }

    var mRunnable: Runnable = object : Runnable {
        override fun run() {
            MusicActivity().lrcView.setIndex(lrcIndex())
            MusicActivity().lrcView.invalidate()
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