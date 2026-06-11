package com.geoagent.ui.documents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.ui.motion.MotionUtils

class DocumentAdapter(
    private val onOpen: (DocumentDto) -> Unit,
    private val onDelete: (DocumentDto) -> Unit,
    private val onRename: (DocumentDto) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.Holder>() {

    private val items = mutableListOf<DocumentDto>()

    fun submit(documents: List<DocumentDto>) {
        val oldItems = items.toList()
        items.clear()
        items.addAll(documents)
        DiffUtil.calculateDiff(DocumentDiff(oldItems, documents)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val doc = items[position]
        holder.name.text = doc.source
        holder.meta.text = doc.collection
        holder.itemView.setOnClickListener {
            MotionUtils.press(holder.itemView)
            onOpen(doc)
        }
        holder.itemView.setOnLongClickListener {
            MotionUtils.press(holder.itemView)
            onRename(doc)
            true
        }
        holder.delete.setOnClickListener {
            MotionUtils.press(holder.delete)
            onDelete(doc)
        }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_name)
        val meta: TextView = view.findViewById(R.id.tv_meta)
        val delete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    private class DocumentDiff(
        private val oldItems: List<DocumentDto>,
        private val newItems: List<DocumentDto>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size
        override fun getNewListSize(): Int = newItems.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].id == newItems[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}
