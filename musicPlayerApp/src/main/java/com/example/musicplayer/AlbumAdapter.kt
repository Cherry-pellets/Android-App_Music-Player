package com.example.musicplayer

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlbumAdapter(val albumList: ArrayList<Album>): RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {
    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val id: TextView = view.findViewById(R.id.MusicID)
        val title: TextView = view.findViewById(R.id.Musictitle)
        val artist: TextView = view.findViewById(R.id.MusicArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.album_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albumList[position]
        holder.id.text = album.id.toString()
        holder.title.text = album.title
        holder.artist.text = album.artist

        holder.itemView.setOnClickListener{
            val position = holder.adapterPosition
            val album = albumList[position]

            //
            val intent2 = Intent(it.context, MusicActivity::class.java)
            intent2.putExtra("url", album.url)
            intent2.putExtra("title", album.title)
            intent2.putExtra("artist", album.artist)
            it.context.startActivity(intent2)  // 跳到播放页面

            val intent = Intent(it.context, MusicService::class.java)
            intent.putExtra("url", album.url)
            intent.putExtra("title", album.title)
            intent.putExtra("artist", album.artist)
            it.context.stopService(intent)
            it.context.startService(intent)  // 开启一个Service
        }
    }
    override fun getItemCount() = albumList.size
}
