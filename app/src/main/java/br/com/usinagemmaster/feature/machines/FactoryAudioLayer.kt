package br.com.usinagemmaster.feature.machines

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import br.com.usinagemmaster.R
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.model.MachineProduction
import kotlinx.coroutines.delay

/**
 * Camada sonora pequena e nativa. Os WAVs são sintéticos e ficam em res/raw,
 * então o jogo não depende de serviço externo nem de licença de áudio.
 */
@Composable
fun FactoryAudioLayer(
    enabled: Boolean,
    machines: List<MachineEntity>,
    production: List<MachineProduction>
) {
    val context = LocalContext.current
    val ambient = remember(context) {
        MediaPlayer.create(context, R.raw.factory_ambient)?.apply {
            isLooping = true
            setVolume(.18f, .18f)
        }
    }
    val soundPool = remember {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
    }
    val machineTick = remember(soundPool) { soundPool.load(context, R.raw.machine_tick, 1) }
    val weldSpark = remember(soundPool) { soundPool.load(context, R.raw.weld_spark, 1) }

    val operatingIds = production.filter { it.isOperating }.map { it.machineId }.toSet()
    val activeMachines = machines.filter { it.id in operatingIds }
    val hasHotWork = activeMachines.any {
        it.machineType.contains("WELD") || it.machineType.contains("LASER") || it.machineType.contains("PLASMA")
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            if (ambient?.isPlaying != true) ambient?.start()
        } else {
            if (ambient?.isPlaying == true) ambient.pause()
        }
    }

    LaunchedEffect(enabled, activeMachines.map { it.id }, hasHotWork) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            if (activeMachines.isNotEmpty()) {
                val soundId = if (hasHotWork) weldSpark else machineTick
                soundPool.play(soundId, .20f, .20f, 1, 0, 1f)
            }
            delay(if (hasHotWork) 2400L else 3100L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ambient?.stop()
            ambient?.release()
            soundPool.release()
        }
    }
}
