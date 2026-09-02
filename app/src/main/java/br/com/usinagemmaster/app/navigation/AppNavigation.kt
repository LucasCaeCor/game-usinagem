package br.com.usinagemmaster.app.navigation
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.usinagemmaster.feature.contracts.ContractsScreen
import br.com.usinagemmaster.feature.dashboard.DashboardScreen
import br.com.usinagemmaster.feature.employees.EmployeesScreen
import br.com.usinagemmaster.feature.facility.FacilityScreen
import br.com.usinagemmaster.feature.finance.FinanceScreen
import br.com.usinagemmaster.feature.goals.GoalsScreen
import br.com.usinagemmaster.feature.machines.MachinesScreen
import br.com.usinagemmaster.feature.menu.MainMenuScreen
import br.com.usinagemmaster.feature.profile.ProfileScreen
import br.com.usinagemmaster.feature.social.SocialScreen
import br.com.usinagemmaster.feature.settings.SettingsScreen
import br.com.usinagemmaster.feature.splash.SplashScreen
import br.com.usinagemmaster.feature.store.StoreScreen

object Routes {
    const val SPLASH = "splash"
    const val MENU = "menu"
    const val DASHBOARD = "dashboard"
    const val MACHINES = "machines"
    const val EMPLOYEES = "employees"
    const val CONTRACTS = "contracts"
    const val STORE = "store"
    const val FACILITY = "facility"
    const val FINANCE = "finance"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val SOCIAL = "social"
}

@Composable
fun AppNavigation(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.SPLASH,
        // V8_FAST_NAV • remove a animação padrão longa do Navigation Compose.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen {
                nav.navigate(Routes.MENU) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.MENU) {
            MainMenuScreen(
                onContinue = { nav.navigate(Routes.DASHBOARD) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onProfile = { nav.navigate(Routes.PROFILE) },
                onSocial = { nav.navigate(Routes.SOCIAL) }
            )
        }
        composable(Routes.DASHBOARD) { DashboardScreen { nav.navigate(it) } }
        composable(Routes.MACHINES) { BackScaffold(nav, "Galpão") { MachinesScreen(onNavigate = { nav.navigate(it) }) } }
        composable(Routes.EMPLOYEES) { BackScaffold(nav, "Funcionários") { EmployeesScreen() } }
        composable(Routes.CONTRACTS) { BackScaffold(nav, "Contratos") { ContractsScreen() } }
        composable(Routes.STORE) { BackScaffold(nav, "Loja") { StoreScreen() } }
        composable(Routes.FACILITY) { BackScaffold(nav, "Reforma") { FacilityScreen() } }
        composable(Routes.FINANCE) { BackScaffold(nav, "Finanças") { FinanceScreen() } }
        composable(Routes.GOALS) { BackScaffold(nav, "Metas") { GoalsScreen() } }
        composable(Routes.SETTINGS) { BackScaffold(nav, "Configurações") { SettingsScreen() } }
        composable(Routes.PROFILE) { BackScaffold(nav, "Meu personagem") { ProfileScreen() } }
        composable(Routes.SOCIAL) { BackScaffold(nav, "Comunidade") { SocialScreen(onEditProfile = { nav.navigate(Routes.PROFILE) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(
    nav: NavHostController,
    title: String,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            content()
        }
    }
}
