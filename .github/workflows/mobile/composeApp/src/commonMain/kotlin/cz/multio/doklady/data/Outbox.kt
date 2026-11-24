package cz.multio.doklady.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.multio.doklady.platform.KeyValueStore
import cz.multio.doklady.platform.LocalFiles
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Fronta neodeslaných dokladů. Doklad se vždy nejdřív uloží do telefonu a teprve pak odesílá –
 * když není signál (obchod, sklep, cesta), nic se neztratí a odešle se při dalším pokusu.
 */
@Serializable
data class OutboxItem(
    val id: String,
    val companyId: Int?,
    val originalFile: String,
    val originalMime: String,
    val originalName: String,
    val processedFile: String?,
    val note: String?,
    val createdAt: Long,
    val lastError: String? = null,
    val attempts: Int = 0,
    val stage: String? = null,
    val costCenter: String? = null,
    val job: String? = null,
    val direction: String? = null,
    /** Text QR platby (SPD) přečtený z dokladu v telefonu. */
    val qr: String? = null,
    /** faktura | uctenka (režim Účtenky) */
    val docType: String? = null,
    /** Druh podle klienta (portál 1.15). */
    val categoryId: Int? = null,
)

object Outbox {
    private const val KEY = "outbox"
    private val mutex = Mutex()
    private val serializer = ListSerializer(OutboxItem.serializer())

    var items by mutableStateOf(load())
        private set

    private fun load(): List<OutboxItem> =
        KeyValueStore.get(KEY)?.let { runCatching { Api.json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    private fun save(list: List<OutboxItem>) {
        items = list
        KeyValueStore.put(KEY, Api.json.encodeToString(serializer, list))
    }

    /** Uloží doklad z telefonu do fronty. Vrátí položku fronty. */
    suspend fun enqueue(
        original: ByteArray,
        originalMime: String,
        originalName: String,
        processed: ByteArray?,
        note: String?,
        stage: String? = null,
        costCenter: String? = null,
        job: String? = null,
        direction: String? = null,
        qr: String? = null,
        docType: String? = null,
        categoryId: Int? = null,
    ): OutboxItem = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "$now-${(1000..9999).random()}"
        val orig = "outbox_${id}_original"
        LocalFiles.write(orig, original)
        val proc = processed?.let { "outbox_${id}_processed".also { name -> LocalFiles.write(name, it) } }
        val item = OutboxItem(id, Api.companyId, orig, originalMime, originalName, proc, note, now,
            stage = stage, costCenter = costCenter, job = job, direction = direction, qr = qr, docType = docType, categoryId = categoryId)
        save(items + item)
        item
    }

    /** Pokusí se odeslat jednu položku. Vrátí vytvořený doklad, nebo null při chybě (zůstane ve frontě). */
    suspend fun send(item: OutboxItem): Document? {
        val original = LocalFiles.read(item.originalFile)
        if (original == null) {
            remove(item)
            return null
        }
        return try {
            val doc = Api.uploadDocument(
                original = original,
                originalMime = item.originalMime,
                originalName = item.originalName,
                processed = item.processedFile?.let { LocalFiles.read(it) },
                note = item.note,
                company = item.companyId ?: Api.companyId,
                stage = item.stage,
                costCenter = item.costCenter,
                job = item.job,
                direction = item.direction,
                qr = item.qr,
                docType = item.docType,
                categoryId = item.categoryId,
            )
            remove(item)
            doc
        } catch (e: Exception) {
            mutex.withLock {
                save(items.map {
                    if (it.id == item.id) it.copy(lastError = e.message, attempts = it.attempts + 1) else it
                })
            }
            null
        }
    }

    /** Odešle vše, co čeká. Vrací počet úspěšně odeslaných. */
    suspend fun flush(): Int {
        var ok = 0
        for (item in items.toList()) {
            if (send(item) != null) ok++
        }
        return ok
    }

    suspend fun remove(item: OutboxItem) = mutex.withLock {
        LocalFiles.delete(item.originalFile)
        item.processedFile?.let { LocalFiles.delete(it) }
        save(items.filterNot { it.id == item.id })
    }
}
