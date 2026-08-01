package com.stuart.atccontroller.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One policy shared by replay capture, metadata persistence, migration, and payload loading.
 *
 * Payloads are encoded UTF-8 bytes. The byte limit is deliberately distinct from String length:
 * non-ASCII replay metadata must not slip past the caller and then fail at the storage boundary.
 */
object ReplayPolicy {
    const val MIN_SUPPORTED_SCHEMA = 2
    const val MAX_SUPPORTED_SCHEMA = 5
    const val MAX_ENCODED_BYTES = 500_000
    const val MAX_COMMAND_COUNT = 4_096
    const val MAX_REPLAY_COUNT = 5

    internal const val MAX_REPLAY_ID_BYTES = 256
    internal const val MAX_SCENARIO_ID_BYTES = 256
    internal const val MAX_TERMINAL_HASH_BYTES = 256

    fun encodedByteCount(payload: String): Int =
        payload.toByteArray(StandardCharsets.UTF_8).size

    /**
     * Replay payloads have three header lines followed by zero or more command lines. This remains
     * tolerant of legacy/minimal payloads while bounding every newline-delimited command log.
     */
    fun commandCount(payload: String): Int =
        (payload.count { it == '\n' } + 1 - REPLAY_HEADER_LINES).coerceAtLeast(0)

    fun validate(record: CompletedReplayRecord): ReplayPolicyViolation? {
        if (record.schemaVersion !in MIN_SUPPORTED_SCHEMA..MAX_SUPPORTED_SCHEMA) {
            return ReplayPolicyViolation.UNSUPPORTED_SCHEMA
        }
        if (record.id.isBlank() || record.id.utf8Size() > MAX_REPLAY_ID_BYTES) {
            return ReplayPolicyViolation.INVALID_REPLAY_ID
        }
        if (record.scenarioId.isBlank() || record.scenarioId.utf8Size() > MAX_SCENARIO_ID_BYTES) {
            return ReplayPolicyViolation.INVALID_SCENARIO_ID
        }
        if (record.terminalHash.utf8Size() > MAX_TERMINAL_HASH_BYTES) {
            return ReplayPolicyViolation.INVALID_TERMINAL_HASH
        }
        if (record.savedAtEpochMillis < 0 || record.terminalTick < 0 || record.finalScore < 0) {
            return ReplayPolicyViolation.INVALID_TERMINAL_METADATA
        }
        if (encodedByteCount(record.payload) > MAX_ENCODED_BYTES) {
            return ReplayPolicyViolation.PAYLOAD_TOO_LARGE
        }
        if (commandCount(record.payload) > MAX_COMMAND_COUNT) {
            return ReplayPolicyViolation.TOO_MANY_COMMANDS
        }
        return null
    }

    fun requireValid(record: CompletedReplayRecord) {
        val violation = validate(record)
        require(violation == null) { "Replay rejected by shared policy: $violation" }
    }

    internal fun retain(metadata: Collection<CompletedReplayMetadata>): List<CompletedReplayMetadata> =
        metadata
            .sortedWith(
                compareByDescending<CompletedReplayMetadata> { it.savedAtEpochMillis }
                    .thenByDescending { it.id },
            )
            .distinctBy { it.id }
            .take(MAX_REPLAY_COUNT)

    private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size

    private const val REPLAY_HEADER_LINES = 3
}

enum class ReplayPolicyViolation {
    UNSUPPORTED_SCHEMA,
    INVALID_REPLAY_ID,
    INVALID_SCENARIO_ID,
    INVALID_TERMINAL_HASH,
    INVALID_TERMINAL_METADATA,
    PAYLOAD_TOO_LARGE,
    TOO_MANY_COMMANDS,
}

/**
 * Bounded DataStore representation. Payload bytes are kept in [ReplayPayloadStore] and loaded only
 * when a replay is opened or shared.
 */
data class CompletedReplayMetadata(
    val schemaVersion: Int,
    val id: String,
    val scenarioId: String,
    val savedAtEpochMillis: Long,
    val terminalTick: Long,
    val finalScore: Int,
    val terminalHash: String,
    val payloadFileId: String,
    val payloadByteCount: Int,
    val payloadSha256: String,
) {
    fun asSummary(): CompletedReplayRecord = CompletedReplayRecord(
        schemaVersion = schemaVersion,
        id = id,
        scenarioId = scenarioId,
        savedAtEpochMillis = savedAtEpochMillis,
        terminalTick = terminalTick,
        finalScore = finalScore,
        terminalHash = terminalHash,
        payload = "",
        payloadFileId = payloadFileId,
        payloadByteCount = payloadByteCount,
        payloadSha256 = payloadSha256,
    )
}

