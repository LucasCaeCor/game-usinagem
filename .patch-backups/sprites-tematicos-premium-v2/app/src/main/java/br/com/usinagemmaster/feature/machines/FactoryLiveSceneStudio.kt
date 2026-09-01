package br.com.usinagemmaster.feature.machines

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.core.designsystem.component.drawPlayerAvatarFigure
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.catalog.LegendaryEmployeeCatalog
import br.com.usinagemmaster.domain.catalog.EmployeeVisualCatalog
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.model.MachineProduction
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Fábrica Viva Studio.
 *
 * A cena privilegia leitura e personalidade: cada máquina possui silhueta própria,
 * cada operador executa uma micro-rotina de trabalho e serviços periféricos nunca
 * atravessam as células de produção.
 */
@Composable
fun FactoryLiveSceneStudio(
    machines: List<MachineEntity>,
    employees: List<EmployeeEntity>,
    production: List<MachineProduction>,
    soundEnabled: Boolean,
    speechEnabled: Boolean,
    speechDurationSeconds: Int,
    playerProfile: LocalPlayerProfile,
    selectedMachineId: String? = null,
    idleEmployeeId: String? = null,
    onReprimand: (String) -> Unit = {},
    onSelect: (MachineEntity) -> Unit
) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "factory_studio")
    val workPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "work"
    )
    val logisticsPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "logistics"
    )
    val pulse by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse),
        label = "pulse"
    )

    var zoom by remember { mutableFloatStateOf(1.12f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var reprimandTargetId by remember { mutableStateOf<String?>(null) }
    val reprimandProgress = remember { Animatable(0f) }
    val minZoom = .82f
    val maxZoom = 3.25f

    FactoryAudioLayer(soundEnabled, machines, production)

    val productionByMachine = production.associateBy { it.machineId }
    val employeeByMachine = employees.filter { it.assignedMachineId != null }.associateBy { it.assignedMachineId!! }
    val operatingIds = production.filter { it.isOperating }.map { it.machineId }.toSet()
    val waiting = employees.filter {
        it.id != idleEmployeeId && (it.assignedMachineId == null || it.assignedMachineId !in operatingIds)
    }
    val inspector = waiting.firstOrNull { it.legendaryCode == "nikao_narizudo" }
    val logistics = waiting.firstOrNull {
        it.id != inspector?.id && it.specialty.contains("STOCK", ignoreCase = true)
    }
    val coffeeEmployees = waiting.filterNot { it.id == inspector?.id || it.id == logistics?.id }.take(5)
    val idleEmployee = employees.firstOrNull { it.id == idleEmployeeId && it.assignedMachineId != null }

    LaunchedEffect(reprimandTargetId) {
        val target = reprimandTargetId ?: return@LaunchedEffect
        reprimandProgress.snapTo(0f)
        reprimandProgress.animateTo(1f, tween(1050))
        onReprimand(target)
        delay(550L)
        reprimandProgress.animateTo(0f, tween(720))
        reprimandTargetId = null
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090F12)),
        border = BorderStroke(1.dp, Color(0xFF53626A).copy(alpha = .58f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF182329), Color(0xFF0B1115))))
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { 620.dp.toPx() }
            val tileW = min(widthPx / 6.05f, with(density) { 108.dp.toPx() })
            val tileH = tileW * .50f
            val sceneVisualScale = (tileW / 70f).coerceIn(1.12f, 2.8f)
            val center = Offset(widthPx / 2f, heightPx / 2f)

            val clampPan: (Offset, Float) -> Offset = { candidate, targetZoom ->
                if (targetZoom <= 1f) Offset.Zero else {
                    val maxX = widthPx * (targetZoom - 1f) * .46f
                    val maxY = heightPx * (targetZoom - 1f) * .44f
                    Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
                }
            }

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                        transformOrigin = TransformOrigin.Center
                    }
            ) {
                val layout = StudioLayout(size.width, size.height, tileW, tileH)
                val visualScale = sceneVisualScale

                studioBackdrop(layout, pulse, logisticsPhase)
                studioArchitecture(layout, logisticsPhase, pulse)
                studioFloor(layout)
                studioServiceLane(layout)
                studioWallProps(layout, pulse)

                val sorted = machines.sortedWith(compareBy<MachineEntity> { it.gridX + it.gridY }.thenBy { it.gridY })
                sorted.forEachIndexed { index, machine ->
                    val point = studioIsoPoint(layout, machine.gridX, machine.gridY)
                    val work = productionByMachine[machine.id]
                    val operating = work?.isOperating == true
                    val local = (workPhase + index * .113f) % 1f
                    val title = MachineCatalog.byType(machine.machineType)?.name ?: machine.machineType

                    studioMachineBay(point, layout, operating, machine.condition, machine.id == selectedMachineId)
                    studioMachine(
                        center = point,
                        type = machine.machineType,
                        title = title,
                        operating = operating,
                        condition = machine.condition,
                        phase = local,
                        pulse = pulse,
                        scale = visualScale
                    )

                    employeeByMachine[machine.id]?.let { employee ->
                        val side = if ((machine.gridX + machine.gridY) % 2 == 0) -1f else 1f
                        val base = point + Offset(layout.tileW * .31f * side, layout.tileH * .55f)
                        studioOperatorAtMachine(
                            base = base,
                            machine = point,
                            employee = employee,
                            operating = operating,
                            phoneIdle = employee.id == idleEmployeeId,
                            phase = local,
                            scale = visualScale
                        )
                    }
                }

                inspector?.let { worker ->
                    val t = studioTriangle((logisticsPhase * 1.08f + .17f) % 1f)
                    val p = studioLerp(
                        Offset(layout.floorLeft + layout.tileW * .55f, layout.backAisleY),
                        Offset(layout.floorRight - layout.tileW * .55f, layout.backAisleY),
                        t
                    )
                    studioWorker(
                        base = p,
                        employee = worker,
                        phase = workPhase,
                        walking = true,
                        carrying = false,
                        scale = visualScale * .96f
                    )
                    studioClipboard(p + Offset(13f * visualScale, -33f * visualScale), visualScale)
                }

                logistics?.let { worker ->
                    val t = studioTriangle((logisticsPhase * 1.27f + .35f) % 1f)
                    val p = Offset(
                        layout.serviceLeft + (layout.serviceRight - layout.serviceLeft) * t,
                        layout.serviceY
                    )
                    studioWorker(
                        base = p,
                        employee = worker,
                        phase = workPhase,
                        walking = true,
                        carrying = true,
                        scale = visualScale
                    )
                }

                studioForklift(layout, logisticsPhase, visualScale)
                studioMaterialCart(layout, (logisticsPhase + .42f) % 1f, visualScale * .9f)

                // O dono da fábrica circula pelo corredor. Ao tocar no funcionário
                // flagrado no celular, ele caminha fisicamente até a célula e dá a bronca.
                val ownerT = studioTriangle((logisticsPhase * .73f + .58f) % 1f)
                val ownerPatrol = Offset(
                    layout.floorRight - layout.tileW * .12f,
                    (layout.backAisleY + layout.tileH * .72f) +
                        ((layout.serviceY - layout.tileH * .42f) - (layout.backAisleY + layout.tileH * .72f)) * ownerT
                )
                val reprimandEmployee = employees.firstOrNull { it.id == reprimandTargetId }
                val reprimandMachine = reprimandEmployee?.assignedMachineId?.let { id -> machines.firstOrNull { it.id == id } }
                val reprimandPoint = reprimandMachine?.let { machine ->
                    val p = studioIsoPoint(layout, machine.gridX, machine.gridY)
                    val side = if ((machine.gridX + machine.gridY) % 2 == 0) -1f else 1f
                    p + Offset(layout.tileW * .31f * side, layout.tileH * .55f) + Offset(-side * 32f * visualScale, 4f * visualScale)
                }
                val ownerBase = if (reprimandPoint != null) {
                    studioLerp(ownerPatrol, reprimandPoint, reprimandProgress.value)
                } else ownerPatrol
                drawPlayerAvatarFigure(
                    base = ownerBase,
                    avatar = playerProfile.avatar,
                    scale = visualScale * 1.02f,
                    phase = workPhase,
                    walking = true,
                    carrying = false
                )
                studioOwnerBadge(
                    position = ownerBase + Offset(0f, -56f * visualScale),
                    name = playerProfile.displayName.ifBlank { "Você" }
                )
                if (reprimandPoint != null && reprimandProgress.value > .72f) {
                    studioSpeechBubble(ownerBase + Offset(0f, -68f * visualScale), "Ei! Guarda o celular e volta pra máquina.")
                }

                if (speechEnabled) {
                    val speakers = sorted.mapNotNull { machine ->
                        val worker = employeeByMachine[machine.id] ?: return@mapNotNull null
                        if (worker.legendaryCode == null) return@mapNotNull null
                        val machinePoint = studioIsoPoint(layout, machine.gridX, machine.gridY)
                        Triple(worker, machinePoint + Offset(0f, -layout.tileH * 1.15f), productionByMachine[machine.id]?.isOperating == true)
                    }
                    if (speakers.isNotEmpty()) {
                        val roundMs = 22000L
                        val now = System.currentTimeMillis()
                        val round = (now / roundMs).toInt()
                        val visible = speechDurationSeconds.coerceIn(5, 12) * 1000L
                        if (now % roundMs < visible) {
                            val speaker = speakers[round % speakers.size]
                            LegendaryEmployeeCatalog.quote(speaker.first.legendaryCode, speaker.third, round)?.let { quote ->
                                studioSpeechBubble(speaker.second, quote)
                            }
                        }
                    }
                }

                studioVignette()
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            val target = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                            zoom = target
                            pan = clampPan(pan + panChange, target)
                        }
                    }
                    .pointerInput(machines, zoom, pan, widthPx, heightPx) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = if (zoom < 1.75f) 2.1f else 1.12f
                                pan = Offset.Zero
                            },
                            onTap = { tap ->
                                val layout = StudioLayout(widthPx, heightPx, tileW, tileH)

                                // O funcionário no celular tem prioridade de toque sobre a máquina.
                                val idleMachine = idleEmployee?.assignedMachineId?.let { id -> machines.firstOrNull { it.id == id } }
                                val idleHit = idleMachine?.let { machine ->
                                    val point = studioIsoPoint(layout, machine.gridX, machine.gridY)
                                    val side = if ((machine.gridX + machine.gridY) % 2 == 0) -1f else 1f
                                    val workerWorld = point + Offset(layout.tileW * .31f * side, layout.tileH * .55f)
                                    val workerScreen = center + (workerWorld - center) * zoom + pan
                                    studioDistance(tap, workerScreen) <= 34f * sceneVisualScale * zoom
                                } == true

                                if (idleHit && idleEmployee != null && reprimandTargetId == null) {
                                    reprimandTargetId = idleEmployee.id
                                } else {
                                    val selected = machines.map { machine ->
                                        val world = studioIsoPoint(layout, machine.gridX, machine.gridY)
                                        val screen = center + (world - center) * zoom + pan
                                        machine to studioDistance(tap, screen)
                                    }.minByOrNull { it.second }
                                        ?.takeIf { it.second <= tileW * zoom * .55f }
                                        ?.first
                                    selected?.let(onSelect)
                                }
                            }
                        )
                    }
            )

            StudioSceneHeader(
                modifier = Modifier.align(Alignment.TopStart).padding(11.dp),
                operating = production.count { it.isOperating },
                waiting = production.count { !it.isOperating }
            )

            StudioZoomControls(
                modifier = Modifier.align(Alignment.TopEnd).padding(11.dp),
                zoom = zoom,
                onMinus = {
                    zoom = (zoom - .25f).coerceIn(minZoom, maxZoom)
                    pan = clampPan(pan, zoom)
                },
                onReset = { zoom = 1.12f; pan = Offset.Zero },
                onPlus = {
                    zoom = (zoom + .25f).coerceIn(minZoom, maxZoom)
                    pan = clampPan(pan, zoom)
                }
            )

        }
    }
}

