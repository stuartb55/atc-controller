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
import java.time.LocalDate
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
    val routeSnappingEnabled: Boolean = true,
    val pauseOnFocusLoss: Boolean = true,
)

data class PlayerProgress(
    val missionStars: Map<String, Int> = emptyMap(),
    val missionBestScores: Map<String, Int> = emptyMap(),
    val unlockedMissionIds: Set<String> = ContentRegistry.firstMissionIds,
    val tutorialCompleted: Boolean = false,
    /** Legacy Manchester value retained as a source-compatible migration field. */
    val endlessHighScore: Int = 0,
    val endlessHighScores: Map<String, Int> = emptyMap(),
) {
    val totalStars: Int get() = missionStars.values.sum()
    val completedMissionCount: Int
        get() = missionStars.keys.count { it in ContentRegistry.missionIds }

    fun endlessHighScoreFor(contentPackId: String): Int = maxOf(
        endlessHighScores[contentPackId] ?: 0,
        endlessHighScore.takeIf { contentPackId == ContentRegistry.DEFAULT_PACK_ID } ?: 0,
    )
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

data class MasteryRecord(
    val attempts: Int = 0,
    val totalContribution: Int = 0,
    val contributingResultIds: List<String> = emptyList(),
) {
    val level: Int get() = if (attempts == 0) 0 else (totalContribution / attempts).coerceIn(0, 100)
}

data class ControllerServiceRecord(
    val schemaVersion: Int = 1,
    val totalSafeMovements: Int = 0,
    val currentSafeStreak: Int = 0,
    val bestSafeStreak: Int = 0,
    val bestScoresByMission: Map<String, Int> = emptyMap(),
    val bestCompletionSecondsByMission: Map<String, Int> = emptyMap(),
    val masteryByFocus: Map<TutorialFocus, MasteryRecord> = emptyMap(),
    val achievementIds: Set<String> = emptySet(),
    /** Bounded idempotency ledger for terminal results retried after process death. */
    val processedResultIds: List<String> = emptyList(),
) {
    fun masteryFor(focus: TutorialFocus): MasteryRecord =
        masteryByFocus[focus] ?: MasteryRecord()
}

data class ValidatedMissionResult(
    val resultId: String,
    val missionId: String,
    val focus: TutorialFocus,
    val stars: Int,
    val score: Int,
    val completionSeconds: Int,
    val safeMovements: Int,
    val strikes: Int,
    val departures: Int,
    val missedExits: Int,
    val routeEfficiencyPercent: Int,
) {
    init {
        require(resultId.isNotBlank() && missionId.isNotBlank())
        require(stars in 0..3 && score >= 0 && completionSeconds >= 0)
        require(safeMovements >= 0 && strikes >= 0 && departures >= 0 && missedExits >= 0)
        require(routeEfficiencyPercent in 0..100)
    }
}

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

data class PracticeResultRecord(
    val resultId: String,
    val configurationIdentity: String,
    val score: Int,
    val stars: Int,
    val completedAtEpochMillis: Long,
    val rankedPreset: Boolean,
)

data class DailyResultEntry(
    val localDateIso: String,
    val configurationIdentity: String,
    val bestScore: Int,
    val firstResultId: String,
    val completedAttempts: Int = 1,
)

data class DailyServiceRecord(
    val schemaVersion: Int = 1,
    val entries: Map<String, DailyResultEntry> = emptyMap(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val processedResultIds: List<String> = emptyList(),
)

enum class EndlessMilestoneChoice { AWAITING, CONTINUE_PENDING, CASH_OUT_PENDING }

data class EndlessMilestoneRecord(
    val schemaVersion: Int = 2,
    val contentPackId: String = ContentRegistry.DEFAULT_PACK_ID,
    val seed: Long,
    val completedStage: Int,
    val stageScore: Int,
    val cumulativeScore: Int,
    val choice: EndlessMilestoneChoice = EndlessMilestoneChoice.AWAITING,
) {
    init {
        require(schemaVersion in 1..2 && completedStage >= 1)
        require(ContentRegistry.pack(contentPackId) != null)
        require(stageScore >= 0 && cumulativeScore >= stageScore)
    }
}

data class PlayerData(
    val settings: PlayerSettings = PlayerSettings(),
    val progress: PlayerProgress = PlayerProgress(),
    val activeSession: ActiveSessionRecord? = null,
    val trainingState: TrainingState = TrainingState(),
    val completedReplays: List<CompletedReplayRecord> = emptyList(),
    val serviceRecord: ControllerServiceRecord = ControllerServiceRecord(),
    val practiceResults: List<PracticeResultRecord> = emptyList(),
    val dailyRecord: DailyServiceRecord = DailyServiceRecord(),
    val endlessMilestone: EndlessMilestoneRecord? = null,
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
            preferences[Keys.ROUTE_SNAPPING] = updated.routeSnappingEnabled
            preferences[Keys.PAUSE_ON_FOCUS_LOSS] = updated.pauseOnFocusLoss
        }
    }

    suspend fun setSettings(settings: PlayerSettings) = updateSettings { settings }

    /** Stores the best result and unlocks the next authored mission after any successful result. */
    suspend fun recordMissionResult(missionId: String, stars: Int, score: Int) {
        require(stars in 0..3) { "Stars must be from zero to three" }
        require(score >= 0) { "Score must not be negative" }
        require(missionId in ContentRegistry.missionIds) { "Unknown authored mission $missionId" }

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
                .filterTo(mutableSetOf()) { it in ContentRegistry.missionIds }
            preferences[Keys.UNLOCKED_MISSIONS] = unlockedAfterMissionCompletion(
                currentlyUnlocked = unlocked,
                completedMissionId = missionId,
            )
        }
    }

    /** Atomically updates ranked progression and the idempotent controller service record. */
    suspend fun recordValidatedMissionResult(result: ValidatedMissionResult) {
        require(result.missionId in ContentRegistry.missionIds)
        dataStore.edit { preferences ->
            val service = ControllerServiceRecordCodec.decode(preferences[Keys.SERVICE_RECORD])
            if (result.resultId in service.processedResultIds) return@edit

            val existingStars = MissionStarsCodec.decode(preferences[Keys.MISSION_STARS]).toMutableMap()
            existingStars[result.missionId] = maxOf(existingStars[result.missionId] ?: 0, result.stars)
            preferences[Keys.MISSION_STARS] = MissionStarsCodec.encode(existingStars)
            val existingResults = MissionResultsCodec.decode(preferences[Keys.MISSION_RESULTS]).toMutableMap()
            val previous = existingResults[result.missionId]
            existingResults[result.missionId] = MissionResultRecord(
                stars = maxOf(previous?.stars ?: 0, result.stars),
                bestScore = maxOf(previous?.bestScore ?: 0, result.score),
            )
            preferences[Keys.MISSION_RESULTS] = MissionResultsCodec.encode(existingResults)
            preferences[Keys.UNLOCKED_MISSIONS] = unlockedAfterMissionCompletion(
                preferences[Keys.UNLOCKED_MISSIONS].orEmpty(),
                result.missionId,
            )

            preferences[Keys.SERVICE_RECORD] = ControllerServiceRecordCodec.encode(
                service.updatedWith(result),
            )
        }
    }

    suspend fun setTutorialCompleted(completed: Boolean = true) {
        dataStore.edit { it[Keys.TUTORIAL_COMPLETED] = completed }
    }

    suspend fun recordEndlessHighScore(contentPackId: String, score: Int) {
        require(ContentRegistry.pack(contentPackId) != null) { "Unknown content pack" }
        require(score >= 0) { "Score must not be negative" }
        dataStore.edit { preferences ->
            val scores = EndlessHighScoreCodec.decode(
                preferences[Keys.ENDLESS_HIGH_SCORES],
                preferences[Keys.ENDLESS_HIGH_SCORE] ?: 0,
            ).toMutableMap()
            scores[contentPackId] = maxOf(scores[contentPackId] ?: 0, score)
            preferences[Keys.ENDLESS_HIGH_SCORES] = EndlessHighScoreCodec.encode(scores)
            if (contentPackId == ContentRegistry.DEFAULT_PACK_ID) {
                preferences[Keys.ENDLESS_HIGH_SCORE] = scores.getValue(contentPackId)
            }
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

    suspend fun savePracticeResult(result: PracticeResultRecord) {
        require(result.resultId.isNotBlank() && result.configurationIdentity.isNotBlank())
        require(result.score >= 0 && result.stars in 0..3 && result.completedAtEpochMillis >= 0)
        dataStore.edit { preferences ->
            val retained = PracticeResultCodec.decode(preferences[Keys.PRACTICE_RESULTS])
                .filterNot { it.resultId == result.resultId }
            preferences[Keys.PRACTICE_RESULTS] = PracticeResultCodec.encode(
                (listOf(result) + retained).take(20),
            )
        }
    }

    suspend fun recordDailyResult(
        localDate: LocalDate,
        configurationIdentity: String,
        resultId: String,
        score: Int,
    ) {
        require(configurationIdentity == DailyShift.identityFor(localDate))
        require(resultId.isNotBlank() && score >= 0)
        dataStore.edit { preferences ->
            val record = DailyServiceRecordCodec.decode(preferences[Keys.DAILY_RECORD])
            preferences[Keys.DAILY_RECORD] = DailyServiceRecordCodec.encode(
                record.updatedWith(localDate, configurationIdentity, resultId, score),
            )
        }
    }

    /** Milestone replaces the active stage atomically; career/settings/replays are untouched. */
    suspend fun saveEndlessMilestone(milestone: EndlessMilestoneRecord) {
        dataStore.edit { preferences ->
            preferences[Keys.ENDLESS_MILESTONE] = EndlessMilestoneCodec.encode(milestone)
            preferences.remove(Keys.ACTIVE_SESSION)
        }
    }

    suspend fun clearEndlessMilestone() {
        dataStore.edit { it.remove(Keys.ENDLESS_MILESTONE) }
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
            preferences.remove(Keys.ENDLESS_HIGH_SCORES)
            preferences.remove(Keys.ACTIVE_SESSION)
            preferences.remove(Keys.TRAINING_STATE)
            preferences.remove(Keys.COMPLETED_REPLAYS)
            preferences.remove(Keys.SERVICE_RECORD)
            preferences.remove(Keys.PRACTICE_RESULTS)
            preferences.remove(Keys.DAILY_RECORD)
            preferences.remove(Keys.ENDLESS_MILESTONE)
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
                endlessHighScores = EndlessHighScoreCodec.decode(
                    preferences[Keys.ENDLESS_HIGH_SCORES],
                    preferences[Keys.ENDLESS_HIGH_SCORE] ?: 0,
                ),
            ),
            activeSession = SessionSnapshotCodec.decode(preferences[Keys.ACTIVE_SESSION]),
            trainingState = TrainingStateCodec.decode(preferences[Keys.TRAINING_STATE]),
            completedReplays = CompletedReplayCodec.decode(preferences[Keys.COMPLETED_REPLAYS]),
            serviceRecord = ControllerServiceRecordCodec.decode(preferences[Keys.SERVICE_RECORD]),
            practiceResults = PracticeResultCodec.decode(preferences[Keys.PRACTICE_RESULTS]),
            dailyRecord = DailyServiceRecordCodec.decode(preferences[Keys.DAILY_RECORD]),
            endlessMilestone = EndlessMilestoneCodec.decode(preferences[Keys.ENDLESS_MILESTONE]),
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
                routeSnappingEnabled = preferences[Keys.ROUTE_SNAPPING] ?: true,
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
        val ROUTE_SNAPPING = booleanPreferencesKey("route_snapping")
        val PAUSE_ON_FOCUS_LOSS = booleanPreferencesKey("pause_on_focus_loss")
        val MISSION_STARS = stringPreferencesKey("mission_stars_v1")
        val MISSION_RESULTS = stringPreferencesKey("mission_results_v2")
        val UNLOCKED_MISSIONS = stringSetPreferencesKey("unlocked_missions")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        val ENDLESS_HIGH_SCORE = intPreferencesKey("endless_high_score")
        val ENDLESS_HIGH_SCORES = stringPreferencesKey("endless_high_scores_v2")
        val ACTIVE_SESSION = stringPreferencesKey("active_session_v1")
        val TRAINING_STATE = stringPreferencesKey("training_state_v1")
        val COMPLETED_REPLAYS = stringPreferencesKey("completed_replays_v1")
        val SERVICE_RECORD = stringPreferencesKey("controller_service_record_v1")
        val PRACTICE_RESULTS = stringPreferencesKey("practice_results_v1")
        val DAILY_RECORD = stringPreferencesKey("daily_service_record_v1")
        val ENDLESS_MILESTONE = stringPreferencesKey("endless_milestone_v1")
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
    addAll(currentlyUnlocked.filter { it in ContentRegistry.missionIds })
    addAll(ContentRegistry.firstMissionIds)
    ContentRegistry.nextMissionId(completedMissionId)?.let(::add)
}

internal fun reconciledMissionUnlocks(
    storedUnlocks: Set<String>,
    completedMissionIds: Set<String>,
): Set<String> = buildSet {
    addAll(ContentRegistry.firstMissionIds)
    addAll(storedUnlocks.filter { it in ContentRegistry.missionIds })
    completedMissionIds
        .filter { it in ContentRegistry.missionIds }
        .forEach { completed -> ContentRegistry.nextMissionId(completed)?.let(::add) }
}

/** Namespaced endless records; the legacy scalar is migrated into the default pack on read. */
internal object EndlessHighScoreCodec {
    fun encode(scores: Map<String, Int>): String = scores.asSequence()
        .filter { (packId, score) -> ContentRegistry.pack(packId) != null && score >= 0 }
        .sortedBy(Map.Entry<String, Int>::key)
        .joinToString(";") { (packId, score) -> "${TextCodec.encode(packId)}:$score" }

    fun decode(encoded: String?, legacyManchesterScore: Int = 0): Map<String, Int> = buildMap {
        if (legacyManchesterScore > 0) {
            put(ContentRegistry.DEFAULT_PACK_ID, legacyManchesterScore)
        }
        encoded.orEmpty().split(';').forEach { entry ->
            val separator = entry.lastIndexOf(':')
            if (separator <= 0) return@forEach
            val packId = TextCodec.decode(entry.substring(0, separator))
                ?.takeIf { ContentRegistry.pack(it) != null } ?: return@forEach
            val score = entry.substring(separator + 1).toIntOrNull()
                ?.takeIf { it >= 0 } ?: return@forEach
            put(packId, maxOf(get(packId) ?: 0, score))
        }
    }
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
    val knownIds = ContentRegistry.missionIds.toSet()
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

internal object PracticeResultCodec {
    private const val MAX_RECORDS = 20
    private const val MAX_CHARS = 100_000

    fun encode(records: List<PracticeResultRecord>): String = records.take(MAX_RECORDS)
        .joinToString("\n") { result ->
            listOf(
                TextCodec.encode(result.resultId),
                TextCodec.encode(result.configurationIdentity),
                result.score,
                result.stars,
                result.completedAtEpochMillis,
                result.rankedPreset,
            ).joinToString(":")
        }

    fun decode(encoded: String?): List<PracticeResultRecord> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CHARS) return emptyList()
        return encoded.lineSequence().take(MAX_RECORDS).mapNotNull { line ->
            runCatching {
                val parts = line.split(':')
                require(parts.size == 6)
                PracticeResultRecord(
                    resultId = checkNotNull(TextCodec.decode(parts[0])).also { require(it.isNotBlank()) },
                    configurationIdentity = checkNotNull(TextCodec.decode(parts[1])).also {
                        require(it.isNotBlank() && ShiftConfigurationCodec.decode(it) != null)
                    },
                    score = parts[2].toInt().also { require(it >= 0) },
                    stars = parts[3].toInt().also { require(it in 0..3) },
                    completedAtEpochMillis = parts[4].toLong().also { require(it >= 0) },
                    rankedPreset = checkNotNull(parts[5].toBooleanStrictOrNull()),
                )
            }.getOrNull()
        }.distinctBy { it.resultId }.toList()
    }
}

