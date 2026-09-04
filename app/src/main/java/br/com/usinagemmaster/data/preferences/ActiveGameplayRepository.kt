package br.com.usinagemmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import br.com.usinagemmaster.domain.gameplay.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private val Context.activeGameplayDataStore by preferencesDataStore(name = "active_gameplay_v20")

@Singleton
class ActiveGameplayRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val batch=stringPreferencesKey("active_batch"); val mastery=stringSetPreferencesKey("mastery_xp")
        val skills=stringSetPreferencesKey("industrial_skills"); val milestones=stringSetPreferencesKey("milestones")
        val achievements=stringSetPreferencesKey("achievements"); val manual=intPreferencesKey("manual_ops")
        val assisted=intPreferencesKey("assisted_ops"); val perfect=intPreferencesKey("perfect_ops")
        val approved=intPreferencesKey("approved_batches"); val shipped=intPreferencesKey("shipped_batches")
        val reworked=intPreferencesKey("reworked_batches"); val scrapped=intPreferencesKey("scrapped_batches")
        val best=intPreferencesKey("best_score"); val streak=intPreferencesKey("streak")
        val points=intPreferencesKey("skill_points"); val policy=stringPreferencesKey("production_policy")
        val last=longPreferencesKey("last_operation_at")
    }

    val state: Flow<CareerState> = context.activeGameplayDataStore.data.map(::decode)
    suspend fun snapshot() = state.first()

    suspend fun recordMachining(machineId:String,machineType:String,contractId:String,result:MinigameResult,manual:Boolean):OwnerWorkBatch {
        var out:OwnerWorkBatch?=null
        context.activeGameplayDataStore.edit { p ->
            val current=decode(p); require(current.activeBatch==null){"Finalize ou descarte o lote atual antes de iniciar outro"}
            val mastery=current.mastery(machineType); val score=result.normalizedScore
            val qty=if(manual) suggestedManualQuantity(machineType,score,mastery,current) else (suggestedManualQuantity(machineType,.42f,mastery,current)*.62).roundToInt().coerceAtLeast(1)
            val quality=(46+score*46f+mastery.qualityBonus+current.manualQualityBonus()-result.mistakes*4).roundToInt().coerceIn(35,100)
            val now=System.currentTimeMillis()
            val batch=OwnerWorkBatch(UUID.randomUUID().toString(),machineId,machineType,contractId,ProductionStage.MACHINED,qty,quality,(result.precision.coerceIn(0f,1f)*100).roundToInt(),(result.speed.coerceIn(0f,1f)*100).roundToInt(),result.mistakes.coerceAtLeast(0),manual&&result.perfect,manual,0,now,now)
            p[K.batch]=encodeBatch(batch)
            val mx=parseMap(p[K.mastery]?: emptySet()).toMutableMap(); mx[machineType]=(mx[machineType]?:0)+(if(manual) 35+(score*85).roundToInt() else 18); p[K.mastery]=encodeMap(mx)
            if(manual){p[K.manual]=(p[K.manual]?:0)+1;p[K.streak]=if(score>=.72f)(p[K.streak]?:0)+1 else 0;if(result.perfect)p[K.perfect]=(p[K.perfect]?:0)+1}else{p[K.assisted]=(p[K.assisted]?:0)+1;p[K.streak]=0}
            p[K.best]=maxOf(p[K.best]?:0,(score*100).roundToInt());p[K.last]=now;updateRewards(p);out=batch
        }
        return requireNotNull(out)
    }

    suspend fun moveToQuality()=updateBatch(setOf(ProductionStage.MACHINED),ProductionStage.WAITING_QC)
    suspend fun beginInspection()=updateBatch(setOf(ProductionStage.WAITING_QC),ProductionStage.QC)

    suspend fun inspectBatch(approve:Boolean,requiredQuality:Int):InspectionOutcome {
        var out:InspectionOutcome?=null
        context.activeGameplayDataStore.edit { p ->
            val batch=requireNotNull(decode(p).activeBatch){"Nenhum lote ativo"};require(batch.stage==ProductionStage.QC||batch.stage==ProductionStage.WAITING_QC){"Leve o lote à Qualidade primeiro"}
            val should=batch.quality>=requiredQuality.coerceIn(0,100);val next=if(approve&&should)ProductionStage.APPROVED else ProductionStage.REWORK
            val updated=batch.copy(stage=next,updatedAt=System.currentTimeMillis());p[K.batch]=encodeBatch(updated)
            if(next==ProductionStage.APPROVED)p[K.approved]=(p[K.approved]?:0)+1 else p[K.reworked]=(p[K.reworked]?:0)+1
            updateRewards(p);out=InspectionOutcome(updated,should,approve==should)
        };return requireNotNull(out)
    }

    suspend fun recordRework(result:MinigameResult):OwnerWorkBatch {
        var out:OwnerWorkBatch?=null
        context.activeGameplayDataStore.edit { p ->
            val batch=requireNotNull(decode(p).activeBatch);require(batch.stage==ProductionStage.REWORK){"Lote não está em retrabalho"}
            val score=result.normalizedScore;val now=System.currentTimeMillis();val improved=(8+score*22-result.mistakes*3).roundToInt()
            val updated=batch.copy(stage=ProductionStage.MACHINED,quality=(batch.quality+improved).coerceIn(35,100),precision=maxOf(batch.precision,(result.precision*100).roundToInt()),speed=((batch.speed+(result.speed*100).roundToInt())/2).coerceIn(0,100),mistakes=batch.mistakes+result.mistakes,perfect=batch.perfect&&result.perfect,reworkCount=batch.reworkCount+1,updatedAt=now)
            p[K.batch]=encodeBatch(updated);val mx=parseMap(p[K.mastery]?: emptySet()).toMutableMap();mx[batch.machineType]=(mx[batch.machineType]?:0)+24+(score*50).roundToInt();p[K.mastery]=encodeMap(mx);p[K.manual]=(p[K.manual]?:0)+1;p[K.last]=now;updateRewards(p);out=updated
        };return requireNotNull(out)
    }

    suspend fun packBatch()=updateBatch(setOf(ProductionStage.APPROVED),ProductionStage.READY_TO_SHIP)
    suspend fun markShipped():OwnerWorkBatch { var out:OwnerWorkBatch?=null;context.activeGameplayDataStore.edit{p->val b=requireNotNull(decode(p).activeBatch);require(b.stage==ProductionStage.READY_TO_SHIP);val u=b.copy(stage=ProductionStage.SHIPPED,updatedAt=System.currentTimeMillis());p[K.batch]=encodeBatch(u);p[K.shipped]=(p[K.shipped]?:0)+1;updateRewards(p);out=u};return requireNotNull(out)}
    suspend fun clearFinished(){context.activeGameplayDataStore.edit{p->p[K.batch]?.let(::decodeBatch)?.takeIf{it.stage==ProductionStage.SHIPPED||it.stage==ProductionStage.SCRAP}?.let{p.remove(K.batch)}}}
    suspend fun scrapBatch(){context.activeGameplayDataStore.edit{p->val b=p[K.batch]?.let(::decodeBatch)?:return@edit;p[K.batch]=encodeBatch(b.copy(stage=ProductionStage.SCRAP,updatedAt=System.currentTimeMillis()));p[K.scrapped]=(p[K.scrapped]?:0)+1;updateRewards(p)}}
    suspend fun abandonBatch(){context.activeGameplayDataStore.edit{it.remove(K.batch)}}

    suspend fun unlockSkill(id:String,companyLevel:Int){context.activeGameplayDataStore.edit{p->val c=decode(p);val d=IndustrialSkillCatalog.byId(id)?:error("Skill inválida");require(IndustrialSkillCatalog.canUnlock(d,c,companyLevel)){"Pré-requisito, nível ou pontos insuficientes"};p[K.skills]=(p[K.skills]?:emptySet())+id}}
    suspend fun setProductionPolicy(policy:ProductionPolicy){context.activeGameplayDataStore.edit{p->val c=decode(p);require(c.has("diretor_industrial")||policy==ProductionPolicy.BALANCED){"Libera com Diretor industrial"};p[K.policy]=policy.name}}

    private suspend fun updateBatch(allowed:Set<ProductionStage>,next:ProductionStage):OwnerWorkBatch {var out:OwnerWorkBatch?=null;context.activeGameplayDataStore.edit{p->val b=p[K.batch]?.let(::decodeBatch)?:error("Nenhum lote ativo");require(b.stage in allowed){"Etapa inválida: ${b.stage.label}"};val u=b.copy(stage=next,updatedAt=System.currentTimeMillis());p[K.batch]=encodeBatch(u);out=u};return requireNotNull(out)}

    private fun decode(p:Preferences)=CareerState(
        activeBatch=p[K.batch]?.let(::decodeBatch),masteryXp=parseMap(p[K.mastery]?:emptySet()),unlockedSkills=p[K.skills]?:emptySet(),milestones=p[K.milestones]?:emptySet(),achievements=p[K.achievements]?:emptySet(),
        totalManualOperations=p[K.manual]?:0,assistedOperations=p[K.assisted]?:0,perfectOperations=p[K.perfect]?:0,approvedBatches=p[K.approved]?:0,shippedBatches=p[K.shipped]?:0,reworkedBatches=p[K.reworked]?:0,scrappedBatches=p[K.scrapped]?:0,bestScore=p[K.best]?:0,operationStreak=p[K.streak]?:0,earnedSkillPoints=p[K.points]?:1,productionPolicy=runCatching{ProductionPolicy.valueOf(p[K.policy]?:ProductionPolicy.BALANCED.name)}.getOrDefault(ProductionPolicy.BALANCED),lastOperationAt=p[K.last]?:0L)

    private fun updateRewards(p:MutablePreferences){
        val manual=p[K.manual]?:0;val perfect=p[K.perfect]?:0;val approved=p[K.approved]?:0;val shipped=p[K.shipped]?:0;val reworked=p[K.reworked]?:0;val best=p[K.best]?:0;val streak=p[K.streak]?:0;val mastery=parseMap(p[K.mastery]?:emptySet())
        val marks=(p[K.milestones]?:emptySet()).toMutableSet();var pts=p[K.points]?:1
        fun award(id:String,n:Int){if(marks.add(id))pts+=n}
        if(manual>=1)award("manual_1",1);if(manual>=10)award("manual_10",1);if(manual>=25)award("manual_25",1);if(manual>=50)award("manual_50",1);if(manual>=100)award("manual_100",2);if(manual>=250)award("manual_250",2);if(perfect>=5)award("perfect_5",1);if(perfect>=20)award("perfect_20",2);if(approved>=10)award("approved_10",1);if(shipped>=20)award("shipped_20",2);if(reworked>=10)award("rework_10",1);if(mastery.values.any{MachineMastery("x",it).level>=10})award("mastery_10",2)
        p[K.milestones]=marks;p[K.points]=pts
        val a=(p[K.achievements]?:emptySet()).toMutableSet();if(manual>=1)a+="Primeiro cavaco";if(perfect>=1)a+="Peça perfeita";if(best>=95)a+="Na medida";if(streak>=5)a+="Ritmo de oficina";if(approved>=25)a+="Zero surpresa";if(shipped>=50)a+="Dono põe a mão na massa";if(mastery.values.any{MachineMastery("x",it).level>=20})a+="Mestre de máquina";p[K.achievements]=a
    }

    private fun encodeBatch(b:OwnerWorkBatch)=listOf(b.id,b.machineId,b.machineType,b.contractId,b.stage.name,b.producedQuantity,b.quality,b.precision,b.speed,b.mistakes,if(b.perfect)1 else 0,if(b.manual)1 else 0,b.reworkCount,b.createdAt,b.updatedAt).joinToString("§")
    private fun decodeBatch(raw:String):OwnerWorkBatch?=runCatching{val x=raw.split('§');require(x.size>=15);OwnerWorkBatch(x[0],x[1],x[2],x[3],ProductionStage.valueOf(x[4]),x[5].toInt(),x[6].toInt(),x[7].toInt(),x[8].toInt(),x[9].toInt(),x[10]=="1",x[11]=="1",x[12].toInt(),x[13].toLong(),x[14].toLong())}.getOrNull()
    private fun parseMap(v:Set<String>)=v.mapNotNull{val i=it.lastIndexOf('=');if(i<=0)null else it.substring(0,i) to (it.substring(i+1).toIntOrNull()?:0)}.toMap()
    private fun encodeMap(v:Map<String,Int>)=v.map{"${it.key}=${it.value.coerceAtLeast(0)}"}.toSet()
}

data class InspectionOutcome(val batch:OwnerWorkBatch,val shouldApprove:Boolean,val correctDecision:Boolean)