@Composable
private fun StudioSceneHeader(modifier: Modifier, operating: Int, waiting: Int) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = Color(0xE60A1216),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .10f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("●", color = Color(0xFF61DEA0), style = MaterialTheme.typography.labelSmall)
            Column {
                Text("CHÃO DE FÁBRICA", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Text("$operating operando  •  $waiting em espera  •  toque em uma máquina", color = Color(0xFFAAB7BD), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StudioZoomControls(
    modifier: Modifier,
    zoom: Float,
    onMinus: () -> Unit,
    onReset: () -> Unit,
    onPlus: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = Color(0xE60A1216),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .10f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudioControlButton("−", onMinus)
            StudioControlButton("${(zoom * 100).roundToInt()}%", onReset, wide = true)
            StudioControlButton("+", onPlus)
        }
    }
}

@Composable
private fun StudioControlButton(text: String, onClick: () -> Unit, wide: Boolean = false) {
    Text(
        text,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = if (wide) 9.dp else 10.dp, vertical = 8.dp),
        color = if (wide) Color(0xFFFFC766) else Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun StudioCopaPanel(employees: List<EmployeeEntity>, modifier: Modifier, phase: Float) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2182024)),
        border = BorderStroke(1.dp, Color(0xFFFFC766).copy(alpha = .36f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("COPA • PAUSA", color = Color(0xFFFFD88D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            if (employees.isEmpty()) {
                Text("Ninguém em pausa agora.", color = Color(0xFFB5C0C5), style = MaterialTheme.typography.bodySmall)
            } else {
                employees.take(4).forEachIndexed { index, employee ->
                    val sip = sin((phase + index * .17f) * PI * 2f).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(if (sip > .28f) "☕" else "👷", style = MaterialTheme.typography.bodyMedium)
                        Column {
                            Text(employee.name, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("pausa rápida", color = Color(0xFF9FABAF), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.studioOwnerBadge(position: Offset, name: String) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10.5f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val safeName = name.take(14)
    val width = (paint.measureText(safeName) + 18f).coerceAtLeast(48f)
    drawRoundRect(
        color = Color(0xE6152026),
        topLeft = Offset(position.x - width / 2f, position.y - 13f),
        size = Size(width, 20f),
        cornerRadius = CornerRadius(10f)
    )
    drawRoundRect(
        color = Color(0xFFFFC766),
        topLeft = Offset(position.x - width / 2f, position.y - 13f),
        size = Size(width, 20f),
        cornerRadius = CornerRadius(10f),
        style = Stroke(1f)
    )
    drawContext.canvas.nativeCanvas.drawText("VOCÊ • $safeName", position.x, position.y + 1f, paint)
}

private data class StudioLayout(
    val width: Float,
    val height: Float,
    val tileW: Float,
    val tileH: Float
) {
    val wallBottom = height * .255f
    val floorTop = wallBottom
    val floorBottom = height * .895f
    val floorLeft = width * .07f
    val floorRight = width * .93f
    val centerX = width * .50f
    val originY = floorTop + tileH * 1.05f
    val backAisleY = floorTop + tileH * .62f
    val serviceY = floorBottom - tileH * .15f
    val serviceLeft = width * .10f
    val serviceRight = width * .90f
}

private fun studioIsoPoint(layout: StudioLayout, gridX: Int, gridY: Int): Offset {
    val x = gridX.coerceIn(0, 4)
    val y = gridY.coerceIn(0, 5)
    return Offset(
        layout.centerX + (x - y) * layout.tileW * .46f,
        layout.originY + (x + y) * layout.tileH * .49f
    )
}

private fun DrawScope.studioBackdrop(layout: StudioLayout, pulse: Float, slow: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF1E2A30), Color(0xFF111A1F), Color(0xFF080D10)),
            startY = 0f,
            endY = layout.height
        )
    )
    drawRect(Color(0xFF202A2F), topLeft = Offset.Zero, size = Size(layout.width, layout.wallBottom))

    // Chapas da parede e juntas verticais.
    repeat(8) { i ->
        val x = layout.width * i / 8f
        drawLine(Color(0xFF344047).copy(alpha = .52f), Offset(x, 0f), Offset(x, layout.wallBottom), 1f)
    }
    drawLine(Color(0xFF080C0E), Offset(0f, layout.wallBottom), Offset(layout.width, layout.wallBottom), 6f)

    // Luz ambiente indireta: pontos fixos, nada atravessando corredores.
    repeat(4) { i ->
        val x = layout.width * (.14f + i * .24f)
        drawCircle(Color(0xFFFFE6B0).copy(alpha = .06f + pulse * .025f), 95f, Offset(x, 42f))
    }

    // Poeira quase imperceptível perto do teto.
    repeat(10) { i ->
        val x = (layout.width * ((i * .127f + slow * .04f) % 1f))
        val y = 42f + (i % 3) * 26f
        drawCircle(Color.White.copy(alpha = .045f), 1.8f, Offset(x, y))
    }
}

private fun DrawScope.studioArchitecture(layout: StudioLayout, phase: Float, pulse: Float) {
    // Vigas superiores.
    drawRect(Color(0xFF0D1418), Offset(0f, 27f), Size(layout.width, 13f))
    repeat(5) { i ->
        val x = layout.width * (.07f + i * .22f)
        drawRect(Color(0xFF11191D), Offset(x, 18f), Size(10f, layout.wallBottom - 18f))
        drawLine(Color(0xFF46535A).copy(alpha = .45f), Offset(x + 2f, 18f), Offset(x + 2f, layout.wallBottom), 1.5f)
    }

    // Luminárias e cones curtos de luz.
    repeat(4) { i ->
        val x = layout.width * (.15f + i * .235f)
        drawRoundRect(Color(0xFFCCD6D8), Offset(x - 28f, 54f), Size(56f, 6f), CornerRadius(3f))
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFF4D6).copy(alpha = .12f * pulse), Color.Transparent),
                startY = 60f,
                endY = layout.floorTop + 110f
            ),
            topLeft = Offset(x - 55f, 60f),
            size = Size(110f, layout.floorTop + 50f)
        )
    }

    // Ponte rolante no alto: movimento fica acima da operação e não cruza pessoas.
    val railY = layout.wallBottom - 28f
    drawLine(Color(0xFF68757B), Offset(layout.width * .10f, railY), Offset(layout.width * .90f, railY), 5f)
    val craneX = layout.width * (.16f + .68f * studioTriangle((phase * .63f) % 1f))
    drawRoundRect(Color(0xFFE1AF35), Offset(craneX - 25f, railY - 8f), Size(50f, 12f), CornerRadius(3f))
    drawLine(Color(0xFF656F73), Offset(craneX, railY + 4f), Offset(craneX, railY + 25f), 2f)
    drawArc(Color(0xFFD7DEE0), 30f, 230f, false, Offset(craneX - 6f, railY + 20f), Size(12f, 14f), style = Stroke(2f))

    // Exaustores na parede.
    repeat(2) { i ->
        val c = Offset(layout.width * (.25f + i * .50f), 116f)
        drawCircle(Color(0xFF0A0F12), 30f, c)
        drawCircle(Color(0xFF4A575D), 29f, c, style = Stroke(3f))
        repeat(4) { blade ->
            val a = (phase * PI * 2 + blade * PI / 2).toFloat()
            val end = c + Offset(cos(a) * 23f, sin(a) * 23f)
            drawLine(Color(0xFF6A787E), c, end, 7f, StrokeCap.Round)
        }
        drawCircle(Color(0xFF9BA6AA), 4f, c)
    }

    // Duto principal.
    drawRoundRect(Color(0xFF525D62), Offset(layout.width * .08f, 76f), Size(layout.width * .84f, 16f), CornerRadius(8f))
    drawLine(Color.White.copy(alpha = .12f), Offset(layout.width * .08f, 79f), Offset(layout.width * .92f, 79f), 2f)
}

