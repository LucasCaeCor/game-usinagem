package br.com.usinagemmaster.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.social.PlayerAvatar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sprite vetorial modular do personagem.
 *
 * Direção de arte:
 * - cada skin altera silhueta, roupa, cabelo/acessórios e linguagem corporal;
 * - continua 100% Compose/Canvas, sem bitmap externo;
 * - escala sem perder nitidez no zoom da Fábrica Viva;
 * - o mesmo renderer é usado no perfil e no dono circulando pela oficina.
 */
@Composable
fun PlayerAvatarPreview(
    avatar: PlayerAvatar,
    modifier: Modifier = Modifier,
    size: Dp = 154.dp,
    phase: Float = .12f
) {
    val transition = rememberInfiniteTransition(label = "avatar_preview")
    val idlePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Restart),
        label = "avatar_idle"
    )
    Box(
        modifier = modifier
            .size(size)
            .background(Color(0xFF10181D), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFFFB21A).copy(alpha = .08f), radius = this.size.minDimension * .42f, center = center)
            drawCircle(Color.White.copy(alpha = .025f), radius = this.size.minDimension * .33f, center = center)
            drawLine(Color.White.copy(alpha = .07f), Offset(this.size.width * .12f, this.size.height * .82f), Offset(this.size.width * .88f, this.size.height * .82f), 2f)
            drawPlayerAvatarFigure(
                base = Offset(this.size.width * .5f, this.size.height * .82f),
                avatar = avatar,
                scale = this.size.minDimension / 118f,
                phase = (idlePhase + phase) % 1f,
                walking = false,
                carrying = false
            )
        }
    }
}

private data class AvatarArt(
    val skin: Color,
    val hair: Color,
    val cloth: Color,
    val clothDark: Color,
    val accent: Color,
    val headwear: Color,
    val outline: Color,
    val boots: Color,
    val width: Float,
    val height: Float
)

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
    val cycle = phase * (PI * 2).toFloat()

    val styleWidth = when (style) {
        "TATUZAO" -> 1.32f
        "MAGRAO" -> .68f
        "KENDAO_KIMONO" -> 1.08f
        "PRINCESA" -> .94f
        else -> 1f
    }
    val styleHeight = when (style) {
        "MAGRAO" -> 1.18f
        "TATUZAO" -> 1.06f
        "PRINCESA" -> 1.03f
        else -> 1f
    }
    val bodyWidth = when (avatar.bodyType) {
        "SLIM" -> .84f
        "STRONG" -> 1.18f
        else -> 1f
    } * styleWidth * if (female) .96f else 1f

    val art = AvatarArt(
        skin = avatarSkinColor(avatar.skinTone),
        hair = avatarHairColor(avatar.hairColor),
        cloth = when (style) {
            "PRINCESA" -> Color(0xFFD764A5)
            "KENDAO_KIMONO" -> Color(0xFFF0EEE8)
            "TATUZAO" -> Color(0xFF314441)
            "PINOQUIO" -> Color(0xFF2B5B83)
            "BEBADO" -> Color(0xFF6D3E3A)
            else -> avatarUniformColor(avatar.uniformColor)
        },
        clothDark = when (style) {
            "PRINCESA" -> Color(0xFF8E346D)
            "KENDAO_KIMONO" -> Color(0xFF30363A)
            "PINOQUIO" -> Color(0xFF1E3A57)
            else -> Color(0xFF1B272D)
        },
        accent = when (style) {
            "PRINCESA" -> Color(0xFFFFE6A8)
            "KENDAO_KIMONO" -> Color(0xFF20272A)
            "TATUZAO" -> Color(0xFFE1A53B)
            "PINOQUIO" -> Color(0xFFE7C24B)
            else -> Color(0xFFFFD54F)
        },
        headwear = avatarHelmetColor(avatar.helmetColor),
        outline = Color(0xFF071015),
        boots = Color(0xFF151A1D),
        width = bodyWidth,
        height = styleHeight
    )

    val tremble = if (style == "TREME_TREME") sin(cycle * 4.2f) * 2.3f * scale else 0f
    val drunk = if (style == "BEBADO") sin(cycle * .72f) * 4.1f * scale else 0f
    val breath = sin(cycle * .55f) * .8f * scale
    val step = if (walking) sin(cycle) * 5.2f * scale else 0f
    val bob = if (walking) abs(sin(cycle)) * 1.8f * scale else breath
    val x = base.x + tremble + drunk
    val y = base.y - bob
    val hipY = y - 28f * scale * art.height
    val shoulderY = hipY - 22f * scale * art.height
    val headCenter = Offset(x + if (style == "BEBADO") drunk * .20f else 0f, shoulderY - 12.8f * scale)
    val torsoW = 20f * scale * art.width
    val torsoH = 27f * scale * art.height

    // Sombra: ancora o sprite no chão e reduz sensação de "boneco flutuando".
    drawOval(Color.Black.copy(alpha = .34f), Offset(x - 15f * scale * art.width, base.y - 3f * scale), Size(30f * scale * art.width, 7f * scale))

    // Cabelo traseiro fica atrás do corpo/cabeça para criar profundidade real.
    drawHairBack(headCenter, avatar, art, scale, phase)

    // Pernas/calçados.
    drawAvatarLegs(x, y, hipY, art, style, scale, step)

    // Roupa: cada skin tem construção própria, não apenas troca de cor.
    drawAvatarOutfit(x, shoulderY, hipY, torsoW, torsoH, art, avatar, scale, phase)

    // Braços e item carregado.
    drawAvatarArms(x, shoulderY, torsoW, art, avatar, scale, cycle, walking, carrying)

    // Pescoço + cabeça.
    drawRoundRect(art.skin, Offset(x - 2.8f * scale, shoulderY - 4.5f * scale), Size(5.6f * scale, 6.5f * scale), CornerRadius(2f * scale))
    drawCircle(art.outline, 9.1f * scale, headCenter)
    drawCircle(art.skin, 8.0f * scale, headCenter)

    drawHairFront(headCenter, avatar, art, scale, phase)
    drawAvatarFace(headCenter, avatar, art, scale, phase)
    drawAvatarHeadwear(headCenter, avatar, art, scale, phase)
    drawAvatarAccessory(headCenter, avatar, art, scale)

    if (style == "TREME_TREME") {
        val shake = 1.5f * scale
        drawLine(art.accent.copy(alpha = .65f), Offset(x - 16f*scale, shoulderY+4f*scale), Offset(x - 19f*scale-shake, shoulderY+8f*scale), 1.2f*scale)
        drawLine(art.accent.copy(alpha = .65f), Offset(x + 16f*scale, shoulderY+4f*scale), Offset(x + 19f*scale+shake, shoulderY+8f*scale), 1.2f*scale)
    }
}

