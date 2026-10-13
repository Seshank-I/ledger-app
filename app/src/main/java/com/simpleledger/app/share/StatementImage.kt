package com.simpleledger.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.PersonEntity
import com.simpleledger.app.domain.AccountState
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.EntryRow
import com.simpleledger.app.domain.Money
import java.time.LocalDate
import kotlin.math.max

/**
 * Draws a person's statement as a picture for WhatsApp, which cannot show real tables.
 * Rows run oldest to newest, so the running balance ends at today's balance shown below the table.
 * Long statements are split into pages so no single image is too big for the phone's memory.
 */
object StatementImage {
    const val ROWS_PER_PAGE = 40

    private const val MIN_WIDTH = 1080
    private const val PAD = 48f
    private const val CELL = 18f

    private val INK = Color.rgb(0x11, 0x11, 0x11)
    private val MUTED = Color.rgb(0x4A, 0x4D, 0x52)
    private val GAVE = Color.rgb(0xA1, 0x1A, 0x12)
    private val GOT = Color.rgb(0x1B, 0x5E, 0x20)
    private val HEADER_BG = Color.rgb(0xE0, 0xE2, 0xE6)
    private val GAVE_TINT = Color.rgb(0xFB, 0xEC, 0xEB)
    private val GOT_TINT = Color.rgb(0xEA, 0xF3, 0xEA)
    private val RULE = Color.rgb(0xC8, 0xCA, 0xCE)

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val titlePaint = paint(62f, bold = true)
    private val subPaint = paint(38f, MUTED)
    private val headPaint = paint(40f, bold = true)
    private val cellPaint = paint(44f)
    private val amountPaint = paint(44f, bold = true)
    private val smallPaint = paint(36f, MUTED)
    private val totalPaint = paint(54f, bold = true)

    /** [rows] are newest first, as the app shows them; [shown] is how many of the newest to include. */
    fun render(
        context: Context,
        person: PersonEntity,
        rows: List<EntryRow>,
        state: AccountState,
        shown: Int,
        includeNotes: Boolean,
    ): List<Bitmap> {
        val picked = rows.take(shown).reversed()
        val pages = picked.chunked(ROWS_PER_PAGE).ifEmpty { listOf(emptyList()) }
        return pages.mapIndexed { i, pageRows ->
            drawPage(context, person, pageRows, picked, rows.size, state, includeNotes, i + 1, pages.size)
        }
    }

    private class Widths(val date: Float, val gave: Float, val got: Float, val balance: Float) {
        val total get() = date + gave + got + balance
    }

    private fun widths(context: Context, rows: List<EntryRow>): Widths {
        fun fit(texts: List<String>, p: Paint) = (texts.maxOfOrNull { p.measureText(it) } ?: 0f) + CELL * 2
        return Widths(
            date = max(fit(rows.map { Dates.formatIso(it.entry.occurredOn) }, cellPaint), fit(listOf(context.getString(R.string.col_date)), headPaint)),
            gave = max(fit(rows.filter { it.entry.direction == Direction.GAVE }.map { Money.format(it.entry.amountPaise) }, amountPaint), fit(listOf(context.getString(R.string.col_gave)), headPaint)),
            got = max(fit(rows.filter { it.entry.direction == Direction.GOT }.map { Money.format(it.entry.amountPaise) }, amountPaint), fit(listOf(context.getString(R.string.col_got)), headPaint)),
            balance = max(fit(rows.map { Money.format(it.balanceAfter) }, amountPaint), fit(listOf(context.getString(R.string.col_balance)), headPaint)),
        )
    }

