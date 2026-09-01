package br.com.usinagemmaster.feature.facility

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader

@Composable
fun FacilityScreen(vm:FacilityViewModel=hiltViewModel()){
    val s by vm.dashboard.collectAsState(); val msg by vm.message.collectAsState(); val snack=remember{SnackbarHostState()};LaunchedEffect(msg){msg?.let{snack.showSnackbar(it);vm.clearMessage()}}
    Scaffold(snackbarHost={SnackbarHost(snack)}){pad->Column(Modifier.padding(pad).padding(top=20.dp)){ScreenHeader("Reforma e Expansão","Infraestrutura que sustenta o crescimento")
        Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("Expansão do galpão",fontWeight=FontWeight.Bold);Text("Capacidade atual: ${s.warehouseSpace} m²");Text("O próximo nível adiciona 50 m² para novas máquinas.");Spacer(Modifier.height(12.dp));Button(onClick=vm::expand){Text("Expandir galpão")}}}
        Text("Próximas melhorias: rede elétrica, iluminação, ventilação, ponte rolante e ar comprimido.",Modifier.padding(18.dp),color=MaterialTheme.colorScheme.secondary)
    }}
}
