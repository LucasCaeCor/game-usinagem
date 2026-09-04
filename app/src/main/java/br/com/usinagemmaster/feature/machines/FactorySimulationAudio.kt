package br.com.usinagemmaster.feature.machines

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.repeatOnLifecycle
import br.com.usinagemmaster.R
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.model.MachineProduction
import kotlinx.coroutines.delay

/**
 * Áudio da Fábrica Viva, separado da camada compartilhada para preservar ajustes locais.
 * Camada sonora pequena e nativa. Os WAVs são sintéticos e ficam em res/raw,
 * então o jogo não depende de serviço externo nem de licença de áudio.
 */
@Composable
internal fun FactorySimulationAudio(
    enabled: Boolean,
    machines: List<MachineEntity>,
    production: List<MachineProduction>
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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

    LifecycleStartEffect(enabled) {
        val player = ambient
        if (enabled && player != null) {
            try {
                if (!player.isPlaying) player.start()
            } catch (_: IllegalStateException) {
            }
        } else if (player != null) {
            try {
                if (player.isPlaying) player.pause()
            } catch (_: IllegalStateException) {
            }
        }
        onStopOrDispose {
            try {
                if (player?.isPlaying == true) player.pause()
            } catch (_: IllegalStateException) {
            }
        }
    }

    LaunchedEffect(enabled, activeMachines.map { it.id }, hasHotWork, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (activeMachines.isNotEmpty()) {
                    val soundId = if (hasHotWork) weldSpark else machineTick
                    soundPool.play(soundId, .20f, .20f, 1, 0, 1f)
                }
                delay(if (hasHotWork) 2400L else 3100L)
            }
        }
    }

    DisposableEffect(ambient) {
        onDispose {
            ambient?.let {
                try {
                    if (it.isPlaying) it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
        }
    }

    DisposableEffect(soundPool) {
        onDispose {
            soundPool.release()
        }
    }
}
