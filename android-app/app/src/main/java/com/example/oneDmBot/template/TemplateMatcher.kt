package com.example.oneDmBot.template

import android.graphics.Bitmap
import android.graphics.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Naive sum-of-absolute-differences (SAD) template matcher. Both haystack
 * (full screenshot) and needle (user-cropped reference) get downscaled by
 * [SCALE] for speed, then SAD is run over each candidate position with an
 * early-exit when the running SAD already exceeds the current best.
 *
 * Tolerance: low enough to find the same icon at a different position, high
 * enough to accept slight color/badge variations (e.g. counter "1" vs "19").
 */
object TemplateMatcher {

    private const val SCALE = 4
    private const val SCAN_STEP = 1

    /**
     * Returns the centre point (in haystack pixel coordinates) of the best
     * match if its normalized SAD is below [maxNormalizedDiff]; null otherwise.
     */
    fun findCenter(
        haystack: Bitmap,
        needle: Bitmap,
        maxNormalizedDiff: Double = 0.18
    ): Point? {
        if (needle.width >= haystack.width || needle.height >= haystack.height) return null

        val hay = haystack.scaled(SCALE)
        val nee = needle.scaled(SCALE)
        if (nee.width < 4 || nee.height < 4) return null
        if (nee.width >= hay.width || nee.height >= hay.height) return null

        val hayW = hay.width
        val hayH = hay.height
        val neeW = nee.width
        val neeH = nee.height

        val hayPx = IntArray(hayW * hayH).also { hay.getPixels(it, 0, hayW, 0, 0, hayW, hayH) }
        val neePx = IntArray(neeW * neeH).also { nee.getPixels(it, 0, neeW, 0, 0, neeW, neeH) }

        var bestSad = Long.MAX_VALUE
        var bestX = -1
        var bestY = -1

        val maxX = hayW - neeW
        val maxY = hayH - neeH

        var y = 0
        while (y <= maxY) {
            var x = 0
            while (x <= maxX) {
                var sad = 0L
                outer@ for (ny in 0 until neeH) {
                    val hayRow = (y + ny) * hayW + x
                    val neeRow = ny * neeW
                    for (nx in 0 until neeW) {
                        val hp = hayPx[hayRow + nx]
                        val np = neePx[neeRow + nx]
                        sad += abs(((hp shr 16) and 0xFF) - ((np shr 16) and 0xFF))
                        sad += abs(((hp shr 8) and 0xFF) - ((np shr 8) and 0xFF))
                        sad += abs((hp and 0xFF) - (np and 0xFF))
                        if (sad >= bestSad) break@outer
                    }
                }
                if (sad < bestSad) {
                    bestSad = sad
                    bestX = x
                    bestY = y
                }
                x += SCAN_STEP
            }
            y += SCAN_STEP
        }

        if (bestX < 0) return null

        val maxPossible = neeW.toLong() * neeH * 3L * 255L
        val normalized = bestSad.toDouble() / maxPossible
        if (normalized > maxNormalizedDiff) return null

        // map the downscaled centre back to original coordinates
        val cxDown = bestX + neeW / 2.0
        val cyDown = bestY + neeH / 2.0
        return Point((cxDown * SCALE).toInt(), (cyDown * SCALE).toInt())
    }

    private fun Bitmap.scaled(factor: Int): Bitmap {
        val w = max(1, width / factor)
        val h = max(1, height / factor)
        return Bitmap.createScaledBitmap(this, w, h, true)
    }
}
