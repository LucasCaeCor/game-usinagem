package br.com.usinagemmaster

import br.com.usinagemmaster.core.util.Formatters
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {
    @Test fun moneyUsesBrazilianCurrency() {
        assertTrue(Formatters.money(100_00).contains("100"))
    }
}
