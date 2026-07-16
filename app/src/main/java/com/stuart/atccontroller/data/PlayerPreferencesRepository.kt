package com.stuart.atccontroller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "atc_player_data"

val Context.atcControllerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME,
)

data class PlayerSettings(
    val musicVolume: Float = 0.65f,
    val effectsVolume: Float = 0.8f,
    /** Last audible values let mute/unmute survive process death without losing slider choices. */
    val lastMusicVolume: Float = 0.65f,
    val lastEffectsVolume: Float = 0.8f,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val trailsEnabled: Boolean = true,
    val highContrast: Boolean = false,
    val labelScale: Float = 1f,
    val labelDeclutteringEnabled: Boolean = true,
    val pauseOnFocusLoss: Boolean = true,
)

data class PlayerProgress(
    val missionStars: Map<String, Int> = emptyMap(),
    val missionBestScores: Map<String, Int> = emptyMap(),
    val unlockedMissionIds: Set<String> = setOf(ManchesterContent.FIRST_MISSION_ID),
    val tutorialCompleted: Boolean = false,
    val endlessHighScore: Int = 0,
) {
    val totalStars: Int get() = missionStars.values.sum()
    val completedMissionCount: Int
        get() = missionStars.keys.count { it in ManchesterContent.missionIds }
}

/**
 * The payload is an opaque, versioned engine snapshot. Keeping persistence unaware of engine
 * internals allows the simulation to evolve its own compact codec and reject old versions safely.
 */
data class ActiveSessionRecord(
    val schemaVersion: Int,
    val scenarioId: String,
    val savedAtEpochMillis: Long,
    val payload: String,
)

data class TrainingState(
    val schemaVersion: Int = 1,
    val activeLessonId: String? = null,
    val activeStep: Int = 0,
    val completedLessonIds: Set<String> = emptySet(),
)

data class CompletedReplayRecord(
    val schemaVersion: Int,
    val id: String,
    val scenarioId: String,
    val savedAtEpochMillis: Long,
    val terminalTick: Long,
    val finalScore: Int,
    val terminalHash: String,
    val payload: String,
)

data class PlayerData(
    val settings: PlayerSettings = PlayerSettings(),
    val progress: PlayerProgress = PlayerProgress(),
    val activeSession: ActiveSessionRecord? = null,
    val trainingState: TrainingState = TrainingState(),
    val completedReplays: List<CompletedReplayRecord> = emptyList(),
)

class PlayerPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val playerData: Flow<PlayerData> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decodePlayerData)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val settings: Flow<PlayerSettings> = playerData
        .map { data: PlayerData -> data.settings }
        .distinctUntilChanged()

    val progress: Flow<PlayerProgress> = playerData
        .map { data: PlayerData -> data.progress }
        .distinctUntilChanged()

    val activeSession: Flow<ActiveSessionRecord?> = playerData
        .map { data: PlayerData -> data.activeSession }
        .distinctUntilChanged()

    suspend fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        dataStore.edit { preferences ->
            val updated = sanitizeSettings(transform(decodeSettings(preferences)))
            preferences[Keys.MUSIC_VOLUME] = updated.musicVolume
            preferences[Keys.EFFECTS_VOLUME] = updated.effectsVolume
            preferences[Keys.LAST_MUSIC_VOLUME] = updated.lastMusicVolume
            preferences[Keys.LAST_EFFECTS_VOLUME] = updated.lastEffectsVolume
            preferences[Keys.HAPTICS] = updated.hapticsEnabled
            preferences[Keys.REDUCED_MOTION] = updated.reducedMotion
            preferences[Keys.TRAILS] = updated.trailsEnabled
            preferences[Keys.HIGH_CONTRAST] = updated.highContrast
            preferences[Keys.LABEL_SCALE] = updated.labelScale
            preferences[Keys.LABEL_DECLUTTERING] = updated.labelDeclutteringEnabled
            preferences[Keys.PAUSE_ON_FOCUS_LOSS] = updated.pauseOnFocusLoss
        }
    }

    suspend fun setSettings(settings: PlayerSettings) = updateSettings { settings }

    /** Stores the best result and unlocks the next authored mission after any successful result. */
    suspend fun recordMissionResult(missionId: String, stars: Int, score: Int) {
        require(stars in 0..3) { "Stars must be from zero to three" }
        require(score >= 0) { "Score must not be negative" }
        require(missionId in ManchesterContent.missionIds) { "Unknown authored mission $missionId" }

        dataStore.edit { preferences ->
            val existingStars = MissionStarsCodec.decode(preferences[Keys.MISSION_STARS]).toMutableMap()
            existingStars[missionId] = maxOf(existingStars[missionId] ?: 0, stars)
            preferences[Keys.MISSION_STARS] = MissionStarsCodec.encode(existingStars)

            val existingResults = MissionResultsCodec.decode(preferences[Keys.MISSION_RESULTS]).toMutableMap()
            val previous = existingResults[missionId]
            existingResults[missionId] = MissionResultRecord(
                stars = maxOf(previous?.stars ?: 0, existingStars[missionId] ?: 0, stars),
                bestScore = maxOf(previous?.bestScore ?: 0, score),
            )
            preferences[Keys.MISSION_RESULTS] = MissionResultsCodec.encode(existingResults)

            val unlocked = preferences[Keys.UNLOCKED_MISSIONS]
                .orEmpty()
                .filterTo(mutableSetOf()) { it in ManchesterContent.missionIds }
            preferences[Keys.UNLOCKED_MISSIONS] = unlockedAfterMissionCompletion(
                currentlyUnlocked = unlocked,
                completedMissionId = missionId,
            )
        }
    }

    suspend fun setTutorialCompleted(completed: Boolean = true) {
        dataStore.edit { it[Keys.TUTORIAL_COMPLETED] = completed }
    }

    suspend fun recordEndlessHighScore(score: Int) {
        require(score >= 0) { "Score must not be negative" }
        dataStore.edit { preferences ->
            preferences[Keys.ENDLESS_HIGH_SCORE] = maxOf(preferences[Keys.ENDLESS_HIGH_SCORE] ?: 0, score)
        }
    }

    suspend fun saveActiveSession(session: ActiveSessionRecord) {
        require(session.schemaVersion > 0) { "Session schema version must be positive" }
        require(session.scenarioId.isNotBlank()) { "Session scenario id must not be blank" }
        require(session.savedAtEpochMillis >= 0) { "Session save time must not be negative" }
        dataStore.edit { it[Keys.ACTIVE_SESSION] = SessionSnapshotCodec.encode(session) }
    }

    suspend fun clearActiveSession() {
        dataStore.edit { it.remove(Keys.ACTIVE_SESSION) }
    }

    suspend fun saveTrainingState(state: TrainingState) {
        require(state.schemaVersion == 1 && state.activeStep >= 0)
        dataStore.edit { it[Keys.TRAINING_STATE] = TrainingStateCodec.encode(state) }
    }

    suspend fun saveCompletedReplay(replay: CompletedReplayRecord) {
        require(replay.schemaVersion > 0 && replay.id.isNotBlank() && replay.scenarioId.isNotBlank())
        require(replay.savedAtEpochMillis >= 0 && replay.terminalTick >= 0 && replay.finalScore >= 0)
        require(replay.payload.length <= MAX_COMPLETED_REPLAY_PAYLOAD)
        dataStore.edit { preferences ->
            val retained = CompletedReplayCodec.decode(preferences[Keys.COMPLETED_REPLAYS])
                .filterNot { it.id == replay.id }
            preferences[Keys.COMPLETED_REPLAYS] = CompletedReplayCodec.encode(
                (listOf(replay) + retained).take(MAX_COMPLETED_REPLAYS),
            )
        }
    }

    suspend fun deleteCompletedReplay(id: String) {
        dataStore.edit { preferences ->
            preferences[Keys.COMPLETED_REPLAYS] = CompletedReplayCodec.encode(
                CompletedReplayCodec.decode(preferences[Keys.COMPLETED_REPLAYS]).filterNot { it.id == id },
            )
        }
    }

    /** Repairs legacy saves whose completion records and explicit unlock set drifted apart. */
    suspend fun reconcileUnlocks() {
        dataStore.edit { preferences ->
            val completed = mergeMissionResults(
                MissionStarsCodec.decode(preferences[Keys.MISSION_STARS]),
                MissionResultsCodec.decode(preferences[Keys.MISSION_RESULTS]),
            ).stars.keys
            val stored = preferences[Keys.UNLOCKED_MISSIONS].orEmpty()
            val reconciled = reconciledMissionUnlocks(stored, completed)
            if (reconciled != stored) preferences[Keys.UNLOCKED_MISSIONS] = reconciled
        }
    }

    /** Clears progression and resumable play while preserving accessibility and audio settings. */
    suspend fun resetProgress() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.MISSION_STARS)
            preferences.remove(Keys.MISSION_RESULTS)
            preferences.remove(Keys.UNLOCKED_MISSIONS)
            preferences.remove(Keys.TUTORIAL_COMPLETED)
            preferences.remove(Keys.ENDLESS_HIGH_SCORE)
            preferences.remove(Keys.ACTIVE_SESSION)
            preferences.remove(Keys.TRAINING_STATE)
            preferences.remove(Keys.COMPLETED_REPLAYS)
        }
    }

    private fun decodePlayerData(preferences: Preferences): PlayerData {
        val legacyStars = MissionStarsCodec.decode(preferences[Keys.MISSION_STARS])
        val results = MissionResultsCodec.decode(preferences[Keys.MISSION_RESULTS])
        val mergedResults = mergeMissionResults(legacyStars, results)
        return PlayerData(
            settings = decodeSettings(preferences),
            progress = PlayerProgress(
                missionStars = mergedResults.stars,
                missionBestScores = mergedResults.bestScores,
                unlockedMissionIds = reconciledMissionUnlocks(
                    preferences[Keys.UNLOCKED_MISSIONS].orEmpty(),
                    mergedResults.stars.keys,
                ),
                tutorialCompleted = preferences[Keys.TUTORIAL_COMPLETED] ?: false,
                endlessHighScore = (preferences[Keys.ENDLESS_HIGH_SCORE] ?: 0).coerceAtLeast(0),
            ),
            activeSession = SessionSnapshotCodec.decode(preferences[Keys.ACTIVE_SESSION]),
            trainingState = TrainingStateCodec.decode(preferences[Keys.TRAINING_STATE]),
            completedReplays = CompletedReplayCodec.decode(preferences[Keys.COMPLETED_REPLAYS]),
        )
    }

    private fun decodeSettings(preferences: Preferences): PlayerSettings {
        val defaults = PlayerSettings()
        val musicVolume = preferences[Keys.MUSIC_VOLUME] ?: defaults.musicVolume
        val effectsVolume = preferences[Keys.EFFECTS_VOLUME] ?: defaults.effectsVolume
        return sanitizeSettings(
            PlayerSettings(
                musicVolume = musicVolume,
                effectsVolume = effectsVolume,
                lastMusicVolume = preferences[Keys.LAST_MUSIC_VOLUME]
                    ?: musicVolume.takeIf { it.isFinite() && it > 0f }
                    ?: defaults.lastMusicVolume,
                lastEffectsVolume = preferences[Keys.LAST_EFFECTS_VOLUME]
                    ?: effectsVolume.takeIf { it.isFinite() && it > 0f }
                    ?: defaults.lastEffectsVolume,
                hapticsEnabled = preferences[Keys.HAPTICS] ?: true,
                reducedMotion = preferences[Keys.REDUCED_MOTION] ?: false,
                trailsEnabled = preferences[Keys.TRAILS] ?: true,
                highContrast = preferences[Keys.HIGH_CONTRAST] ?: false,
                labelScale = preferences[Keys.LABEL_SCALE] ?: 1f,
                labelDeclutteringEnabled = preferences[Keys.LABEL_DECLUTTERING] ?: true,
                pauseOnFocusLoss = preferences[Keys.PAUSE_ON_FOCUS_LOSS] ?: true,
            ),
        )
    }

    private fun sanitizeSettings(settings: PlayerSettings): PlayerSettings {
        val defaults = PlayerSettings()
        return settings.copy(
            musicVolume = settings.musicVolume.finiteOr(defaults.musicVolume).coerceIn(0f, 1f),
            effectsVolume = settings.effectsVolume.finiteOr(defaults.effectsVolume).coerceIn(0f, 1f),
            lastMusicVolume = settings.lastMusicVolume.finiteOr(defaults.lastMusicVolume)
                .coerceIn(MIN_AUDIBLE_VOLUME, 1f),
            lastEffectsVolume = settings.lastEffectsVolume.finiteOr(defaults.lastEffectsVolume)
                .coerceIn(MIN_AUDIBLE_VOLUME, 1f),
            labelScale = settings.labelScale.finiteOr(defaults.labelScale).coerceIn(0.8f, 1.4f),
        )
    }

    private fun Float.finiteOr(default: Float): Float = if (isFinite()) this else default

    private object Keys {
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val EFFECTS_VOLUME = floatPreferencesKey("effects_volume")
        val LAST_MUSIC_VOLUME = floatPreferencesKey("last_music_volume")
        val LAST_EFFECTS_VOLUME = floatPreferencesKey("last_effects_volume")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val TRAILS = booleanPreferencesKey("trails_enabled")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val LABEL_SCALE = floatPreferencesKey("label_scale")
        val LABEL_DECLUTTERING = booleanPreferencesKey("label_decluttering")
        val PAUSE_ON_FOCUS_LOSS = booleanPreferencesKey("pause_on_focus_loss")
        val MISSION_STARS = stringPreferencesKey("mission_stars_v1")
        val MISSION_RESULTS = stringPreferencesKey("mission_results_v2")
        val UNLOCKED_MISSIONS = stringSetPreferencesKey("unlocked_missions")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        val ENDLESS_HIGH_SCORE = intPreferencesKey("endless_high_score")
        val ACTIVE_SESSION = stringPreferencesKey("active_session_v1")
        val TRAINING_STATE = stringPreferencesKey("training_state_v1")
        val COMPLETED_REPLAYS = stringPreferencesKey("completed_replays_v1")
    }

    private companion object {
        const val MIN_AUDIBLE_VOLUME = 0.01f
        const val MAX_COMPLETED_REPLAYS = 5
        const val MAX_COMPLETED_REPLAY_PAYLOAD = 250_000
    }
}

