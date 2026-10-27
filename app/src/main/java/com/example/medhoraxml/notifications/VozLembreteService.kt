package com.example.medhoraxml.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.Dose
import com.example.medhoraxml.data.repository.MedicamentoRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Serviço curto, visível e cancelável, somente enquanto a voz está sendo reproduzida. */
class VozLembreteService : Service() {
    private data class Pedido(val id: String, val texto: String, val dose: Dose? = null)
    private val handler = Handler(Looper.getMainLooper())
    private val fila = java.util.ArrayDeque<Pedido>()
    private var atual: Pedido? = null
    private var engine: TextToSpeech? = null
    private var pronto = false
    private var encerrado = false
    private var foco: AudioFocusRequest? = null
    private var possuiFoco = false
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val atributos = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val timeout = Runnable { encerrar() }
    private val listenerFoco = AudioManager.OnAudioFocusChangeListener { mudanca ->
        if (mudanca < 0) handler.post { encerrar() }
    }

    private val observer = androidx.lifecycle.Observer<com.example.medhoraxml.data.model.MedHoraState> {
        val pedido = atual
        if (!encerrado && pedido != null && !pedidoValido(pedido)) {
            engine?.stop()
            atual = null
            falarProximo()
        }
    }

    override fun onCreate() {
        super.onCreate()
        MedicamentoRepository.state.observeForever(observer)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { encerrar(); return START_NOT_STICKY }
        if (encerrado) { stopSelf(); return START_NOT_STICKY }
        // startForeground é imediato, inclusive enquanto o motor de voz inicializa.
        try {
            val aviso = notificacaoDeLeitura()
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID_NOTIFICACAO, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ID_NOTIFICACAO, aviso)
        } catch (_: SecurityException) { encerrar(); return START_NOT_STICKY }
          catch (_: IllegalStateException) { encerrar(); return START_NOT_STICKY }
        val pedido = intent?.let(::interpretar)
        if (pedido == null) {
            if (atual == null && fila.isEmpty()) encerrar()
            return START_NOT_STICKY
        }
        if (atual?.id != pedido.id && fila.none { it.id == pedido.id }) fila.addLast(pedido)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 90_000)
        if (engine == null) iniciarMotor() else if (pronto && atual == null) falarProximo()
        return START_NOT_STICKY
    }

    private fun interpretar(intent: Intent): Pedido? {
        if (intent.getStringExtra("sessao") != MedicamentoRepository.sessao) return null
        if (intent.action == ACTION_PREVIEW) {
            val med = MedicamentoRepository.state.value?.medicamentos?.firstOrNull()
            val texto = med?.let { frase(it.nome, it.dosagem) } ?: getString(R.string.voice_preview_empty)
            return Pedido("previa", texto)
        }
        if (intent.action != ACTION_FALAR || MedicamentoRepository.state.value?.vozAtivada != true) return null
        val med = MedicamentoRepository.buscar(intent.getLongExtra("medicamentoId", -1)) ?: return null
        val data = runCatching { LocalDate.parse(intent.getStringExtra("data")) }.getOrNull() ?: return null
        val hora = runCatching { LocalTime.parse(intent.getStringExtra("horario")) }.getOrNull() ?: return null
        if (data.isBefore(med.criadoEm) || data.isAfter(LocalDate.now()) || !med.frequencia.ocorreEm(data) || hora !in med.horarios) return null
        val dose = Dose(med.id, med.nome, med.dosagem, med.cor, data, hora)
        if (MedicamentoRepository.state.value?.doses.orEmpty().any { it.chave == dose.chave && it.tomadaEm != null }) return null
        return Pedido(dose.chave, frase(med.nome, med.dosagem), dose)
    }

    private fun frase(nome: String, dosagem: String) = getString(R.string.voice_sentence, nome, TextoDaDose.paraFala(dosagem))

    private fun iniciarMotor() {
        engine = TextToSpeech(applicationContext) { status ->
            handler.post {
                if (encerrado) return@post
                val tts = engine ?: return@post
                if (status != TextToSpeech.SUCCESS) { falhar(R.string.voice_missing); return@post }
                val idioma = tts.setLanguage(Locale.forLanguageTag("pt-BR"))
                val voz = tts.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == "pt" }
                    ?.sortedBy { if (it.locale.country == "BR") 0 else 1 }?.firstOrNull()
                if (idioma < TextToSpeech.LANG_AVAILABLE || voz == null || tts.setVoice(voz) == TextToSpeech.ERROR) {
                    falhar(R.string.voice_missing)
                    return@post
                }
                tts.setAudioAttributes(atributos)
                tts.setSpeechRate(0.85f)
                tts.setPitch(1.0f)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { finalizarFrase(utteranceId) }
                    @Deprecated("Callback de compatibilidade do Android")
                    override fun onError(utteranceId: String?) { finalizarFrase(utteranceId, erro = true) }
                    override fun onError(utteranceId: String?, errorCode: Int) { finalizarFrase(utteranceId, erro = true) }
                })
                pronto = true
                falarProximo()
            }
        }
    }

    private fun finalizarFrase(id: String?, erro: Boolean = false) {
        handler.post {
            if (!encerrado && atual?.id == id) {
                if (erro) Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
                atual = null
                falarProximo()
            }
        }
    }

    private fun falarProximo() {
        if (encerrado || !pronto || atual != null) return
        var pedido = fila.pollFirst()
        // A dose pode ter sido tomada ou excluída enquanto aguardava outra leitura.
        while (pedido != null && !pedidoValido(pedido)) pedido = fila.pollFirst()
        if (pedido == null) { encerrar(); return }
        if (!obterFoco()) { falhar(R.string.voice_unavailable); return }
        atual = pedido
        val parametros = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        if (engine?.speak(pedido.texto, TextToSpeech.QUEUE_FLUSH, parametros, pedido.id) == TextToSpeech.ERROR) {
            falhar(R.string.voice_unavailable)
        }
    }

    private fun pedidoValido(pedido: Pedido): Boolean {
        val dose = pedido.dose ?: return true
        val state = MedicamentoRepository.state.value ?: return false
        val med = state.medicamentos.firstOrNull { it.id == dose.medicamentoId } ?: return false
        return state.lembretes && state.vozAtivada && med.nome == dose.nome && med.dosagem == dose.dosagem &&
            dose.horario in med.horarios && med.frequencia.ocorreEm(dose.data) &&
            state.doses.none { it.chave == dose.chave && it.tomadaEm != null }
    }

    @Suppress("DEPRECATION")
    private fun obterFoco(): Boolean {
        if (possuiFoco) return true
        if (audio.mode != AudioManager.MODE_NORMAL) return false
        val resultado = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(atributos).setOnAudioFocusChangeListener(listenerFoco, handler)
                .setAcceptsDelayedFocusGain(false).build()
            foco = request
            audio.requestAudioFocus(request)
        } else audio.requestAudioFocus(listenerFoco, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        possuiFoco = resultado == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return possuiFoco
    }

    private fun notificacaoDeLeitura(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CANAL_VOZ, getString(R.string.voice_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                })
        }
        val parar = PendingIntent.getService(this, 0, Intent(this, VozLembreteService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CANAL_VOZ).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.voice_playing)).setOngoing(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(R.drawable.ic_check, getString(R.string.voice_stop), parar).build()
    }

    private fun falhar(mensagem: Int) {
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
        encerrar()
    }

    private fun encerrar() {
        if (encerrado) return
        encerrado = true
        engine?.stop()
        fila.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        encerrado = true
        handler.removeCallbacksAndMessages(null)
        MedicamentoRepository.state.removeObserver(observer)
        engine?.stop()
        engine?.shutdown()
        engine = null
        if (possuiFoco) {
            if (Build.VERSION.SDK_INT >= 26) foco?.let { audio.abandonAudioFocusRequest(it) }
            else audio.abandonAudioFocus(listenerFoco)
        }
        possuiFoco = false
        super.onDestroy()
    }

    companion object {
        private const val CANAL_VOZ = "medhora_voz_v1"
        private const val ID_NOTIFICACAO = 2001
        private const val ACTION_FALAR = "com.example.medhoraxml.FALAR"
        private const val ACTION_PREVIEW = "com.example.medhoraxml.PREVIA_VOZ"
        private const val ACTION_STOP = "com.example.medhoraxml.PARAR_VOZ"

        fun intentDose(context: Context, dose: Dose): Intent = Intent(context, VozLembreteService::class.java).apply {
            action = ACTION_FALAR
            data = Uri.Builder().scheme("medhora").authority("voz").appendPath(MedicamentoRepository.sessao).appendPath(dose.chave).build()
            putExtra("sessao", MedicamentoRepository.sessao)
            putExtra("medicamentoId", dose.medicamentoId)
            putExtra("data", dose.data.toString())
            putExtra("horario", dose.horario.toString())
        }

        fun iniciar(context: Context, intent: Intent): Boolean = try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: IllegalStateException) { false } catch (_: SecurityException) { false }

        fun ouvirPrevia(context: Context): Boolean = iniciar(context,
            Intent(context, VozLembreteService::class.java).setAction(ACTION_PREVIEW)
                .putExtra("sessao", MedicamentoRepository.sessao))
    }
}
