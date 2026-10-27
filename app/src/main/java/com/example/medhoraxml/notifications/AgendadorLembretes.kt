package com.example.medhoraxml.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.medhoraxml.data.model.Dose
import com.example.medhoraxml.data.model.Medicamento
import com.example.medhoraxml.data.model.MedHoraState
import com.example.medhoraxml.data.repository.MedicamentoRepository
import java.time.LocalDateTime
import java.time.ZoneId

/** Agenda somente a próxima ocorrência de cada horário da sessão, sem persistir medicamentos. */
object AgendadorLembretes {
    private data class Alarme(val medicamento: Medicamento, val dose: Dose, val operacao: PendingIntent,
                              val instante: Long, val exato: Boolean)
    private data class Assinatura(val medicamentos: List<Medicamento>, val tomadas: Set<String>,
                                  val ativado: Boolean, val permitido: Boolean, val exato: Boolean)
    private val agendados = mutableMapOf<String, Alarme>()
    private var assinatura: Assinatura? = null

    fun atualizar(context: Context, state: MedHoraState, forcar: Boolean = false) {
        val app = context.applicationContext
        Notificacoes.criarCanal(app)
        val alarmes = app.getSystemService(AlarmManager::class.java)
        val exato = Build.VERSION.SDK_INT < 31 || alarmes.canScheduleExactAlarms()
        val tomadas = state.doses.filter { it.tomadaEm != null }.map { it.chave }.toSet()
        val nova = Assinatura(state.medicamentos, tomadas, state.lembretes, Notificacoes.permitidas(app), exato)
        if (!forcar && nova == assinatura) return
        assinatura = nova
        val permitidos = mutableSetOf<String>()
        val agora = LocalDateTime.now()
        val epoch = System.currentTimeMillis()
        if (nova.ativado && nova.permitido) {
            state.medicamentos.forEach { med ->
                med.horarios.forEach horarioLoop@ { hora ->
                    val chaveHorario = "${med.id}|$hora"
                    permitidos += chaveHorario
                    val anterior = agendados[chaveHorario]
                    // Um alarme inexato já vencido pode estar aguardando o Android: não o adiar para amanhã.
                    if (!forcar && anterior != null && anterior.medicamento == med &&
                        anterior.dose.chave !in tomadas && anterior.instante <= epoch &&
                        epoch - anterior.instante < 86_400_000L) return@horarioLoop
                    val dia = (0L..7L).map { agora.toLocalDate().plusDays(it) }.firstOrNull {
                        !it.isBefore(med.criadoEm) && med.frequencia.ocorreEm(it) &&
                            it.atTime(hora).isAfter(agora) && "${med.id}|$it|$hora" !in tomadas
                    } ?: return@horarioLoop
                    val dose = Dose(med.id, med.nome, med.dosagem, med.cor, dia, hora)
                    val instante = dia.atTime(hora).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (anterior != null && anterior.dose == dose && anterior.exato == exato && anterior.instante == instante) return@horarioLoop
                    anterior?.let { cancelar(alarmes, it) }
                    val intent = Intent(app, LembreteReceiver::class.java).apply {
                        action = ACTION_LEMBRETE
                        data = Uri.Builder().scheme("medhora").authority("lembrete")
                            .appendPath(MedicamentoRepository.sessao).appendPath(chaveHorario).build()
                        putExtra("sessao", MedicamentoRepository.sessao)
                        putExtra("horarioId", chaveHorario)
                        putExtra("doseId", dose.chave)
                    }
                    val operacao = PendingIntent.getBroadcast(app, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    if (exato) {
                        try { alarmes.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, operacao) }
                        catch (_: SecurityException) { alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, operacao) }
                    } else alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, operacao)
                    agendados[chaveHorario] = Alarme(med, dose, operacao, instante, exato)
                }
            }
        }
        agendados.keys.toList().filter { it !in permitidos }.forEach { chave ->
            agendados.remove(chave)?.let { cancelar(alarmes, it) }
        }
        // Remove avisos de doses tomadas ou de medicamentos alterados/excluídos.
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        manager.activeNotifications.forEach { aviso ->
            val chave = aviso.tag ?: return@forEach
            val id = chave.substringBefore('|').toLongOrNull() ?: return@forEach
            val med = state.medicamentos.firstOrNull { it.id == id }
            val titulo = aviso.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
            val texto = aviso.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            val horario = chave.substringAfterLast('|')
            val corpoEsperado = med?.let { app.getString(com.example.medhoraxml.R.string.reminder_body, it.dosagem, horario) }
            if (!nova.ativado || !nova.permitido || chave in tomadas || med == null ||
                titulo != app.getString(com.example.medhoraxml.R.string.reminder_title, med.nome) || texto != corpoEsperado ||
                med.horarios.none { it.toString() == horario }) {
                manager.cancel(chave, 1)
            }
        }
    }

    fun entregar(context: Context, intent: Intent) {
        // Nunca restaura dados a partir de extras: alarmes de um processo anterior são descartados.
        if (intent.getStringExtra("sessao") != MedicamentoRepository.sessao) return
        val horarioId = intent.getStringExtra("horarioId") ?: return
        val esperado = agendados[horarioId] ?: return
        if (intent.getStringExtra("doseId") != esperado.dose.chave) return
        agendados.remove(horarioId)
        esperado.operacao.cancel()
        val state = MedicamentoRepository.state.value ?: return
        val med = state.medicamentos.firstOrNull { it.id == esperado.medicamento.id }
        if (med == esperado.medicamento && state.lembretes &&
            state.doses.none { it.chave == esperado.dose.chave && it.tomadaEm != null } &&
            esperado.instante <= System.currentTimeMillis()) {
            if (Notificacoes.mostrarDose(context, esperado.dose) && state.vozAtivada) {
                // Alarmes exatos solicitados pelo usuário permitem o serviço em segundo plano.
                // Se o sistema impedir o início, o aviso mantém a ação explícita “Ouvir lembrete”.
                VozLembreteService.iniciar(context, VozLembreteService.intentDose(context, esperado.dose))
            }
        }
        assinatura = null
        atualizar(context, state)
    }

    private fun cancelar(manager: AlarmManager, alarme: Alarme) {
        manager.cancel(alarme.operacao)
        alarme.operacao.cancel()
    }

    const val ACTION_LEMBRETE = "com.example.medhoraxml.LEMBRETE"
}
