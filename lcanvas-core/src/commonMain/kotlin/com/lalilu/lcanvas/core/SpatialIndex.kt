package com.lalilu.lcanvas.core

import androidx.compose.ui.geometry.Rect
import kotlin.math.floor

class SpatialIndex(
    private val cellSize: Float = 256f
) {
    private val buckets = mutableMapOf<Long, MutableList<Int>>()

    fun clear() {
        buckets.clear()
    }

    fun add(index: Int, rect: Rect) {
        val minCx = cell(rect.left)
        val minCy = cell(rect.top)
        val maxCx = cell(rect.right)
        val maxCy = cell(rect.bottom)
        for (cx in minCx..maxCx) {
            for (cy in minCy..maxCy) {
                val key = pack(cx, cy)
                val list = buckets.getOrPut(key) { mutableListOf() }
                list.add(index)
            }
        }
    }

    fun query(range: Rect): List<Int> {
        val minCx = cell(range.left)
        val minCy = cell(range.top)
        val maxCx = cell(range.right)
        val maxCy = cell(range.bottom)
        val set = LinkedHashSet<Int>()
        for (cx in minCx..maxCx) {
            for (cy in minCy..maxCy) {
                val list = buckets[pack(cx, cy)] ?: continue
                set.addAll(list)
            }
        }
        return set.toList()
    }

    private fun cell(coord: Float): Int = floor(coord / cellSize).toInt()
    private fun pack(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
}