private fun DrawScope.drawAvatarLegs(
    x: Float,
    y: Float,
    hipY: Float,
    art: AvatarArt,
    style: String,
    s: Float,
    step: Float
) {
    val legTop = hipY + 8f*s
    val long = if (style == "MAGRAO") 1.10f else 1f
    val legColor = if (style == "PRINCESA") Color(0xFF313642) else art.clothDark
    drawLine(legColor, Offset(x - 5f*s*art.width, legTop), Offset(x - 6f*s*art.width + step, y - 5f*s*long), 5.8f*s*art.width, StrokeCap.Round)
    drawLine(legColor, Offset(x + 5f*s*art.width, legTop), Offset(x + 6f*s*art.width - step, y - 5f*s*long), 5.8f*s*art.width, StrokeCap.Round)
    drawLine(art.boots, Offset(x - 7f*s*art.width + step, y - 4f*s), Offset(x - 1f*s*art.width + step, y - 4f*s), 4.2f*s, StrokeCap.Round)
    drawLine(art.boots, Offset(x + 4f*s*art.width - step, y - 4f*s), Offset(x + 10f*s*art.width - step, y - 4f*s), 4.2f*s, StrokeCap.Round)
}

private fun DrawScope.drawAvatarOutfit(
    x: Float,
    shoulderY: Float,
    hipY: Float,
    torsoW: Float,
    torsoH: Float,
    art: AvatarArt,
    avatar: PlayerAvatar,
    s: Float,
    phase: Float
) {
    when (avatar.skinStyle) {
        "PRINCESA" -> {
            // Corpete + saia rodada em camadas. Mantém botas por baixo: fantasia temática de jogo,
            // mas ainda com leitura de "personagem da oficina".
            val bodice = Path().apply {
                moveTo(x - torsoW*.43f, shoulderY)
                lineTo(x + torsoW*.43f, shoulderY)
                lineTo(x + torsoW*.34f, hipY + 2f*s)
                lineTo(x - torsoW*.34f, hipY + 2f*s)
                close()
            }
            drawPath(bodice, art.cloth)
            drawLine(art.accent.copy(alpha=.9f), Offset(x, shoulderY+2f*s), Offset(x, hipY+1f*s), 1.1f*s)
            val sway = sin(phase * PI * 2).toFloat() * 1.1f*s
            val skirt = Path().apply {
                moveTo(x - torsoW*.32f, hipY)
                quadraticBezierTo(x - torsoW*.58f + sway, hipY + 10f*s, x - torsoW*.78f + sway, hipY + 24f*s)
                quadraticBezierTo(x, hipY + 30f*s, x + torsoW*.78f + sway, hipY + 24f*s)
                quadraticBezierTo(x + torsoW*.58f + sway, hipY + 10f*s, x + torsoW*.32f, hipY)
                close()
            }
            drawPath(skirt, art.cloth)
            drawPath(skirt, art.clothDark.copy(alpha=.35f), style = Stroke(1.2f*s))
            drawLine(art.accent, Offset(x - torsoW*.65f + sway, hipY+21f*s), Offset(x + torsoW*.65f + sway, hipY+21f*s), 1.5f*s)
            // Pequena "faixa refletiva" estilizada para manter conexão com a fábrica.
            drawRoundRect(Color.White.copy(alpha=.38f), Offset(x - torsoW*.30f, shoulderY+15f*s), Size(torsoW*.60f, 2f*s), CornerRadius(1f*s))
        }
        "KENDAO_KIMONO" -> {
            drawRoundRect(art.outline, Offset(x - torsoW/2f - 1.2f*s, shoulderY-1.2f*s), Size(torsoW+2.4f*s, torsoH+2.4f*s), CornerRadius(4f*s))
            drawRoundRect(art.cloth, Offset(x - torsoW/2f, shoulderY), Size(torsoW, torsoH), CornerRadius(4f*s))
            drawLine(art.clothDark, Offset(x - torsoW*.40f, shoulderY+2f*s), Offset(x + torsoW*.28f, shoulderY+17f*s), 2.2f*s)
            drawLine(art.clothDark, Offset(x + torsoW*.40f, shoulderY+2f*s), Offset(x - torsoW*.28f, shoulderY+17f*s), 2.2f*s)
            drawRect(art.clothDark, Offset(x - torsoW/2f, shoulderY+19f*s), Size(torsoW, 4.2f*s))
            drawRect(Color(0xFF9E2F2F), Offset(x - 1.4f*s, shoulderY+19f*s), Size(2.8f*s, 8f*s))
        }
        "PINOQUIO" -> {
            drawRoundRect(art.cloth, Offset(x - torsoW/2f, shoulderY), Size(torsoW, torsoH), CornerRadius(4f*s))
            drawRect(Color(0xFFD8C05B), Offset(x - torsoW*.33f, shoulderY+2f*s), Size(2.2f*s, 17f*s))
            drawRect(Color(0xFFD8C05B), Offset(x + torsoW*.23f, shoulderY+2f*s), Size(2.2f*s, 17f*s))
            drawCircle(Color(0xFFFFDF70), 1.6f*s, Offset(x - torsoW*.23f, shoulderY+15f*s))
            drawCircle(Color(0xFFFFDF70), 1.6f*s, Offset(x + torsoW*.23f, shoulderY+15f*s))
        }
        else -> {
            drawRoundRect(art.outline, Offset(x - torsoW/2f - 1.2f*s, shoulderY-1.2f*s), Size(torsoW+2.4f*s, torsoH+2.4f*s), CornerRadius(5f*s))
            drawRoundRect(art.cloth, Offset(x - torsoW/2f, shoulderY), Size(torsoW, torsoH), CornerRadius(if (avatar.gender == "FEMALE") 6f*s else 4f*s))
            drawRect(art.accent.copy(alpha=.78f), Offset(x - torsoW/2f, shoulderY+17f*s), Size(torsoW, 2.3f*s))
            if (avatar.skinStyle == "TATUZAO") {
                drawRect(Color(0xFF263632), Offset(x - torsoW*.32f, shoulderY+2f*s), Size(torsoW*.64f, 4f*s))
                drawCircle(art.accent.copy(alpha=.82f), 2.1f*s, Offset(x, shoulderY+9f*s))
            }
        }
    }
}