private fun DrawScope.studioFloor(layout: StudioLayout) {
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF31383B), Color(0xFF252C2F), Color(0xFF1C2225))),
        topLeft = Offset(layout.floorLeft, layout.floorTop),
        size = Size(layout.floorRight - layout.floorLeft, layout.floorBottom - layout.floorTop)
    )

    // Juntas do concreto.
    repeat(7) { row ->
        val y = layout.floorTop + (layout.floorBottom - layout.floorTop) * row / 7f
        drawLine(Color.Black.copy(alpha = .18f), Offset(layout.floorLeft, y), Offset(layout.floorRight, y), 1.2f)
    }
    repeat(6) { col ->
        val x = layout.floorLeft + (layout.floorRight - layout.floorLeft) * col / 6f
        drawLine(Color.Black.copy(alpha = .13f), Offset(x, layout.floorTop), Offset(x, layout.floorBottom), 1f)
    }

    // Corredores de pedestres - bordas, não o miolo.
    val yellow = Color(0xFFE9B93D).copy(alpha = .72f)
    drawLine(yellow, Offset(layout.floorLeft + 8f, layout.backAisleY), Offset(layout.floorRight - 8f, layout.backAisleY), 3f)
    drawLine(yellow, Offset(layout.floorLeft + 8f, layout.serviceY - 17f), Offset(layout.floorRight - 8f, layout.serviceY - 17f), 3f)

    // Marcas discretas de uso.
    repeat(12) { i ->
        val x = layout.floorLeft + 26f + (i * 73f) % (layout.floorRight - layout.floorLeft - 52f)
        val y = layout.floorTop + 70f + (i * 47f) % (layout.floorBottom - layout.floorTop - 100f)
        drawCircle(Color(0xFF111719).copy(alpha = .18f), 4f + i % 3, Offset(x, y))
    }
}

private fun DrawScope.studioServiceLane(layout: StudioLayout) {
    val laneTop = layout.serviceY - 34f
    drawRect(Color(0xFF161D20).copy(alpha = .92f), Offset(layout.serviceLeft, laneTop), Size(layout.serviceRight - layout.serviceLeft, 58f))
    drawLine(Color(0xFFE8B63C).copy(alpha = .8f), Offset(layout.serviceLeft, laneTop), Offset(layout.serviceRight, laneTop), 2.5f)
    drawLine(Color(0xFFE8B63C).copy(alpha = .65f), Offset(layout.serviceLeft, laneTop + 58f), Offset(layout.serviceRight, laneTop + 58f), 2f)
    studioText("LOGÍSTICA", layout.serviceLeft + 12f, laneTop + 20f, 10f, Color(0xFF9DA7AA), false, true)
}

private fun DrawScope.studioWallProps(layout: StudioLayout, pulse: Float) {
    // Quadro de produção.
    val board = Offset(layout.width * .065f, 102f)
    drawRoundRect(Color(0xFF0B1114), board, Size(104f, 62f), CornerRadius(7f))
    drawRoundRect(Color(0xFF526067), board, Size(104f, 62f), CornerRadius(7f), style = Stroke(1.4f))
    studioText("USINAGEM", board.x + 8f, board.y + 19f, 10f, Color.White, false, true)
    studioText("MASTER", board.x + 8f, board.y + 34f, 13f, Color(0xFFFFC766), false, true)
    drawCircle(Color(0xFF55E39A).copy(alpha = .55f + pulse * .35f), 4f, Offset(board.x + 91f, board.y + 49f))

    // Porta da copa: acessível, mas fora do chão produtivo.
    val doorW = 70f
    val doorH = 88f
    val doorX = layout.width - doorW - 30f
    val doorY = layout.wallBottom - doorH
    drawRect(Color(0xFF151D21), Offset(doorX, doorY), Size(doorW, doorH))
    drawRect(Color(0xFF6B7478), Offset(doorX, doorY), Size(doorW, doorH), style = Stroke(2f))
    drawRoundRect(Color(0xFF614617), Offset(doorX + 8f, doorY + 15f), Size(54f, 24f), CornerRadius(5f))
    studioText("☕ COPA", doorX + 35f, doorY + 31f, 10f, Color(0xFFFFD991), true, true)
    drawCircle(Color(0xFFD8B36A), 2.5f, Offset(doorX + 58f, doorY + 60f))

    // Armário de ferramentas na parede esquerda.
    val cabinet = Offset(layout.floorLeft + 12f, layout.wallBottom - 70f)
    drawRoundRect(Color(0xFF26333A), cabinet, Size(58f, 65f), CornerRadius(4f))
    drawLine(Color(0xFF61727A), Offset(cabinet.x + 29f, cabinet.y + 4f), Offset(cabinet.x + 29f, cabinet.y + 61f), 1f)
    repeat(4) { i -> drawCircle(Color(0xFFE6B84A), 1.8f, Offset(cabinet.x + 10f + i * 12f, cabinet.y + 14f)) }
}

