package com.samsa.ncfu_shka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.samsa.ncfu_shka.R

class ServerAdapter(
    private val onItemClick: (Triple<String, String, Int>) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    private var servers = listOf<Triple<String, String, Int>>()

    fun updateList(newList: List<Triple<String, String, Int>>) {
        servers = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = servers[position]
        holder.bind(server, onItemClick)
    }

    override fun getItemCount(): Int = servers.size

    class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvServerName)
        private val tvIp: TextView = itemView.findViewById(R.id.tvServerIp)

        fun bind(server: Triple<String, String, Int>, onItemClick: (Triple<String, String, Int>) -> Unit) {
            val (name, ip, port) = server
            tvName.text = name
            tvIp.text = "$ip:$port"
            itemView.setOnClickListener { onItemClick(server) }
        }
    }
}