internal fun unlockedAfterMissionCompletion(
    currentlyUnlocked: Set<String>,
    completedMissionId: String,
): Set<String> = buildSet {
    addAll(currentlyUnlocked.filter { it in ManchesterContent.missionIds })
    add(ManchesterContent.FIRST_MISSION_ID)
    ManchesterContent.nextMissionId(completedMissionId)?.let(::add)
}

internal fun reconciledMissionUnlocks(
    storedUnlocks: Set<String>,
    completedMissionIds: Set<String>,
): Set<String> = buildSet {
    add(ManchesterContent.FIRST_MISSION_ID)
    addAll(storedUnlocks.filter { it in ManchesterContent.missionIds })
    completedMissionIds
        .filter { it in ManchesterContent.missionIds }
        .forEach { completed -> ManchesterContent.nextMissionId(completed)?.let(::add) }
}

internal object MissionStarsCodec {
    fun encode(stars: Map<String, Int>): String = stars
        .asSequence()
        .filter { (id, value) -> id.isNotBlank() && value in 0..3 }
        .sortedBy(Map.Entry<String, Int>::key)
        .joinToString(";") { (id, value) -> "${TextCodec.encode(id)}:$value" }

    fun decode(encoded: String?): Map<String, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        encoded.split(';').forEach { entry ->
            val separator = entry.lastIndexOf(':')
            if (separator <= 0) return@forEach
            val id = TextCodec.decode(entry.substring(0, separator)) ?: return@forEach
            val stars = entry.substring(separator + 1).toIntOrNull() ?: return@forEach
            if (id.isNotBlank() && stars in 0..3) result[id] = maxOf(result[id] ?: 0, stars)
        }
        return result
    }
}