private fun DrawScope.studioMachineBay(center: Offset, layout: StudioLayout, operating: Boolean, condition: Int, selected: Boolean) {
    val w = layout.tileW * .88f
    val h = layout.tileH * 1.15f
    val safe = Path().apply {
        moveTo(center.x, center.y - h * .55f)
        lineTo(center.x + w * .52f, center.y)
        lineTo(center.x, center.y + h * .55f)
        lineTo(center.x - w * .52f, center.y)
        close()
    }
    drawPath(safe, Color(0xFF090D0F).copy(alpha = .20f))
    drawPath(safe, if (operating) Color(0xFF55D98A).copy(alpha = .18f) else Color(0xFFE4B243).copy(alpha = .16f), style = Stroke(2f))
    if (selected) {
        drawPath(safe, Color(0xFFFFC84D).copy(alpha = .95f), style = Stroke(4f))
        drawCircle(Color(0xFFFFC84D).copy(alpha = .18f), w * .42f, center)
    }

    // Piso sob a máquina / sombra.
    drawOval(Color.Black.copy(alpha = .30f), topLeft = Offset(center.x - w * .38f, center.y + h * .10f), size = Size(w * .76f, h * .32f))

    // Estado pela borda, sem etiquetas flutuantes enormes.
    val state = when {
        condition < 30 -> Color(0xFFFF6262)
        operating -> Color(0xFF55E39A)
        else -> Color(0xFFE8B84A)
    }
    drawCircle(state, 3.5f, Offset(center.x + w * .42f, center.y - h * .35f))
}

private fun DrawScope.studioMachine(
    center: Offset,
    type: String,
    title: String,
    operating: Boolean,
    condition: Int,
    phase: Float,
    pulse: Float,
    scale: Float
) {
    when {
        type.contains("LASER") || type.contains("PLASMA") -> studioLaser(center, type, operating, phase, scale)
        type.contains("ROBOTIC_WELDING") -> studioRobotWelder(center, operating, phase, scale)
        type.contains("WELDING_BENCH") -> studioWeldingBench(center, operating, phase, scale)
        type.contains("EDM") -> studioEdm(center, operating, phase, scale)
        type.contains("CNC_MACHINING_CENTER_5") -> studioMachiningCenter(center, operating, phase, scale, fiveAxis = true)
        type.contains("CNC_MACHINING_CENTER_3") -> studioMachiningCenter(center, operating, phase, scale, fiveAxis = false)
        type.contains("CNC_LATHE") -> studioCncLathe(center, operating, phase, scale)
        type.contains("CNC_GRINDER") -> studioCncGrinder(center, operating, phase, scale)
        type.contains("CNC_DRILL") -> studioCncDrill(center, operating, phase, scale)
        type.contains("MECHANICAL_LATHE") -> studioMechanicalLathe(center, operating, phase, scale)
        type.contains("UNIVERSAL_MILL") -> studioUniversalMill(center, operating, phase, scale)
        type.contains("COLUMN_DRILL") -> studioColumnDrill(center, operating, phase, scale)
        type.contains("CYLINDRICAL_GRINDER") -> studioCylindricalGrinder(center, operating, phase, scale)
        else -> studioGenericMachine(center, operating, phase, scale)
    }

    // Plaqueta pequena na própria máquina.
    val short = title.replace("Centro de Usinagem", "Centro").take(17)
    studioText(short, center.x, center.y + 27f * scale, 6.8f * scale, Color(0xFFD7DEE1), true, true)
    studioText(
        if (operating) "● PRODUZINDO" else "● EM ESPERA",
        center.x,
        center.y + 35f * scale,
        5.1f * scale,
        if (operating) Color(0xFF66E3A0) else Color(0xFFE6B64A),
        true,
        true
    )

    if (condition < 30) {
        drawCircle(Color(0xFFFF5E5E).copy(alpha = .45f + pulse * .35f), 5f * scale, center + Offset(24f * scale, -27f * scale))
    }
}

private fun DrawScope.studioMechanicalLathe(c: Offset, operating: Boolean, phase: Float, s: Float) {
    val green = Color(0xFF315B4B)
    val steel = Color(0xFF89969B)
    val base = c + Offset(0f, 6f * s)
    drawRoundRect(Color(0xFF1A2622), base + Offset(-32f*s, 9f*s), Size(64f*s, 18f*s), CornerRadius(3f*s))
    drawRoundRect(green, base + Offset(-31f*s, -12f*s), Size(62f*s, 25f*s), CornerRadius(4f*s))
    drawRoundRect(Color(0xFF24483B), base + Offset(-31f*s, -22f*s), Size(21f*s, 17f*s), CornerRadius(4f*s))
    drawCircle(steel, 9f*s, base + Offset(-10f*s, -8f*s))
    drawCircle(Color(0xFF21282B), 6f*s, base + Offset(-10f*s, -8f*s))
    if (operating) {
        repeat(3) { i ->
            val a = (phase * PI * 2 + i * 2.1).toFloat()
            drawLine(Color(0xFFD9E2E4), base + Offset(-10f*s, -8f*s), base + Offset((-10f + cos(a)*6f)*s, (-8f + sin(a)*6f)*s), 1.2f*s)
        }
    }
    val carriage = (-1f + 2f * studioTriangle(phase)) * 12f
    drawRoundRect(Color(0xFF69787C), base + Offset((carriage-3f)*s, -2f*s), Size(16f*s, 8f*s), CornerRadius(2f*s))
    drawLine(Color(0xFFBFC8CA), base + Offset((carriage+5f)*s, -5f*s), base + Offset((carriage+5f)*s, -13f*s), 2f*s)
}

private fun DrawScope.studioUniversalMill(c: Offset, operating: Boolean, phase: Float, s: Float) {
    val green = Color(0xFF39634E)
    drawRoundRect(Color(0xFF27312D), c + Offset(-26f*s, 8f*s), Size(52f*s, 19f*s), CornerRadius(3f*s))
    drawRoundRect(green, c + Offset(-20f*s, -26f*s), Size(22f*s, 41f*s), CornerRadius(4f*s))
    drawRoundRect(Color(0xFF506F60), c + Offset(-12f*s, -31f*s), Size(31f*s, 13f*s), CornerRadius(4f*s))
    val tableShift = if (operating) (studioTriangle(phase) - .5f) * 16f else 0f
    drawRoundRect(Color(0xFF899498), c + Offset((-28f+tableShift)*s, -4f*s), Size(57f*s, 7f*s), CornerRadius(2f*s))
    val spindleDrop = if (operating) studioTriangle((phase + .25f) % 1f) * 7f else 1f
    drawLine(Color(0xFFD5DDDF), c + Offset(12f*s, -18f*s), c + Offset(12f*s, (-8f+spindleDrop)*s), 3f*s, StrokeCap.Round)
    if (operating) studioCuttingMist(c + Offset(12f*s, 0f), phase, s)
}

private fun DrawScope.studioColumnDrill(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFF26343D), c + Offset(-22f*s, 15f*s), Size(44f*s, 10f*s), CornerRadius(3f*s))
    drawRoundRect(Color(0xFF355D71), c + Offset(-5f*s, -29f*s), Size(10f*s, 47f*s), CornerRadius(3f*s))
    drawRoundRect(Color(0xFF49788E), c + Offset(-15f*s, -34f*s), Size(30f*s, 13f*s), CornerRadius(5f*s))
    drawRoundRect(Color(0xFF89999E), c + Offset(-17f*s, 1f*s), Size(34f*s, 5f*s), CornerRadius(2f*s))
    val drop = if (operating) studioTriangle(phase) * 11f else 2f
    drawLine(Color(0xFFE0E5E6), c + Offset(0f, -21f*s), c + Offset(0f, (-7f+drop)*s), 2f*s)
}

private fun DrawScope.studioCylindricalGrinder(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFF2D5B57), c + Offset(-31f*s, -8f*s), Size(62f*s, 34f*s), CornerRadius(5f*s))
    drawRoundRect(Color(0xFF193A37), c + Offset(-31f*s, -23f*s), Size(24f*s, 20f*s), CornerRadius(4f*s))
    val wheel = c + Offset(11f*s, -10f*s)
    drawCircle(Color(0xFF686D70), 12f*s, wheel)
    drawCircle(Color(0xFF1B2123), 5f*s, wheel)
    if (operating) {
        repeat(5) { i ->
            val a = (phase * PI * 2 + i * 1.25).toFloat()
            drawLine(Color(0xFFC7CFD1), wheel, wheel + Offset(cos(a)*10f*s, sin(a)*10f*s), 1f*s)
        }
        studioSparks(c + Offset(25f*s, -2f*s), phase, s, Color(0xFFFFC05A))
    }
}