    /** The grey line under a row: interest period, cancellation, and the note. */
    private fun subLine(context: Context, row: EntryRow, includeNotes: Boolean): String? {
        val e = row.entry
        val parts = buildList {
            if (e.kind == EntryKind.INTEREST && e.interestFrom != null && e.interestTo != null) {
                add(context.getString(R.string.type_interest) + " · " +
                    context.getString(R.string.interest_period, Dates.formatIso(e.interestFrom), Dates.formatIso(e.interestTo)))
            }
            row.cancelledAt?.let { add(context.getString(R.string.entry_cancelled_on, Dates.format(Dates.dateOfMillis(it)))) }
            if (includeNotes) e.note?.let { add(it) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun layout(text: String, p: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    private fun lineHeight(p: Paint) = p.fontSpacing

    private fun drawPage(
        context: Context,
        person: PersonEntity,
        pageRows: List<EntryRow>,
        allPicked: List<EntryRow>,
        totalRows: Int,
        state: AccountState,
        includeNotes: Boolean,
        page: Int,
        pages: Int,
    ): Bitmap {
        // Same column widths on every page, measured over all rows being sent.
        val w = widths(context, allPicked)
        val width = max(MIN_WIDTH.toFloat(), w.total + PAD * 2)
        val contentW = width - PAD * 2
        val extra = (contentW - w.total).coerceAtLeast(0f)
        val cols = floatArrayOf(w.date + extra / 4, w.gave + extra / 4, w.got + extra / 4, w.balance + extra / 4)

        val title = layout(context.getString(R.string.statement_title, person.name), titlePaint, contentW)
        val today = Dates.format(LocalDate.now())
        val subtitleText = buildString {
            append(
                if (allPicked.size >= totalRows) context.getString(R.string.statement_all, totalRows, today)
                else context.getString(R.string.statement_last, allPicked.size, totalRows, today),
            )
            if (pages > 1) append(" · ").append(context.getString(R.string.statement_page, page, pages))
        }
        val subtitle = layout(subtitleText, subPaint, contentW)

        val headerH = lineHeight(headPaint) + CELL * 2
        val mainH = lineHeight(amountPaint) + lineHeight(smallPaint) + CELL * 2
        val subLayouts = pageRows.map { r -> subLine(context, r, includeNotes)?.let { layout(it, smallPaint, contentW - CELL * 2) } }
        val rowHeights = subLayouts.map { mainH + (it?.height?.toFloat()?.plus(CELL) ?: 0f) }

        val isLast = page == pages
        val totalText = balanceText(context, person.name, state.balancePaise)
        val totalLayout = layout(totalText, TextPaint(totalPaint).apply { color = balanceColor(state.balancePaise) }, contentW)
        val interestLayout = if (state.accruedPaise != 0L) {
            layout(context.getString(R.string.accrued_line, Money.format(state.accruedPaise)), subPaint, contentW)
        } else null
        val footer = context.getString(R.string.statement_footer)
        val footerH = if (isLast) {
            PAD + totalLayout.height + (interestLayout?.height?.plus(12) ?: 0) + 24 + lineHeight(smallPaint)
        } else PAD / 2 + lineHeight(smallPaint)

        val emptyH = if (pageRows.isEmpty()) lineHeight(cellPaint) + CELL * 2 else 0f
        val height = PAD + title.height + 12 + subtitle.height + PAD / 2 + headerH + rowHeights.sum() + emptyH + footerH + PAD
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = PAD
        canvas.save(); canvas.translate(PAD, y); title.draw(canvas); canvas.restore()
        y += title.height + 12
        canvas.save(); canvas.translate(PAD, y); subtitle.draw(canvas); canvas.restore()
        y += subtitle.height + PAD / 2

        // Header row
        fill.color = HEADER_BG
        canvas.drawRect(PAD, y, PAD + contentW, y + headerH, fill)
        val heads = listOf(R.string.col_date, R.string.col_gave, R.string.col_got, R.string.col_balance)
        var x = PAD
        heads.forEachIndexed { i, res ->
            canvas.drawText(context.getString(res), x + CELL, y + CELL - headPaint.ascent(), headPaint)
            x += cols[i]
        }
        y += headerH

        if (pageRows.isEmpty()) {
            canvas.drawText(context.getString(R.string.no_entries_short), PAD + CELL, y + CELL - cellPaint.ascent(), cellPaint)
            y += emptyH
        }

        pageRows.forEachIndexed { i, row ->
            val e = row.entry
            val h = rowHeights[i]
            // Column tints so "gave" and "got" read at a glance, like the app.
            fill.color = GAVE_TINT
            canvas.drawRect(PAD + cols[0], y, PAD + cols[0] + cols[1], y + h, fill)
            fill.color = GOT_TINT
            canvas.drawRect(PAD + cols[0] + cols[1], y, PAD + cols[0] + cols[1] + cols[2], y + h, fill)

            val baseline = y + CELL - amountPaint.ascent()
            canvas.drawText(Dates.formatIso(e.occurredOn), PAD + CELL, baseline, cellPaint)

            val cancelled = row.cancelledAt != null
            val amount = Money.format(e.amountPaise)
            val ap = TextPaint(amountPaint).apply {
                color = if (cancelled) MUTED else if (e.direction == Direction.GAVE) GAVE else GOT
                isStrikeThruText = cancelled
            }
            val amountX = PAD + cols[0] + (if (e.direction == Direction.GAVE) 0f else cols[1]) + CELL
            canvas.drawText(amount, amountX, baseline, ap)

            val b = row.balanceAfter
            val balX = PAD + cols[0] + cols[1] + cols[2] + CELL
            canvas.drawText(Money.format(b), balX, baseline, TextPaint(amountPaint).apply { color = balanceColor(b) })
            val word = context.getString(
                when {
                    b > 0 -> R.string.bal_they_owe
                    b < 0 -> R.string.bal_you_owe
                    else -> R.string.bal_settled
                },
            )
            canvas.drawText(word, balX, baseline + lineHeight(smallPaint), smallPaint)

            subLayouts[i]?.let { sub ->
                canvas.save()
                canvas.translate(PAD + CELL, y + mainH)
                sub.draw(canvas)
                canvas.restore()
            }
            y += h
            fill.color = RULE
            canvas.drawRect(PAD, y - 2, PAD + contentW, y, fill)
        }

        if (isLast) {
            y += PAD
            canvas.save(); canvas.translate(PAD, y); totalLayout.draw(canvas); canvas.restore()
            y += totalLayout.height
            interestLayout?.let {
                y += 12
                canvas.save(); canvas.translate(PAD, y); it.draw(canvas); canvas.restore()
                y += it.height
            }
            y += 24
        } else {
            y += PAD / 2
        }
        canvas.drawText(footer, PAD, y - smallPaint.ascent(), smallPaint)
        return bitmap
    }

    private fun balanceColor(b: Long) = when {
        b > 0 -> GOT
        b < 0 -> GAVE
        else -> INK
    }

    fun balanceText(context: Context, name: String, b: Long): String = when {
        b > 0 -> context.getString(R.string.balance_they_owe, name, Money.format(b))
        b < 0 -> context.getString(R.string.balance_you_owe, name, Money.format(b))
        else -> context.getString(R.string.balance_settled)
    }
}
