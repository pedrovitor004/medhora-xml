package com.example.medhoraxml.ui.cadastro

import androidx.lifecycle.ViewModel
import com.example.medhoraxml.data.model.CorMedicamento
import com.example.medhoraxml.data.model.Frequencia
import com.example.medhoraxml.data.model.Medicamento
import java.time.LocalTime

/** Rascunho sobrevive à rotação, apenas em memória e restrito a este formulário. */
data class FormularioMedicamento(
    val nome: String = "",
    val dosagem: String = "",
    val horarios: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val frequencia: Frequencia = Frequencia.TODOS_OS_DIAS,
    val observacoes: String = "",
    val cor: CorMedicamento = CorMedicamento.VERDE
)

class CadastroViewModel : ViewModel() {
    var inicial: FormularioMedicamento? = null
        private set
    var rascunho = FormularioMedicamento()

    fun inicializar(medicamento: Medicamento?) {
        if (inicial != null) return
        rascunho = medicamento?.let {
            FormularioMedicamento(it.nome, it.dosagem, it.horarios, it.frequencia, it.observacoes, it.cor)
        } ?: FormularioMedicamento()
        inicial = rascunho
    }

    val alterado: Boolean get() = inicial != rascunho
}