private fun DrawScope.studioWeldingBench(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFF34393B), c + Offset(-33f*s, -4f*s), Size(66f*s, 10f*s), CornerRadius(2f*s))
    repeat(2) { i -> drawRect(Color(0xFF282D2F), c + Offset((-28f+i*47f)*s, 6f*s), Size(8f*s, 22f*s)) }
    drawRect(Color(0xFF747E82), c + Offset(-17f*s, -10f*s), Size(34f*s, 5f*s))
    if (operating) {
        val tip = c + Offset((studioTriangle(phase)-.5f)*16f*s, -15f*s)
        drawLine(Color(0xFF262B2D), c + Offset(24f*s, -26f*s), tip, 4f*s, StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = .72f), 5f*s, tip)
        studioSparks(tip, phase, s, Color(0xFFFFB74F))
    }
}

private fun DrawScope.studioCncLathe(c: Offset, operating: Boolean, phase: Float, s: Float) {
    val body = Color(0xFFE2E6E5)
    drawRoundRect(Color(0xFF293239), c + Offset(-36f*s, 17f*s), Size(72f*s, 11f*s), CornerRadius(3f*s))
    drawRoundRect(body, c + Offset(-35f*s, -28f*s), Size(70f*s, 47f*s), CornerRadius(6f*s))
    drawRoundRect(Color(0xFF355361), c + Offset(-22f*s, -20f*s), Size(36f*s, 28f*s), CornerRadius(3f*s))
    drawRoundRect(Color(0xFF15262E), c + Offset(-18f*s, -17f*s), Size(28f*s, 21f*s), CornerRadius(2f*s))
    if (operating) {
        val glow = .14f + .10f * sin(phase * PI * 2).toFloat().let(::abs)
        drawRoundRect(Color(0xFF80D8E8).copy(alpha = glow), c + Offset(-18f*s, -17f*s), Size(28f*s, 21f*s), CornerRadius(2f*s))
        drawCircle(Color(0xFFCBD4D6), 5f*s, c + Offset(-5f*s, -6f*s))
        drawLine(Color(0xFFE8F0F1), c + Offset(-5f*s, -6f*s), c + Offset((studioTriangle(phase)*8f-9f)*s, -6f*s), 1.5f*s)
    }
    studioControlPanel(c + Offset(24f*s, -7f*s), operating, s)
}

private fun DrawScope.studioMachiningCenter(c: Offset, operating: Boolean, phase: Float, s: Float, fiveAxis: Boolean) {
    val body = if (fiveAxis) Color(0xFFE8EAE8) else Color(0xFFDDE4E6)
    val accent = if (fiveAxis) Color(0xFF2D536F) else Color(0xFF315F70)
    drawRoundRect(Color(0xFF263037), c + Offset(-38f*s, 18f*s), Size(76f*s, 10f*s), CornerRadius(3f*s))
    drawRoundRect(body, c + Offset(-38f*s, -31f*s), Size(76f*s, 51f*s), CornerRadius(6f*s))
    drawRoundRect(accent, c + Offset(-27f*s, -23f*s), Size(45f*s, 34f*s), CornerRadius(4f*s))
    drawRoundRect(Color(0xFF10262F), c + Offset(-23f*s, -19f*s), Size(37f*s, 26f*s), CornerRadius(3f*s))
    if (operating) {
        val toolX = (-12f + studioTriangle(phase) * 20f) * s
        val toolY = (-12f + studioTriangle((phase + .25f)%1f) * 8f) * s
        drawLine(Color(0xFFD6E0E2), c + Offset(toolX, -19f*s), c + Offset(toolX, toolY), 2.6f*s, StrokeCap.Round)
        drawCircle(Color(0xFF68BDD1).copy(alpha = .18f), 13f*s, c + Offset(toolX, -2f*s))
        if (fiveAxis) {
            drawArc(Color(0xFFE6AA47), 190f, 160f, false, c + Offset(-15f*s, -12f*s), Size(28f*s, 23f*s), style = Stroke(2f*s))
        }
    }
    studioControlPanel(c + Offset(27f*s, -8f*s), operating, s)
}

private fun DrawScope.studioCncGrinder(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFFE0E4E3), c + Offset(-34f*s, -27f*s), Size(68f*s, 50f*s), CornerRadius(6f*s))
    drawRoundRect(Color(0xFF34505A), c + Offset(-25f*s, -20f*s), Size(40f*s, 29f*s), CornerRadius(3f*s))
    val wheel = c + Offset(-2f*s, -5f*s)
    drawCircle(Color(0xFF72787A), 10f*s, wheel)
    if (operating) studioSparks(c + Offset(12f*s, 2f*s), phase, s, Color(0xFFFFB951))
    studioControlPanel(c + Offset(25f*s, -6f*s), operating, s)
}

private fun DrawScope.studioCncDrill(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFFDDE4E6), c + Offset(-29f*s, -30f*s), Size(58f*s, 54f*s), CornerRadius(6f*s))
    drawRoundRect(Color(0xFF294957), c + Offset(-19f*s, -21f*s), Size(30f*s, 31f*s), CornerRadius(3f*s))
    val drop = if (operating) studioTriangle(phase) * 13f else 2f
    drawLine(Color(0xFFE2E9EA), c + Offset(-3f*s, -18f*s), c + Offset(-3f*s, (-2f+drop)*s), 2f*s)
    studioControlPanel(c + Offset(20f*s, -7f*s), operating, s)
}

private fun DrawScope.studioRobotWelder(c: Offset, operating: Boolean, phase: Float, s: Float) {
    // Célula com proteção amarela e robô próprio.
    drawRoundRect(Color(0xFF22292C), c + Offset(-36f*s, -18f*s), Size(72f*s, 43f*s), CornerRadius(4f*s))
    drawRect(Color(0xFFE1B23F), c + Offset(-36f*s, -22f*s), Size(4f*s, 46f*s))
    drawRect(Color(0xFFE1B23F), c + Offset(32f*s, -22f*s), Size(4f*s, 46f*s))
    drawLine(Color(0xFFE1B23F), c + Offset(-34f*s, -20f*s), c + Offset(34f*s, -20f*s), 2f*s)
    val shoulder = c + Offset(-12f*s, 4f*s)
    val a = if (operating) sin(phase * PI * 2).toFloat() * .45f else -.2f
    val elbow = shoulder + Offset(cos(a)*20f*s, (-16f + sin(a)*6f)*s)
    val tip = elbow + Offset((18f + sin(phase*PI*2).toFloat()*4f)*s, 8f*s)
    drawCircle(Color(0xFFE8B62F), 8f*s, shoulder)
    drawLine(Color(0xFFE8B62F), shoulder, elbow, 8f*s, StrokeCap.Round)
    drawCircle(Color(0xFF394349), 5f*s, elbow)
    drawLine(Color(0xFFE8B62F), elbow, tip, 6f*s, StrokeCap.Round)
    if (operating) {
        drawCircle(Color.White.copy(alpha = .85f), 4f*s, tip)
        studioSparks(tip, phase, s, Color(0xFFFFAF38))
    }
}

private fun DrawScope.studioEdm(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFFCBD2D4), c + Offset(-32f*s, -28f*s), Size(64f*s, 52f*s), CornerRadius(6f*s))
    drawRoundRect(Color(0xFF264A56), c + Offset(-23f*s, -18f*s), Size(37f*s, 29f*s), CornerRadius(3f*s))
    drawRect(Color(0xFF3F7585).copy(alpha = .5f), c + Offset(-19f*s, -8f*s), Size(29f*s, 15f*s))
    val drop = if (operating) studioTriangle(phase) * 8f else 1f
    drawLine(Color(0xFFE3E8E9), c + Offset(-5f*s, -18f*s), c + Offset(-5f*s, (-6f+drop)*s), 2f*s)
    if (operating) drawCircle(Color(0xFF72DAE3).copy(alpha = .35f), 4f*s, c + Offset(-5f*s, (1f+drop)*s))
    studioControlPanel(c + Offset(23f*s, -7f*s), operating, s)
}

private fun DrawScope.studioLaser(c: Offset, type: String, operating: Boolean, phase: Float, s: Float) {
    val plasma = type.contains("PLASMA")
    val baseColor = if (plasma) Color(0xFF574132) else Color(0xFF304B58)
    drawRoundRect(Color(0xFF20272A), c + Offset(-39f*s, 10f*s), Size(78f*s, 17f*s), CornerRadius(4f*s))
    drawRect(baseColor, c + Offset(-34f*s, -6f*s), Size(68f*s, 18f*s))
    repeat(6) { i -> drawLine(Color(0xFF838D90), c + Offset((-28f+i*11f)*s, -5f*s), c + Offset((-28f+i*11f)*s, 9f*s), 1f*s) }
    val headX = (-24f + studioTriangle(phase) * 48f) * s
    drawLine(Color(0xFFBEC8CA), c + Offset(-30f*s, -15f*s), c + Offset(30f*s, -15f*s), 4f*s)
    drawRoundRect(Color(0xFFDAE1E2), c + Offset(headX-5f*s, -20f*s), Size(10f*s, 16f*s), CornerRadius(2f*s))
    if (operating) {
        val beam = if (plasma) Color(0xFFFFD26C) else Color(0xFF72DBFF)
        drawLine(beam.copy(alpha = .85f), c + Offset(headX, -4f*s), c + Offset(headX, 7f*s), 2f*s)
        studioSparks(c + Offset(headX, 8f*s), phase, s, beam)
    }
}

