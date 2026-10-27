package com.example.medhoraxml

import android.app.Application
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.notifications.AgendadorLembretes
import com.example.medhoraxml.notifications.Notificacoes

class MedHoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanal(this)
        // Observer pertence ao processo inteiro; não depende de uma Activity estar visível.
        MedicamentoRepository.state.observeForever { state -> AgendadorLembretes.atualizar(this, state) }
    }
}