private fun DrawScope.drawAvatarArms(
    x: Float,
    shoulderY: Float,
    torsoW: Float,
    art: AvatarArt,
    avatar: PlayerAvatar,
    s: Float,
    cycle: Float,
    walking: Boolean,
    carrying: Boolean
) {
    val style = avatar.skinStyle
    val armSwing = if (walking) sin(cycle) * 4.0f*s else sin(cycle*.72f) * 1.1f*s
    val armWeight = when (style) { "TATUZAO" -> 6.2f*s; "MAGRAO" -> 3.4f*s; else -> 4.7f*s }
    val leftShoulder = Offset(x - torsoW/2f + 1.2f*s, shoulderY+6f*s)
    val rightShoulder = Offset(x + torsoW/2f - 1.2f*s, shoulderY+6f*s)
    val wideSleeve = style == "KENDAO_KIMONO"

    if (wideSleeve) {
        val lp = Path().apply {
            moveTo(leftShoulder.x, leftShoulder.y-2f*s); lineTo(leftShoulder.x-10f*s, leftShoulder.y+10f*s); lineTo(leftShoulder.x-5f*s, leftShoulder.y+15f*s); lineTo(leftShoulder.x+2f*s, leftShoulder.y+4f*s); close()
        }
        val rp = Path().apply {
            moveTo(rightShoulder.x, rightShoulder.y-2f*s); lineTo(rightShoulder.x+10f*s, rightShoulder.y+10f*s); lineTo(rightShoulder.x+5f*s, rightShoulder.y+15f*s); lineTo(rightShoulder.x-2f*s, rightShoulder.y+4f*s); close()
        }
        drawPath(lp, art.cloth); drawPath(rp, art.cloth)
    }

    val leftHand = Offset(leftShoulder.x - 5f*s - armSwing, shoulderY+21f*s)
    val rightHand = if (carrying) Offset(x+10f*s, shoulderY+15f*s) else Offset(rightShoulder.x+5f*s+armSwing, shoulderY+21f*s)
    if (!wideSleeve) {
        drawLine(art.cloth, leftShoulder, leftHand, armWeight, StrokeCap.Round)
        drawLine(art.cloth, rightShoulder, rightHand, armWeight, StrokeCap.Round)
    }
    drawCircle(art.skin, if (style=="TATUZAO") 3.2f*s else 2.6f*s, leftHand)
    drawCircle(art.skin, if (style=="TATUZAO") 3.2f*s else 2.6f*s, rightHand)

    if (style == "TATUZAO") {
        // Tatuagens geométricas simples nos antebraços.
        drawLine(Color(0xFF26302E), leftHand+Offset(-2f*s,-2f*s), leftHand+Offset(2f*s,1f*s), 1.1f*s)
        drawLine(Color(0xFF26302E), rightHand+Offset(-2f*s,1f*s), rightHand+Offset(2f*s,-2f*s), 1.1f*s)
    }

    if (carrying) {
        drawRoundRect(Color(0xFFA8733E), Offset(x+3f*s, shoulderY+13f*s), Size(19f*s, 12f*s), CornerRadius(2f*s))
        drawLine(Color(0xFFD4A261), Offset(x+3f*s, shoulderY+18f*s), Offset(x+22f*s, shoulderY+18f*s), 1f*s)
    }
}