internal fun DailyServiceRecord.updatedWith(
    localDate: LocalDate,
    configurationIdentity: String,
    resultId: String,
    score: Int,
): DailyServiceRecord {
    if (resultId in processedResultIds) return this
    val dateId = localDate.toString()
    val existing = entries[dateId]
    val updatedEntries = entries + (
        dateId to if (existing == null) {
            DailyResultEntry(dateId, configurationIdentity, score, resultId)
        } else {
            existing.copy(
                bestScore = maxOf(existing.bestScore, score),
                completedAttempts = existing.completedAttempts + 1,
            )
        }
        )
    val dates = updatedEntries.keys.map(LocalDate::parse).sorted()
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    dates.forEach { date ->
        run = if (previous?.plusDays(1) == date) run + 1 else 1
        best = maxOf(best, run)
        previous = date
    }
    var current = 0
    var cursor = dates.lastOrNull()
    while (cursor != null && cursor.toString() in updatedEntries) {
        current += 1
        cursor = cursor.minusDays(1)
    }
    return copy(
        entries = updatedEntries,
        currentStreak = current,
        bestStreak = best,
        processedResultIds = (listOf(resultId) + processedResultIds).distinct().take(200),
    )
}

internal object DailyServiceRecordCodec {
    private const val MAX_CHARS = 100_000

