package com.geoagent.ui.documents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.data.api.dto.DocumentDto

class DocumentAdapter(
    private val onOpen: (DocumentDto) -> Unit,
    private val onDelete: (DocumentDto) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.Holder>() {

    private val items = mutableListOf<DocumentDto>()

    fun submit(documents: List<DocumentDto>) {
        items.clear()
        items.addAll(documents)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val doc = items[position]
        holder.name.text = doc.source
        holder.meta.text = doc.collection
        holder.itemView.setOnClickListener { onOpen(doc) }
        holder.delete.setOnClickListener { onDelete(doc) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_name)
        val meta: TextView = view.findViewById(R.id.tv_meta)
        val delete: ImageButton = view.findViewById(R.id.btn_delete)
    }
}
