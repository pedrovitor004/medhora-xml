package com.example.medhoraxml.ui.welcome

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.medhoraxml.R

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val comecar = View.OnClickListener {
            findNavController().navigate(R.id.homeFragment, null,
                NavOptions.Builder().setPopUpTo(R.id.onboardingFragment, true).build())
        }
        view.findViewById<View>(R.id.start).setOnClickListener(comecar)
        view.findViewById<View>(R.id.skip).setOnClickListener(comecar)
    }
}
