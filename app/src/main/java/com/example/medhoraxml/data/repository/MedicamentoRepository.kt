package com.example.medhoraxml.data.repository

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.medhoraxml.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Única fonte de dados do processo. Não faz I/O nem agenda notificações. */
object MedicamentoRepository {
    val sessao: String = java.util.UUID.randomUUID().toString()
    private var proximoId = System.currentTimeMillis()
    private val medicamentos = mutableListOf<Medicamento>()
    private val doses = linkedMapOf<String, Dose>()
    private val diasGerados = mutableSetOf<Pair<Long, LocalDate>>()
    private var lembretes = false
    private val mutableState = MutableLiveData(MedHoraState())
    val state: LiveData<MedHoraState> = mutableState

    fun buscar(id: Long): Medicamento? = medicamentos.firstOrNull { it.id == id }

    @MainThread
    fun salvar(id: Long?, nome: String, dosagem: String, horarios: List<LocalTime>,
               frequencia: Frequencia, observacoes: String, cor: CorMedicamento): Medicamento {
        require(nome.isNotBlank() && nome.trim().length <= 80)
        require(dosagem.isNotBlank() && dosagem.trim().length <= 80)
        require(horarios.isNotEmpty() && horarios.size <= 12 && horarios.distinct().size == horarios.size)
        require(observacoes.length <= 500)
        val existente = id?.let(::buscar)
        require(id == null || existente != null) { "Medicamento inexistente" }
        val medicamento = Medicamento(existente?.id ?: proximoId++, nome.trim(), dosagem.trim(),
            horarios.sorted(), frequencia, observacoes.trim(), cor, existente?.criadoEm ?: LocalDate.now())
        medicamentos.removeAll { it.id == medicamento.id }
        medicamentos += medicamento
        // A nova agenda vale a partir de hoje; doses anteriores e tomadas são preservadas.
        val hoje = LocalDate.now()
        doses.entries.removeAll { it.value.medicamentoId == medicamento.id && it.value.data == hoje && it.value.tomadaEm == null }
        diasGerados.remove(medicamento.id to hoje)
        atualizar()
        return medicamento
    }

    @MainThread
    fun excluir(id: Long) {
        medicamentos.removeAll { it.id == id }
        doses.entries.removeAll { it.value.medicamentoId == id && it.value.tomadaEm == null }
        diasGerados.removeAll { it.first == id }
        publicar()
    }

    /** Idempotente: a mesma dose só pode ser registrada uma vez, no dia ou após ele. */
    @MainThread
    fun registrarDose(chave: String): Dose? {
        atualizar()
        val dose = doses[chave] ?: return null
        if (dose.tomadaEm != null || dose.data.isAfter(LocalDate.now()) || buscar(dose.medicamentoId) == null) return null
        val registrada = dose.copy(tomadaEm = LocalDateTime.now())
        doses[chave] = registrada
        publicar()
        return registrada
    }

    @MainThread
    fun definirLembretes(ativados: Boolean) {
        lembretes = ativados
        publicar()
    }

    /** Materializa a agenda ao abrir a tela e a cada minuto, inclusive na virada do dia. */
    @MainThread
    fun atualizar() {
        val hoje = LocalDate.now()
        medicamentos.forEach { medicamento ->
            var dia = maxOf(medicamento.criadoEm, hoje.minusDays(29))
            while (!dia.isAfter(hoje)) {
                if (diasGerados.add(medicamento.id to dia) && medicamento.frequencia.ocorreEm(dia)) {
                    medicamento.horarios.forEach { horario ->
                        val dose = doseDe(medicamento, dia, horario)
                        doses.putIfAbsent(dose.chave, dose)
                    }
                }
                dia = dia.plusDays(1)
            }
        }
        publicar()
    }

    fun dosesDoDia(data: LocalDate, snapshot: MedHoraState = state.value ?: MedHoraState()): List<Dose> {
        val lista = if (data.isAfter(LocalDate.now())) {
            snapshot.medicamentos.filter { !data.isBefore(it.criadoEm) && it.frequencia.ocorreEm(data) }
                .flatMap { med -> med.horarios.map { doseDe(med, data, it) } }
        } else snapshot.doses.filter { it.data == data }
        return lista.sortedWith(compareBy<Dose> { it.horario }.thenBy { it.nome })
    }

    private fun doseDe(m: Medicamento, data: LocalDate, horario: LocalTime) =
        Dose(m.id, m.nome, m.dosagem, m.cor, data, horario)

    private fun publicar() {
        mutableState.value = MedHoraState(medicamentos.toList(), doses.values.toList(), lembretes)
    }
}