private fun DrawScope.studioGenericMachine(c: Offset, operating: Boolean, phase: Float, s: Float) {
    drawRoundRect(Color(0xFF4A6268), c + Offset(-28f*s, -22f*s), Size(56f*s, 45f*s), CornerRadius(6f*s))
    drawRoundRect(Color(0xFF162329), c + Offset(-18f*s, -15f*s), Size(28f*s, 23f*s), CornerRadius(3f*s))
    if (operating) drawCircle(Color(0xFF5BDEA0), 4f*s, c + Offset(20f*s, -14f*s))
}

private fun DrawScope.studioControlPanel(c: Offset, operating: Boolean, s: Float) {
    drawRoundRect(Color(0xFF242D31), c + Offset(-8f*s, -14f*s), Size(16f*s, 28f*s), CornerRadius(2f*s))
    drawRoundRect(Color(0xFF15262D), c + Offset(-5f*s, -10f*s), Size(10f*s, 8f*s), CornerRadius(1f*s))
    drawCircle(if (operating) Color(0xFF54DE93) else Color(0xFFE5B24A), 2f*s, c + Offset(-3f*s, 4f*s))
    drawCircle(Color(0xFFCF5D5D), 2f*s, c + Offset(3f*s, 4f*s))
}

private fun DrawScope.studioCuttingMist(c: Offset, phase: Float, s: Float) {
    repeat(4) { i ->
        val dx = sin((phase + i*.19f) * PI * 2).toFloat() * (4f+i) * s
        val dy = -(3f + (i * 2f)) * s
        drawCircle(Color(0xFF9BD8E4).copy(alpha = .12f), (2f+i*.5f)*s, c + Offset(dx, dy))
    }
}

private fun DrawScope.studioSparks(c: Offset, phase: Float, s: Float, color: Color) {
    repeat(7) { i ->
        val p = (phase + i * .13f) % 1f
        val angle = (-.4f + i * .16f)
        val len = (7f + 15f * p) * s
        val end = c + Offset(cos(angle) * len, (sin(angle) * len + p * 12f*s))
        drawLine(color.copy(alpha = (1f-p).coerceAtLeast(.15f)), c, end, (1.1f + (i%2)*.5f)*s, StrokeCap.Round)
    }
}

private fun DrawScope.studioOperatorAtMachine(
    base: Offset,
    machine: Offset,
    employee: EmployeeEntity,
    operating: Boolean,
    phoneIdle: Boolean,
    phase: Float,
    scale: Float
) {
    val cycle = phase.coerceIn(0f, 1f)
    val toward = (machine - base)
    val dir = if (toward.x >= 0f) 1f else -1f

    // Micro-rotina: painel -> inclina -> inspeção curta -> retorna.
    // Quando flagrado ocioso, a rotina troca para celular e a máquina para de produzir.
    val workLean = if (operating && !phoneIdle) {
        when {
            cycle < .28f -> cycle / .28f
            cycle < .58f -> 1f
            cycle < .78f -> 1f - (cycle-.58f)/.20f
            else -> 0f
        }
    } else 0f
    val inspectStep = if (operating && !phoneIdle && cycle in .64f..90f) sin(((cycle-.64f)/.26f) * PI).toFloat() else 0f
    val approach = if (operating && !phoneIdle) sin(cycle * PI).toFloat().coerceAtLeast(0f) else 0f
    val body = base + Offset(
        dir * (workLean * 8.5f + inspectStep * 6.5f + approach * 2.5f) * scale,
        -abs(sin(cycle * PI * 2).toFloat()) * 1.3f * scale
    )
    val armTarget = machine + Offset(-dir * (11f + 3f * studioTriangle(cycle)) * scale, (-5f + 3f * sin(cycle * PI * 2).toFloat()) * scale)

    studioWorker(
        base = body,
        employee = employee,
        phase = phase * 1.35f,
        walking = operating && !phoneIdle && (cycle < .18f || cycle > .82f),
        carrying = false,
        phone = phoneIdle,
        scale = scale,
        workLean = workLean,
        armTarget = if (operating && !phoneIdle) armTarget else null
    )

    if (phoneIdle) {
        studioPhoneStatus(body + Offset(0f, -66f * scale), scale)
    } else if (!operating && cycle > .52f) {
        studioClipboard(body + Offset(dir * 13f * scale, -31f * scale), scale)
    }
}

private data class StudioWorkerPalette(
    val uniform: Color,
    val accent: Color,
    val helmet: Color,
    val skin: Color,
    val hair: Color,
    val width: Float,
    val height: Float,
    val female: Boolean,
    val hairStyle: String,
    val skinStyle: String
)

private fun studioWorkerPalette(employee: EmployeeEntity): StudioWorkerPalette {
    val visual = EmployeeVisualCatalog.resolve(employee)
    val base = when (visual.skinStyle) {
        "TATUZAO" -> StudioWorkerPalette(Color(0xFF37464C), Color(0xFFE3A63D), Color(0xFFD49B32), Color(0xFFB97A56), Color(0xFF2D211C), 1.28f, 1.07f, false, "SHORT", visual.skinStyle)
        "KENDAO_KIMONO" -> StudioWorkerPalette(Color(0xFFE4E0D5), Color(0xFF29323A), Color(0xFF252B2E), Color(0xFFB87957), Color(0xFF241D1A), 1.11f, 1.04f, false, "SHORT", visual.skinStyle)
        "PINOQUIO" -> StudioWorkerPalette(Color(0xFF25374A), Color(0xFF65B7E8), Color(0xFFFFFFFF), Color(0xFFB77955), Color(0xFF33261F), .98f, 1.00f, false, "SHORT", visual.skinStyle)
        "MAGRAO" -> StudioWorkerPalette(Color(0xFF344433), Color(0xFFB9D85D), Color(0xFFE2BA46), Color(0xFFA46C4C), Color(0xFF2C211D), .73f, 1.14f, false, "SHORT", visual.skinStyle)
        "TREME_TREME" -> StudioWorkerPalette(Color(0xFF38404E), Color(0xFFE3BE59), Color(0xFFDFB847), Color(0xFFB77A59), Color(0xFF2E2521), .95f, .99f, false, "SHORT", visual.skinStyle)
        "BEBADO" -> StudioWorkerPalette(Color(0xFF4B3433), Color(0xFFF1924A), Color(0xFF353A3C), Color(0xFFC48660), Color(0xFF30221E), 1.03f, 1.00f, false, "SHORT", visual.skinStyle)
        "PRINCESA" -> StudioWorkerPalette(Color(0xFF654867), Color(0xFFFFB0D0), Color(0xFFFF9FC7), Color(0xFFC78C68), Color(0xFF4C302A), .92f, 1.02f, true, visual.hairStyle, visual.skinStyle)
        else -> when (employee.legendaryCode) {
            "moskitao" -> StudioWorkerPalette(Color(0xFF263E46), Color(0xFF6ED2BA), Color(0xFFF0C44F), Color(0xFF9E684A), Color(0xFF2A201B), .92f, 1.02f, false, visual.hairStyle, visual.skinStyle)
            "gumersvaldo" -> StudioWorkerPalette(Color(0xFF222B36), Color(0xFF58C6E0), Color(0xFF252B2E), Color(0xFFC38A68), Color(0xFF2C231E), 1.00f, 1.00f, false, visual.hairStyle, visual.skinStyle)
            "pedrao" -> StudioWorkerPalette(Color(0xFF463A36), Color(0xFFFFA047), Color(0xFF34383A), Color(0xFFB57752), Color(0xFF2D211C), 1.18f, 1.05f, false, visual.hairStyle, visual.skinStyle)
            "merciao" -> StudioWorkerPalette(Color(0xFF364956), Color(0xFF7FD5CB), Color(0xFFE8C04B), Color(0xFFC28B67), Color(0xFF3B2C25), .98f, 1.00f, false, visual.hairStyle, visual.skinStyle)
            "bodybuilder" -> StudioWorkerPalette(Color(0xFF283B34), Color(0xFFF0B84A), Color(0xFF303638), Color(0xFFA96D4E), Color(0xFF241C18), 1.32f, 1.08f, false, visual.hairStyle, visual.skinStyle)
            else -> StudioWorkerPalette(
                uniform = if (visual.female) Color(0xFF40516B) else Color(0xFF31434D),
                accent = if (visual.female) Color(0xFF8ED0E7) else Color(0xFFEAB943),
                helmet = if (visual.female) Color(0xFFF5C85B) else Color(0xFFE6B843),
                skin = Color(0xFFB77C5A),
                hair = when (visual.hairColor) {
                    "BROWN" -> Color(0xFF684735)
                    "BLONDE" -> Color(0xFFD2B369)
                    "GRAY" -> Color(0xFF8D9498)
                    else -> Color(0xFF27282A)
                },
                width = if (visual.female) .92f else 1.00f,
                height = if (visual.female) 1.02f else 1.00f,
                female = visual.female,
                hairStyle = visual.hairStyle,
                skinStyle = visual.skinStyle
            )
        }
    }
    return base.copy(
        hair = when (visual.hairColor) {
            "BROWN" -> Color(0xFF684735)
            "BLONDE" -> Color(0xFFD2B369)
            "GRAY" -> Color(0xFF8D9498)
            else -> base.hair
        }
    )
}