    fun encode(record: DailyServiceRecord): String = buildList {
        add("H|1|${record.currentStreak}|${record.bestStreak}")
        record.entries.toSortedMap().values.forEach { entry ->
            add(
                "D|${entry.localDateIso}|${TextCodec.encode(entry.configurationIdentity)}|" +
                    "${entry.bestScore}|${TextCodec.encode(entry.firstResultId)}|${entry.completedAttempts}",
            )
        }
        add("P|${record.processedResultIds.take(200).joinToString(",", transform = TextCodec::encode)}")
    }.joinToString("\n")

    fun decode(encoded: String?): DailyServiceRecord {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CHARS) return DailyServiceRecord()
        return runCatching {
            val lines = encoded.lineSequence().toList()
            val header = lines.first().split('|')
            require(header.size == 4 && header[0] == "H" && header[1] == "1")
            val storedCurrent = header[2].toInt().also { require(it >= 0) }
            val storedBest = header[3].toInt().also { require(it >= storedCurrent) }
            val entries = mutableMapOf<String, DailyResultEntry>()
            var processed = emptyList<String>()
            lines.drop(1).forEach { line ->
                val parts = line.split('|')
                when (parts.firstOrNull()) {
                    "D" -> if (parts.size == 6) {
                        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull()
                            ?: return@forEach
                        val identity = TextCodec.decode(parts[2])
                            ?.takeIf { it == DailyShift.identityFor(date) } ?: return@forEach
                        val score = parts[3].toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
                        val firstId = TextCodec.decode(parts[4])?.takeIf(String::isNotBlank)
                            ?: return@forEach
                        val attempts = parts[5].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                        entries[parts[1]] = DailyResultEntry(
                            parts[1], identity, score, firstId, attempts,
                        )
                    }
                    "P" -> processed = parts.getOrNull(1).orEmpty().split(',')
                        .filter(String::isNotBlank).take(200).mapNotNull(TextCodec::decode)
                        .filter(String::isNotBlank).distinct()
                }
            }
            val recomputed = DailyServiceRecord(entries = entries)
            val normalized = entries.values.fold(DailyServiceRecord()) { record, entry ->
                record.updatedWith(
                    LocalDate.parse(entry.localDateIso),
                    entry.configurationIdentity,
                    entry.firstResultId,
                    entry.bestScore,
                ).copy(
                    entries = record.entries + (entry.localDateIso to entry),
                )
            }
            require(storedCurrent == normalized.currentStreak && storedBest == normalized.bestStreak)
            normalized.copy(processedResultIds = processed)
        }.getOrDefault(DailyServiceRecord())
    }
}

