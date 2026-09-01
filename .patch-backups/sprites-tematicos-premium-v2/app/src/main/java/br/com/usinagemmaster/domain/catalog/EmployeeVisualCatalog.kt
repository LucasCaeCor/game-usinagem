package br.com.usinagemmaster.domain.catalog

import br.com.usinagemmaster.data.local.entity.EmployeeEntity

/**
 * Direção visual dos personagens do chão de fábrica.
 *
 * Não altera o save/Room: o visual é resolvido a partir do funcionário existente,
 * permitindo evoluir a arte sem migração de banco.
 */
data class EmployeeVisualStyle(
    val skinStyle: String,
    val female: Boolean,
    val hairStyle: String,
    val hairColor: String
)

object EmployeeVisualCatalog {
    private val femaleFirstNames = setOf(
        "Luciana", "Patrícia", "Camila", "Fernanda", "Amanda", "Juliana", "Mariana",
        "Beatriz", "Renata", "Larissa", "Daniela", "Aline", "Carolina", "Bianca",
        "Vanessa", "Jéssica", "Natália", "Priscila", "Letícia", "Isabela"
    )

    fun resolve(employee: EmployeeEntity): EmployeeVisualStyle {
        val legendarySkin = when (employee.legendaryCode) {
            "tatu_banhado" -> "TATUZAO"
            "kendao" -> "KENDAO_KIMONO"
            "nikao_narizudo" -> "PINOQUIO"
            "magrao" -> "MAGRAO"
            "nelsinho_treme_treme" -> "TREME_TREME"
            "chupa_engole" -> "BEBADO"
            else -> null
        }

        val firstName = employee.name.substringBefore(' ')
        val female = firstName in femaleFirstNames
        val variant = kotlin.math.abs(employee.id.hashCode()) % 100
        val skinStyle = legendarySkin ?: when {
            female && variant < 18 -> "PRINCESA"
            !female && variant < 7 -> "TATUZAO"
            !female && variant in 7..13 -> "MAGRAO"
            variant in 14..17 -> "TREME_TREME"
            else -> "WORKSHOP"
        }
        val hairStyle = when {
            female && employee.id.hashCode() % 2 == 0 -> "PONYTAIL"
            female -> "LONG"
            employee.id.hashCode() % 5 == 0 -> "BUZZ"
            else -> "SHORT"
        }
        val hairColor = when (kotlin.math.abs(employee.id.hashCode()) % 4) {
            0 -> "DARK"
            1 -> "BROWN"
            2 -> "BLONDE"
            else -> "GRAY"
        }
        return EmployeeVisualStyle(skinStyle, female, hairStyle, hairColor)
    }

    fun label(style: String): String = when (style) {
        "TATUZAO" -> "Tatuzão"
        "PRINCESA" -> "Princesa"
        "PINOQUIO" -> "Pinóquio narigudo"
        "MAGRAO" -> "Magrão e alto"
        "KENDAO_KIMONO" -> "Kendão de kimono"
        "TREME_TREME" -> "Treme-treme"
        "BEBADO" -> "Bêbado"
        else -> "Operário"
    }
}
