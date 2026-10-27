package com.example.medhoraxml.ui.detalhes

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.ui.common.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class DetalhesFragment : Fragment(R.layout.fragment_detalhes) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarCabecalho(view, voltar = true)
        val id = requireArguments().getLong("medicamentoId", -1)
        MedicamentoRepository.state.observe(viewLifecycleOwner) { state ->
            val med = state.medicamentos.firstOrNull { it.id == id }
            if (med == null) {
                if (findNavController().currentDestination?.id == R.id.detalhesFragment) {
                    Snackbar.make(view, R.string.missing_medicine, Snackbar.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                return@observe
            }
            view.findViewById<TextView>(R.id.medicine_name).text = med.nome
            view.findViewById<TextView>(R.id.medicine_dose).text = med.dosagem
            view.findViewById<ImageView>(R.id.medicine_icon).identificar(med.cor)
            view.findViewById<TextView>(R.id.medicine_frequency).text = resources.getStringArray(R.array.frequency_options)[med.frequencia.ordinal]
            view.findViewById<TextView>(R.id.medicine_notes).text = med.observacoes.ifBlank { getString(R.string.no_notes) }
            val last = state.doses.filter { it.medicamentoId == id && it.tomadaEm != null }.maxByOrNull { it.tomadaEm!! }
            view.findViewById<TextView>(R.id.last_dose).text = last?.tomadaEm?.let { registro ->
                getString(R.string.last_dose_value, registro.toLocalTime().texto(), registro.toLocalDate().textoCompleto())
            } ?: getString(R.string.no_last_dose)
            view.findViewById<LinearLayout>(R.id.detail_times).apply {
                removeAllViews()
                med.horarios.forEach { time ->
                    val row = layoutInflater.inflate(R.layout.item_time, this, false)
                    row.findViewById<MaterialButton>(R.id.time_value).apply {
                        text = time.texto()
                        isClickable = false
                        isFocusable = false
                    }
                    row.findViewById<View>(R.id.remove_time).visibility = View.GONE
                    addView(row)
                }
            }
        }
        view.findViewById<View>(R.id.edit).setOnClickListener {
            if (MedicamentoRepository.buscar(id) != null)
                findNavController().navigate(R.id.cadastroFragment, bundleOf("medicamentoId" to id))
        }
        view.findViewById<View>(R.id.delete).setOnClickListener {
            val med = MedicamentoRepository.buscar(id) ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_title).setMessage(getString(R.string.delete_body, med.nome))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    // Sair primeiro evita que o observer tente remover a tela duas vezes.
                    findNavController().popBackStack()
                    MedicamentoRepository.excluir(id)
                    Snackbar.make(requireActivity().findViewById(R.id.main), R.string.deleted, Snackbar.LENGTH_SHORT).show()
                }.show()
        }
    }
}
