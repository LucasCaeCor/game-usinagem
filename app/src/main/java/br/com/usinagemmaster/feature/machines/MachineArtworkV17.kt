package br.com.usinagemmaster.feature.machines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * V17 — miniatura original estilo catálogo industrial.
 *
 * Não usa imagem da internet nem asset com licença externa.
 * A categoria é inferida do nome/tipo da máquina e cada família recebe
 * uma silhueta própria. Depois é possível trocar apenas este componente
 * por PNG/WebP sem alterar Loja, Fábrica ou regras de negócio.
 */
@Composable
fun MachineArtworkV17(
    label: String,
    modifier: Modifier = Modifier,
    machineType: String = label,
) {
    val key = "$machineType $label".uppercase(Locale.ROOT)

    Box(
        modifier = modifier
            .width(118.dp)
            .height(84.dp)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF263740),
                        Color(0xFF111D23),
                        Color(0xFF091116),
                    )
                ),
                shape = RoundedCornerShape(15.dp),
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            // parede de showroom
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF31444D),
                        Color(0xFF18262D),
                        Color(0xFF0C151A),
                    )
                )
            )

            // luz superior
            drawRoundRect(
                color = Color(0xFFEAF8FF).copy(alpha = .12f),
                topLeft = Offset(size.width * .18f, size.height * .08f),
                size = Size(size.width * .64f, size.height * .05f),
                cornerRadius = CornerRadius(30f, 30f),
            )

            // piso
            drawRect(
                color = Color(0xFF0B1114),
                topLeft = Offset(0f, size.height * .70f),
                size = Size(size.width, size.height * .30f),
            )
            drawLine(
                color = Color(0xFFFFC247).copy(alpha = .75f),
                start = Offset(size.width * .07f, size.height * .87f),
                end = Offset(size.width * .93f, size.height * .87f),
                strokeWidth = 2.4f,
            )

            // sombra da máquina
            drawOval(
                color = Color.Black.copy(alpha = .40f),
                topLeft = Offset(size.width * .18f, size.height * .70f),
                size = Size(size.width * .66f, size.height * .13f),
            )

            when {
                "5_AXIS" in key || "5 EIX" in key || "5-EIX" in key ->
                    drawMachiningCenter(fiveAxis = true)

                "MACHINING_CENTER" in key || "CENTRO DE USINAGEM" in key ||
                    ("CNC" in key && ("MILL" in key || "FRESA" in key)) ->
                    drawMachiningCenter(fiveAxis = false)

                "CNC_LATHE" in key || ("CNC" in key && "TORNO" in key) ->
                    drawLathe(cnc = true)

                "LATHE" in key || "TORNO" in key ->
                    drawLathe(cnc = false)

                "MILL" in key || "FRESA" in key || "FRESADORA" in key ->
                    drawMill()

                "DRILL" in key || "FURADEIRA" in key ->
                    drawDrill()

                "GRINDER" in key || "RETIF" in key ->
                    drawGrinder()

                "WELD" in key || "SOLDA" in key || "ROBOT" in key ->
                    drawWelding()

                "LASER" in key || "PLASMA" in key || "CORTE" in key ->
                    drawLaser()

                "EDM" in key || "EROS" in key ->
                    drawEdm()

                else -> drawGenericMachine()
            }

            // badge visual / parafusos
            drawCircle(
                color = Color(0xFF63E8A5),
                radius = size.minDimension * .028f,
                center = Offset(size.width * .88f, size.height * .18f),
            )
            drawCircle(
                color = Color.White.copy(alpha = .45f),
                radius = size.minDimension * .011f,
                center = Offset(size.width * .88f, size.height * .18f),
            )

            drawRoundRect(
                color = Color.White.copy(alpha = .10f),
                topLeft = Offset(1.5f, 1.5f),
                size = Size(size.width - 3f, size.height - 3f),
                cornerRadius = CornerRadius(15.dp.toPx(), 15.dp.toPx()),
                style = Stroke(width = 1.2f),
            )
        }
    }
}

