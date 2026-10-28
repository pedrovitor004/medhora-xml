package com.example.medhoraxml.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Frequencia {
    TODOS_OS_DIAS, DIAS_UTEIS, FINS_DE_SEMANA;

    fun ocorreEm(data: LocalDate): Boolean = when (this) {
        TODOS_OS_DIAS -> true
        DIAS_UTEIS -> data.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        FINS_DE_SEMANA -> data.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

enum class CorMedicamento { VERDE, VERMELHO, AMARELO, LILAS }

data class Medicamento(
    val id: Long,
    val nome: String,
    val dosagem: String,
    val horarios: List<LocalTime>,
    val frequencia: Frequencia = Frequencia.TODOS_OS_DIAS,
    val observacoes: String = "",
    val cor: CorMedicamento = CorMedicamento.VERDE,
    val criadoEm: LocalDate = LocalDate.now()
)

enum class StatusDose { TOMADA, PENDENTE, ATRASADA, PROGRAMADA }

/** Snapshot do medicamento: uma edição posterior não altera uma dose já registrada. */
data class Dose(
    val medicamentoId: Long,
    val nome: String,
    val dosagem: String,
    val cor: CorMedicamento,
    val data: LocalDate,
    val horario: LocalTime,
    val tomadaEm: LocalDateTime? = null
) {
    val chave: String get() = "$medicamentoId|$data|$horario"
    fun status(agora: LocalDateTime = LocalDateTime.now()): StatusDose = when {
        tomadaEm != null -> StatusDose.TOMADA
        data.isAfter(agora.toLocalDate()) -> StatusDose.PROGRAMADA
        data.atTime(horario).isBefore(agora) -> StatusDose.ATRASADA
        else -> StatusDose.PENDENTE
    }
}

data class MedHoraState(
    val medicamentos: List<Medicamento> = emptyList(),
    val doses: List<Dose> = emptyList(),
    val lembretes: Boolean = false
)
