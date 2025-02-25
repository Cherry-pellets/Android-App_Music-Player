package com.example.musicplayer

import android.util.Log
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.regex.Pattern

class LrcParse(path: String) {

    private val lrcInfo = LrcInfo()
    private var Path: String =  path.replace(".mp3", ".lrc") //mp3歌词存放地地方
    private var currentTime: Long = 0  //mp3时间
    private var currentContent: String? = null //MP3对应时间的内容
    internal var lrcLists = ArrayList<LrcList>()  //保存时间点和内容
    private var inputStream: InputStream? = null
    init {
        this.Path = path.replace(".mp3", ".lrc")
    }

    fun readLrc(): LrcInfo {
        //定义一个StringBuilder对象，用来存放歌词内容
        val stringBuilder = StringBuilder()
        try {
//            Log.d("MusicService", "--------- ${this.Path}")
            inputStream = FileInputStream(this.Path)
            val reader = BufferedReader(InputStreamReader(inputStream, charSet))
            var str: String? = null
            //逐行解析
            str = reader.readLine()
            while (str != null) {
                if (str != "") {
                    decodeLine(str!!)
                }
                str = reader.readLine()
            }
            //全部解析完后，设置lrcLists
            lrcInfo.lrcLists = lrcLists
//            Log.d("MusicService", "解析完成")
            return lrcInfo
        } catch (e: FileNotFoundException) {
//            Log.d("MusicService", "2--------- $e")
            e.printStackTrace()
            val lrcList = LrcList()
            //设置时间点和内容的映射
            lrcList.content = "未发现歌词......"
            lrcLists.add(lrcList)
            lrcInfo.lrcLists = lrcLists
            return lrcInfo
        } catch (e: IOException) {
//            Log.d("MusicService", "3--------- $e")
            e.printStackTrace()
            val lrcList = LrcList()
            //设置时间点和内容的映射
            lrcList.content = "木有读取到歌词！"
            lrcLists.add(lrcList)
            lrcInfo.lrcLists = lrcLists
            return lrcInfo
        }

    }

    //    单行解析
    private fun decodeLine(str: String): LrcInfo {
        if (str.startsWith("[ti:")) {
            // 歌曲名
            lrcInfo.title = str.substring(4, str.lastIndexOf("]"))
            //   lrcTable.put("ti", str.substring(4, str.lastIndexOf("]")));
        } else if (str.startsWith("[ar:")) {// 艺术家
            lrcInfo.artist = str.substring(4, str.lastIndexOf("]"))
        } else if (str.startsWith("[al:")) {// 专辑
            lrcInfo.album = str.substring(4, str.lastIndexOf("]"))
        } else if (str.startsWith("[by:")) {// 作词
            lrcInfo.bySomeBody = str.substring(4, str.lastIndexOf("]"))
        } else if (str.startsWith("[la:")) {// 语言
            lrcInfo.language = str.substring(4, str.lastIndexOf("]"))
        } else {
            //设置正则表达式，可能出现一些特殊的情况
            val timeflag = "\\[(\\d{1,2}:\\d{1,2}\\.\\d{1,2})\\]|\\[(\\d{1,2}:\\d{1,2})\\]"

            val pattern = Pattern.compile(timeflag)
            val matcher = pattern.matcher(str)
            //如果存在匹配项则执行如下操作
            while (matcher.find()) {
                //得到匹配的内容
                val msg = matcher.group()
                //得到这个匹配项开始的索引
                val start = matcher.start()
                //得到这个匹配项结束的索引
                val end = matcher.end()
                //得到这个匹配项中的数组
                val groupCount = matcher.groupCount()
                for (index in 0 until groupCount) {
                    val timeStr = matcher.group(index)
                    Log.i("", "time[$index]=$timeStr")
                    if (index == 0) {
                        //将第二组中的内容设置为当前的一个时间点
                        currentTime = str2Long(timeStr!!.substring(1, timeStr.length - 1))
                    }
                }
                //得到时间点后的内容
                val content = pattern.split(str)

                //将内容设置为当前内容，需要判断只出现时间的情况，没有内容的情况
                if (content.size == 0) {
                    currentContent = ""
                } else {
                    currentContent = content[content.size - 1]
                }
                val lrcList = LrcList()
                //设置时间点和内容的映射
                lrcList.currentTime = currentTime
                lrcList.content = currentContent
                lrcLists.add(lrcList)
            }
        }
        return this.lrcInfo
    }

    private fun str2Long(timeStr: String): Long {
        //将时间格式为xx:xx.xx，返回的long要求以毫秒为单位
        Log.i("", "timeStr=$timeStr")
        val s = timeStr.split("\\:".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val min = Integer.parseInt(s[0])
        var sec = 0
        var mill = 0
        if (s[1].contains(".")) {
            val ss = s[1].split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            sec = Integer.parseInt(ss[0])
            mill = Integer.parseInt(ss[1])
            Log.i("", "s[0]=" + s[0] + "s[1]" + s[1] + "ss[0]=" + ss[0] + "ss[1]=" + ss[1])
        } else {
            sec = Integer.parseInt(s[1])
            Log.i("", "s[0]=" + s[0] + "s[1]" + s[1])
        }
        //时间的组成
        return (min * 60 * 1000 + sec * 1000 + mill * 10).toLong()
    }

    companion object {
        var charSet = "gbk"
    }
}