private fun DrawScope.drawLathe(cnc: Boolean) {
    val left = size.width * .17f
    val top = size.height * .30f
    val w = size.width * .67f
    val h = size.height * .43f

    drawRoundRect(
        color = if (cnc) Color(0xFFDFE6E8) else Color(0xFF466B73),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(7f, 7f),
    )
    drawRect(
        color = Color(0xFF16242A),
        topLeft = Offset(left + w * .09f, top + h * .15f),
        size = Size(w * .55f, h * .48f),
    )

    if (cnc) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF345D68), Color(0xFF17323B))),
            topLeft = Offset(left + w * .10f, top + h * .19f),
            size = Size(w * .51f, h * .40f),
        )
        drawRect(
            color = Color(0xFF27373C),
            topLeft = Offset(left + w * .70f, top + h * .12f),
            size = Size(w * .20f, h * .58f),
        )
        drawRect(
            color = Color(0xFF61DFA0),
            topLeft = Offset(left + w * .74f, top + h * .20f),
            size = Size(w * .10f, h * .12f),
        )
    } else {
        drawLine(
            color = Color(0xFFCFD8DB),
            start = Offset(left + w * .17f, top + h * .39f),
            end = Offset(left + w * .67f, top + h * .39f),
            strokeWidth = 5f,
        )
        drawCircle(
            color = Color(0xFF26383E),
            radius = h * .16f,
            center = Offset(left + w * .21f, top + h * .39f),
        )
        drawCircle(
            color = Color(0xFF9BA8AC),
            radius = h * .10f,
            center = Offset(left + w * .21f, top + h * .39f),
        )
    }

    drawRect(
        color = Color(0xFF071013),
        topLeft = Offset(left + w * .05f, top + h),
        size = Size(w * .12f, h * .15f),
    )
    drawRect(
        color = Color(0xFF071013),
        topLeft = Offset(left + w * .80f, top + h),
        size = Size(w * .12f, h * .15f),
    )
}

private fun DrawScope.drawMachiningCenter(fiveAxis: Boolean) {
    val left = size.width * .21f
    val top = size.height * .20f
    val w = size.width * .60f
    val h = size.height * .55f

    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE3E9EA), Color(0xFFADB9BC), Color(0xFF727F83))
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(8f, 8f),
    )

    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF305667), Color(0xFF102832))),
        topLeft = Offset(left + w * .14f, top + h * .13f),
        size = Size(w * .52f, h * .56f),
    )

    drawLine(
        color = Color.White.copy(alpha = .35f),
        start = Offset(left + w * .40f, top + h * .14f),
        end = Offset(left + w * .40f, top + h * .68f),
        strokeWidth = 2f,
    )

    drawRect(
        color = Color(0xFF253439),
        topLeft = Offset(left + w * .73f, top + h * .12f),
        size = Size(w * .18f, h * .55f),
    )
    drawRect(
        color = Color(0xFF57E0A0),
        topLeft = Offset(left + w * .77f, top + h * .20f),
        size = Size(w * .10f, h * .10f),
    )

    if (fiveAxis) {
        drawCircle(
            color = Color(0xFFFFC247),
            radius = h * .10f,
            center = Offset(left + w * .40f, top + h * .49f),
        )
        drawCircle(
            color = Color(0xFF1C3038),
            radius = h * .066f,
            center = Offset(left + w * .40f, top + h * .49f),
        )
    }

    drawRect(
        color = Color(0xFF081013),
        topLeft = Offset(left + w * .06f, top + h),
        size = Size(w * .18f, h * .11f),
    )
    drawRect(
        color = Color(0xFF081013),
        topLeft = Offset(left + w * .72f, top + h),
        size = Size(w * .18f, h * .11f),
    )
}

