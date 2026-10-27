package com.example.medhoraxml.ui.cadastro

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R
import com.example.medhoraxml.data.model.CorMedicamento
import com.example.medhoraxml.data.model.Frequencia
import com.example.medhoraxml.data.repository.MedicamentoRepository
import com.example.medhoraxml.ui.common.identificar
import com.example.medhoraxml.ui.common.texto
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime

class CadastroFragment : Fragment(R.layout.fragment_cadastro) {
    private val model: CadastroViewModel by viewModels()
    private var id: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        id = requireArguments().getLong("medicamentoId", -1).takeIf { it != -1L }
        val existente = id?.let(MedicamentoRepository::buscar)
        if (id != null && existente == null) {
            Snackbar.make(requireActivity().findViewById(R.id.main), R.string.missing_medicine, Snackbar.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }
        model.inicializar(existente)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(if (id == null) R.string.cadastro_title else R.string.editar_title)
        toolbar.setNavigationOnClickListener { voltar() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = voltar()
        })
        val nome = view.findViewById<TextInputEditText>(R.id.name)
        val dose = view.findViewById<TextInputEditText>(R.id.dose)
        val notes = view.findViewById<TextInputEditText>(R.id.notes)
        nome.setText(model.rascunho.nome)
        dose.setText(model.rascunho.dosagem)
        notes.setText(model.rascunho.observacoes)
        nome.doAfterTextChanged {
            model.rascunho = model.rascunho.copy(nome = it?.toString().orEmpty())
            view.findViewById<TextInputLayout>(R.id.name_layout).error = null
        }
        dose.doAfterTextChanged {
            model.rascunho = model.rascunho.copy(dosagem = it?.toString().orEmpty())
            view.findViewById<TextInputLayout>(R.id.dose_layout).error = null
        }
        notes.doAfterTextChanged { model.rascunho = model.rascunho.copy(observacoes = it?.toString().orEmpty()) }
        val frequency = view.findViewById<MaterialAutoCompleteTextView>(R.id.frequency)
        frequency.keyListener = null
        frequency.setSimpleItems(R.array.frequency_options)
        frequency.setText(resources.getStringArray(R.array.frequency_options)[model.rascunho.frequencia.ordinal], false)
        frequency.setOnItemClickListener { _, _, position, _ ->
            model.rascunho = model.rascunho.copy(frequencia = Frequencia.entries[position])
        }
        val colorIds = listOf(R.id.color_green, R.id.color_red, R.id.color_yellow, R.id.color_lilac)
        val colors = view.findViewById<ChipGroup>(R.id.color_group)
        colors.check(colorIds[model.rascunho.cor.ordinal])
        view.findViewById<ImageView>(R.id.medicine_icon).identificar(model.rascunho.cor)
        colors.setOnCheckedStateChangeListener { _, checked ->
            val index = checked.firstOrNull()?.let(colorIds::indexOf) ?: -1
            if (index >= 0) {
                model.rascunho = model.rascunho.copy(cor = CorMedicamento.entries[index])
                view.findViewById<ImageView>(R.id.medicine_icon).identificar(model.rascunho.cor)
            }
        }
        renderizarHorarios(view)
        view.findViewById<View>(R.id.add_time).setOnClickListener {
            if (model.rascunho.horarios.size >= 12) {
                Snackbar.make(view, R.string.error_max_times, Snackbar.LENGTH_SHORT).show()
            } else selecionarHorario(null)
        }
        view.findViewById<MaterialButton>(R.id.save).apply {
            setText(if (id == null) R.string.save else R.string.save_changes)
            setOnClickListener { salvar(view) }
        }
        // Reassocia o listener ao picker restaurado pelo FragmentManager na rotação.
        (childFragmentManager.findFragmentByTag(TIME_PICKER) as? MaterialTimePicker)?.let(::observarPicker)
    }

    private fun renderizarHorarios(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.time_container)
        container.removeAllViews()
        model.rascunho.horarios.sorted().forEach { time ->
            val row = layoutInflater.inflate(R.layout.item_time, container, false)
            row.findViewById<MaterialButton>(R.id.time_value).apply {
                text = time.texto()
                setOnClickListener { selecionarHorario(time) }
            }
            row.findViewById<View>(R.id.remove_time).apply {
                contentDescription = getString(R.string.remove_time, time.texto())
                setOnClickListener {
                    model.rascunho = model.rascunho.copy(horarios = model.rascunho.horarios - time)
                    renderizarHorarios(view)
                }
            }
            container.addView(row)
        }
        view.findViewById<TextView>(R.id.time_error).isVisible = false
    }

    private fun selecionarHorario(anterior: LocalTime?) {
        if (childFragmentManager.findFragmentByTag(TIME_PICKER) != null) return
        val horario = anterior ?: LocalTime.of(8, 0)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H).setHour(horario.hour).setMinute(horario.minute)
            .setTitleText(R.string.select_time).setPositiveButtonText(R.string.ok)
            .setNegativeButtonText(R.string.cancel).build()
        picker.requireArguments().putString("horarioAnterior", anterior?.toString())
        observarPicker(picker)
        picker.show(childFragmentManager, TIME_PICKER)
    }

    private fun observarPicker(picker: MaterialTimePicker) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener {
            val view = view ?: return@addOnPositiveButtonClickListener
            val anterior = picker.arguments?.getString("horarioAnterior")?.let(LocalTime::parse)
            val novo = LocalTime.of(picker.hour, picker.minute)
            if (novo != anterior && novo in model.rascunho.horarios) {
                Snackbar.make(view, R.string.error_duplicate, Snackbar.LENGTH_SHORT).show()
            } else {
                model.rascunho = model.rascunho.copy(horarios = (model.rascunho.horarios - listOfNotNull(anterior) + novo).sorted())
                renderizarHorarios(view)
            }
        }
    }

    private fun salvar(view: View) {
        val draft = model.rascunho
        val nameLayout = view.findViewById<TextInputLayout>(R.id.name_layout)
        val doseLayout = view.findViewById<TextInputLayout>(R.id.dose_layout)
        nameLayout.error = if (draft.nome.isBlank()) getString(R.string.error_name) else null
        doseLayout.error = if (draft.dosagem.isBlank()) getString(R.string.error_dose) else null
        view.findViewById<TextView>(R.id.time_error).apply {
            isVisible = draft.horarios.isEmpty()
            setText(R.string.error_time)
        }
        if (draft.nome.isBlank()) { view.findViewById<View>(R.id.name).requestFocus(); return }
        if (draft.dosagem.isBlank()) { view.findViewById<View>(R.id.dose).requestFocus(); return }
        if (draft.horarios.isEmpty()) return
        if (id != null && MedicamentoRepository.buscar(id!!) == null) {
            Snackbar.make(view, R.string.missing_medicine, Snackbar.LENGTH_SHORT).show()
            return
        }
        view.findViewById<View>(R.id.save).isEnabled = false
        MedicamentoRepository.salvar(id, draft.nome, draft.dosagem, draft.horarios,
            draft.frequencia, draft.observacoes, draft.cor)
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
        val nav = findNavController()
        nav.popBackStack()
        val aviso = Snackbar.make(requireActivity().findViewById(R.id.main),
            if (id == null) R.string.saved else R.string.updated, Snackbar.LENGTH_LONG)
        if (MedicamentoRepository.state.value?.lembretes != true) {
            aviso.setAction(R.string.enable_reminders) { nav.navigate(R.id.configuracoesFragment) }
        }
        aviso.show()
    }

    private fun voltar() {
        if (!model.alterado) { findNavController().popBackStack(); return }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_body).setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.discard) { _, _ -> findNavController().popBackStack() }.show()
    }

    companion object { private const val TIME_PICKER = "medhora_time_picker" }
}
