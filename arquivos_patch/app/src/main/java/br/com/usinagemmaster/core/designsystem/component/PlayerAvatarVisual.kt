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
import kotlin.math.PI
import kotlin.math.cos
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
            drawLine(Color.White.copy(alpha = .06f), Offset(size.width * .12f, size.height * .82f), Offset(size.width * .88f, size.height * .82f), 2f)
            drawPlayerAvatarFigure(
                base = Offset(size.width * .5f, size.height * .80f),
                avatar = avatar,
                scale = size.minDimension / 118f,
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
    val style = avatar.skinStyle
    val female = avatar.gender == "FEMALE"
    val styleWidth = when (style) {
        "TATUZAO" -> 1.24f
        "MAGRAO" -> .72f
        "KENDAO_KIMONO" -> 1.08f
        else -> 1f
    }
    val styleHeight = when (style) {
        "MAGRAO" -> 1.14f
        "TATUZAO" -> 1.06f
        else -> 1f
    }
    val bodyWidthFactor = (when (avatar.bodyType) {
        "SLIM" -> .82f
        "STRONG" -> 1.18f
        else -> 1f
    }) * styleWidth * if (female) .94f else 1f
    val skin = avatarSkinColor(avatar.skinTone)
    val hair = avatarHairColor(avatar.hairColor)
    val baseUniform = avatarUniformColor(avatar.uniformColor)
    val uniform = when (style) {
        "PRINCESA" -> Color(0xFF6F476E)
        "KENDAO_KIMONO" -> Color(0xFFE7E3D9)
        "TATUZAO" -> Color(0xFF3A4642)
        else -> baseUniform
    }
    val helmet = when (style) {
        "PRINCESA" -> Color(0xFFFF9FC7)
        "KENDAO_KIMONO" -> Color(0xFF242A2D)
        else -> avatarHelmetColor(avatar.helmetColor)
    }
    val outline = Color(0xFF061015)
    val boot = Color(0xFF171A1C)
    val reflect = if (style == "PRINCESA") Color(0xFFFFC7E0) else Color(0xFFFFD54F)
    val cycle = phase * 6.28318f
    val styleJitter = when (style) {
        "TREME_TREME" -> sin(cycle * 3.2f) * 2.1f * scale
        "BEBADO" -> sin(cycle * .72f) * 3.2f * scale
        else -> 0f
    }
    val step = if (walking) sin(cycle) * 4.8f * scale else 0f
    val bob = if (walking) kotlin.math.abs(sin(cycle)) * 1.7f * scale else sin(cycle) * .5f * scale
    val torsoW = 20f * scale * bodyWidthFactor
    val torsoH = 28f * scale * styleHeight
    val headR = 7.8f * scale
    val hipY = base.y - 29f * scale * styleHeight + bob
    val shoulderY = hipY - 23f * scale * styleHeight
    val headCenter = Offset(base.x + styleJitter, shoulderY - 12.5f * scale)
    val x = base.x + styleJitter

    drawOval(Color.Black.copy(alpha = .32f), topLeft = Offset(x - 14f * scale, base.y - 2f * scale), size = Size(28f * scale, 6f * scale))

    val legTop = hipY + 9f * scale
    drawLine(uniform.copy(alpha = .9f), Offset(x - 5f * scale, legTop), Offset(x - 6f * scale + step, base.y - 5f * scale), 6.2f * scale)
    drawLine(uniform.copy(alpha = .9f), Offset(x + 5f * scale, legTop), Offset(x + 6f * scale - step, base.y - 5f * scale), 6.2f * scale)
    drawLine(boot, Offset(x - 7f * scale + step, base.y - 4f * scale), Offset(x - 1f * scale + step, base.y - 4f * scale), 4f * scale)
    drawLine(boot, Offset(x + 4f * scale - step, base.y - 4f * scale), Offset(x + 10f * scale - step, base.y - 4f * scale), 4f * scale)

    drawRoundRect(
        color = outline,
        topLeft = Offset(x - torsoW / 2f - 1.5f * scale, shoulderY - 1.5f * scale),
        size = Size(torsoW + 3f * scale, torsoH + 3f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * scale)
    )
    drawRoundRect(
        color = uniform,
        topLeft = Offset(x - torsoW / 2f, shoulderY),
        size = Size(torsoW, torsoH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (female) 6f * scale else 4f * scale)
    )
    drawRect(reflect.copy(alpha = .85f), Offset(x - torsoW / 2f, shoulderY + 17f * scale), Size(torsoW, 2.2f * scale))

    if (style == "KENDAO_KIMONO") {
        drawLine(Color(0xFF39434A), Offset(x - torsoW*.35f, shoulderY + 3f*scale), Offset(x + torsoW*.30f, shoulderY + 17f*scale), 2f*scale)
        drawLine(Color(0xFF39434A), Offset(x + torsoW*.35f, shoulderY + 3f*scale), Offset(x - torsoW*.30f, shoulderY + 17f*scale), 2f*scale)
        drawRect(Color(0xFF20272A), Offset(x - torsoW/2f, shoulderY + 19f*scale), Size(torsoW, 3.5f*scale))
    }

    val armSwing = if (walking) step * .75f else sin(cycle) * 1.6f * scale
    val leftHand = Offset(x - torsoW / 2f - 5f * scale - armSwing, shoulderY + 21f * scale)
    val rightHand = if (carrying) Offset(x + 11f * scale, shoulderY + 15f * scale) else Offset(x + torsoW / 2f + 5f * scale + armSwing, shoulderY + 21f * scale)
    drawLine(uniform, Offset(x - torsoW / 2f + 1f * scale, shoulderY + 6f * scale), leftHand, 5.2f * scale)
    drawLine(uniform, Offset(x + torsoW / 2f - 1f * scale, shoulderY + 6f * scale), rightHand, 5.2f * scale)
    drawCircle(skin, 2.7f * scale, leftHand)
    drawCircle(skin, 2.7f * scale, rightHand)

    if (carrying) {
        drawRoundRect(Color(0xFFB47A3A), Offset(x + 4f * scale, shoulderY + 14f * scale), Size(18f * scale, 11f * scale), androidx.compose.ui.geometry.CornerRadius(2f * scale))
        drawLine(Color(0xFFD9A766), Offset(x + 4f * scale, shoulderY + 18f * scale), Offset(x + 22f * scale, shoulderY + 18f * scale), 1f * scale)
    }

    drawRoundRect(skin, Offset(x - 2.8f * scale, shoulderY - 4f * scale), Size(5.6f * scale, 6f * scale), androidx.compose.ui.geometry.CornerRadius(2f * scale))
    drawCircle(outline, headR + 1.1f * scale, headCenter)
    drawCircle(skin, headR, headCenter)

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
        "LONG" -> {
            drawArc(hair, 175f, 190f, true, Offset(headCenter.x - headR*1.05f, headCenter.y - headR), Size(headR * 2.1f, headR * 1.65f))
            drawRoundRect(hair, Offset(headCenter.x - 7f*scale, headCenter.y + 2f*scale), Size(14f*scale, 11f*scale), androidx.compose.ui.geometry.CornerRadius(4f*scale))
        }
        "PONYTAIL" -> {
            drawArc(hair, 180f, 180f, true, Offset(headCenter.x - headR, headCenter.y - headR), Size(headR * 2f, headR * 1.55f))
            drawCircle(hair, 4.8f*scale, headCenter + Offset(8f*scale, 5f*scale))
        }
        "CURLY" -> {
            repeat(7) { i ->
                val a = PI * (0.85 + i * .22)
                drawCircle(hair, 3f*scale, headCenter + Offset(cos(a).toFloat()*8f*scale, sin(a).toFloat()*7f*scale - 2f*scale))
            }
        }
        "BALD" -> Unit
        else -> drawArc(hair, 185f, 170f, true, Offset(headCenter.x - headR, headCenter.y - headR), Size(headR * 2f, headR * 1.55f))
    }

    drawCircle(Color(0xFF182127), 1f * scale, Offset(headCenter.x - 2.7f * scale, headCenter.y))
    drawCircle(Color(0xFF182127), 1f * scale, Offset(headCenter.x + 2.7f * scale, headCenter.y))
    drawLine(Color(0xFF8B5E48), Offset(headCenter.x - 2f * scale, headCenter.y + 4f * scale), Offset(headCenter.x + 2f * scale, headCenter.y + 4f * scale), .8f * scale)

    if (style == "PINOQUIO") {
        drawLine(skin, headCenter + Offset(3f*scale, 2f*scale), headCenter + Offset(11f*scale, 4f*scale), 2f*scale)
    }
    if (style == "TATUZAO") {
        drawArc(Color(0xFF3A2B24), 15f, 150f, false, Offset(headCenter.x - 6f*scale, headCenter.y + 1f*scale), Size(12f*scale, 8f*scale), style = Stroke(1.6f*scale))
    }
    if (style == "BEBADO") {
        drawCircle(Color(0xFFB65A5A).copy(alpha = .35f), 2.7f*scale, headCenter + Offset(-4f*scale, 3f*scale))
        drawCircle(Color(0xFFB65A5A).copy(alpha = .35f), 2.7f*scale, headCenter + Offset(4f*scale, 3f*scale))
    }

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