private fun DrawScope.drawMill() {
    val cx = size.width * .51f
    val top = size.height * .20f
    drawRect(
        color = Color(0xFF4B7077),
        topLeft = Offset(cx - size.width * .09f, top),
        size = Size(size.width * .18f, size.height * .42f),
    )
    drawRoundRect(
        color = Color(0xFF789197),
        topLeft = Offset(cx - size.width * .20f, top + size.height * .05f),
        size = Size(size.width * .39f, size.height * .17f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawLine(
        color = Color(0xFFCDD8DA),
        start = Offset(cx, top + size.height * .22f),
        end = Offset(cx, top + size.height * .46f),
        strokeWidth = 5f,
    )
    drawRect(
        color = Color(0xFF24363B),
        topLeft = Offset(cx - size.width * .25f, top + size.height * .43f),
        size = Size(size.width * .50f, size.height * .10f),
    )
    drawRect(
        color = Color(0xFF132025),
        topLeft = Offset(cx - size.width * .16f, top + size.height * .53f),
        size = Size(size.width * .32f, size.height * .15f),
    )
}

private fun DrawScope.drawDrill() {
    val cx = size.width * .51f
    val top = size.height * .18f
    drawRect(
        color = Color(0xFF425E66),
        topLeft = Offset(cx - size.width * .035f, top + size.height * .17f),
        size = Size(size.width * .07f, size.height * .43f),
    )
    drawRoundRect(
        color = Color(0xFF71848A),
        topLeft = Offset(cx - size.width * .17f, top),
        size = Size(size.width * .34f, size.height * .20f),
        cornerRadius = CornerRadius(9f, 9f),
    )
    drawLine(
        color = Color(0xFFD4DDDF),
        start = Offset(cx, top + size.height * .18f),
        end = Offset(cx, top + size.height * .45f),
        strokeWidth = 4f,
    )
    drawRect(
        color = Color(0xFF273A40),
        topLeft = Offset(cx - size.width * .18f, top + size.height * .45f),
        size = Size(size.width * .36f, size.height * .08f),
    )
    drawRect(
        color = Color(0xFF19272C),
        topLeft = Offset(cx - size.width * .22f, top + size.height * .62f),
        size = Size(size.width * .44f, size.height * .08f),
    )
}

private fun DrawScope.drawGrinder() {
    val left = size.width * .19f
    val top = size.height * .28f
    val w = size.width * .64f
    val h = size.height * .42f
    drawRoundRect(
        color = Color(0xFF526F76),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawCircle(
        color = Color(0xFF202D31),
        radius = h * .22f,
        center = Offset(left + w * .25f, top + h * .43f),
    )
    drawCircle(
        color = Color(0xFFC1CACD),
        radius = h * .15f,
        center = Offset(left + w * .25f, top + h * .43f),
    )
    drawRect(
        color = Color(0xFF1F333A),
        topLeft = Offset(left + w * .48f, top + h * .22f),
        size = Size(w * .39f, h * .38f),
    )
}

private fun DrawScope.drawWelding() {
    val left = size.width * .18f
    val top = size.height * .26f
    val w = size.width * .64f
    val h = size.height * .45f
    drawRect(
        color = Color(0xFF203239),
        topLeft = Offset(left, top + h * .60f),
        size = Size(w, h * .17f),
    )
    drawLine(
        color = Color(0xFFE6B24A),
        start = Offset(left + w * .22f, top + h * .58f),
        end = Offset(left + w * .42f, top + h * .22f),
        strokeWidth = 9f,
    )
    drawLine(
        color = Color(0xFFE6B24A),
        start = Offset(left + w * .42f, top + h * .22f),
        end = Offset(left + w * .61f, top + h * .50f),
        strokeWidth = 8f,
    )
    drawCircle(
        color = Color(0xFF293B41),
        radius = h * .12f,
        center = Offset(left + w * .22f, top + h * .58f),
    )
    drawCircle(
        color = Color(0xFF293B41),
        radius = h * .10f,
        center = Offset(left + w * .42f, top + h * .22f),
    )
    drawCircle(
        color = Color(0xFFFFCB52),
        radius = h * .035f,
        center = Offset(left + w * .64f, top + h * .55f),
    )
}

private fun DrawScope.drawLaser() {
    val left = size.width * .15f
    val top = size.height * .31f
    val w = size.width * .70f
    val h = size.height * .39f
    drawRoundRect(
        color = Color(0xFF415F68),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRect(
        color = Color(0xFF10262E),
        topLeft = Offset(left + w * .06f, top + h * .14f),
        size = Size(w * .66f, h * .48f),
    )
    drawLine(
        color = Color(0xFFFF5A54),
        start = Offset(left + w * .23f, top + h * .40f),
        end = Offset(left + w * .72f, top + h * .40f),
        strokeWidth = 2.4f,
    )
    drawRect(
        color = Color(0xFF24363B),
        topLeft = Offset(left + w * .78f, top + h * .10f),
        size = Size(w * .15f, h * .52f),
    )
}

private fun DrawScope.drawEdm() {
    val left = size.width * .22f
    val top = size.height * .23f
    val w = size.width * .58f
    val h = size.height * .51f
    drawRoundRect(
        color = Color(0xFF5B737A),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(7f, 7f),
    )
    drawRect(
        color = Color(0xFF18323B),
        topLeft = Offset(left + w * .10f, top + h * .15f),
        size = Size(w * .55f, h * .48f),
    )
    drawLine(
        color = Color(0xFF62C8FF),
        start = Offset(left + w * .39f, top + h * .12f),
        end = Offset(left + w * .39f, top + h * .58f),
        strokeWidth = 3f,
    )
    drawRect(
        color = Color(0xFF273A3F),
        topLeft = Offset(left + w * .71f, top + h * .13f),
        size = Size(w * .18f, h * .50f),
    )
}

private fun DrawScope.drawGenericMachine() {
    val left = size.width * .20f
    val top = size.height * .27f
    val w = size.width * .62f
    val h = size.height * .44f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF6E858B), Color(0xFF3D5961), Color(0xFF253940))
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(9f, 9f),
    )
    drawRect(
        color = Color(0xFF102229),
        topLeft = Offset(left + w * .11f, top + h * .17f),
        size = Size(w * .52f, h * .45f),
    )
    drawRect(
        color = Color(0xFF57DEA0),
        topLeft = Offset(left + w * .73f, top + h * .18f),
        size = Size(w * .10f, h * .12f),
    )
}