private fun DrawScope.studioWorker(
    base: Offset,
    employee: EmployeeEntity,
    phase: Float,
    walking: Boolean,
    carrying: Boolean,
    phone: Boolean = false,
    scale: Float,
    workLean: Float = 0f,
    armTarget: Offset? = null
) {
    val palette = studioWorkerPalette(employee)
    val s = scale * palette.height
    val width = palette.width
    val tremble = if (palette.skinStyle == "TREME_TREME") sin(phase * PI * 18).toFloat() * 1.8f * s else 0f
    val drunkSway = if (palette.skinStyle == "BEBADO") sin(phase * PI * 1.55).toFloat() * 3.2f * s else 0f
    val walk = if (walking) sin(phase * PI * 2).toFloat() else 0f
    val bob = if (walking) abs(sin(phase * PI * 2).toFloat()) * 1.6f * s else 0f
    val x = base.x + tremble + drunkSway
    val y = base.y - bob

    drawOval(Color.Black.copy(alpha = .26f), Offset(x - 11f*s*width, base.y - 3f*s), Size(22f*s*width, 7f*s))

    val hip = Offset(x, y - 18f*s)
    val shoulder = Offset(x + workLean * 2f*s, y - 33f*s)
    val head = Offset(x + workLean * 2.5f*s + if (palette.skinStyle == "BEBADO") drunkSway*.22f else 0f, y - 46f*s)

    val legSwing = walk * 5f*s
    drawLine(Color(0xFF1D272C), hip + Offset(-4f*s*width, 0f), Offset(x - 6f*s*width + legSwing, y - 3f*s), 5f*s*width, StrokeCap.Round)
    drawLine(Color(0xFF1D272C), hip + Offset(4f*s*width, 0f), Offset(x + 6f*s*width - legSwing, y - 3f*s), 5f*s*width, StrokeCap.Round)
    drawLine(Color(0xFF151A1D), Offset(x - 6f*s*width + legSwing, y - 3f*s), Offset(x - 10f*s*width + legSwing, y), 4f*s*width, StrokeCap.Round)
    drawLine(Color(0xFF151A1D), Offset(x + 6f*s*width - legSwing, y - 3f*s), Offset(x + 10f*s*width - legSwing, y), 4f*s*width, StrokeCap.Round)

    drawRoundRect(
        palette.uniform,
        Offset(shoulder.x - 9f*s*width, shoulder.y),
        Size(18f*s*width, 18f*s),
        CornerRadius(if (palette.female) 6f*s else 5f*s)
    )
    drawLine(palette.accent.copy(alpha = .9f), Offset(shoulder.x - 8f*s*width, shoulder.y + 7f*s), Offset(shoulder.x + 8f*s*width, shoulder.y + 7f*s), 2f*s)
    drawLine(Color(0xFFCDD4D5).copy(alpha = .55f), Offset(shoulder.x - 7f*s*width, shoulder.y + 12f*s), Offset(shoulder.x + 7f*s*width, shoulder.y + 12f*s), 1.4f*s)

    if (palette.skinStyle == "KENDAO_KIMONO") {
        drawLine(Color(0xFF30383D), shoulder + Offset(-6f*s, 2f*s), shoulder + Offset(6f*s, 15f*s), 1.8f*s)
        drawLine(Color(0xFF30383D), shoulder + Offset(6f*s, 2f*s), shoulder + Offset(-6f*s, 15f*s), 1.8f*s)
        drawRect(Color(0xFF202628), shoulder + Offset(-9f*s*width, 14f*s), Size(18f*s*width, 3f*s))
    }

    val leftShoulder = shoulder + Offset(-8f*s*width, 5f*s)
    val rightShoulder = shoulder + Offset(8f*s*width, 5f*s)
    when {
        phone -> {
            val phoneCenter = Offset(x, shoulder.y + 13f*s)
            val leftHand = phoneCenter + Offset(-4f*s, 1f*s)
            val rightHand = phoneCenter + Offset(4f*s, 1f*s)
            drawLine(palette.uniform, leftShoulder, leftHand, 4f*s, StrokeCap.Round)
            drawLine(palette.uniform, rightShoulder, rightHand, 4f*s, StrokeCap.Round)
            drawCircle(palette.skin, 2.4f*s, leftHand)
            drawCircle(palette.skin, 2.4f*s, rightHand)
            drawRoundRect(Color(0xFF101619), phoneCenter + Offset(-3.4f*s, -5.5f*s), Size(6.8f*s, 11f*s), CornerRadius(1.2f*s))
            drawRoundRect(Color(0xFF66C7EE), phoneCenter + Offset(-2.4f*s, -4.2f*s), Size(4.8f*s, 7.2f*s), CornerRadius(.8f*s))
            val scroll = studioTriangle((phase*1.7f)%1f)
            drawLine(Color.White.copy(alpha=.75f), phoneCenter + Offset(-1.4f*s, (-2.2f+scroll*3f)*s), phoneCenter + Offset(1.4f*s, (-2.2f+scroll*3f)*s), .7f*s)
        }
        armTarget != null -> {
            val near = if (armTarget.x >= shoulder.x) rightShoulder else leftShoulder
            val far = if (armTarget.x >= shoulder.x) leftShoulder else rightShoulder
            val hand = studioLerp(near, armTarget, .63f + .18f * workLean)
            drawLine(palette.uniform, near, hand, 4f*s, StrokeCap.Round)
            drawCircle(palette.skin, 2.7f*s, hand)
            val idleHand = far + Offset(if (far.x < shoulder.x) -5f*s else 5f*s, 11f*s)
            drawLine(palette.uniform, far, idleHand, 4f*s, StrokeCap.Round)
            drawCircle(palette.skin, 2.5f*s, idleHand)
        }
        carrying -> {
            val box = Offset(x, y - 22f*s)
            drawLine(palette.uniform, leftShoulder, box + Offset(-9f*s, 0f), 4f*s, StrokeCap.Round)
            drawLine(palette.uniform, rightShoulder, box + Offset(9f*s, 0f), 4f*s, StrokeCap.Round)
            drawRoundRect(Color(0xFF9C7140), box + Offset(-11f*s, -6f*s), Size(22f*s, 13f*s), CornerRadius(2f*s))
            drawLine(Color(0xFFC69C60), box + Offset(0f, -6f*s), box + Offset(0f, 7f*s), 1f*s)
        }
        else -> {
            val armSwing = walk * 5f*s
            drawLine(palette.uniform, leftShoulder, leftShoulder + Offset(-4f*s, 11f*s - armSwing), 4f*s, StrokeCap.Round)
            drawLine(palette.uniform, rightShoulder, rightShoulder + Offset(4f*s, 11f*s + armSwing), 4f*s, StrokeCap.Round)
        }
    }

    drawRoundRect(palette.skin, head + Offset(-6f*s*width, -1f*s), Size(12f*s*width, 12f*s), CornerRadius(5f*s))

    // Cabelo fica sob o capacete; mulheres têm silhuetas próprias e continuam usando EPI.
    when (palette.hairStyle) {
        "LONG" -> drawRoundRect(palette.hair, head + Offset(-6f*s, -2f*s), Size(12f*s, 15f*s), CornerRadius(5f*s))
        "PONYTAIL" -> drawCircle(palette.hair, 4.3f*s, head + Offset(-7f*s, 6f*s))
        "BUZZ" -> drawLine(palette.hair, head + Offset(-5f*s, 0f), head + Offset(5f*s, 0f), 2f*s)
    }

    drawCircle(Color(0xFF2B201B).copy(alpha = .60f), 1.15f*s, head + Offset(3.1f*s, 4f*s))
    drawCircle(Color(0xFF2B201B).copy(alpha = .45f), 1.0f*s, head + Offset(-2.2f*s, 4f*s))

    if (palette.skinStyle == "PINOQUIO") {
        drawLine(palette.skin, head + Offset(4f*s, 5f*s), head + Offset(10f*s, 7f*s), 2.2f*s, StrokeCap.Round)
    }
    if (employee.legendaryCode == "gumersvaldo") {
        drawLine(Color(0xFF67CAE0), head + Offset(-5f*s, 4f*s), head + Offset(5f*s, 4f*s), 1.5f*s)
    }
    if (palette.skinStyle == "TATUZAO") {
        drawArc(Color(0xFF3A2A23), 20f, 145f, false, head + Offset(-5f*s, 4f*s), Size(10f*s, 7f*s), style = Stroke(1.4f*s))
    }
    if (palette.skinStyle == "BEBADO") {
        drawCircle(Color(0xFFB94D4D).copy(alpha=.30f), 2.2f*s, head + Offset(-3.7f*s, 6f*s))
        drawCircle(Color(0xFFB94D4D).copy(alpha=.30f), 2.2f*s, head + Offset(3.7f*s, 6f*s))
    }

    drawArc(palette.helmet, 180f, 180f, true, head + Offset(-8f*s*width, -5f*s), Size(16f*s*width, 11f*s))
    drawLine(palette.helmet, head + Offset(-9f*s*width, 1f*s), head + Offset(8f*s*width, 1f*s), 2.5f*s, StrokeCap.Round)

    if (employee.isLegendary) {
        drawCircle(Color(0xFFFFD268), 2.3f*s, head + Offset(-10f*s*width, -3f*s))
        studioText(employee.name.take(12), x, y + 10f*s, 5.9f*s, Color(0xFFFFD781), true, true)
    }
}

