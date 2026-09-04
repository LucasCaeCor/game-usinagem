package br.com.usinagemmaster.feature.gameplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.data.preferences.ActiveGameplayRepository
import br.com.usinagemmaster.data.preferences.ExpansionRepository
import br.com.usinagemmaster.domain.gameplay.*
import br.com.usinagemmaster.domain.model.ContractStatus
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveGameplayViewModel @Inject constructor(
    private val activeRepository: ActiveGameplayRepository,
    private val gameRepository: GameRepository,
    private val expansionRepository: ExpansionRepository,
):ViewModel(){
    val career=activeRepository.state.stateIn(viewModelScope,SharingStarted.Eagerly,CareerState())
    val contracts=gameRepository.contracts().stateIn(viewModelScope,SharingStarted.Eagerly, emptyList())
    val dashboard=gameRepository.dashboard().stateIn(viewModelScope,SharingStarted.Eagerly,br.com.usinagemmaster.domain.model.DashboardStatus())
    private val _busy=MutableStateFlow(false);val busy=_busy.asStateFlow()
    private val _message=MutableStateFlow<String?>(null);val message=_message.asStateFlow()
    fun activeContracts():List<ContractEntity> = contracts.value.filter{it.status==ContractStatus.ACTIVE.name&&it.completedQuantity<it.quantity}.sortedBy{it.deadlineAt}
    fun operateManually(machine:MachineEntity,contract:ContractEntity,result:MinigameResult)=action{
        require(machine.condition>80){"Faça manutenção antes de operar"};val b=activeRepository.recordMachining(machine.id,machine.machineType,contract.id,result,true);expansionRepository.addPlayerXp(30L+(result.normalizedScore*70).toLong());syncCharacterGoals();_message.value="Lote usinado: ${b.producedQuantity} pç • Q${b.quality}. Agora leve ao Q (Qualidade)."
    }
    fun assistedCycle(machine:MachineEntity,contract:ContractEntity)=action{require(machine.condition>80);val b=activeRepository.recordMachining(machine.id,machine.machineType,contract.id,MinigameResult(.46f,.54f,.34f,.52f),false);expansionRepository.addPlayerXp(12L);_message.value="Ciclo assistido: ${b.producedQuantity} pç. Sem bônus manual; leve o lote ao Q."}
    fun moveToQuality()=action{activeRepository.moveToQuality();activeRepository.beginInspection();_message.value="Lote no Controle de Qualidade. Faça a medição dimensional."}
    fun inspect(approve:Boolean)=action{val b=career.value.activeBatch?:error("Nenhum lote");val c=contracts.value.firstOrNull{it.id==b.contractId}?:error("Contrato não encontrado");val o=activeRepository.inspectBatch(approve,c.requiredQuality);_message.value=if(o.batch.stage==ProductionStage.APPROVED)"Lote aprovado. Leve ao P (Embalagem)." else "Retrabalho necessário. Volte à máquina."}
    fun rework(result:MinigameResult)=action{val b=activeRepository.recordRework(result);expansionRepository.addPlayerXp(20L+(result.normalizedScore*45).toLong());_message.value="Retrabalho concluído • Q${b.quality}. Leve novamente à Qualidade."}
    fun pack()=action{val b=activeRepository.packBatch();_message.value="${b.producedQuantity} peça(s) embaladas. Leve ao E (Expedição)."}
    fun ship()=action{val b=career.value.activeBatch?:error("Nenhum lote");val result=gameRepository.settleOwnerBatch(b,career.value.commercialCompletionBonusPct()).getOrThrow();activeRepository.markShipped();syncCharacterGoals();_message.value="Expedido: ${result.appliedQuantity} pç em ${result.contractName}${if(result.contractRewardCents>0)" • CONTRATO PAGO" else ""}";delay(650);activeRepository.clearFinished()}
    fun scrap()=action{activeRepository.scrapBatch();_message.value="Lote refugado; nenhuma peça foi creditada.";delay(450);activeRepository.clearFinished()}
    fun abandon()=action{activeRepository.abandonBatch();_message.value="Lote descartado."}
    fun unlockSkill(id:String)=action{activeRepository.unlockSkill(id,dashboard.value.companyLevel);_message.value="Skill industrial aprendida."}
    fun setPolicy(p:ProductionPolicy)=action{activeRepository.setProductionPolicy(p);_message.value="Política: ${p.label}"}
    fun clearMessage(){_message.value=null}
    private suspend fun syncCharacterGoals(){expansionRepository.syncCareerCharacterRewards(activeRepository.snapshot())}
    private fun action(block:suspend()->Unit){if(_busy.value)return;viewModelScope.launch{_busy.value=true;runCatching{block()}.onFailure{_message.value=it.message?:"Falha na operação"};_busy.value=false}}
}
