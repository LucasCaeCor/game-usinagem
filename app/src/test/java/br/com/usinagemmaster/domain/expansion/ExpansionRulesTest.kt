package br.com.usinagemmaster.domain.expansion

import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionRulesTest {
    @Test fun legendarySkinAddsRealProductionBenefit() {
        val state = ExpansionState(equippedSkin = "kendao", ownedSkins = setOf("operador_padrao", "kendao"))
        val modifiers = state.productionModifiers()
        assertTrue(modifiers.millingMultiplier > 1.0)
        assertTrue(modifiers.cncMultiplier > 1.0)
    }

    @Test fun highAdvanceToolIsFasterThanBasicTool() {
        val high = ExpansionCatalog.tools.first { it.id == "fresa_alto_avanco" }
        val basic = ExpansionCatalog.tools.first { it.id == "broca_madeira" }
        assertTrue(high.speedMultiplier > basic.speedMultiplier)
    }

    @Test fun gachaCharacterCanBoostProduction() {
        val state = ExpansionState(ownedCharacters = setOf("mestre_5_eixos"), equippedCharacter = "mestre_5_eixos")
        assertTrue(state.productionModifiers().globalSpeedMultiplier > 1.0)
    }

    @Test fun topContractHasProgressionGate() {
        val gate = ExpansionCatalog.contractGate(5)
        assertTrue(gate.minLevel >= 10)
        assertTrue(gate.requiresSpecialty)
    }
}
