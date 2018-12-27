package com.nikitvad.android.musicplayer.utils

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nikitvad.android.musicplayer.BR

class AppRecyclerAdapter<VH : ViewDataBinding, I : Any>(val layoutId: Int,
                                                        val varId: Int,
                                                        var onItemClick: AppOnItemClickListener<I>? = null)
    : RecyclerView.Adapter<AppRecyclerAdapter.AppRecyclerViewHolder<VH, I>>() {

    private val itemsList = ArrayList<I>()

    var selectedItem: Int = -1

    public fun setItems(items: List<I>) {
        itemsList.clear()
        itemsList.addAll(items)
        notifyDataSetChanged()
    }

    public fun selectItem(item: I) {
        val prevSelected = selectedItem
        selectedItem = itemsList.indexOf(item)
        notifyItemChanged(selectedItem)

        Log.d("AppRecyclerAdapter", "selectItem: $selectedItem, $prevSelected")

        if (prevSelected >= 0) notifyItemChanged(prevSelected)
//        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppRecyclerViewHolder<VH, I> {
        val viewHolderBinding = DataBindingUtil.inflate<VH>(LayoutInflater.from(parent.context), layoutId, parent, false)
        return AppRecyclerViewHolder(viewHolderBinding, onItemClick)
    }

    override fun getItemCount(): Int {
        return itemsList.size
    }

    override fun onBindViewHolder(holder: AppRecyclerViewHolder<VH, I>, position: Int) {
        holder.bind(itemsList[position], selectedItem == position, varId)
    }

    class AppRecyclerViewHolder<VH : ViewDataBinding, I : Any>(private val viewHolderBinding: VH, val onItemClick: AppOnItemClickListener<I>?) : RecyclerView.ViewHolder(viewHolderBinding.root) {
        fun bind(item: I, selected: Boolean, varId: Int) {
            viewHolderBinding.setVariable(varId, item)
            viewHolderBinding.setVariable(BR.selected, selected)
            viewHolderBinding.executePendingBindings()
            onItemClick?.let { itemClick ->
                itemView.setOnClickListener {
                    itemClick.onItemClick(itemView, item)
                }
            }
        }
    }
}

interface AppOnItemClickListener<I : Any> {
    fun onItemClick(view: View, item: I)
}