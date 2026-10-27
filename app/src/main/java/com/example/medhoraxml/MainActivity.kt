package com.example.medhoraxml

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.medhoraxml.data.repository.MedicamentoRepository

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Rota e rascunhos de um processo encerrado não pertencem ao novo repositório.
        val estadoDaSessao = savedInstanceState?.takeIf {
            it.getString("medhoraSession") == MedicamentoRepository.sessao
        }
        super.onCreate(estadoDaSessao)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        val bottom = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottom.itemIconTintList = null
        val principais = setOf(R.id.homeFragment, R.id.historicoFragment,
            R.id.medicamentosFragment, R.id.configuracoesFragment)
        bottom.setOnItemSelectedListener { item ->
            if (nav.currentDestination?.id != item.itemId) {
                if (item.itemId == R.id.cadastroFragment) nav.navigate(item.itemId)
                else nav.navigate(item.itemId, null, NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, false).setLaunchSingleTop(true).build())
            }
            true
        }
        bottom.setOnItemReselectedListener { }
        nav.addOnDestinationChangedListener { _, destination, _ ->
            val tela = if (destination.id == R.id.doseConfirmadaDialog) R.id.homeFragment else destination.id
            bottom.isVisible = tela in principais
            if (tela in principais) bottom.menu.findItem(tela)?.isChecked = true
            for (index in 0 until bottom.menu.size()) {
                val item = bottom.menu.getItem(index)
                if (item.itemId != R.id.cadastroFragment) {
                    item.icon = item.icon?.mutate()?.apply {
                        setTint(getColor(if (item.itemId == tela) R.color.medhora_green_primary else R.color.medhora_muted))
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        MedicamentoRepository.state.value?.let {
            com.example.medhoraxml.notifications.AgendadorLembretes.atualizar(this, it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("medhoraSession", MedicamentoRepository.sessao)
        super.onSaveInstanceState(outState)
    }
}
