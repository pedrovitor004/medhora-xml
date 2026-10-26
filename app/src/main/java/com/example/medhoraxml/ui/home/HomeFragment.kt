package com.example.medhoraxml.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R

class HomeFragment : Fragment(R.layout.fragment_home) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_cadastro).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_cadastro)
        }
        view.findViewById<Button>(R.id.button_detalhes).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_detalhes)
        }
        view.findViewById<Button>(R.id.button_historico).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_historico)
        }
    }
}