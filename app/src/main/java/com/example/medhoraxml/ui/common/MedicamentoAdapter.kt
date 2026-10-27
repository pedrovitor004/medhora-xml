package com.example.medhoraxml.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.Medicamento

class MedicamentoAdapter(private val onClick: (Long) -> Unit) :
    ListAdapter<Medicamento, MedicamentoAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_medicamento, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(medicamento: Medicamento) {
            itemView.findViewById<TextView>(R.id.medicine_name).text = medicamento.nome
            itemView.findViewById<TextView>(R.id.medicine_dose).text = medicamento.dosagem
            itemView.findViewById<TextView>(R.id.medicine_schedule).text =
                itemView.context.getString(R.string.daily_times, medicamento.horarios.size)
            itemView.findViewById<ImageView>(R.id.medicine_icon).identificar(medicamento.cor)
            itemView.setOnClickListener { onClick(medicamento.id) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Medicamento>() {
        override fun areItemsTheSame(old: Medicamento, new: Medicamento) = old.id == new.id
        override fun areContentsTheSame(old: Medicamento, new: Medicamento) = old == new
    }
}
