package com.example.medhoraxml.ui.cadastro

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R

class CadastroFragment : Fragment(R.layout.fragment_cadastro) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_back_home).setOnClickListener {
            findNavController().popBackStack()
        }
    }
}