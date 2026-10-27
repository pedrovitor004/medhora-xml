package com.example.medhoraxml.ui.home

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DoseConfirmadaDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = layoutInflater.inflate(R.layout.dialog_dose_confirmada, null)
        val args = requireArguments()
        content.findViewById<TextView>(R.id.confirmation_body).text = getString(R.string.confirm_body,
            args.getString("nome"), args.getString("dosagem"), args.getString("horario"))
        content.findViewById<android.view.View>(R.id.confirm_ok).setOnClickListener { findNavController().popBackStack() }
        return MaterialAlertDialogBuilder(requireContext()).setView(content).create().apply {
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setDimAmount(0.55f)
    }
}
