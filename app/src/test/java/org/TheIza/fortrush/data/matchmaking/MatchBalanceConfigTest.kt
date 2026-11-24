package org.TheIza.fortrush.data.matchmaking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatchBalanceConfigTest {
    private fun balanceJson() = JSONObject(
        """{"balance":{"version":"fort-rush-2026-11-23.1","match":{"durationMs":240000},"economy":{"startingCoins":150,"maximumCoins":1000,"incomeBands":[{"fromElapsedMs":0,"untilElapsedMs":180000,"coinsPerSecond":5},{"fromElapsedMs":180000,"untilElapsedMs":240000,"coinsPerSecond":10}]},"cards":{"towers":{"arrow_tower":{"cost":100},"cannon_tower":{"cost":130}}}}}"""
    )

    @Test
    fun parsesVersionedCostsAndIncomeBandsFromTheServer() {
        val balance = balanceJson().matchBalanceConfig()

        assertNotNull(balance)
        assertEquals("fort-rush-2026-11-23.1", balance!!.version)
        assertEquals(100, balance.towerCost(MatchTowerCard.ARROW_TOWER))
        assertEquals(130, balance.towerCost(MatchTowerCard.CANNON_TOWER))
        assertEquals(5, balance.incomeRateAt(179_999))
        assertEquals(10, balance.incomeRateAt(180_000))
    }

    @Test
    fun projectsCoinsFromServerBalanceAcrossIncomeBandsAndCap() {
        val balance = balanceJson().matchBalanceConfig()!!
        val message = JSONObject().put("economy", JSONObject()
            .put("balanceVersion", balance.version)
            .put("startingCoins", 150)
            .put("incomePerSecond", 10)
            .put("startedAtServerMs", 1_000)
            .put("serverTimeMs", 181_000)
            .put("elapsedMatchMs", 180_000)
            .put("coinsSpent", 100)
            .put("coins", 900)
            .put("maximumCoins", 1_000))
        val snapshot = message.economySnapshot(balance)

        assertNotNull(snapshot)
        assertEquals(910, snapshot!!.coinsAfterElapsed(1_000))
        assertEquals(10, snapshot.incomeRateAfterElapsed(0))
    }

    @Test
    fun passiveIncomeDoesNotReduceARewardBalanceAboveTheCap() {
        val balance = balanceJson().matchBalanceConfig()!!
        val message = JSONObject().put("economy", JSONObject()
            .put("balanceVersion", balance.version)
            .put("startingCoins", 150)
            .put("incomePerSecond", 10)
            .put("startedAtServerMs", 1_000)
            .put("serverTimeMs", 181_000)
            .put("elapsedMatchMs", 180_000)
            .put("coinsSpent", 0)
            .put("coins", 1_050)
            .put("maximumCoins", 1_000))

        assertEquals(1_050, message.economySnapshot(balance)!!.coinsAfterElapsed(5_000))
    }

    @Test
    fun rejectsEconomySnapshotsForAnotherBalanceVersion() {
        val balance = balanceJson().matchBalanceConfig()!!
        val message = JSONObject().put("economy", JSONObject()
            .put("balanceVersion", "different-version")
            .put("startingCoins", 150)
            .put("incomePerSecond", 5)
            .put("startedAtServerMs", 1_000)
            .put("serverTimeMs", 1_000)
            .put("elapsedMatchMs", 0)
            .put("coinsSpent", 0)
            .put("coins", 150)
            .put("maximumCoins", 1_000))

        assertNull(message.economySnapshot(balance))
    }
}