private fun DrawScope.studioPhoneStatus(c: Offset, s: Float) {
    drawRoundRect(Color(0xE6391E1E), c + Offset(-28f*s, -8f*s), Size(56f*s, 16f*s), CornerRadius(7f*s))
    drawRoundRect(Color(0xFFFF8A65).copy(alpha=.7f), c + Offset(-28f*s, -8f*s), Size(56f*s, 16f*s), CornerRadius(7f*s), style = Stroke(1f*s))
    studioText("📱 OCIOSO • TOQUE", c.x, c.y + 3f*s, 6.4f*s, Color(0xFFFFD0C4), true, true)
}

private fun DrawScope.studioClipboard(c: Offset, s: Float) {
    drawRoundRect(Color(0xFFE8E4D5), c + Offset(-5f*s, -7f*s), Size(10f*s, 14f*s), CornerRadius(1.5f*s))
    drawRect(Color(0xFF58666C), c + Offset(-2.5f*s, -9f*s), Size(5f*s, 3f*s))
    repeat(3) { i -> drawLine(Color(0xFF889397), c + Offset(-3f*s, (-3f+i*3f)*s), c + Offset(3f*s, (-3f+i*3f)*s), .8f*s) }
}

private fun DrawScope.studioForklift(layout: StudioLayout, phase: Float, s: Float) {
    val t = studioTriangle(phase)
    val x = layout.serviceLeft + 45f*s + (layout.serviceRight - layout.serviceLeft - 90f*s) * t
    val y = layout.serviceY + 3f*s
    val direction = if (phase < .5f) 1f else -1f
    withTransform({ scale(direction, 1f, pivot = Offset(x, y)) }) {
        drawOval(Color.Black.copy(alpha = .27f), Offset(x-22f*s, y+7f*s), Size(49f*s, 8f*s))
        drawRoundRect(Color(0xFFE0A62D), Offset(x-20f*s, y-14f*s), Size(31f*s, 21f*s), CornerRadius(4f*s))
        drawRoundRect(Color(0xFF1E2B31), Offset(x-8f*s, y-27f*s), Size(17f*s, 16f*s), CornerRadius(3f*s))
        drawLine(Color(0xFF2B3438), Offset(x+12f*s, y-25f*s), Offset(x+12f*s, y+7f*s), 4f*s)
        drawLine(Color(0xFF717B7E), Offset(x+12f*s, y+1f*s), Offset(x+31f*s, y+1f*s), 2f*s)
        drawCircle(Color(0xFF171C1E), 6f*s, Offset(x-12f*s, y+7f*s))
        drawCircle(Color(0xFF171C1E), 6f*s, Offset(x+7f*s, y+7f*s))
        drawRoundRect(Color(0xFF95693A), Offset(x+18f*s, y-7f*s), Size(18f*s, 9f*s), CornerRadius(1.5f*s))
    }
}

private fun DrawScope.studioMaterialCart(layout: StudioLayout, phase: Float, s: Float) {
    val t = studioTriangle(phase)
    val x = layout.serviceRight - 36f*s - (layout.serviceRight - layout.serviceLeft - 110f*s) * t
    val y = layout.serviceY + 12f*s
    drawOval(Color.Black.copy(alpha = .22f), Offset(x - 16f*s, y + 4f*s), Size(34f*s, 6f*s))
    drawRoundRect(Color(0xFF5D6870), Offset(x - 15f*s, y - 9f*s), Size(30f*s, 13f*s), CornerRadius(2f*s))
    drawLine(Color(0xFFABB5B9), Offset(x + 14f*s, y - 8f*s), Offset(x + 22f*s, y - 17f*s), 2f*s)
    drawCircle(Color(0xFF151A1D), 3.5f*s, Offset(x - 9f*s, y + 5f*s))
    drawCircle(Color(0xFF151A1D), 3.5f*s, Offset(x + 10f*s, y + 5f*s))
    repeat(3) { i ->
        drawRoundRect(Color(0xFF92704A), Offset(x - 12f*s + i*8f*s, y - (15f+i%2*3f)*s), Size(8f*s, 7f*s), CornerRadius(1f*s))
    }
}

private fun DrawScope.studioSpeechBubble(anchor: Offset, text: String) {
    val clean = text.take(48)
    val width = (clean.length * 5.7f + 25f).coerceIn(78f, 190f)
    val height = if (clean.length > 26) 43f else 30f
    val left = (anchor.x - width / 2f).coerceIn(8f, size.width - width - 8f)
    val top = (anchor.y - height).coerceAtLeast(8f)
    drawRoundRect(Color(0xFFF8F4E8), Offset(left, top), Size(width, height), CornerRadius(9f))
    drawRoundRect(Color(0xFFC89432).copy(alpha = .85f), Offset(left, top), Size(width, height), CornerRadius(9f), style = Stroke(1.2f))
    val shown = if (clean.length > 26) listOf(clean.take(25), clean.drop(25)) else listOf(clean)
    shown.forEachIndexed { i, line ->
        studioText(line, left + width/2f, top + 19f + i*14f, 10.5f, Color(0xFF28231D), true, true)
    }
}

private fun DrawScope.studioVignette() {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color.Transparent, Color(0xFF020405).copy(alpha = .20f)),
            startY = size.height * .68f,
            endY = size.height
        )
    )
}

private fun studioTriangle(value: Float): Float = 1f - abs(((value * 2f) % 2f) - 1f)

private fun studioLerp(a: Offset, b: Offset, t: Float): Offset = Offset(a.x + (b.x-a.x)*t, a.y + (b.y-a.y)*t)

private fun studioDistance(a: Offset, b: Offset): Float {
    val dx = a.x-b.x
    val dy = a.y-b.y
    return sqrt((dx*dx + dy*dy).toDouble()).toFloat()
}

private fun DrawScope.studioText(
    text: String,
    x: Float,
    y: Float,
    sizePx: Float,
    color: Color,
    centered: Boolean,
    bold: Boolean
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(
            (color.alpha*255).roundToInt(),
            (color.red*255).roundToInt(),
            (color.green*255).roundToInt(),
            (color.blue*255).roundToInt()
        )
        textSize = sizePx
        textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
