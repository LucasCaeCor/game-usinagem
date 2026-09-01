package br.com.usinagemmaster.core.util

import java.text.NumberFormat
import java.util.Locale

object Formatters {
    private val br = Locale("pt", "BR")
    private val currency = NumberFormat.getCurrencyInstance(br)

    fun money(cents: Long): String = currency.format(cents / 100.0)

    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}min"
            h > 0 -> "${h}h"
            else -> "${m}min"
        }
    }
}
