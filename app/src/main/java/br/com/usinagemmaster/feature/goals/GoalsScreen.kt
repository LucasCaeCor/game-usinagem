package br.com.usinagemmaster.feature.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.util.Formatters

@Composable
fun GoalsScreen(vm:GoalsViewModel=hiltViewModel()){
    val goals by vm.goals.collectAsState(); val msg by vm.message.collectAsState(); val snack=remember{SnackbarHostState()};LaunchedEffect(msg){msg?.let{snack.showSnackbar(it);vm.clearMessage()}}
    Scaffold(snackbarHost={SnackbarHost(snack)}){pad->Column(Modifier.padding(pad).padding(top=20.dp)){ScreenHeader("Metas e Recompensas","Objetivos para acelerar a evolução da empresa")
        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(goals,key={it.id}){g->Card{Column(Modifier.padding(16.dp)){Text(g.title,fontWeight=FontWeight.Bold);Text("Recompensa: ${Formatters.money(g.rewardCents)}");Spacer(Modifier.height(8.dp));Button(onClick={vm.claim(g)},enabled=!g.claimed){Text(if(g.claimed)"Coletada" else "Coletar quando concluída")}}}}
        }}
    }
}