private fun DrawScope.drawHairBack(head: Offset, avatar: PlayerAvatar, art: AvatarArt, s: Float, phase: Float) {
    val style = if (avatar.skinStyle == "PRINCESA") "LONG" else avatar.hairStyle
    when (style) {
        "LONG" -> {
            val sway = sin(phase * PI * 2).toFloat()*1.2f*s
            val mass = Path().apply {
                moveTo(head.x-7.5f*s, head.y-5f*s)
                cubicTo(head.x-11f*s, head.y+2f*s, head.x-9f*s+sway, head.y+16f*s, head.x-4f*s+sway, head.y+20f*s)
                lineTo(head.x+5f*s+sway, head.y+20f*s)
                cubicTo(head.x+10f*s+sway, head.y+12f*s, head.x+10f*s, head.y+1f*s, head.x+7f*s, head.y-5f*s)
                close()
            }
            drawPath(mass, art.hair)
            // Fios separados: detalhe importante quando o jogador aproxima o zoom.
            listOf(-5f,-2.5f,0f,2.5f,5f).forEachIndexed { i, dx ->
                val drift = sway * (0.35f + i*.07f)
                val p = Path().apply {
                    moveTo(head.x+dx*s, head.y+2f*s)
                    cubicTo(head.x+(dx-1f)*s, head.y+8f*s, head.x+(dx+1f)*s+drift, head.y+14f*s, head.x+dx*s+drift, head.y+19f*s)
                }
                drawPath(p, art.hair.copy(alpha=.72f), style=Stroke(.8f*s, cap=StrokeCap.Round))
            }
        }
        "PONYTAIL" -> {
            drawCircle(art.hair, 5.4f*s, head+Offset(8f*s,5f*s))
            val p = Path().apply { moveTo(head.x+9f*s, head.y+6f*s); quadraticBezierTo(head.x+15f*s, head.y+12f*s, head.x+11f*s, head.y+19f*s) }
            drawPath(p, art.hair, style=Stroke(4.2f*s, cap=StrokeCap.Round))
        }
        "CURLY" -> repeat(8) { i ->
            val a = PI*(.72+i*.20)
            drawCircle(art.hair, 3.3f*s, head+Offset(cos(a).toFloat()*8.3f*s, sin(a).toFloat()*7.5f*s-1f*s))
        }
    }
}

