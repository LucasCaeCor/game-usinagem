package br.com.usinagemmaster.domain.social

import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    fun isFirebaseConfigured(): Boolean
    suspend fun connect(): Result<String>
    suspend fun publishProfile(
        profile: LocalPlayerProfile,
        dashboard: DashboardStatus,
        production: ProductionSnapshot
    ): Result<Unit>
    fun observePlayers(): Flow<List<OnlinePlayer>>
    fun observeIncomingHelp(): Flow<List<SocialHelpGift>>
    suspend fun sendHelp(toUid: String, fromName: String): Result<Unit>
    suspend fun claimHelp(giftId: String): Result<Int>
}
