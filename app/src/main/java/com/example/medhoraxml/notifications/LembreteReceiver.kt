package com.example.medhoraxml.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medhoraxml.data.repository.MedicamentoRepository

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AgendadorLembretes.ACTION_LEMBRETE) {
            AgendadorLembretes.entregar(context, intent)
        } else {
            MedicamentoRepository.state.value?.let { AgendadorLembretes.atualizar(context, it, forcar = true) }
        }
    }
}