internal object EndlessMilestoneCodec {
    fun encode(record: EndlessMilestoneRecord): String = listOf(
        2,
        record.contentPackId,
        record.seed,
        record.completedStage,
        record.stageScore,
        record.cumulativeScore,
        record.choice.name,
    ).joinToString("|")

    fun decode(encoded: String?): EndlessMilestoneRecord? {
        if (encoded.isNullOrBlank() || encoded.length > 500) return null
        return runCatching {
            val parts = encoded.split('|')
            when {
                parts.size == 6 && parts[0] == "1" -> EndlessMilestoneRecord(
                    schemaVersion = 1,
                    contentPackId = ContentRegistry.DEFAULT_PACK_ID,
                    seed = parts[1].toLong(),
                    completedStage = parts[2].toInt(),
                    stageScore = parts[3].toInt(),
                    cumulativeScore = parts[4].toInt(),
                    choice = EndlessMilestoneChoice.valueOf(parts[5]),
                )
                parts.size == 7 && parts[0] == "2" -> EndlessMilestoneRecord(
                    schemaVersion = 2,
                    contentPackId = parts[1],
                    seed = parts[2].toLong(),
                    completedStage = parts[3].toInt(),
                    stageScore = parts[4].toInt(),
                    cumulativeScore = parts[5].toInt(),
                    choice = EndlessMilestoneChoice.valueOf(parts[6]),
                )
                else -> error("Unsupported endless milestone")
            }
        }.getOrNull()
    }
}

