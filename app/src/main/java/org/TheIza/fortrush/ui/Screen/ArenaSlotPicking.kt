package org.TheIza.fortrush.ui.Screen

import org.TheIza.fortrush.data.matchmaking.MatchPoint
import kotlin.math.hypot

internal data class ArenaSlotTarget(
    val id: String,
    val ownerPlayerNumber: Int,
    val x: Float,
    val y: Float,
    val touchRadius: Float
)

/** Returns the nearest touched slot owned by [localPlayer], or null for an opponent/empty area. */
internal fun pickOwnSlot(
    point: MatchPoint,
    slots: List<ArenaSlotTarget>,
    localPlayer: Int
): ArenaSlotTarget? {
    if (localPlayer !in 1..2 || !point.x.isFinite() || !point.y.isFinite()) return null

    return slots.asSequence()
        .filter { slot ->
            slot.ownerPlayerNumber == localPlayer &&
                slot.x.isFinite() && slot.y.isFinite() &&
                slot.touchRadius.isFinite() && slot.touchRadius >= 0f
        }
        .map { slot -> slot to hypot(slot.x - point.x, slot.y - point.y) }
        .filter { (slot, distance) -> distance <= slot.touchRadius }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}
