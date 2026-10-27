package com.example.medhoraxml.ui.historico

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medhoraxml.R
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.ui.common.*
import com.google.android.material.button.MaterialButtonToggleGroup
import java.time.LocalDate

class HistoricoFragment : Fragment(R.layout.fragment_historico) {
    private var days = 1
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        days = savedInstanceState?.getInt("days", days) ?: days
        configurarCabecalho(view)
        val adapter = DoseAdapter({ abrirDetalhes(it) })
        view.findViewById<RecyclerView>(R.id.history_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        fun render() {
            val today = LocalDate.now()
            val start = today.minusDays(days.toLong() - 1)
            val doses = MedicamentoRepository.state.value?.doses.orEmpty()
                .filter { !it.data.isBefore(start) && !it.data.isAfter(today) }
            val rows = mutableListOf<TimelineItem>()
            doses.groupBy { it.data }.toSortedMap(compareByDescending { it }).forEach { (date, records) ->
                rows += TimelineItem.Header(if (date == today) getString(R.string.date_today, date.texto()) else date.textoCompleto())
                rows += records.sortedWith(compareBy({ it.horario }, { it.nome })).map { TimelineItem.Item(it) }
            }
            adapter.submitList(rows)
            view.findViewById<View>(R.id.empty).isVisible = rows.isEmpty()
        }
        val filter = view.findViewById<MaterialButtonToggleGroup>(R.id.period_filter)
        filter.check(when (days) { 7 -> R.id.filter_week; 30 -> R.id.filter_month; else -> R.id.filter_today })
        filter.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                days = when (id) { R.id.filter_week -> 7; R.id.filter_month -> 30; else -> 1 }
                render()
            }
        }
        MedicamentoRepository.state.observe(viewLifecycleOwner) { render() }
        atualizarEnquantoVisivel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("days", days)
        super.onSaveInstanceState(outState)
    }
}
