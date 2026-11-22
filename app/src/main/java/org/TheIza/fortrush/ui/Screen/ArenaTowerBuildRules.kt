package org.TheIza.fortrush.ui.Screen

internal enum class ArenaBuildBlockReason {
    INVALID_STATE,
    NOT_YOUR_SLOT,
    SLOT_OCCUPIED,
    NOT_ENOUGH_COINS,
    ON_COOLDOWN
}

internal data class ArenaBuildEligibility(
    val allowed: Boolean,
    val reason: ArenaBuildBlockReason? = null
)

internal fun assessArenaBuild(
    slotOwnerPlayerNumber: Int?,
    localPlayerNumber: Int,
    isMatchActive: Boolean,
    isSlotOccupied: Boolean,
    hasEnoughCoins: Boolean,
    cooldownRemainingMs: Long = 0L
): ArenaBuildEligibility {
    if (!isMatchActive) return ArenaBuildEligibility(false, ArenaBuildBlockReason.INVALID_STATE)
    if (slotOwnerPlayerNumber == null || slotOwnerPlayerNumber != localPlayerNumber) {
        return ArenaBuildEligibility(false, ArenaBuildBlockReason.NOT_YOUR_SLOT)
    }
    if (isSlotOccupied) return ArenaBuildEligibility(false, ArenaBuildBlockReason.SLOT_OCCUPIED)
    if (cooldownRemainingMs > 0L) return ArenaBuildEligibility(false, ArenaBuildBlockReason.ON_COOLDOWN)
    if (!hasEnoughCoins) return ArenaBuildEligibility(false, ArenaBuildBlockReason.NOT_ENOUGH_COINS)
    return ArenaBuildEligibility(true)
}
