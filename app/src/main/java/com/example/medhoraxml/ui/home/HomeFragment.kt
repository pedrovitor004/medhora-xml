package com.example.medhoraxml.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.StatusDose
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.ui.common.*
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var selectedDate = LocalDate.now()
    private var referenceToday = LocalDate.now()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedDate = savedInstanceState?.getLong("selectedDate")?.let(LocalDate::ofEpochDay) ?: selectedDate
        configurarCabecalho(view)
        val adapter = DoseAdapter({ abrirDetalhes(it) }) { dose ->
            MedicamentoRepository.registrarDose(dose.chave)?.let { registrada ->
                findNavController().navigate(R.id.doseConfirmadaDialog, bundleOf(
                    "nome" to registrada.nome, "dosagem" to registrada.dosagem, "horario" to registrada.horario.texto()))
            }
        }
        val list = view.findViewById<RecyclerView>(R.id.dose_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        view.findViewById<View>(R.id.add_medicine).setOnClickListener { findNavController().navigate(R.id.cadastroFragment) }
        val days = view.findViewById<LinearLayout>(R.id.week_days)
        fun render() {
            val hoje = LocalDate.now()
            if (referenceToday != hoje) {
                if (selectedDate == referenceToday) selectedDate = hoje
                referenceToday = hoje
            }
            val monday = hoje.with(DayOfWeek.MONDAY)
            days.removeAllViews()
            repeat(7) { offset ->
                val date = monday.plusDays(offset.toLong())
                val day = layoutInflater.inflate(R.layout.item_day, days, false) as TextView
                day.text = date.format(DateTimeFormatter.ofPattern("EEE", portugues)).replace(".", "") + "\n" + date.dayOfMonth
                day.isSelected = date == selectedDate
                day.contentDescription = getString(R.string.day_description,
                    date.format(DateTimeFormatter.ofPattern("EEEE", portugues)), date.texto())
                day.setOnClickListener {
                    selectedDate = date
                    days.children.forEach { it.isSelected = it == day }
                    render()
                }
                days.addView(day)
            }
            val doses = MedicamentoRepository.dosesDoDia(selectedDate)
            val taken = doses.count { it.status() == StatusDose.TOMADA }
            val late = doses.count { it.status() == StatusDose.ATRASADA }
            val pending = doses.size - taken - late
            view.findViewById<TextView>(R.id.today_label).text = if (selectedDate == hoje)
                getString(R.string.date_today, selectedDate.texto()) else selectedDate.texto()
            view.findViewById<TextView>(R.id.summary_count).text = getString(R.string.daily_progress, taken, doses.size)
            view.findViewById<TextView>(R.id.summary_pending).text = getString(R.string.daily_pending, pending, late)
            view.findViewById<LinearProgressIndicator>(R.id.daily_progress).apply {
                max = maxOf(1, doses.size)
                setProgressCompat(taken, true)
            }
            view.findViewById<TextView>(R.id.doses_heading).text = if (selectedDate == hoje)
                getString(R.string.next_doses) else getString(R.string.selected_date, selectedDate.texto())
            view.findViewById<View>(R.id.empty).isVisible = doses.isEmpty()
            adapter.submitList(doses.map { TimelineItem.Item(it) })
        }
        MedicamentoRepository.state.observe(viewLifecycleOwner) { render() }
        atualizarEnquantoVisivel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("selectedDate", selectedDate.toEpochDay())
        super.onSaveInstanceState(outState)
    }
}