private fun DrawScope.drawHairFront(head: Offset, avatar: PlayerAvatar, art: AvatarArt, s: Float, phase: Float) {
    val style = if (avatar.skinStyle == "PRINCESA") "LONG" else avatar.hairStyle
    when (style) {
        "BALD" -> Unit
        "BUZZ" -> drawArc(art.hair, 190f, 160f, true, Offset(head.x-8f*s, head.y-8f*s), Size(16f*s, 10f*s))
        "MOHAWK" -> {
            val p = Path().apply {
                moveTo(head.x-5f*s, head.y-5f*s); lineTo(head.x-2f*s, head.y-13f*s); lineTo(head.x+1f*s, head.y-6f*s); lineTo(head.x+4f*s, head.y-12f*s); lineTo(head.x+6f*s, head.y-4f*s); close()
            }
            drawPath(p, art.hair)
        }
        "LONG" -> {
            drawArc(art.hair, 185f, 170f, true, Offset(head.x-8.5f*s, head.y-8f*s), Size(17f*s, 10.5f*s))
            drawLine(art.hair, head+Offset(-5f*s,-2f*s), head+Offset(-6.4f*s,7f*s), 1.8f*s, StrokeCap.Round)
            drawLine(art.hair, head+Offset(5f*s,-2f*s), head+Offset(6.3f*s,7f*s), 1.8f*s, StrokeCap.Round)
        }
        "PONYTAIL", "CURLY", "SHORT" -> drawArc(art.hair, 185f, 170f, true, Offset(head.x-8f*s, head.y-8f*s), Size(16f*s, 10f*s))
        else -> drawArc(art.hair, 185f, 170f, true, Offset(head.x-8f*s, head.y-8f*s), Size(16f*s, 10f*s))
    }
}

private fun DrawScope.drawAvatarFace(head: Offset, avatar: PlayerAvatar, art: AvatarArt, s: Float, phase: Float) {
    val style = avatar.skinStyle
    val eyeY = head.y+1.2f*s
    val eyeTilt = if (style=="BEBADO") sin(phase*PI*2).toFloat()*.7f*s else 0f
    drawCircle(Color(0xFF1E2528), 1f*s, Offset(head.x-2.8f*s, eyeY+eyeTilt))
    drawCircle(Color(0xFF1E2528), 1f*s, Offset(head.x+2.8f*s, eyeY-eyeTilt))
    drawLine(Color(0xFF8C5E4A), Offset(head.x-2f*s,head.y+4.8f*s), Offset(head.x+2f*s,head.y+4.8f*s), .8f*s, StrokeCap.Round)

    when (style) {
        "PINOQUIO" -> {
            val nose = Path().apply {
                moveTo(head.x+3f*s, head.y+2f*s)
                lineTo(head.x+14f*s, head.y+4.5f*s)
                lineTo(head.x+3f*s, head.y+5.5f*s)
                close()
            }
            drawPath(nose, art.skin)
            drawPath(nose, art.outline.copy(alpha=.35f), style=Stroke(.8f*s))
        }
        "TATUZAO" -> {
            drawArc(Color(0xFF3B2A24), 18f, 145f, false, Offset(head.x-6f*s,head.y+2f*s), Size(12f*s,8f*s), style=Stroke(1.5f*s))
            drawLine(Color(0xFF3B2A24), Offset(head.x-5f*s,head.y+6f*s), Offset(head.x-3f*s,head.y+8f*s), 1f*s)
            drawLine(Color(0xFF3B2A24), Offset(head.x+5f*s,head.y+6f*s), Offset(head.x+3f*s,head.y+8f*s), 1f*s)
        }
        "BEBADO" -> {
            drawCircle(Color(0xFFB94646).copy(alpha=.34f), 2.7f*s, head+Offset(-4f*s,3.7f*s))
            drawCircle(Color(0xFFB94646).copy(alpha=.34f), 2.7f*s, head+Offset(4f*s,3.7f*s))
            drawLine(Color(0xFF7E5144), head+Offset(-2f*s,5.2f*s), head+Offset(2f*s,6.2f*s), 1f*s)
        }
        "PRINCESA" -> {
            drawLine(Color(0xFF7A405D), head+Offset(-2.2f*s,5f*s), head+Offset(2.2f*s,5f*s), 1f*s, StrokeCap.Round)
            drawCircle(Color(0xFFFFA8C5).copy(alpha=.22f), 2.3f*s, head+Offset(-4.4f*s,3.8f*s))
            drawCircle(Color(0xFFFFA8C5).copy(alpha=.22f), 2.3f*s, head+Offset(4.4f*s,3.8f*s))
        }
    }
}