sealed interface ReplayLoadResult {
    data class Loaded(val replay: CompletedReplayRecord) : ReplayLoadResult
    data object NotFound : ReplayLoadResult
    data class Corrupt(val replayId: String, val reason: ReplayCorruptionReason) : ReplayLoadResult
    data class StorageFailure(val replayId: String, val retryable: Boolean) : ReplayLoadResult
}

enum class ReplayCorruptionReason {
    MISSING_FILE,
    INVALID_SIZE,
    CHECKSUM_MISMATCH,
    INVALID_UTF8,
    POLICY_REJECTED,
}

data class ReplayMaintenanceResult(
    val migratedLegacyRecords: Int = 0,
    val removedMissingRecords: Int = 0,
    val removedOrphanFiles: Int = 0,
    val migrationPending: Boolean = false,
    val failure: ReplayStorageFailure? = null,
)

enum class ReplayStorageFailure {
    IO_RETRYABLE,
    PERMISSION_PERMANENT,
    STORAGE_NOT_CONFIGURED,
}

/**
 * Storage boundary used by production files and deterministic failure-injection tests.
 */
interface ReplayPayloadStore {
    suspend fun writeAtomically(fileId: String, bytes: ByteArray)
    suspend fun read(fileId: String): ByteArray?
    suspend fun delete(fileId: String): Boolean
    suspend fun existingFileIds(): Set<String>
}

/**
 * App-private replay payload directory. Current backup rules exclude this directory from cloud
 * backup and device transfer; transferred DataStore metadata is therefore pruned as missing.
 */
