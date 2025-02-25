package com.example.musicplayer
import java.io.Serializable
import java.util.ArrayList

class LrcInfo:Serializable {
    var title: String? = null//标题
    var artist: String? = null//歌手
    var album: String? = null//专辑名字
    var bySomeBody: String? = null//歌词制作者
    var offset: String? = null
    var language: String? = null   //语言
    var errorinfo: String? = null   //错误信息
    lateinit var lrcLists: ArrayList<LrcList>  //保存歌词信息和时间点
}

