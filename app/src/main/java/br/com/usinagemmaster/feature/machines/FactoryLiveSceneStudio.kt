package br.com.usinagemmaster.feature.machines
// Fábrica Viva 2.1 — projeção em fileiras para telas verticais

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.core.designsystem.component.drawPlayerAvatarFigure
import br.com.usinagemmaster.data.local.entity.ProductionCargoEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.catalog.LegendaryEmployeeCatalog
import br.com.usinagemmaster.domain.catalog.EmployeeVisualCatalog
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.simulation.FactoryFrame
import br.com.usinagemmaster.domain.simulation.FactoryFloor
import br.com.usinagemmaster.domain.simulation.FactoryMachineInput
import br.com.usinagemmaster.domain.simulation.FactoryMachineState
import br.com.usinagemmaster.domain.simulation.FactoryWorkerFrame
import br.com.usinagemmaster.domain.simulation.FloorPoint
import br.com.usinagemmaster.domain.simulation.WorkerActivity
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
    factoryFrame: State<FactoryFrame>,
    pendingCargo: State<List<ProductionCargoEntity>>,
    delivering: Boolean,
    onDeliver: () -> Unit,
    production: List<MachineProduction>,
    soundEnabled: Boolean,
    speechEnabled: Boolean,
    speechDurationSeconds: Int,
    playerProfile: LocalPlayerProfile,
    selectedMachineId: String? = null,
    onReprimand: (String) -> Unit = {},
    onSelect: (MachineEntity) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // A cena se adapta à altura real do aparelho. Em celulares menores, evita
    // transformar a Fábrica Viva em um bloco de 620dp que empurra todo o resto para baixo.
    val sceneHeight = when {
        configuration.screenHeightDp <= 680 -> 405.dp
        configuration.screenHeightDp <= 760 -> 435.dp
        configuration.screenHeightDp <= 860 -> 475.dp
        configuration.screenHeightDp <= 980 -> 500.dp
        else -> 540.dp
    }
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

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selectedWorkerId by remember { mutableStateOf<String?>(null) }
    var reprimandTargetId by remember { mutableStateOf<String?>(null) }
    var ownerRoute by remember { mutableStateOf(listOf(FactoryFloor.ENTRY.point())) }
    val latestReprimand by rememberUpdatedState(onReprimand)
    val latestDeliver by rememberUpdatedState(onDeliver)
    val deliveryBusy by rememberUpdatedState(delivering)
    val reprimandProgress = remember { Animatable(0f) }
    val minZoom = .78f
    val maxZoom = 3.25f

    StudioSimulationAudio(soundEnabled, machines, production, factoryFrame)
    val employeesById = remember(employees) { employees.associateBy { it.id } }
    val sceneFloor = remember(machines) {
        FactoryFloor(machines.map { FactoryMachineInput(it.id, it.gridX, it.gridY, it.installed) })
    }

    LaunchedEffect(reprimandTargetId, sceneFloor, delivering) {
        if (delivering) {
            reprimandTargetId = null
            ownerRoute = listOf(FactoryFloor.ENTRY.point())
            reprimandProgress.snapTo(0f)
            return@LaunchedEffect
        }
        val target = reprimandTargetId ?: return@LaunchedEffect
        val worker = factoryFrame.value.workers.firstOrNull { it.id == target }
        if (worker == null) {
            reprimandTargetId = null
            return@LaunchedEffect
        }
        ownerRoute = sceneFloor.route(FactoryFloor.ENTRY, sceneFloor.nearestWalkable(worker.position))
        if (ownerRoute.isEmpty()) {
            reprimandTargetId = null
            return@LaunchedEffect
        }
        val duration = (ownerRoute.size * 180).coerceIn(900, 12000)
        reprimandProgress.snapTo(0f)
        reprimandProgress.animateTo(1f, tween(duration, easing = LinearEasing))
        if (factoryFrame.value.workers.any { it.id == target && it.activity == WorkerActivity.PHONE }) {
            latestReprimand(target)
        }
        delay(550L)
        reprimandProgress.animateTo(0f, tween(duration, easing = LinearEasing))
        reprimandTargetId = null
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090F12)),
        border = BorderStroke(1.dp, Color(0xFF53626A).copy(alpha = .58f))
    ) {
        StudioLiveHeader(Modifier.fillMaxWidth().padding(10.dp), factoryFrame)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(sceneHeight)
                .clipToBounds()
                .background(Brush.verticalGradient(listOf(Color(0xFF182329), Color(0xFF0B1115))))
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { sceneHeight.toPx() }
            val occupiedRows = (machines.maxOfOrNull { it.gridY + 1 } ?: 3).coerceIn(3, 6)
            val layout = remember(widthPx, heightPx, occupiedRows) {
                StudioLayout(widthPx, heightPx, occupiedRows)
            }
            val sceneVisualScale = layout.projection.machineScale
            val workerScale = layout.projection.workerScale
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
                val visualScale = sceneVisualScale
                studioPortraitBuilding(layout, pulse)
                studioFloor(layout)

                val frame = factoryFrame.value
                val states = frame.machines.associateBy { it.id }
                studioStations(layout, workerScale)
                val waitingCargo = pendingCargo.value.count { it.id !in frame.cargoInTransit }
                studioCargoDock(layout, frame.depositedLots, waitingCargo, pulse)
                frame.workers.firstOrNull { it.id == selectedWorkerId }?.let { worker ->
                    val route = listOf(worker.position) + worker.route
                    route.zipWithNext().forEach { (a, b) ->
                        drawLine(Color(0xFF67D9F5).copy(alpha = .7f), studioWorldPoint(layout, a),
                            studioWorldPoint(layout, b), 2f * visualScale, StrokeCap.Round)
                    }
                }

                // Draw machines and people in floor-depth order, so a worker can pass behind a bay.
                val drawables = machines.map { machine ->
                    StudioDrawable(studioIsoPoint(layout, machine.gridX, machine.gridY).y, machine = machine)
                } + frame.workers.filter { it.activity != WorkerActivity.OFF_SHIFT }.map { worker ->
                    StudioDrawable(studioWorldPoint(layout, worker.position).y, worker = worker)
                }
                drawables.sortedBy { it.depth }.forEach { drawable ->
                    drawable.machine?.let { machine ->
                        val point = studioIsoPoint(layout, machine.gridX, machine.gridY)
                        val state = states[machine.id]
                        val operating = state?.state == FactoryMachineState.RUNNING
                        val local = (workPhase + Math.floorMod(machine.id.hashCode(), 100) / 100f) % 1f
                        studioMachineBay(point, layout, operating, machine.condition, machine.id == selectedMachineId)
                        studioMachine(point, machine.machineType,
                            operating, machine.condition, local, pulse, visualScale)
                        state?.let {
                            studioMachineStatus(point, layout, it.state, it.progress, it.needsMaintenance, visualScale)
                        }
                    }
                    drawable.worker?.let { worker ->
                        val employee = employeesById[worker.id] ?: return@let
                        val point = studioWorldPoint(layout, worker.position)
                        val working = worker.activity == WorkerActivity.WORKING || worker.activity == WorkerActivity.SETTING_UP
                        val bay = machines.firstOrNull { it.id == worker.machineId }
                        if (worker.id == selectedWorkerId) {
                            drawOval(Color(0xFF67D9F5).copy(alpha = .65f), point + Offset(-12f, -4f) * workerScale,
                                Size(24f * workerScale, 8f * workerScale), style = Stroke(2f * workerScale))
                        }
                        studioWorker(point, employee, workPhase, worker.walking, worker.carrying,
                            worker.activity == WorkerActivity.PHONE, workerScale,
                            workLean = if (working) .35f + pulse * .35f else 0f,
                            armTarget = if (working && bay != null) studioIsoPoint(layout, bay.gridX, bay.gridY) else null)
                        if (worker.activity == WorkerActivity.PHONE) studioPhoneStatus(point + Offset(0f, -66f * workerScale), workerScale)
                        if (worker.activity == WorkerActivity.INSPECTING) studioClipboard(point + Offset(12f, -30f) * workerScale, workerScale)
                        if (worker.activity == WorkerActivity.BREAK) studioText("☕", point.x, point.y - 56f * workerScale,
                            13f * workerScale, Color(0xFFFFD38A), centered = true, bold = false)
                    }
                }

                val owner = frame.owner
                val ownerBase = studioWorldPoint(layout, if (owner.busy) owner.position else studioRoutePoint(ownerRoute, reprimandProgress.value))
                drawPlayerAvatarFigure(ownerBase, playerProfile.avatar, workerScale * 1.02f, workPhase,
                    walking = if (owner.busy) owner.walking else reprimandProgress.isRunning, carrying = owner.carrying)
                if (owner.carrying) studioCargoCart(ownerBase + Offset(-19f, 0f) * workerScale, workerScale)
                // The owner's name remains in the profile; no floating nameplate over the map.
                if (!deliveryBusy && reprimandTargetId != null && reprimandProgress.value > .98f) {
                    studioSpeechBubble(ownerBase + Offset(0f, -68f * workerScale), "Vamos voltar ao trabalho!")
                }
                if (speechEnabled) {
                    val speakers = frame.workers.filter {
                        it.id == selectedWorkerId && it.activity != WorkerActivity.OFF_SHIFT && employeesById[it.id]?.legendaryCode != null
                    }
                    val now = System.currentTimeMillis()
                    val round = (now / 22000L).toInt()
                    if (speakers.isNotEmpty() && now % 22000L < speechDurationSeconds.coerceIn(5, 12) * 1000L) {
                        val speaker = speakers[Math.floorMod(round, speakers.size)]
                        LegendaryEmployeeCatalog.quote(employeesById[speaker.id]?.legendaryCode,
                            speaker.activity == WorkerActivity.WORKING, round)?.let { quote ->
                            studioSpeechBubble(studioWorldPoint(layout, speaker.position) + Offset(0f, -65f * workerScale), quote)
                        }
                    }
                }

                studioVignette()
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    // UX mobile: UM dedo pertence ao scroll vertical da tela.
                    // Zoom e pan da câmera só capturam o gesto quando existem 2+ dedos.
                    // Isso evita que o Canvas "roube" o gesto e impeça o usuário de
                    // chegar aos cards/funcionários abaixo da fábrica.
                    .pointerInput(layout) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val target = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                                    zoom = target
                                    pan = clampPan(pan + panChange, target)
                                    event.changes.forEach { it.consume() }
                                }
                                event = awaitPointerEvent()
                            }
                        }
                    }
                    .pointerInput(machines, zoom, pan, layout) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = if (zoom < 1.75f) 2.1f else 1f
                                pan = Offset.Zero
                            },
                            onTap = { tap ->
                                val worldTap = Offset(
                                    layout.projection.unproject(tap.x, center.x, zoom, pan.x),
                                    layout.projection.unproject(tap.y, center.y, zoom, pan.y),
                                )

                                val dock = studioWorldPoint(layout, FactoryFloor.STAGING.point())
                                if (studioDistance(worldTap, dock) < maxOf(24f, layout.projection.cellWidth * .45f)
                                    && pendingCargo.value.isNotEmpty() && !deliveryBusy) {
                                    latestDeliver()
                                    return@detectTapGestures
                                }
                                val touchedWorker = factoryFrame.value.workers
                                    .filter { it.activity != WorkerActivity.OFF_SHIFT }
                                    .map { worker ->
                                        val world = studioWorldPoint(layout, worker.position) + Offset(0f, -24f * workerScale)
                                        worker to studioDistance(worldTap, world)
                                    }.minByOrNull { it.second }
                                    ?.takeIf { it.second <= maxOf(22f * workerScale, layout.projection.cellWidth * .15f) }?.first
                                if (touchedWorker != null) {
                                    selectedWorkerId = touchedWorker.id
                                    if (touchedWorker.activity == WorkerActivity.PHONE && reprimandTargetId == null && !deliveryBusy) {
                                        reprimandTargetId = touchedWorker.id
                                    }
                                } else {
                                    selectedWorkerId = null
                                    val selected = machines.filter { machine ->
                                        layout.projection.hitsMachine(worldTap.x, worldTap.y, machine.gridX, machine.gridY)
                                    }.minByOrNull { machine ->
                                        studioDistance(worldTap, studioIsoPoint(layout, machine.gridX, machine.gridY))
                                    }
                                    selected?.let(onSelect)
                                }
                            }
                        )
                    }
            )

            StudioZoomControls(
                modifier = Modifier.align(Alignment.TopEnd).padding(11.dp),
                zoom = zoom,
                onMinus = {
                    zoom = (zoom - .25f).coerceIn(minZoom, maxZoom)
                    pan = clampPan(pan, zoom)
                },
                onReset = { zoom = 1f; pan = Offset.Zero },
                onPlus = {
                    zoom = (zoom + .25f).coerceIn(minZoom, maxZoom)
                    pan = clampPan(pan, zoom)
                }
            )

        }
        FactoryOperationsPanel(factoryFrame, employeesById, selectedWorkerId, machines, selectedMachineId)
    }
}

