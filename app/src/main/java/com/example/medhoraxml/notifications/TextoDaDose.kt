package com.example.medhoraxml.notifications

/** Expande apenas unidades conhecidas. Não converte quantidades ou unidades médicas. */
object TextoDaDose {
    fun paraFala(dosagem: String): String {
        val unidades = mapOf("mcg" to "microgramas", "µg" to "microgramas", "μg" to "microgramas",
            "mg" to "miligramas", "ml" to "mililitros", "g" to "gramas", "ui" to "unidades internacionais")
        return Regex("(?i)(?<=\\d)\\s*(mcg|µg|μg|mg|ml|g|ui)\\b").replace(dosagem) {
            " " + unidades.getValue(it.groupValues[1].lowercase(java.util.Locale.ROOT))
        }
    }
}