internal data class MissionResultRecord(
    val stars: Int,
    val bestScore: Int?,
)

internal data class MergedMissionResults(
    val stars: Map<String, Int>,
    val bestScores: Map<String, Int>,
)

internal fun mergeMissionResults(
    legacyStars: Map<String, Int>,
    results: Map<String, MissionResultRecord>,
): MergedMissionResults {
    val knownIds = ManchesterContent.missionIds.toSet()
    val stars = buildMap {
        legacyStars.filterKeys { it in knownIds }.forEach { (id, value) -> put(id, value) }
        results.filterKeys { it in knownIds }.forEach { (id, result) ->
            put(id, maxOf(get(id) ?: 0, result.stars))
        }
    }
    val bestScores = results.mapNotNull { (id, result) ->
        result.bestScore?.takeIf { id in knownIds }?.let { id to it }
    }.toMap()
    return MergedMissionResults(stars, bestScores)
}

/** Version-two mission results. A blank score preserves honest legacy "not recorded" data. */
internal object MissionResultsCodec {
    fun encode(results: Map<String, MissionResultRecord>): String = results
        .asSequence()
        .filter { (id, result) ->
            id.isNotBlank() && result.stars in 0..3 &&
                (result.bestScore == null || result.bestScore >= 0)
        }
        .sortedBy(Map.Entry<String, MissionResultRecord>::key)
        .joinToString(";") { (id, result) ->
            "${TextCodec.encode(id)}:${result.stars}:${result.bestScore?.toString().orEmpty()}"
        }

