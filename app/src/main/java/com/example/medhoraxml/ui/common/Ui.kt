package com.example.medhoraxml.ui.common

import android.view.View
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.CorMedicamento
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val portugues: Locale = Locale.forLanguageTag("pt-BR")
val formatoHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", portugues)
fun LocalTime.texto(): String = format(formatoHora)
fun LocalDate.texto(): String = format(DateTimeFormatter.ofPattern("d 'de' MMMM", portugues))
fun LocalDate.textoCompleto(): String = format(DateTimeFormatter.ofPattern("dd/MM/yyyy", portugues))

fun Fragment.configurarCabecalho(view: View, voltar: Boolean = false) {
    view.findViewById<MaterialToolbar>(R.id.toolbar)?.apply {
        if (voltar) setNavigationOnClickListener { findNavController().popBackStack() }
        setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.open_settings) {
                if (findNavController().currentDestination?.id != R.id.configuracoesFragment) {
                    findNavController().navigate(R.id.configuracoesFragment)
                }
                true
            } else false
        }
    }
}

fun Fragment.abrirDetalhes(id: Long) {
    if (MedicamentoRepository.buscar(id) != null) {
        findNavController().navigate(R.id.detalhesFragment, bundleOf("medicamentoId" to id))
    } else view?.let { Snackbar.make(it, R.string.missing_medicine, Snackbar.LENGTH_SHORT).show() }
}

fun ImageView.identificar(cor: CorMedicamento) {
    imageTintList = null
    setImageResource(when (cor) {
        CorMedicamento.VERDE -> R.drawable.ic_capsule_green
        CorMedicamento.VERMELHO -> R.drawable.ic_capsule_red
        CorMedicamento.AMARELO -> R.drawable.ic_capsule_yellow
        CorMedicamento.LILAS -> R.drawable.ic_capsule_lilac
    })
}

fun Fragment.atualizarEnquantoVisivel() {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                MedicamentoRepository.atualizar()
                delay(30_000)
            }
        }
    }
}

