package com.simpleledger.app.backup

import androidx.annotation.StringRes
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.InterestRateEntity
import com.simpleledger.app.data.LedgerEntryEntity
import com.simpleledger.app.data.PersonEntity
import com.simpleledger.app.domain.HashChain
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BackupContents(
    val people: List<PersonEntity>,
    val rates: List<InterestRateEntity>,
    val entries: List<LedgerEntryEntity>,
    val exportedAt: Long,
)

class BackupException(@StringRes val messageRes: Int) : Exception()

/** The backup file: plain JSON with every person, rate and entry, including the hash chain. */
object BackupFormat {
    private const val FORMAT = "simple-ledger-backup"
    private const val VERSION = 1

    fun toJson(c: BackupContents): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", c.exportedAt)
        .put("people", JSONArray(c.people.map(::personJson)))
        .put("rates", JSONArray(c.rates.map(::rateJson)))
        .put("entries", JSONArray(c.entries.sortedBy { it.seq }.map(::entryJson)))
        .toString(2)

    /** Reads and checks a backup. Throws [BackupException] with a message for the user if anything is wrong. */
    fun parse(text: String): BackupContents {
        val contents = try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT) throw BackupException(R.string.backup_bad_file)
            if (root.optInt("version", 0) > VERSION) throw BackupException(R.string.backup_newer)
            BackupContents(
                people = root.getJSONArray("people").objects().map(::personOf),
                rates = root.getJSONArray("rates").objects().map(::rateOf),
                entries = root.getJSONArray("entries").objects().map(::entryOf),
                exportedAt = root.getLong("exportedAt"),
            )
        } catch (e: JSONException) {
            throw BackupException(R.string.backup_bad_file)
        } catch (e: IllegalArgumentException) {
            throw BackupException(R.string.backup_bad_file)
        }
        val personIds = contents.people.map { it.id }.toSet()
        val linked = contents.entries.all { it.personId in personIds } && contents.rates.all { it.personId in personIds }
        if (!linked || HashChain.firstBroken(contents.entries) != null) throw BackupException(R.string.backup_tampered)
        return contents
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    private fun personJson(p: PersonEntity) = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("phone", p.phone.orNull())
        .put("hidden", p.hidden)
        .put("createdAt", p.createdAt)

    private fun personOf(o: JSONObject) = PersonEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        phone = o.str("phone"),
        hidden = o.getBoolean("hidden"),
        createdAt = o.getLong("createdAt"),
    )

    private fun rateJson(r: InterestRateEntity) = JSONObject()
        .put("seq", r.seq)
        .put("personId", r.personId)
        .put("rateBp", r.rateBp)
        .put("effectiveFrom", r.effectiveFrom)
        .put("recordedAt", r.recordedAt)

    private fun rateOf(o: JSONObject) = InterestRateEntity(
        seq = o.getLong("seq"),
        personId = o.getString("personId"),
        rateBp = o.getInt("rateBp"),
        effectiveFrom = o.getString("effectiveFrom"),
        recordedAt = o.getLong("recordedAt"),
    )

    private fun entryJson(e: LedgerEntryEntity) = JSONObject()
        .put("seq", e.seq)
        .put("id", e.id)
        .put("personId", e.personId)
        .put("direction", e.direction.name)
        .put("amountPaise", e.amountPaise)
        .put("note", e.note.orNull())
        .put("occurredOn", e.occurredOn)
        .put("recordedAt", e.recordedAt)
        .put("kind", e.kind.name)
        .put("reversesSeq", e.reversesSeq.orNull())
        .put("interestFrom", e.interestFrom.orNull())
        .put("interestTo", e.interestTo.orNull())
        .put("rateBp", e.rateBp.orNull())
        .put("prevHash", e.prevHash)
        .put("hash", e.hash)

    private fun entryOf(o: JSONObject) = LedgerEntryEntity(
        seq = o.getLong("seq"),
        id = o.getString("id"),
        personId = o.getString("personId"),
        direction = Direction.valueOf(o.getString("direction")),
        amountPaise = o.getLong("amountPaise"),
        note = o.str("note"),
        occurredOn = o.getString("occurredOn"),
        recordedAt = o.getLong("recordedAt"),
        kind = EntryKind.valueOf(o.getString("kind")),
        reversesSeq = o.long("reversesSeq"),
        interestFrom = o.str("interestFrom"),
        interestTo = o.str("interestTo"),
        rateBp = o.int("rateBp"),
        prevHash = o.getString("prevHash"),
        hash = o.getString("hash"),
    )
}
