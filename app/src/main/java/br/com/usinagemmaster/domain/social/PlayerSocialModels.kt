package br.com.usinagemmaster.domain.social

data class PlayerAvatar(
    val gender: String = "MALE",
    val skinStyle: String = "WORKSHOP",
    val bodyType: String = "STANDARD",
    val skinTone: String = "MEDIUM",
    val hairStyle: String = "SHORT",
    val hairColor: String = "DARK",
    val uniformColor: String = "NAVY",
    val helmetColor: String = "YELLOW",
    val accessory: String = "NONE"
)

data class LocalPlayerProfile(
    val displayName: String = "",
    val avatar: PlayerAvatar = PlayerAvatar(),
    val onboardingComplete: Boolean = false
)

data class OnlinePlayer(
    val uid: String,
    val displayName: String,
    val companyName: String,
    val companyLevel: Int,
    val reputation: Int,
    val machineCount: Int,
    val employeeCount: Int,
    val productionPer10Minutes: Double,
    val avatar: PlayerAvatar,
    val lastSeenAt: Long
)

data class SocialHelpGift(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val createdAt: Long,
    val claimed: Boolean,
    val rewardBoosts: Int = 1
)

data class SocialConnectionState(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val uid: String? = null,
    val message: String? = null
)