/** Small physical staging area; the card shows the exact amount and piece count. */
private fun DrawScope.studioCargoDock(layout: StudioLayout, depositedLots: Int, readyLoads: Int, pulse: Float) {
    val base = studioWorldPoint(layout, FactoryFloor.STAGING.point())
    val scale = layout.projection.machineScale
    val width = 68f * scale
    drawRoundRect(if (readyLoads > 0) Color(0xFF68DE9A).copy(alpha = .3f + pulse * .25f) else Color(0xFF617A71),
        base + Offset(-width / 2f, -12f * scale), Size(width, 22f * scale), CornerRadius(4f * scale))
    repeat(3) { row ->
        drawLine(Color(0xFFAD8557), base + Offset(-width / 2f, row * 5f * scale),
            base + Offset(width / 2f, row * 5f * scale), 3f * scale)
    }
    val boxes = (readyLoads.coerceAtMost(4) * 2 + depositedLots.coerceAtMost(4)).coerceAtMost(8)
    repeat(boxes) { box ->
        val origin = base + Offset((-29f + (box % 4) * 15f) * scale, (-13f - (box / 4) * 13f) * scale)
        drawRect(if (readyLoads > 0) Color(0xFFE6BA75) else Color(0xFF9B8970), origin, Size(13f * scale, 12f * scale))
        drawLine(Color(0xFFFFE5AE), origin + Offset(6f * scale, 0f), origin + Offset(6f * scale, 12f * scale), 2f * scale)
    }
    studioText("CARGA", base.x, base.y + 24f * scale, 10f * scale, Color(0xFFBEEBD1), centered = true, bold = true)
}

