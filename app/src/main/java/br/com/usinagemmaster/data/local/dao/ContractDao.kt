package br.com.usinagemmaster.data.local.dao

import androidx.room.*
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.data.local.entity.FinancialTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractDao {
    @Query("SELECT * FROM contracts WHERE status != 'COMPLETED' ORDER BY generatedAt DESC") fun observeAll(): Flow<List<ContractEntity>>
    @Query("SELECT * FROM contracts WHERE status = 'ACTIVE' ORDER BY startedAt ASC") suspend fun getActive(): List<ContractEntity>
    @Query("SELECT * FROM contracts WHERE status = 'COMPLETED' ORDER BY generatedAt DESC") suspend fun getCompleted(): List<ContractEntity>

    @Query("SELECT * FROM contracts WHERE status = 'COMPLETED' ORDER BY generatedAt DESC")
    fun observeCompleted(): Flow<List<ContractEntity>>
    @Query("SELECT COUNT(*) FROM contracts WHERE status = 'ACTIVE'") fun observeActiveCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM contracts WHERE status = 'AVAILABLE'") suspend fun availableCount(): Int
    @Query("SELECT * FROM contracts WHERE id = :id") suspend fun byId(id: String): ContractEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(values: List<ContractEntity>)
    @Update suspend fun update(value: ContractEntity)

    @Query("SELECT COUNT(*) FROM financial_transactions WHERE id = :transactionId")
    suspend fun payoutTransactionCount(transactionId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM financial_transactions
        WHERE type = 'INCOME'
          AND category = 'CONTRACT'
          AND amountCents = :rewardCents
          AND createdAt >= :since
          AND description LIKE '%' || :clientName || '%'
        """
    )
    suspend fun legacyPayoutCount(rewardCents: Long, clientName: String, since: Long): Int

    @Query(
        """
        UPDATE contracts
        SET status = 'COMPLETED',
            completedQuantity = quantity,
            productionProgressMilli = quantity * 1000
        WHERE id = :contractId
        """
    )
    suspend fun markCompleted(contractId: String)

    @Query(
        """
        UPDATE company
        SET cashCents = cashCents + :rewardCents,
            reputation = reputation + :reputationReward
        WHERE id = 1
        """
    )
    suspend fun creditCompany(rewardCents: Long, reputationReward: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayout(value: FinancialTransactionEntity): Long

    /**
     * Liquida contratos novos como uma única transação Room.
     * O lançamento financeiro usa ID determinístico por contrato, então a operação
     * pode ser repetida com segurança sem pagar duas vezes.
     */
    @Transaction
    suspend fun settleReward(
        contract: ContractEntity,
        payout: FinancialTransactionEntity
    ): Boolean {
        markCompleted(contract.id)
        if (payoutTransactionCount(payout.id) > 0) return false

        val inserted = insertPayout(payout)
        if (inserted == -1L) return false

        creditCompany(contract.rewardCents, contract.reputationReward)
        return true
    }

    /**
     * Reparo automático conservador para saves antigos. Se já existir um lançamento
     * legado compatível, não tenta adivinhar se o caixa foi ou não creditado.
     */
    @Transaction
    suspend fun repairRewardIfClearlyMissing(
        contract: ContractEntity,
        payout: FinancialTransactionEntity
    ): Boolean {
        markCompleted(contract.id)
        if (payoutTransactionCount(payout.id) > 0) return false

        val legacyAlreadyExists = legacyPayoutCount(
            rewardCents = contract.rewardCents,
            clientName = contract.clientName,
            since = contract.startedAt ?: contract.generatedAt
        ) > 0
        if (legacyAlreadyExists) return false

        val inserted = insertPayout(payout)
        if (inserted == -1L) return false

        creditCompany(contract.rewardCents, contract.reputationReward)
        return true
    }

    /**
     * Recuperação manual para saves que exibem o contrato como concluído, mas o
     * jogador confirma que o dinheiro não entrou. Ignora o lançamento legado, pois
     * versões antigas podiam gravar o financeiro antes de atualizar o caixa.
     * O ID determinístico continua impedindo uma segunda recuperação.
     */
    @Transaction
    suspend fun recoverReward(
        contract: ContractEntity,
        payout: FinancialTransactionEntity
    ): Boolean {
        markCompleted(contract.id)
        if (payoutTransactionCount(payout.id) > 0) return false

        val inserted = insertPayout(payout)
        if (inserted == -1L) return false

        creditCompany(contract.rewardCents, contract.reputationReward)
        return true
    }

    /** Remove somente o registro do contrato já pago/concluído. Não toca no financeiro. */
    @Query("DELETE FROM contracts WHERE id = :contractId AND status = 'COMPLETED'")
    suspend fun dismissCompleted(contractId: String)


    /** FAILED é só histórico; apagar não devolve multa/reputação. */
    @Query("DELETE FROM contracts WHERE id = :contractId AND status = 'FAILED'")
    suspend fun dismissFailed(contractId: String)

    // V8 • estoque elegível por nível; evita ficar sem contratos executáveis.
    @Query("SELECT * FROM contracts WHERE status = 'AVAILABLE' ORDER BY generatedAt DESC")
    suspend fun getAvailableForProgression(): List<ContractEntity>
}
