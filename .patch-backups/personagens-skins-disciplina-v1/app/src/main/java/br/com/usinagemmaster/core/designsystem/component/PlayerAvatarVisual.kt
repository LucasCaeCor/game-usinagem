package br.com.usinagemmaster.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.social.PlayerAvatar
import kotlin.math.sin

@Composable
fun PlayerAvatarPreview(
    avatar: PlayerAvatar,
    modifier: Modifier = Modifier,
    size: Dp = 154.dp,
    phase: Float = .12f
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color(0xFF111A1F), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFFFB21A).copy(alpha = .08f), radius = this.size.minDimension * .42f, center = center)
            drawLine(Color.White.copy(alpha = .06f), Offset(this.size.width * .12f, this.size.height * .82f), Offset(this.size.width * .88f, this.size.height * .82f), 2f)
            drawPlayerAvatarFigure(
                base = Offset(this.size.width * .5f, this.size.height * .80f),
                avatar = avatar,
                scale = this.size.minDimension / 118f,
                phase = phase,
                walking = false,
                carrying = false
            )
        }
    }
}

fun DrawScope.drawPlayerAvatarFigure(
    base: Offset,
    avatar: PlayerAvatar,
    scale: Float,
    phase: Float,
    walking: Boolean,
    carrying: Boolean
) {
    val bodyWidthFactor = when (avatar.bodyType) {
        "SLIM" -> .82f
        "STRONG" -> 1.18f
        else -> 1f
    }
    val skin = avatarSkinColor(avatar.skinTone)
    val hair = avatarHairColor(avatar.hairColor)
    val uniform = avatarUniformColor(avatar.uniformColor)
    val helmet = avatarHelmetColor(avatar.helmetColor)
    val outline = Color(0xFF061015)
    val boot = Color(0xFF171A1C)
    val reflect = Color(0xFFFFD54F)
    val step = if (walking) sin(phase * 6.28318f) * 4.8f * scale else 0f
    val bob = if (walking) kotlin.math.abs(sin(phase * 6.28318f)) * 1.7f * scale else sin(phase * 6.28318f) * .5f * scale
    val torsoW = 20f * scale * bodyWidthFactor
    val torsoH = 28f * scale
    val headR = 7.8f * scale
    val hipY = base.y - 29f * scale + bob
    val shoulderY = hipY - 23f * scale
    val headCenter = Offset(base.x, shoulderY - 12.5f * scale)

    // Sombra.
    drawOval(Color.Black.copy(alpha = .32f), topLeft = Offset(base.x - 14f * scale, base.y - 2f * scale), size = Size(28f * scale, 6f * scale))

    // Pernas com passada.
    val legTop = hipY + 9f * scale
    drawLine(uniform.copy(alpha = .9f), Offset(base.x - 5f * scale, legTop), Offset(base.x - 6f * scale + step, base.y - 5f * scale), 6.2f * scale)
    drawLine(uniform.copy(alpha = .9f), Offset(base.x + 5f * scale, legTop), Offset(base.x + 6f * scale - step, base.y - 5f * scale), 6.2f * scale)
    drawLine(boot, Offset(base.x - 7f * scale + step, base.y - 4f * scale), Offset(base.x - 1f * scale + step, base.y - 4f * scale), 4f * scale)
    drawLine(boot, Offset(base.x + 4f * scale - step, base.y - 4f * scale), Offset(base.x + 10f * scale - step, base.y - 4f * scale), 4f * scale)

    // Tronco e colete.
    drawRoundRect(
        color = outline,
        topLeft = Offset(base.x - torsoW / 2f - 1.5f * scale, shoulderY - 1.5f * scale),
        size = Size(torsoW + 3f * scale, torsoH + 3f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * scale)
    )
    drawRoundRect(
        color = uniform,
        topLeft = Offset(base.x - torsoW / 2f, shoulderY),
        size = Size(torsoW, torsoH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale)
    )
    drawRect(reflect.copy(alpha = .85f), Offset(base.x - torsoW / 2f, shoulderY + 17f * scale), Size(torsoW, 2.2f * scale))

    // Braços, com movimento discreto.
    val armSwing = if (walking) step * .75f else sin(phase * 6.28318f) * 1.6f * scale
    val leftHand = Offset(base.x - torsoW / 2f - 5f * scale - armSwing, shoulderY + 21f * scale)
    val rightHand = if (carrying) Offset(base.x + 11f * scale, shoulderY + 15f * scale) else Offset(base.x + torsoW / 2f + 5f * scale + armSwing, shoulderY + 21f * scale)
    drawLine(uniform, Offset(base.x - torsoW / 2f + 1f * scale, shoulderY + 6f * scale), leftHand, 5.2f * scale)
    drawLine(uniform, Offset(base.x + torsoW / 2f - 1f * scale, shoulderY + 6f * scale), rightHand, 5.2f * scale)
    drawCircle(skin, 2.7f * scale, leftHand)
    drawCircle(skin, 2.7f * scale, rightHand)

    if (carrying) {
        drawRoundRect(Color(0xFFB47A3A), Offset(base.x + 4f * scale, shoulderY + 14f * scale), Size(18f * scale, 11f * scale), androidx.compose.ui.geometry.CornerRadius(2f * scale))
        drawLine(Color(0xFFD9A766), Offset(base.x + 4f * scale, shoulderY + 18f * scale), Offset(base.x + 22f * scale, shoulderY + 18f * scale), 1f * scale)
    }

    // Pescoço e cabeça.
    drawRoundRect(skin, Offset(base.x - 2.8f * scale, shoulderY - 4f * scale), Size(5.6f * scale, 6f * scale), androidx.compose.ui.geometry.CornerRadius(2f * scale))
    drawCircle(outline, headR + 1.1f * scale, headCenter)
    drawCircle(skin, headR, headCenter)

    // Cabelo / penteado.
    when (avatar.hairStyle) {
        "BUZZ" -> drawArc(hair, 190f, 160f, true, Offset(headCenter.x - headR, headCenter.y - headR), Size(headR * 2, headR * 1.4f))
        "MOHAWK" -> {
            val p = Path().apply {
                moveTo(headCenter.x - 4f * scale, headCenter.y - 5f * scale)
                lineTo(headCenter.x - 1f * scale, headCenter.y - 13f * scale)
                lineTo(headCenter.x + 2f * scale, headCenter.y - 6f * scale)
                lineTo(headCenter.x + 5f * scale, headCenter.y - 12f * scale)
                lineTo(headCenter.x + 6f * scale, headCenter.y - 4f * scale)
                close()
            }
            drawPath(p, hair)
        }
        "BALD" -> Unit
        else -> drawArc(hair, 185f, 170f, true, Offset(headCenter.x - headR, headCenter.y - headR), Size(headR * 2f, headR * 1.55f))
    }

    // Rosto.
    drawCircle(Color(0xFF182127), 1f * scale, Offset(headCenter.x - 2.7f * scale, headCenter.y))
    drawCircle(Color(0xFF182127), 1f * scale, Offset(headCenter.x + 2.7f * scale, headCenter.y))
    drawLine(Color(0xFF8B5E48), Offset(headCenter.x - 2f * scale, headCenter.y + 4f * scale), Offset(headCenter.x + 2f * scale, headCenter.y + 4f * scale), .8f * scale)

    // Capacete: NONE é permitido para editor, mas o dono da fábrica usa EPI por padrão.
    if (avatar.helmetColor != "NONE") {
        drawArc(helmet, 180f, 180f, true, Offset(headCenter.x - 9f * scale, headCenter.y - 10f * scale), Size(18f * scale, 11f * scale))
        drawLine(helmet, Offset(headCenter.x - 10f * scale, headCenter.y - 3.5f * scale), Offset(headCenter.x + 10f * scale, headCenter.y - 3.5f * scale), 2.2f * scale)
    }

    when (avatar.accessory) {
        "GLASSES" -> {
            drawCircle(Color(0xFF0E171B), 2.5f * scale, Offset(headCenter.x - 3f * scale, headCenter.y), style = Stroke(1f * scale))
            drawCircle(Color(0xFF0E171B), 2.5f * scale, Offset(headCenter.x + 3f * scale, headCenter.y), style = Stroke(1f * scale))
            drawLine(Color(0xFF0E171B), Offset(headCenter.x - .5f * scale, headCenter.y), Offset(headCenter.x + .5f * scale, headCenter.y), .8f * scale)
        }
        "HEADSET" -> {
            drawArc(Color(0xFF2C3B43), 205f, 130f, false, Offset(headCenter.x - 10f * scale, headCenter.y - 10f * scale), Size(20f * scale, 18f * scale), style = Stroke(1.6f * scale))
            drawCircle(Color(0xFF27343B), 2.8f * scale, Offset(headCenter.x + 8f * scale, headCenter.y + 1f * scale))
        }
    }
}

private fun avatarSkinColor(value: String): Color = when (value) {
    "LIGHT" -> Color(0xFFF2C5A0)
    "TAN" -> Color(0xFFC8885F)
    "DARK" -> Color(0xFF7D4E38)
    else -> Color(0xFFD6A178)
}

private fun avatarHairColor(value: String): Color = when (value) {
    "BROWN" -> Color(0xFF6D4937)
    "BLONDE" -> Color(0xFFD5B56A)
    "GRAY" -> Color(0xFF91979B)
    else -> Color(0xFF27282A)
}

private fun avatarUniformColor(value: String): Color = when (value) {
    "GRAPHITE" -> Color(0xFF38434A)
    "GREEN" -> Color(0xFF315B4C)
    "BLUE" -> Color(0xFF265279)
    "ORANGE" -> Color(0xFF8A4C22)
    else -> Color(0xFF243C55)
}

private fun avatarHelmetColor(value: String): Color = when (value) {
    "WHITE" -> Color(0xFFE6EBED)
    "BLUE" -> Color(0xFF4B8CC4)
    "RED" -> Color(0xFFD94D4D)
    "BLACK" -> Color(0xFF343A3D)
    else -> Color(0xFFFFC238)
}
