package com.example.musicplayer

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AlbumAdapter(val albumList: ArrayList<Album>): RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {
    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val id: TextView = view.findViewById(R.id.MusicID)
        val title: TextView = view.findViewById(R.id.Musictitle)
        val artist: TextView = view.findViewById(R.id.MusicArtist)
//        val tv:TextView = view.findViewById(R.id.tv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.album_item, parent, false)
        return ViewHolder(view)
    }
    //点击事件
    public interface OnItemClickListerner{
        fun onItemClick(position: Int, context: Context)
    }
    private var mOnItemClickListerner:OnItemClickListerner? = null

    public fun setOnItemClickListerner(onItemClickListerner:OnItemClickListerner?){
        mOnItemClickListerner = onItemClickListerner
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albumList[position]
        holder.id.text = album.id.toString()
        holder.title.text = album.title
        holder.artist.text = album.artist

        holder.itemView.setOnClickListener {
            mOnItemClickListerner!!.onItemClick(position, it.context)

        }
    }
    override fun getItemCount() = albumList.size
}
