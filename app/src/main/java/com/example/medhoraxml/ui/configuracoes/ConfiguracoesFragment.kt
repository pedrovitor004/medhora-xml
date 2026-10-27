package com.example.medhoraxml.ui.configuracoes

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.media.AudioManager
import android.widget.SeekBar
import com.example.medhoraxml.notifications.VozLembreteService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.medhoraxml.R
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.notifications.Notificacoes
import com.example.medhoraxml.ui.common.configurarCabecalho
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

class ConfiguracoesFragment : Fragment(R.layout.fragment_configuracoes) {
    private var sincronizando = false
    private val permissao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        MedicamentoRepository.definirLembretes(granted && Notificacoes.permitidas(requireContext()))
        if (!granted) mostrarPermissaoNegada()
        atualizarEstado()
    }
    @Suppress("DEPRECATION")
    private val escolherSom = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            Notificacoes.somLegado = if (Build.VERSION.SDK_INT >= 33)
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            atualizarEstado()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configurarCabecalho(view)
        Notificacoes.criarCanal(requireContext())
        view.findViewById<MaterialSwitch>(R.id.reminders).setOnCheckedChangeListener { _, checked ->
            if (!sincronizando) {
                if (!checked) MedicamentoRepository.definirLembretes(false)
                else if (Notificacoes.permitidas(requireContext())) MedicamentoRepository.definirLembretes(true)
                else if (Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else mostrarPermissaoNegada()
                atualizarEstado()
            }
        }
        view.findViewById<View>(R.id.sound).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 26) Notificacoes.abrirPreferencias(requireContext())
            else {
                val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Notificacoes.somLegado)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.sound_choose))
                try { escolherSom.launch(picker) } catch (_: ActivityNotFoundException) {
                    Notificacoes.abrirPreferencias(requireContext())
                }
            }
        }
        view.findViewById<View>(R.id.exact_alarm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${requireContext().packageName}")))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${requireContext().packageName}")))
                }
            }
        }
        view.findViewById<MaterialSwitch>(R.id.voice_enabled).setOnCheckedChangeListener { _, enabled ->
            if (!sincronizando) {
                MedicamentoRepository.definirVoz(enabled)
                if (!enabled) requireContext().stopService(Intent(requireContext(), VozLembreteService::class.java))
            }
        }
        val audio = requireContext().getSystemService(AudioManager::class.java)
        view.findViewById<SeekBar>(R.id.voice_volume).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    view.findViewById<TextView>(R.id.voice_volume_label).text = getString(R.string.voice_volume,
                        progress * 100 / maxOf(1, max))
                    if (fromUser) {
                        try { audio.setStreamVolume(AudioManager.STREAM_ALARM, progress, 0) }
                        catch (_: SecurityException) {
                            Snackbar.make(view, R.string.voice_unavailable, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        view.findViewById<View>(R.id.voice_preview).setOnClickListener {
            if (!VozLembreteService.ouvirPrevia(requireContext())) {
                Snackbar.make(view, R.string.voice_unavailable, Snackbar.LENGTH_LONG).show()
            }
        }
        view.findViewById<View>(R.id.voice_settings).setOnClickListener {
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            catch (_: ActivityNotFoundException) {
                Snackbar.make(view, R.string.voice_settings_missing, Snackbar.LENGTH_LONG).show()
            }
        }
        MedicamentoRepository.state.observe(viewLifecycleOwner) { atualizarEstado() }
    }

    override fun onResume() {
        super.onResume()
        atualizarEstado()
    }

    private fun atualizarEstado() {
        val view = view ?: return
        sincronizando = true
        view.findViewById<MaterialSwitch>(R.id.reminders).isChecked =
            MedicamentoRepository.state.value?.lembretes == true && Notificacoes.permitidas(requireContext())
        view.findViewById<MaterialSwitch>(R.id.voice_enabled).isChecked = MedicamentoRepository.state.value?.vozAtivada == true
        sincronizando = false
        val audio = requireContext().getSystemService(AudioManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = maxOf(1, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
        view.findViewById<SeekBar>(R.id.voice_volume).progress = volume
        view.findViewById<TextView>(R.id.voice_volume_label).text = getString(R.string.voice_volume, volume * 100 / maxVolume)
        view.findViewById<TextView>(R.id.sound_name).text = Notificacoes.nomeDoSom(requireContext())
        view.findViewById<MaterialButton>(R.id.exact_alarm).apply {
            val exatos = Build.VERSION.SDK_INT < 31 || requireContext().getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            setText(if (exatos) R.string.exact_alarm_enabled else R.string.exact_alarm_disabled)
            isEnabled = Build.VERSION.SDK_INT >= 31
        }
    }

    private fun mostrarPermissaoNegada() {
        view?.let {
            Snackbar.make(it, R.string.notification_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.notification_settings) { Notificacoes.abrirPreferencias(requireContext()) }.show()
        }
    }
}
