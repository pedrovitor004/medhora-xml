package com.example.medhoraxml.ui.medicamentos

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medhoraxml.R
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.ui.common.*
import com.google.android.material.textfield.TextInputEditText
import java.text.Normalizer

class MedicamentosFragment : Fragment(R.layout.fragment_medicamentos) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarCabecalho(view)
        val adapter = MedicamentoAdapter { abrirDetalhes(it) }
        view.findViewById<RecyclerView>(R.id.medicine_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        val search = view.findViewById<TextInputEditText>(R.id.search)
        fun normalizar(value: String) = Normalizer.normalize(value.lowercase(portugues), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        fun render() {
            val query = normalizar(search.text?.toString().orEmpty().trim())
            val medicines = MedicamentoRepository.state.value?.medicamentos.orEmpty()
                .filter { normalizar(it.nome).contains(query) }
                .sortedBy { it.nome.lowercase(portugues) }
            adapter.submitList(medicines)
            view.findViewById<TextView>(R.id.empty).apply {
                isVisible = medicines.isEmpty()
                setText(if (query.isBlank()) R.string.empty_medicines else R.string.no_search)
            }
        }
        search.doAfterTextChanged { render() }
        MedicamentoRepository.state.observe(viewLifecycleOwner) { render() }
        view.findViewById<View>(R.id.add_medicine).setOnClickListener { findNavController().navigate(R.id.cadastroFragment) }
    }
}
