package br.com.usinagemmaster.domain.catalog

import br.com.usinagemmaster.data.local.entity.EmployeeEntity

/**
 * Resolve a identidade visual de cada funcionário de forma determinística.
 * O mesmo funcionário mantém a mesma aparência na lista, fábrica e retratos.
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

    fun resolve(employee: EmployeeEntity): EmployeeVisualStyle = resolve(
        id = employee.id,
        name = employee.name,
        legendaryCode = employee.legendaryCode
    )

    fun resolve(id: String, name: String, legendaryCode: String?): EmployeeVisualStyle {
        val legendarySkin = when (legendaryCode) {
            "tatu_banhado" -> "TATUZAO"
            "kendao" -> "KENDAO_KIMONO"
            "nikao_narizudo" -> "PINOQUIO"
            "magrao" -> "MAGRAO"
            "nelsinho_treme_treme" -> "TREME_TREME"
            "chupa_engole" -> "BEBADO"
            else -> null
        }
        val firstName = name.substringBefore(' ')
        val female = firstName in femaleFirstNames
        val seed = kotlin.math.abs((id.ifBlank { name }).hashCode())
        val variant = seed % 100
        val skinStyle = legendarySkin ?: when {
            female && variant < 18 -> "PRINCESA"
            !female && variant < 7 -> "TATUZAO"
            !female && variant in 7..13 -> "MAGRAO"
            variant in 14..17 -> "TREME_TREME"
            else -> "WORKSHOP"
        }
        val hairStyle = when {
            skinStyle == "PRINCESA" -> "LONG"
            female && seed % 3 == 0 -> "PONYTAIL"
            female && seed % 3 == 1 -> "LONG"
            female -> "CURLY"
            seed % 5 == 0 -> "BUZZ"
            else -> "SHORT"
        }
        val hairColor = when (seed % 4) {
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