    fun decode(encoded: String?): Map<String, MissionResultRecord> {
        if (encoded.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, MissionResultRecord>()
        encoded.split(';').forEach { entry ->
            val parts = entry.split(':', limit = 3)
            if (parts.size != 3) return@forEach
            val id = TextCodec.decode(parts[0])?.takeIf(String::isNotBlank) ?: return@forEach
            val stars = parts[1].toIntOrNull()?.takeIf { it in 0..3 } ?: return@forEach
            val score = when {
                parts[2].isBlank() -> null
                else -> parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
            }
            val previous = result[id]
            result[id] = MissionResultRecord(
                stars = maxOf(previous?.stars ?: 0, stars),
                bestScore = listOfNotNull(previous?.bestScore, score).maxOrNull(),
            )
        }
        return result
    }
}

internal object SessionSnapshotCodec {
    fun encode(session: ActiveSessionRecord): String = listOf(
        session.schemaVersion.toString(),
        TextCodec.encode(session.scenarioId),
        session.savedAtEpochMillis.toString(),
        TextCodec.encode(session.payload),
    ).joinToString(":")

    fun decode(encoded: String?): ActiveSessionRecord? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.split(':', limit = 4)
        if (parts.size != 4) return null
        val schemaVersion = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val scenarioId = TextCodec.decode(parts[1])?.takeIf(String::isNotBlank) ?: return null
        val savedAt = parts[2].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val payload = TextCodec.decode(parts[3]) ?: return null
        return ActiveSessionRecord(schemaVersion, scenarioId, savedAt, payload)
    }
}

internal object TrainingStateCodec {
    fun encode(state: TrainingState): String = listOf(
        state.schemaVersion.toString(),
        TextCodec.encode(state.activeLessonId.orEmpty()),
        state.activeStep.toString(),
        state.completedLessonIds.filter(String::isNotBlank).sorted()
            .joinToString(",", transform = TextCodec::encode),
    ).joinToString(":")

    fun decode(encoded: String?): TrainingState {
        if (encoded.isNullOrBlank()) return TrainingState()
        return runCatching {
            val parts = encoded.split(':', limit = 4)
            require(parts.size == 4 && parts[0].toInt() == 1)
            TrainingState(
                schemaVersion = 1,
                activeLessonId = TextCodec.decode(parts[1])?.takeIf(String::isNotBlank),
                activeStep = parts[2].toInt().also { require(it in 0..100) },
                completedLessonIds = if (parts[3].isBlank()) emptySet() else parts[3]
                    .split(',').mapNotNull(TextCodec::decode).filter(String::isNotBlank).take(100).toSet(),
            )
        }.getOrDefault(TrainingState())
    }
}

internal object CompletedReplayCodec {
    private const val MAX_RECORDS = 5
    private const val MAX_PAYLOAD = 250_000

    fun encode(records: List<CompletedReplayRecord>): String = records.take(MAX_RECORDS).joinToString("\n") { replay ->
        listOf(
            replay.schemaVersion.toString(),
            TextCodec.encode(replay.id),
            TextCodec.encode(replay.scenarioId),
            replay.savedAtEpochMillis.toString(),
            replay.terminalTick.toString(),
            replay.finalScore.toString(),
            TextCodec.encode(replay.terminalHash),
            TextCodec.encode(replay.payload.take(MAX_PAYLOAD)),
        ).joinToString(":")
    }

    fun decode(encoded: String?): List<CompletedReplayRecord> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_PAYLOAD * MAX_RECORDS * 2) return emptyList()
        return encoded.lineSequence().take(MAX_RECORDS).mapNotNull { line ->
            runCatching {
                val parts = line.split(':', limit = 8)
                require(parts.size == 8)
                CompletedReplayRecord(
                    schemaVersion = parts[0].toInt().also { require(it > 0) },
                    id = checkNotNull(TextCodec.decode(parts[1])).also { require(it.isNotBlank()) },
                    scenarioId = checkNotNull(TextCodec.decode(parts[2])).also { require(it.isNotBlank()) },
                    savedAtEpochMillis = parts[3].toLong().also { require(it >= 0) },
                    terminalTick = parts[4].toLong().also { require(it >= 0) },
                    finalScore = parts[5].toInt().also { require(it >= 0) },
                    terminalHash = checkNotNull(TextCodec.decode(parts[6])),
                    payload = checkNotNull(TextCodec.decode(parts[7])).also { require(it.length <= MAX_PAYLOAD) },
                )
            }.getOrNull()
        }.toList()
    }
}

private object TextCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    fun decode(value: String): String? = try {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