internal object ServiceAchievements {
    const val ZERO_STRIKES = "zero_strikes"
    const val PERFECT_EXITS = "perfect_exits"
    const val EFFICIENT_ROUTING = "efficient_routing"
    val all = setOf(ZERO_STRIKES, PERFECT_EXITS, EFFICIENT_ROUTING)
}

internal fun ControllerServiceRecord.updatedWith(
    result: ValidatedMissionResult,
): ControllerServiceRecord {
    if (result.resultId in processedResultIds) return this
    val contribution = (
        result.stars * 15 + result.routeEfficiencyPercent * 35 / 100 +
            if (result.strikes == 0) 20 else 0
        ).coerceIn(0, 100)
    val priorMastery = masteryByFocus[result.focus] ?: MasteryRecord()
    val mastery = priorMastery.copy(
        attempts = priorMastery.attempts + 1,
        totalContribution = priorMastery.totalContribution + contribution,
        contributingResultIds = (listOf(result.resultId) + priorMastery.contributingResultIds)
            .distinct()
            .take(20),
    )
    val streak = if (result.strikes == 0) {
        currentSafeStreak + result.safeMovements
    } else {
        0
    }
    val achievements = buildSet {
        addAll(achievementIds.filter { it in ServiceAchievements.all })
        if (result.strikes == 0) add(ServiceAchievements.ZERO_STRIKES)
        if (result.departures > 0 && result.missedExits == 0) {
            add(ServiceAchievements.PERFECT_EXITS)
        }
        if (result.safeMovements > 0 && result.routeEfficiencyPercent >= 85) {
            add(ServiceAchievements.EFFICIENT_ROUTING)
        }
    }
    return copy(
        totalSafeMovements = totalSafeMovements + result.safeMovements,
        currentSafeStreak = streak,
        bestSafeStreak = maxOf(bestSafeStreak, streak),
        bestScoresByMission = bestScoresByMission + (
            result.missionId to maxOf(bestScoresByMission[result.missionId] ?: 0, result.score)
            ),
        bestCompletionSecondsByMission = bestCompletionSecondsByMission + (
            result.missionId to minOf(
                bestCompletionSecondsByMission[result.missionId] ?: Int.MAX_VALUE,
                result.completionSeconds,
            )
            ),
        masteryByFocus = masteryByFocus + (result.focus to mastery),
        achievementIds = achievements,
        processedResultIds = (listOf(result.resultId) + processedResultIds).distinct().take(200),
    )
}