private fun DrawScope.studioCargoCart(base: Offset, scale: Float) {
    drawRect(Color(0xFFE6BA75), base + Offset(-16f, -22f) * scale, Size(27f * scale, 18f * scale))
    drawLine(Color(0xFFB6CAD0), base + Offset(-19f, -3f) * scale, base + Offset(16f, -3f) * scale, 3f * scale)
    drawLine(Color(0xFFB6CAD0), base + Offset(16f, -3f) * scale, base + Offset(19f, -27f) * scale, 3f * scale)
    drawCircle(Color(0xFF26343B), 4f * scale, base + Offset(-10f, 1f) * scale)
    drawCircle(Color(0xFF26343B), 4f * scale, base + Offset(10f, 1f) * scale)
}

private data class StudioDrawable(val depth: Float, val machine: MachineEntity? = null, val worker: FactoryWorkerFrame? = null)

private fun studioWorldPoint(layout: StudioLayout, point: FloorPoint): Offset =
    Offset(layout.projection.x(point.x), layout.projection.y(point.y))

private fun studioRoutePoint(route: List<FloorPoint>, progress: Float): FloorPoint {
    if (route.isEmpty()) return FactoryFloor.ENTRY.point()
    if (route.size == 1) return route.first()
    val distance = progress.coerceIn(0f, 1f) * (route.size - 1)
    val index = distance.toInt().coerceAtMost(route.size - 2)
    val t = distance - index
    val a = route[index]
    val b = route[index + 1]
    return FloorPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

private fun DrawScope.studioStations(layout: StudioLayout, scale: Float) {
    listOf(FactoryFloor.STOCK to "M", FactoryFloor.TOOLS to "F",
        FactoryFloor.INSPECTION to "Q", FactoryFloor.SHIPPING to "E",
        FactoryFloor.BREAK_ROOM to "C").forEach { (cell, name) ->
        val point = studioWorldPoint(layout, cell.point())
        val radius = layout.projection.cellWidth * .16f
        drawCircle(Color(0xFF162D35), radius, point)
        drawCircle(Color(0xFF77C5CD).copy(alpha = .65f), radius, point, style = Stroke(maxOf(1f, scale)))
        studioText(name, point.x, point.y + radius * .35f, radius * 1.05f, Color(0xFFCAE5E6), true, true)
    }
}

private fun DrawScope.studioMachineStatus(point: Offset, layout: StudioLayout, state: FactoryMachineState,
    progress: Float, maintenance: Boolean, scale: Float) {
    val color = when (state) {
        FactoryMachineState.RUNNING -> Color(0xFF66E4A6)
        FactoryMachineState.BROKEN -> Color(0xFFFF7474)
        FactoryMachineState.SETUP, FactoryMachineState.MAINTENANCE -> Color(0xFFFFC766)
        FactoryMachineState.WAITING_MATERIAL -> Color(0xFF77CDED)
        else -> Color(0xFF9CAAB3)
    }
    // A lamp and compact progress bar replace the repeated multi-line labels.
    val lamp = point + Offset(36f * scale, -35f * scale)
    drawCircle(color, 3.5f * scale, lamp)
    if (maintenance) drawCircle(Color(0xFFFFC766), 6f * scale, lamp, style = Stroke(1.5f * scale))
    if (state == FactoryMachineState.RUNNING || state == FactoryMachineState.SETUP) {
        val left = point + Offset(-19f * scale, 30f * scale)
        drawLine(Color(0xFF20343C), left, left + Offset(38f * scale, 0f), 2.5f * scale, StrokeCap.Round)
        drawLine(color, left, left + Offset(38f * scale * progress, 0f), 2.5f * scale, StrokeCap.Round)
    }
}

@Composable
private fun StudioSimulationAudio(enabled: Boolean, machines: List<MachineEntity>, production: List<MachineProduction>, frame: State<FactoryFrame>) {
    val audioState by remember(frame) { derivedStateOf {
        frame.value.open to frame.value.machines.filter { it.state == FactoryMachineState.RUNNING }.map { it.id }.toSet()
    } }
    FactorySimulationAudio(enabled && audioState.first, machines,
        production.map { it.copy(isOperating = it.machineId in audioState.second) })
}

@Composable
private fun StudioLiveHeader(modifier: Modifier, frame: State<FactoryFrame>) {
    val counts by remember(frame) { derivedStateOf {
        val running = frame.value.machines.count { it.state == FactoryMachineState.RUNNING }
        Triple(running, frame.value.machines.size - running, frame.value.open)
    } }
    StudioSceneHeader(modifier, counts.first, counts.second, counts.third)
}

private data class StudioOperationSummary(
    val moving: Int, val setup: Int, val quality: Int, val resting: Int,
    val activity: WorkerActivity?, val target: WorkerActivity?, val fatigue: Int?,
)

@Composable
private fun FactoryOperationsPanel(frame: State<FactoryFrame>, employees: Map<String, EmployeeEntity>, selectedId: String?,
    machines: List<MachineEntity>, selectedMachineId: String?) {
    val summary by remember(frame, selectedId) { derivedStateOf {
        val workers = frame.value.workers
        val selected = workers.firstOrNull { it.id == selectedId }
        StudioOperationSummary(workers.count { it.walking }, workers.count { it.activity == WorkerActivity.SETTING_UP },
            workers.count { it.activity == WorkerActivity.INSPECTING }, workers.count { it.activity == WorkerActivity.BREAK },
            selected?.activity, selected?.destinationActivity, selected?.fatigue)
    } }
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("ROTINA DA FÁBRICA", color = Color(0xFF77CDED), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("${summary.moving} em deslocamento • ${summary.setup} em preparação\n${summary.quality} na inspeção • ${summary.resting} na copa",
            color = Color(0xFFD6E0E5), style = MaterialTheme.typography.bodySmall)
        val employee = selectedId?.let { employees[it] }
        if (employee != null && summary.activity != null) {
            Text(employee.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
            val action = if (summary.activity == WorkerActivity.WALKING || summary.activity == WorkerActivity.CARRYING_PART)
                "${summary.activity?.label} → ${summary.target?.label}" else summary.activity?.label.orEmpty()
            Text("$action • cansaço ${summary.fatigue}%", color = Color(0xFFFFD38A), style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Toque em um funcionário para acompanhar a tarefa e a rota.", color = Color(0xFFAABBC4), style = MaterialTheme.typography.bodySmall)
        }
        val focusedMachine = machines.firstOrNull { it.id == selectedMachineId }
        if (employee == null && focusedMachine != null) {
            val machineState by remember(frame, selectedMachineId) { derivedStateOf {
                frame.value.machines.firstOrNull { it.id == selectedMachineId }?.state?.label.orEmpty()
            } }
            Text(focusedMachine.customName ?: MachineCatalog.byType(focusedMachine.machineType)?.name ?: focusedMachine.machineType,
                color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(machineState, color = Color(0xFF77CDED), style = MaterialTheme.typography.bodySmall)
        }
        Text("M Material • F Ferramentas • Q Qualidade • E Expedição • C Copa",
            color = Color(0xFF8C9FA9), style = MaterialTheme.typography.labelSmall)
        Text("1 dedo rola a tela • 2 dedos movem e ampliam a fábrica", color = Color(0xFF8C9FA9), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StudioSceneHeader(modifier: Modifier, operating: Int, waiting: Int, open: Boolean) {
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
            Text("●", color = if (open) Color(0xFF61DEA0) else Color(0xFF9CAAB3), style = MaterialTheme.typography.labelSmall)
            Column {
                Text("FÁBRICA VIVA 2.1", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Text(if (open) "$operating usinando  •  $waiting em outras etapas" else "Turno encerrado • equipe indo para casa", color = Color(0xFFAAB7BD), style = MaterialTheme.typography.labelSmall)
                Text("Toque na equipe para seguir a rota", color = Color(0xFF7F949E), style = MaterialTheme.typography.labelSmall)
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

private data class StudioLayout(val width: Float, val height: Float, val rows: Int) {
    val projection = FactorySceneGeometry(width, height, rows)
    val tileW = projection.cellWidth
    val tileH = projection.cellHeight
    val wallBottom = height * .10f
    val floorTop = projection.top
    val floorBottom = height * .97f
    val floorLeft = projection.left
    val floorRight = projection.right
    val centerX = width * .50f
    val originY = floorTop
    val backAisleY = floorTop
    val serviceY = projection.serviceBottom
    val serviceLeft = floorLeft
    val serviceRight = floorRight
}

private fun studioIsoPoint(layout: StudioLayout, gridX: Int, gridY: Int): Offset =
    Offset(layout.projection.machineX(gridX), layout.projection.machineY(gridY))

private fun DrawScope.studioPortraitBuilding(layout: StudioLayout, pulse: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF26383E), Color(0xFF0B151A))))
    drawRoundRect(Color(0xFF364B53), Offset(layout.width * .03f, layout.height * .025f),
        Size(layout.width * .94f, layout.height * .05f), CornerRadius(layout.width * .01f))
    repeat(5) { index ->
        val x = layout.floorLeft + (index + .5f) * layout.tileW
        drawLine(Color(0xFFC1DBD5).copy(alpha = .55f + pulse * .2f),
            Offset(x - layout.tileW * .23f, layout.height * .05f),
            Offset(x + layout.tileW * .23f, layout.height * .05f), layout.height * .004f, StrokeCap.Round)
    }
}

private fun DrawScope.studioBackdrop(layout: StudioLayout, pulse: Float, slow: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF263941), Color(0xFF14242B), Color(0xFF071116)),
            startY = 0f,
            endY = layout.height
        )
    )
    drawRect(Color(0xFF2A3940), topLeft = Offset.Zero, size = Size(layout.width, layout.wallBottom))

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
    drawRoundRect(Color(0xFF202D32), Offset(layout.width * .02f, layout.height * .11f),
        Size(layout.width * .96f, layout.height * .86f), CornerRadius(layout.width * .02f))
    // Bay outlines make the free positions readable and use the same projection as the machines.
    repeat(layout.rows) { row -> repeat(5) { column ->
        val c = studioIsoPoint(layout, column, row)
        val w = layout.tileW * .82f
        val h = layout.tileH * .78f
        drawRoundRect(Color(0xFF34474F).copy(alpha = .35f), c - Offset(w / 2f, h / 2f),
            Size(w, h), CornerRadius(w * .08f), style = Stroke(maxOf(1f, w * .015f)))
    } }
    val lane = Color(0xFFCEAD56).copy(alpha = .48f)
    drawLine(lane, Offset(layout.floorLeft, layout.floorTop), Offset(layout.floorLeft, layout.serviceY),
        maxOf(1f, layout.width * .003f))
    drawLine(lane, Offset(layout.floorRight, layout.floorTop), Offset(layout.floorRight, layout.serviceY),
        maxOf(1f, layout.width * .003f))
    drawLine(lane, Offset(layout.floorLeft, layout.serviceY), Offset(layout.floorRight, layout.serviceY),
        maxOf(1f, layout.width * .003f))
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
    val w = layout.tileW * .82f
    val h = layout.tileH * .78f
    val color = when {
        selected -> Color(0xFFFFC84D)
        condition <= 80 -> Color(0xFFFF7474)
        operating -> Color(0xFF66E4A6)
        else -> Color(0xFF77CDED)
    }
    drawRoundRect(color.copy(alpha = if (selected) .14f else .045f), center - Offset(w / 2f, h / 2f),
        Size(w, h), CornerRadius(w * .08f))
    drawRoundRect(color.copy(alpha = if (selected) .9f else .3f), center - Offset(w / 2f, h / 2f),
        Size(w, h), CornerRadius(w * .08f), style = Stroke(maxOf(1f, w * if (selected) .035f else .015f)))
}

private fun DrawScope.studioMachine(
    center: Offset,
    type: String,
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


    if (condition <= 80) {
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
    val cycle = phase * PI * 2
    val tremble = if (palette.skinStyle == "TREME_TREME") sin(phase * PI * 18).toFloat() * 2.2f * s else 0f
    val drunkSway = if (palette.skinStyle == "BEBADO") sin(phase * PI * 1.55).toFloat() * 3.8f * s else 0f
    val walk = if (walking) sin(cycle).toFloat() else 0f
    val bob = if (walking) abs(sin(cycle).toFloat()) * 1.7f * s else sin(cycle*.45).toFloat()*.45f*s
    val x = base.x + tremble + drunkSway
    val y = base.y - bob

    drawOval(Color.Black.copy(alpha = .29f), Offset(x - 12f*s*width, base.y - 3f*s), Size(24f*s*width, 7f*s))

    val hip = Offset(x, y - 18f*s)
    val shoulder = Offset(x + workLean * 2f*s, y - 33f*s)
    val head = Offset(x + workLean * 2.5f*s + if (palette.skinStyle == "BEBADO") drunkSway*.22f else 0f, y - 46f*s)

    studioWorkerHairBack(head, palette, phase, s)

    val legSwing = walk * 5.2f*s
    val legWidth = when (palette.skinStyle) { "MAGRAO" -> 3.7f*s; "TATUZAO" -> 6.1f*s; else -> 5f*s*width }
    drawLine(Color(0xFF1D272C), hip + Offset(-4f*s*width, 0f), Offset(x - 6f*s*width + legSwing, y - 3f*s), legWidth, StrokeCap.Round)
    drawLine(Color(0xFF1D272C), hip + Offset(4f*s*width, 0f), Offset(x + 6f*s*width - legSwing, y - 3f*s), legWidth, StrokeCap.Round)
    drawLine(Color(0xFF151A1D), Offset(x - 6f*s*width + legSwing, y - 3f*s), Offset(x - 10f*s*width + legSwing, y), 4f*s*width, StrokeCap.Round)
    drawLine(Color(0xFF151A1D), Offset(x + 6f*s*width - legSwing, y - 3f*s), Offset(x + 10f*s*width - legSwing, y), 4f*s*width, StrokeCap.Round)

    studioWorkerOutfit(shoulder, hip, x, palette, s, phase)

    val leftShoulder = shoulder + Offset(-8f*s*width, 5f*s)
    val rightShoulder = shoulder + Offset(8f*s*width, 5f*s)
    val armStroke = when (palette.skinStyle) { "TATUZAO" -> 6.2f*s; "MAGRAO" -> 3.5f*s; else -> 4.2f*s }
    when {
        phone -> {
            val phoneCenter = Offset(x, shoulder.y + 13f*s)
            val leftHand = phoneCenter + Offset(-4f*s, 1f*s)
            val rightHand = phoneCenter + Offset(4f*s, 1f*s)
            drawLine(palette.uniform, leftShoulder, leftHand, armStroke, StrokeCap.Round)
            drawLine(palette.uniform, rightShoulder, rightHand, armStroke, StrokeCap.Round)
            drawCircle(palette.skin, 2.5f*s, leftHand)
            drawCircle(palette.skin, 2.5f*s, rightHand)
            drawRoundRect(Color(0xFF101619), phoneCenter + Offset(-3.4f*s, -5.5f*s), Size(6.8f*s, 11f*s), CornerRadius(1.2f*s))
            drawRoundRect(Color(0xFF66C7EE), phoneCenter + Offset(-2.4f*s, -4.2f*s), Size(4.8f*s, 7.2f*s), CornerRadius(.8f*s))
            val scroll = studioTriangle((phase*1.7f)%1f)
            drawLine(Color.White.copy(alpha=.75f), phoneCenter + Offset(-1.4f*s, (-2.2f+scroll*3f)*s), phoneCenter + Offset(1.4f*s, (-2.2f+scroll*3f)*s), .7f*s)
        }
        armTarget != null -> {
            val near = if (armTarget.x >= shoulder.x) rightShoulder else leftShoulder
            val far = if (armTarget.x >= shoulder.x) leftShoulder else rightShoulder
            val hand = studioLerp(near, armTarget, .63f + .18f * workLean)
            drawLine(palette.uniform, near, hand, armStroke, StrokeCap.Round)
            drawCircle(palette.skin, if (palette.skinStyle=="TATUZAO") 3.1f*s else 2.7f*s, hand)
            val idleHand = far + Offset(if (far.x < shoulder.x) -5f*s else 5f*s, 11f*s)
            drawLine(palette.uniform, far, idleHand, armStroke, StrokeCap.Round)
            drawCircle(palette.skin, if (palette.skinStyle=="TATUZAO") 3.1f*s else 2.5f*s, idleHand)
        }
        carrying -> {
            val box = Offset(x, y - 22f*s)
            drawLine(palette.uniform, leftShoulder, box + Offset(-9f*s, 0f), armStroke, StrokeCap.Round)
            drawLine(palette.uniform, rightShoulder, box + Offset(9f*s, 0f), armStroke, StrokeCap.Round)
            drawRoundRect(Color(0xFF9C7140), box + Offset(-11f*s, -6f*s), Size(22f*s, 13f*s), CornerRadius(2f*s))
            drawLine(Color(0xFFC69C60), box + Offset(0f, -6f*s), box + Offset(0f, 7f*s), 1f*s)
        }
        else -> {
            val armSwing = walk * 5f*s
            if (palette.skinStyle == "KENDAO_KIMONO") {
                val leftSleeve = Path().apply {
                    moveTo(leftShoulder.x+2f*s,leftShoulder.y-2f*s); lineTo(leftShoulder.x-11f*s,leftShoulder.y+9f*s); lineTo(leftShoulder.x-6f*s,leftShoulder.y+15f*s); lineTo(leftShoulder.x+3f*s,leftShoulder.y+4f*s); close()
                }
                val rightSleeve = Path().apply {
                    moveTo(rightShoulder.x-2f*s,rightShoulder.y-2f*s); lineTo(rightShoulder.x+11f*s,rightShoulder.y+9f*s); lineTo(rightShoulder.x+6f*s,rightShoulder.y+15f*s); lineTo(rightShoulder.x-3f*s,rightShoulder.y+4f*s); close()
                }
                drawPath(leftSleeve,palette.uniform); drawPath(rightSleeve,palette.uniform)
            } else {
                drawLine(palette.uniform, leftShoulder, leftShoulder + Offset(-4f*s, 11f*s - armSwing), armStroke, StrokeCap.Round)
                drawLine(palette.uniform, rightShoulder, rightShoulder + Offset(4f*s, 11f*s + armSwing), armStroke, StrokeCap.Round)
            }
        }
    }

    drawRoundRect(palette.skin, head + Offset(-6f*s*width, -1f*s), Size(12f*s*width, 12f*s), CornerRadius(5f*s))
    studioWorkerHairFront(head, palette, phase, s)

    drawCircle(Color(0xFF2B201B).copy(alpha = .65f), 1.15f*s, head + Offset(3.1f*s, 4f*s))
    drawCircle(Color(0xFF2B201B).copy(alpha = .50f), 1.0f*s, head + Offset(-2.2f*s, 4f*s))

    when (palette.skinStyle) {
        "PINOQUIO" -> {
            val nose = Path().apply {
                moveTo(head.x+3f*s,head.y+4f*s); lineTo(head.x+14f*s,head.y+6f*s); lineTo(head.x+3f*s,head.y+7f*s); close()
            }
            drawPath(nose,palette.skin)
            drawPath(nose,Color.Black.copy(alpha=.18f),style=Stroke(.8f*s))
        }
        "TATUZAO" -> {
            drawArc(Color(0xFF3A2A23), 20f, 145f, false, head + Offset(-5f*s, 4f*s), Size(10f*s, 7f*s), style = Stroke(1.5f*s))
        }
        "BEBADO" -> {
            drawCircle(Color(0xFFB94D4D).copy(alpha=.34f), 2.3f*s, head + Offset(-3.7f*s, 6f*s))
            drawCircle(Color(0xFFB94D4D).copy(alpha=.34f), 2.3f*s, head + Offset(3.7f*s, 6f*s))
            drawLine(Color(0xFF7E5144),head+Offset(-2f*s,7f*s),head+Offset(2f*s,8f*s),1f*s)
        }
        "PRINCESA" -> {
            drawCircle(Color(0xFFFFA8C5).copy(alpha=.20f), 2.1f*s, head+Offset(-4f*s,6f*s))
            drawCircle(Color(0xFFFFA8C5).copy(alpha=.20f), 2.1f*s, head+Offset(4f*s,6f*s))
        }
    }

    if (employee.legendaryCode == "gumersvaldo") {
        drawLine(Color(0xFF67CAE0), head + Offset(-5f*s, 4f*s), head + Offset(5f*s, 4f*s), 1.5f*s)
    }

    studioWorkerHeadwear(head, palette, s)

    if (palette.skinStyle == "TREME_TREME") {
        drawLine(palette.accent.copy(alpha=.72f),Offset(x-15f*s,shoulder.y+3f*s),Offset(x-19f*s,shoulder.y+7f*s),1.2f*s)
        drawLine(palette.accent.copy(alpha=.72f),Offset(x+15f*s,shoulder.y+3f*s),Offset(x+19f*s,shoulder.y+7f*s),1.2f*s)
    }

    if (employee.isLegendary) {
        drawCircle(Color(0xFFFFD268), 2.3f*s, head + Offset(-10f*s*width, -3f*s))
        // Names and task details are shown in the selection panel, not over other workers.
    }
}

private fun DrawScope.studioWorkerOutfit(shoulder: Offset, hip: Offset, x: Float, p: StudioWorkerPalette, s: Float, phase: Float) {
    val w = 18f*s*p.width
    when (p.skinStyle) {
        "PRINCESA" -> {
            val bodice = Path().apply {
                moveTo(shoulder.x-w*.42f,shoulder.y); lineTo(shoulder.x+w*.42f,shoulder.y); lineTo(x+w*.30f,hip.y+1f*s); lineTo(x-w*.30f,hip.y+1f*s); close()
            }
            drawPath(bodice,p.uniform)
            val sway=sin(phase*PI*2).toFloat()*1.1f*s
            val skirt=Path().apply {
                moveTo(x-w*.28f,hip.y); quadraticBezierTo(x-w*.55f+sway,hip.y+10f*s,x-w*.80f+sway,hip.y+23f*s); quadraticBezierTo(x,hip.y+29f*s,x+w*.80f+sway,hip.y+23f*s); quadraticBezierTo(x+w*.55f+sway,hip.y+10f*s,x+w*.28f,hip.y); close()
            }
            drawPath(skirt,Color(0xFFD764A5)); drawPath(skirt,Color(0xFF8E346D).copy(alpha=.45f),style=Stroke(1f*s))
            drawLine(Color(0xFFFFE6A8),Offset(x-w*.63f+sway,hip.y+20f*s),Offset(x+w*.63f+sway,hip.y+20f*s),1.3f*s)
        }
        "KENDAO_KIMONO" -> {
            drawRoundRect(p.uniform, Offset(shoulder.x-w/2f,shoulder.y), Size(w,18f*s), CornerRadius(4f*s))
            drawLine(Color(0xFF30383D), shoulder+Offset(-6f*s,2f*s), shoulder+Offset(6f*s,15f*s), 2f*s)
            drawLine(Color(0xFF30383D), shoulder+Offset(6f*s,2f*s), shoulder+Offset(-6f*s,15f*s), 2f*s)
            drawRect(Color(0xFF202628), shoulder+Offset(-w/2f,14f*s), Size(w,4f*s))
            drawRect(Color(0xFF9E2F2F), shoulder+Offset(-1.4f*s,14f*s),Size(2.8f*s,8f*s))
        }
        "PINOQUIO" -> {
            drawRoundRect(p.uniform,Offset(shoulder.x-w/2f,shoulder.y),Size(w,18f*s),CornerRadius(5f*s))
            drawRect(Color(0xFFE7C24B),Offset(x-w*.30f,shoulder.y+1f*s),Size(2f*s,14f*s))
            drawRect(Color(0xFFE7C24B),Offset(x+w*.20f,shoulder.y+1f*s),Size(2f*s,14f*s))
            drawCircle(Color(0xFFFFE27A),1.4f*s,Offset(x-w*.20f,shoulder.y+13f*s)); drawCircle(Color(0xFFFFE27A),1.4f*s,Offset(x+w*.20f,shoulder.y+13f*s))
        }
        else -> {
            drawRoundRect(p.uniform,Offset(shoulder.x-w/2f,shoulder.y),Size(w,18f*s),CornerRadius(if(p.female)6f*s else 5f*s))
            drawLine(p.accent.copy(alpha=.9f),Offset(shoulder.x-w*.44f,shoulder.y+7f*s),Offset(shoulder.x+w*.44f,shoulder.y+7f*s),2f*s)
            drawLine(Color(0xFFCDD4D5).copy(alpha=.55f),Offset(shoulder.x-w*.39f,shoulder.y+12f*s),Offset(shoulder.x+w*.39f,shoulder.y+12f*s),1.4f*s)
        }
    }
}

private fun DrawScope.studioWorkerHairBack(head: Offset, p: StudioWorkerPalette, phase: Float, s: Float) {
    val style = if (p.skinStyle=="PRINCESA") "LONG" else p.hairStyle
    when(style) {
        "LONG" -> {
            val sway=sin(phase*PI*2).toFloat()*1.1f*s
            val mass=Path().apply {
                moveTo(head.x-7f*s,head.y-3f*s); cubicTo(head.x-10f*s,head.y+3f*s,head.x-8f*s+sway,head.y+15f*s,head.x-4f*s+sway,head.y+19f*s); lineTo(head.x+5f*s+sway,head.y+19f*s); cubicTo(head.x+9f*s+sway,head.y+12f*s,head.x+9f*s,head.y+2f*s,head.x+7f*s,head.y-3f*s); close()
            }
            drawPath(mass,p.hair)
            listOf(-4.5f,-2f,.5f,3f,5f).forEachIndexed { i,dx ->
                val path=Path().apply { moveTo(head.x+dx*s,head.y+2f*s); cubicTo(head.x+(dx-1f)*s,head.y+8f*s,head.x+(dx+1f)*s+sway*.4f,head.y+14f*s,head.x+dx*s+sway*.5f,head.y+18f*s) }
                drawPath(path,p.hair.copy(alpha=.75f),style=Stroke(.7f*s,cap=StrokeCap.Round))
            }
        }
        "PONYTAIL" -> {
            drawCircle(p.hair,4.8f*s,head+Offset(-7f*s,6f*s)); drawLine(p.hair,head+Offset(-8f*s,7f*s),head+Offset(-11f*s,17f*s),3f*s,StrokeCap.Round)
        }
    }
}

private fun DrawScope.studioWorkerHairFront(head: Offset, p: StudioWorkerPalette, phase: Float, s: Float) {
    val style=if(p.skinStyle=="PRINCESA") "LONG" else p.hairStyle
    when(style) {
        "LONG" -> {
            drawArc(p.hair,185f,170f,true,head+Offset(-8f*s,-7f*s),Size(16f*s,10f*s))
            drawLine(p.hair,head+Offset(-5f*s,-1f*s),head+Offset(-6f*s,7f*s),1.5f*s,StrokeCap.Round)
            drawLine(p.hair,head+Offset(5f*s,-1f*s),head+Offset(6f*s,7f*s),1.5f*s,StrokeCap.Round)
        }
        "PONYTAIL","SHORT" -> drawArc(p.hair,185f,170f,true,head+Offset(-8f*s,-7f*s),Size(16f*s,10f*s))
        "BUZZ" -> drawLine(p.hair,head+Offset(-5f*s,-1f*s),head+Offset(5f*s,-1f*s),2f*s)
    }
}

private fun DrawScope.studioWorkerHeadwear(head: Offset, p: StudioWorkerPalette, s: Float) {
    if (p.skinStyle=="PRINCESA") {
        val crown=Path().apply {
            moveTo(head.x-7f*s,head.y-6f*s); lineTo(head.x-5f*s,head.y-13f*s); lineTo(head.x-1.5f*s,head.y-8f*s); lineTo(head.x+1f*s,head.y-14f*s); lineTo(head.x+4f*s,head.y-8f*s); lineTo(head.x+7f*s,head.y-13f*s); lineTo(head.x+7f*s,head.y-5f*s); close()
        }
        drawPath(crown,Color(0xFFFFD45C)); drawPath(crown,Color(0xFFFFF0A6),style=Stroke(.8f*s)); drawCircle(Color(0xFFE45C86),1.3f*s,head+Offset(1f*s,-10f*s))
    } else if(p.skinStyle=="BEBADO") {
        drawArc(p.helmet,180f,180f,true,head+Offset(-8f*s*p.width,-5f*s),Size(16f*s*p.width,11f*s)); drawLine(p.helmet,head+Offset(-9f*s*p.width,1f*s),head+Offset(7f*s*p.width,2f*s),2.3f*s,StrokeCap.Round)
    } else {
        drawArc(p.helmet,180f,180f,true,head+Offset(-8f*s*p.width,-5f*s),Size(16f*s*p.width,11f*s)); drawLine(p.helmet,head+Offset(-9f*s*p.width,1f*s),head+Offset(8f*s*p.width,1f*s),2.5f*s,StrokeCap.Round)
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
