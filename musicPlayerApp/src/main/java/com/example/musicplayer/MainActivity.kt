package com.example.musicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*
import android.provider.MediaStore
import android.widget.Toast
import android.content.pm.PackageManager
import android.nfc.Tag
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.annotation.NonNull
import android.widget.AdapterView
import java.io.Serializable
import java.lang.Exception

// MainActivity.kt
class MainActivity : AppCompatActivity(){
    // 存储数据的数组列表
    var albumList = ArrayList<Album>()
    lateinit var  songList:List<Album>
    private var topBtn: FloatingActionButton? = null

    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
//        设置滑动菜单的导航按钮
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)  // 显示导航按钮
            it.setHomeAsUpIndicator(R.drawable.ic_menu)  // 设置导航图标
        }

        requirePermission()
        initAlbum()

        topBtn = findViewById(R.id.top_btn)
        //悬浮按钮的点击事件的监听
        topBtn!!.setOnClickListener(View.OnClickListener {
            //listView返回到顶部
            recycleView.smoothScrollToPosition(0)
        })
    }
    private fun initAlbum() {
        songList = getSong()
        MusicActivity().songList = songList
        Toast.makeText(this, "111${songList.size}", Toast.LENGTH_SHORT).show()
        getData(songList)
        val layoutManager = LinearLayoutManager(this)
        recycleView.layoutManager = layoutManager
        val adapter = AlbumAdapter(albumList)
        adapter.setOnItemClickListerner(object:AlbumAdapter.OnItemClickListerner{
            override fun onItemClick(position: Int, context: Context) {
                Log.d(TAG, "你点击了$position")
                try {
                    val musicIntent = Intent(context, MusicActivity::class.java)
                    //当前播放的位置
                    musicIntent.putExtra("position", position)
                    var albumList1 = AlbumList(songList)
                    val bundle = Bundle()
                    bundle.putSerializable("songList", albumList1)
                    musicIntent.putExtras(bundle)
                    //启动音乐播放界面
                    startActivity(musicIntent)
                } catch (e: Exception) {
                    Log.d(TAG, "$e")
                }
            }
        })
        recycleView.adapter = adapter
        adapter!!.notifyDataSetChanged()
    }
    fun requirePermission() {
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) !== PackageManager.PERMISSION_GRANTED
        ) {
            //如果没有授权，则申请权限
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf<String>(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100
            )
            return
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        if (requestCode == 100) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //授权成功
                initAlbum()
            } else {
                //授权拒绝
                Toast.makeText(this, "请授权！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> drawerLayout.openDrawer(GravityCompat.START)
        }
        return true
    }

    fun getSong():List<Album> {
        val mp3Infos = ArrayList<Album>()
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
            MediaStore.Audio.Media.TITLE
        ) //按标题升序排序
//         遍历cursor
        if (cursor!!.moveToFirst()) {
            while (!cursor.isAfterLast) {
                //获取音乐信息
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title =cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val url = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
//                val album_id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))

//                val bitmap = getMusicBitemp(applicationContext, id, album_id)
                //创建音乐对象，并添加到list中
                val m =Album(id, title, "$artist", duration, url)
                mp3Infos.add(m)
                cursor.moveToNext()
            }
            cursor.close()
        }
        return mp3Infos
    }
    fun getData(mp3Infos: List<Album>) {
        for (i in mp3Infos.indices) {
            val album =Album((i + 1).toLong(), mp3Infos[i].title,
                mp3Infos[i].artist, mp3Infos[i].duration, mp3Infos[i].url)
            albumList.add(album)
        }
    }
}