internal object ControllerServiceRecordCodec {
    private const val MAX_CHARS = 100_000

    fun encode(record: ControllerServiceRecord): String = buildList {
        add(
            listOf(
                "H",
                record.schemaVersion,
                record.totalSafeMovements,
                record.currentSafeStreak,
                record.bestSafeStreak,
            ).joinToString("|"),
        )
        (record.bestScoresByMission.keys + record.bestCompletionSecondsByMission.keys)
            .filter { it in ContentRegistry.missionIds }
            .distinct()
            .sorted()
            .forEach { missionId ->
                add(
                    "S|${TextCodec.encode(missionId)}|" +
                        "${record.bestScoresByMission[missionId].orEmpty()}|" +
                        record.bestCompletionSecondsByMission[missionId].orEmpty(),
                )
            }
        record.masteryByFocus.toSortedMap(compareBy(TutorialFocus::name)).forEach { (focus, mastery) ->
            val ids = mastery.contributingResultIds.take(20).joinToString(",", transform = TextCodec::encode)
            add("M|${focus.name}|${mastery.attempts}|${mastery.totalContribution}|$ids")
        }
        add("A|${record.achievementIds.filter { it in ServiceAchievements.all }.sorted().joinToString(",")}")
        add("P|${record.processedResultIds.take(200).joinToString(",", transform = TextCodec::encode)}")
    }.joinToString("\n")