private fun DrawScope.drawAvatarHeadwear(head: Offset, avatar: PlayerAvatar, art: AvatarArt, s: Float, phase: Float) {
    when (avatar.skinStyle) {
        "PRINCESA" -> {
            // Coroa grande o suficiente para ser identificável em zoom normal.
            val crown = Path().apply {
                moveTo(head.x-7f*s, head.y-8f*s)
                lineTo(head.x-5f*s, head.y-15f*s)
                lineTo(head.x-1.5f*s, head.y-10f*s)
                lineTo(head.x+1f*s, head.y-16f*s)
                lineTo(head.x+4f*s, head.y-10f*s)
                lineTo(head.x+7f*s, head.y-15f*s)
                lineTo(head.x+7.5f*s, head.y-7f*s)
                close()
            }
            drawPath(crown, Color(0xFFFFD45C))
            drawPath(crown, Color(0xFFFFF0A6), style=Stroke(.9f*s))
            drawCircle(Color(0xFFE45C86), 1.5f*s, Offset(head.x+1f*s, head.y-11.5f*s))
        }
        "BEBADO" -> {
            // Capacete propositalmente torto para reforçar a personalidade, sem representar bebida em cena.
            drawArc(art.headwear, 180f, 180f, true, Offset(head.x-9f*s,head.y-10.5f*s), Size(18f*s,11f*s))
            drawLine(art.headwear, Offset(head.x-10f*s,head.y-4f*s), Offset(head.x+8f*s,head.y-2.8f*s), 2.3f*s)
        }
        else -> if (avatar.helmetColor != "NONE") {
            drawArc(art.headwear, 180f, 180f, true, Offset(head.x-9f*s,head.y-10f*s), Size(18f*s,11f*s))
            drawLine(art.headwear, Offset(head.x-10f*s,head.y-3.5f*s), Offset(head.x+10f*s,head.y-3.5f*s), 2.2f*s)
        }
    }
}

private fun DrawScope.drawAvatarAccessory(head: Offset, avatar: PlayerAvatar, art: AvatarArt, s: Float) {
    when (avatar.accessory) {
        "GLASSES" -> {
            drawCircle(Color(0xFF0E171B), 2.5f*s, Offset(head.x-3f*s,head.y+1f*s), style=Stroke(1f*s))
            drawCircle(Color(0xFF0E171B), 2.5f*s, Offset(head.x+3f*s,head.y+1f*s), style=Stroke(1f*s))
            drawLine(Color(0xFF0E171B), Offset(head.x-.5f*s,head.y+1f*s), Offset(head.x+.5f*s,head.y+1f*s), .8f*s)
        }
        "HEADSET" -> {
            drawArc(Color(0xFF2C3B43), 205f, 130f, false, Offset(head.x-10f*s,head.y-10f*s), Size(20f*s,18f*s), style=Stroke(1.6f*s))
            drawCircle(Color(0xFF27343B), 2.8f*s, Offset(head.x+8f*s,head.y+2f*s))
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