class FileReplayPayloadStore(
    private val directory: File,
    private val moveIntoPlace: (Path, Path) -> Unit = ::moveAtomically,
) : ReplayPayloadStore {
    override suspend fun writeAtomically(fileId: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            requireValidFileId(fileId)
            require(bytes.size <= ReplayPolicy.MAX_ENCODED_BYTES) {
                "Replay payload exceeds ${ReplayPolicy.MAX_ENCODED_BYTES} bytes"
            }
            ensureDirectory()
            val target = payloadFile(fileId)
            val temporary = File(directory, ".$fileId-${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                moveIntoPlace(temporary.toPath(), target.toPath())
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }

    override suspend fun read(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        requireValidFileId(fileId)
        val file = payloadFile(fileId)
        if (!file.isFile) return@withContext null
        val length = file.length()
        if (length < 0 || length > ReplayPolicy.MAX_ENCODED_BYTES) {
            throw ReplayPayloadCorruptException(ReplayCorruptionReason.INVALID_SIZE)
        }
        file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream(length.toInt().coerceAtLeast(32))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > ReplayPolicy.MAX_ENCODED_BYTES) {
                    throw ReplayPayloadCorruptException(ReplayCorruptionReason.INVALID_SIZE)
                }
                output.write(buffer, 0, read)
            }
            val bytes = output.toByteArray()
            if (bytes.size != length.toInt()) {
                throw ReplayPayloadCorruptException(ReplayCorruptionReason.INVALID_SIZE)
            }
            bytes
        }
    }

    override suspend fun delete(fileId: String): Boolean = withContext(Dispatchers.IO) {
        requireValidFileId(fileId)
        val file = payloadFile(fileId)
        !file.exists() || file.delete()
    }

    override suspend fun existingFileIds(): Set<String> = withContext(Dispatchers.IO) {
        if (!directory.exists()) return@withContext emptySet()
        if (!directory.isDirectory) throw IOException("Replay path is not a directory")
        directory.listFiles().orEmpty().mapNotNullTo(mutableSetOf()) { file ->
            when {
                file.name.endsWith(TEMP_SUFFIX) || file.name.startsWith(".") -> {
                    file.delete()
                    null
                }
                file.isFile && file.name.endsWith(PAYLOAD_SUFFIX) -> {
                    file.name.removeSuffix(PAYLOAD_SUFFIX).takeIf(FILE_ID_PATTERN::matches)
                }
                else -> null
            }
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create replay directory")
        }
        if (!directory.isDirectory) throw IOException("Replay path is not a directory")
    }

    private fun payloadFile(fileId: String): File = File(directory, "$fileId$PAYLOAD_SUFFIX")

    private fun requireValidFileId(fileId: String) {
        require(FILE_ID_PATTERN.matches(fileId)) { "Invalid replay file identifier" }
    }

    private companion object {
        const val PAYLOAD_SUFFIX = ".replay"
        const val TEMP_SUFFIX = ".tmp"
        val FILE_ID_PATTERN = Regex("[a-f0-9]{64}")

        fun moveAtomically(source: Path, target: Path) {
            try {
                Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

internal class ReplayPayloadCorruptException(
    val reason: ReplayCorruptionReason,
) : IOException("Replay payload is corrupt: $reason")

internal object CompletedReplayMetadataCodec {
    private const val CODEC_VERSION = 1
    private const val MAX_METADATA_CHARS = 32_000
    private val fileIdPattern = Regex("[a-f0-9]{64}")
    private val checksumPattern = Regex("[a-f0-9]{64}")

    fun encode(records: List<CompletedReplayMetadata>): String =
        ReplayPolicy.retain(records).joinToString("\n") { replay ->
            listOf(
                CODEC_VERSION,
                replay.schemaVersion,
                TextCodec.encode(replay.id),
                TextCodec.encode(replay.scenarioId),
                replay.savedAtEpochMillis,
                replay.terminalTick,
                replay.finalScore,
                TextCodec.encode(replay.terminalHash),
                replay.payloadFileId,
                replay.payloadByteCount,
                replay.payloadSha256,
            ).joinToString(":")
        }.also { encoded ->
            require(encoded.length <= MAX_METADATA_CHARS) { "Replay metadata is not bounded" }
        }

    fun decode(encoded: String?): List<CompletedReplayMetadata> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_METADATA_CHARS) return emptyList()
        return encoded.lineSequence()
            .take(ReplayPolicy.MAX_REPLAY_COUNT)
            .mapNotNull(::decodeLine)
            .distinctBy { it.id }
            .toList()
    }

    private fun decodeLine(line: String): CompletedReplayMetadata? = runCatching {
        val parts = line.split(':')
        require(parts.size == 11 && parts[0].toInt() == CODEC_VERSION)
        val metadata = CompletedReplayMetadata(
            schemaVersion = parts[1].toInt(),
            id = checkNotNull(TextCodec.decode(parts[2])),
            scenarioId = checkNotNull(TextCodec.decode(parts[3])),
            savedAtEpochMillis = parts[4].toLong(),
            terminalTick = parts[5].toLong(),
            finalScore = parts[6].toInt(),
            terminalHash = checkNotNull(TextCodec.decode(parts[7])),
            payloadFileId = parts[8],
            payloadByteCount = parts[9].toInt(),
            payloadSha256 = parts[10],
        )
        require(metadata.payloadFileId.matches(fileIdPattern))
        require(metadata.payloadSha256.matches(checksumPattern))
        require(metadata.payloadByteCount in 0..ReplayPolicy.MAX_ENCODED_BYTES)
        val policyProbe = metadata.asSummary()
        require(ReplayPolicy.validate(policyProbe) == null)
        metadata
    }.getOrNull()
}

internal fun replayMetadataFor(record: CompletedReplayRecord): CompletedReplayMetadata {
    ReplayPolicy.requireValid(record)
    val bytes = record.payload.toByteArray(StandardCharsets.UTF_8)
    val checksum = sha256(bytes)
    val fileId = sha256("${record.id}\u0000$checksum".toByteArray(StandardCharsets.UTF_8))
    return CompletedReplayMetadata(
        schemaVersion = record.schemaVersion,
        id = record.id,
        scenarioId = record.scenarioId,
        savedAtEpochMillis = record.savedAtEpochMillis,
        terminalTick = record.terminalTick,
        finalScore = record.finalScore,
        terminalHash = record.terminalHash,
        payloadFileId = fileId,
        payloadByteCount = bytes.size,
        payloadSha256 = checksum,
    )
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
        "%02x".format(byte)
    }

internal fun decodeUtf8Strict(bytes: ByteArray): String? = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: Exception) {
    null
}

internal inline fun <T> runReplayStorageCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