    fun decode(encoded: String?): ControllerServiceRecord {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CHARS) return ControllerServiceRecord()
        return runCatching {
            val lines = encoded.lineSequence().toList()
            val header = lines.first().split('|')
            require(header.size == 5 && header[0] == "H" && header[1].toInt() == 1)
            val total = header[2].toInt().also { require(it >= 0) }
            val current = header[3].toInt().also { require(it >= 0) }
            val best = header[4].toInt().also { require(it >= current) }
            val scores = mutableMapOf<String, Int>()
            val times = mutableMapOf<String, Int>()
            val mastery = mutableMapOf<TutorialFocus, MasteryRecord>()
            var achievements = emptySet<String>()
            var processed = emptyList<String>()
            lines.drop(1).forEach { line ->
                val parts = line.split('|')
                when (parts.firstOrNull()) {
                    "S" -> if (parts.size == 4) {
                        val id = TextCodec.decode(parts[1])?.takeIf { it in ContentRegistry.missionIds }
                            ?: return@forEach
                        parts[2].toIntOrNull()?.takeIf { it >= 0 }?.let { scores[id] = it }
                        parts[3].toIntOrNull()?.takeIf { it >= 0 }?.let { times[id] = it }
                    }
                    "M" -> if (parts.size == 5) {
                        val focus = runCatching { TutorialFocus.valueOf(parts[1]) }.getOrNull()
                            ?.takeIf { it != TutorialFocus.NONE } ?: return@forEach
                        val attempts = parts[2].toIntOrNull()?.takeIf { it in 1..100_000 }
                            ?: return@forEach
                        val contribution = parts[3].toIntOrNull()
                            ?.takeIf { it in 0..attempts * 100 } ?: return@forEach
                        val ids = parts[4].split(',').filter(String::isNotBlank).take(20)
                            .mapNotNull(TextCodec::decode).filter(String::isNotBlank)
                        mastery[focus] = MasteryRecord(attempts, contribution, ids)
                    }
                    "A" -> achievements = parts.getOrNull(1).orEmpty().split(',')
                        .filterTo(mutableSetOf()) { it in ServiceAchievements.all }
                    "P" -> processed = parts.getOrNull(1).orEmpty().split(',')
                        .filter(String::isNotBlank).take(200).mapNotNull(TextCodec::decode)
                        .filter(String::isNotBlank).distinct()
                }
            }
            ControllerServiceRecord(
                totalSafeMovements = total,
                currentSafeStreak = current,
                bestSafeStreak = best,
                bestScoresByMission = scores,
                bestCompletionSecondsByMission = times,
                masteryByFocus = mastery,
                achievementIds = achievements,
                processedResultIds = processed,
            )
        }.getOrDefault(ControllerServiceRecord())
    }

    private fun Int?.orEmpty(): String = this?.toString().orEmpty()
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
