package com.example.medhoraxml.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.medhoraxml.MainActivity
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.Dose
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.ui.common.texto

/** Apresentação dos lembretes. O som do canal é controlado pelo usuário no Android 8+. */
object Notificacoes {
    const val CANAL_DOSES = "medhora_doses_v1"
    var somLegado: Uri? = Settings.System.DEFAULT_NOTIFICATION_URI

    fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val canal = NotificationChannel(CANAL_DOSES, context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_channel_description)
                enableVibration(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
            // Recriar o mesmo ID não sobrescreve as escolhas feitas pelo usuário no sistema.
            manager.createNotificationChannel(canal)
        }
    }

    fun permitidas(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CANAL_DOSES)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun abrirPreferencias(context: Context) {
        criarCanal(context)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CANAL_DOSES)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }

    fun nomeDoSom(context: Context): String {
        criarCanal(context)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CANAL_DOSES)?.sound else somLegado
        if (uri == null) return context.getString(R.string.sound_silent)
        return try {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: context.getString(R.string.sound_default)
        } catch (_: SecurityException) { context.getString(R.string.sound_default) }
    }

    @Suppress("MissingPermission")
    fun mostrarDose(context: Context, dose: Dose): Boolean {
        criarCanal(context)
        if (!permitidas(context)) return false
        val abrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val titulo = context.getString(R.string.reminder_title, dose.nome)
        val texto = context.getString(R.string.reminder_body, dose.dosagem, dose.horario.texto())
        val builder = NotificationCompat.Builder(context, CANAL_DOSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.medhora_green_primary))
            .setContentTitle(titulo).setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$titulo\n$texto"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true)
            .setSound(somLegado)
        if (MedicamentoRepository.state.value?.vozAtivada == true) {
            val intentVoz = VozLembreteService.intentDose(context, dose)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val falar = if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, 0, intentVoz, flags)
                else PendingIntent.getService(context, 0, intentVoz, flags)
            builder.addAction(R.drawable.ic_bell, context.getString(R.string.voice_listen), falar)
        }
        val notificacao = builder.build()
        return try {
            // Tag completa evita colisões entre medicamentos e entre dias/horários.
            NotificationManagerCompat.from(context).notify(dose.chave, 1, notificacao)
            true
        } catch (_: SecurityException) { false }
    }

    fun cancelarDose(context: Context, chave: String) {
        NotificationManagerCompat.from(context).cancel(chave, 1)
    }
}
