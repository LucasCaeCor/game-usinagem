package br.com.usinagemmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.usinagemmaster.app.navigation.AppNavigation
import br.com.usinagemmaster.core.designsystem.theme.UsinagemMasterTheme
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: GameRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    repository.tickProduction()
                    delay(15_000L)
                }
            }
        }

        setContent {
            UsinagemMasterTheme {
                AppNavigation()
            }
        }
    }
}
