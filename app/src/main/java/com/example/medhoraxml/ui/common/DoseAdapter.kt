package com.example.medhoraxml.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.Dose
import com.example.medhoraxml.data.model.StatusDose
import com.google.android.material.button.MaterialButton

sealed class TimelineItem {
    data class Header(val label: String) : TimelineItem()
    data class Item(val dose: Dose, val status: StatusDose = dose.status()) : TimelineItem()
}

class DoseAdapter(
    private val onDetails: (Long) -> Unit,
    private val onTake: ((Dose) -> Unit)? = null
) : ListAdapter<TimelineItem, RecyclerView.ViewHolder>(Diff) {
    override fun getItemViewType(position: Int) = if (getItem(position) is TimelineItem.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) HeaderHolder(inflater.inflate(R.layout.item_history_header, parent, false))
        else DoseHolder(inflater.inflate(R.layout.item_dose, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimelineItem.Header -> (holder as HeaderHolder).title.text = item.label
            is TimelineItem.Item -> (holder as DoseHolder).bind(item)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.history_date)
    }

    private inner class DoseHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.medicine_name)
        private val dosage = view.findViewById<TextView>(R.id.medicine_dose)
        private val time = view.findViewById<TextView>(R.id.dose_time)
        private val info = view.findViewById<TextView>(R.id.registered_time)
        private val icon = view.findViewById<ImageView>(R.id.medicine_icon)
        private val details = view.findViewById<View>(R.id.dose_details)
        private val action = view.findViewById<MaterialButton>(R.id.dose_action)

        fun bind(item: TimelineItem.Item) {
            val dose = item.dose
            val context = itemView.context
            val statusText = when (item.status) {
                StatusDose.TOMADA -> R.string.taken
                StatusDose.PENDENTE -> R.string.pending
                StatusDose.ATRASADA -> R.string.late
                StatusDose.PROGRAMADA -> R.string.scheduled
            }
            val statusColor = context.getColor(when (item.status) {
                StatusDose.TOMADA -> R.color.medhora_green_primary
                StatusDose.ATRASADA -> R.color.medhora_error
                StatusDose.PENDENTE -> R.color.medhora_warning
                StatusDose.PROGRAMADA -> R.color.medhora_muted
            })
            name.text = dose.nome
            dosage.text = dose.dosagem
            time.text = dose.horario.texto()
            time.setTextColor(if (item.status == StatusDose.ATRASADA) statusColor else context.getColor(R.color.medhora_text))
            icon.identificar(dose.cor)
            details.contentDescription = context.getString(R.string.dose_accessibility,
                dose.nome, dose.dosagem, dose.horario.texto(), context.getString(statusText))
            details.setOnClickListener { onDetails(dose.medicamentoId) }
            val podeTomar = onTake != null && item.status in listOf(StatusDose.PENDENTE, StatusDose.ATRASADA)
            action.isEnabled = podeTomar
            action.setText(if (podeTomar) R.string.take else statusText)
            action.setTextColor(if (podeTomar) context.getColor(R.color.medhora_white) else statusColor)
            action.strokeColor = ColorStateList.valueOf(if (podeTomar) context.getColor(R.color.medhora_green_primary) else statusColor)
            action.backgroundTintList = ColorStateList.valueOf(context.getColor(
                if (podeTomar) R.color.medhora_green_primary else R.color.medhora_white))
            action.setOnClickListener { if (podeTomar) onTake?.invoke(dose) }
            info.isVisible = dose.tomadaEm != null || onTake != null
            info.text = dose.tomadaEm?.let { context.getString(R.string.history_registered, it.toLocalTime().texto()) }
                ?: context.getString(statusText)
            info.setTextColor(statusColor)
        }
    }

    private object Diff : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(old: TimelineItem, new: TimelineItem): Boolean = when {
            old is TimelineItem.Header && new is TimelineItem.Header -> old.label == new.label
            old is TimelineItem.Item && new is TimelineItem.Item -> old.dose.chave == new.dose.chave
            else -> false
        }
        override fun areContentsTheSame(old: TimelineItem, new: TimelineItem) = old == new
    }
}
