package com.stuart.atccontroller.data

data class ContentPack(
    val id: String,
    val displayName: String,
    val version: Int,
    val airport: AirportDefinition,
    val authoredMissions: List<ScenarioDefinition>,
    val primaryRunwayEndIds: List<String>,
    val reciprocalRunwayEndIds: List<String>,
    val overview: String,
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank() && overview.isNotBlank() && version > 0)
        require(authoredMissions.isNotEmpty())
        require(authoredMissions.all { it.airportId == airport.id })
        val airportEndIds = airport.runwayEnds.mapTo(mutableSetOf(), RunwayEndDefinition::id)
        require(primaryRunwayEndIds.isNotEmpty() && primaryRunwayEndIds.all { it in airportEndIds })
        require(reciprocalRunwayEndIds.isNotEmpty() && reciprocalRunwayEndIds.all { it in airportEndIds })
        require(primaryRunwayEndIds.toSet().intersect(reciprocalRunwayEndIds.toSet()).isEmpty())
    }

    fun runwayEnds(direction: RunwayDirection): List<String> = when (direction) {
        RunwayDirection.WESTERLY -> primaryRunwayEndIds
        RunwayDirection.EASTERLY -> reciprocalRunwayEndIds
    }
}

/** Stable offline registry for every airport, campaign, and authored scenario. */
object ContentRegistry {
    const val DEFAULT_PACK_ID = "manchester_v1"

    val packs: List<ContentPack> = listOf(
        ContentPack(
            id = DEFAULT_PACK_ID,
            displayName = "Manchester Approach",
            version = 1,
            airport = ManchesterContent.airport,
            authoredMissions = ManchesterContent.authoredMissions,
            primaryRunwayEndIds = listOf("23R", "23L"),
            reciprocalRunwayEndIds = listOf("05L", "05R"),
            overview = "A simplified parallel-runway campaign inspired by the Manchester terminal area.",
        ),
        ContentPack(
            id = CoastalContent.PACK_ID,
            displayName = "Harbour Approach",
            version = 1,
            airport = CoastalContent.airport,
            authoredMissions = CoastalContent.authoredMissions,
            primaryRunwayEndIds = listOf("09", "18"),
            reciprocalRunwayEndIds = listOf("27", "36"),
            overview = "An original fictional compact sector built around crossing runways and coastal fixes.",
        ),
    )

    private val packsById = packs.associateBy(ContentPack::id)
    private val airportsById = packs.associateBy { it.airport.id }
    private val missionsById = packs.flatMap(ContentPack::authoredMissions)
        .associateBy(ScenarioDefinition::id)

    val authoredMissions: List<ScenarioDefinition> = packs.flatMap(ContentPack::authoredMissions)
    val missionIds: List<String> = authoredMissions.map(ScenarioDefinition::id)
    val firstMissionIds: Set<String> = packs.mapTo(linkedSetOf()) { it.authoredMissions.first().id }

    fun pack(id: String): ContentPack? = packsById[id]
    fun packForAirport(airportId: String): ContentPack? = airportsById[airportId]
    fun packForMission(missionId: String): ContentPack? = packs.firstOrNull { pack ->
        pack.authoredMissions.any { it.id == missionId }
    }

    fun airport(airportId: String): AirportDefinition? = airportsById[airportId]?.airport
    fun mission(id: String): ScenarioDefinition? = missionsById[id]

    fun nextMissionId(afterMissionId: String): String? {
        val pack = packForMission(afterMissionId) ?: return null
        val index = pack.authoredMissions.indexOfFirst { it.id == afterMissionId }
        return pack.authoredMissions.getOrNull(index + 1)?.id
    }

    fun validate(): List<ScenarioValidationIssue> = buildList {
        val duplicatePackIds = packs.groupingBy(ContentPack::id).eachCount().filterValues { it > 1 }.keys
        duplicatePackIds.forEach {
            add(ScenarioValidationIssue("duplicate_pack_id", "registry.packs", "duplicate id $it"))
        }
        val duplicateAirportIds = packs.groupingBy { it.airport.id }.eachCount().filterValues { it > 1 }.keys
        duplicateAirportIds.forEach {
            add(ScenarioValidationIssue("duplicate_registry_airport", "registry.packs", "duplicate id $it"))
        }
        val duplicateMissionIds = authoredMissions.groupingBy(ScenarioDefinition::id)
            .eachCount().filterValues { it > 1 }.keys
        duplicateMissionIds.forEach {
            add(ScenarioValidationIssue("duplicate_registry_mission", "registry.missions", "duplicate id $it"))
        }
        packs.forEach { pack ->
            addAll(ScenarioValidator.validateAirport(pack.airport))
            pack.authoredMissions.forEach { mission ->
                addAll(ScenarioValidator.validate(mission, pack.airport).issues)
            }
        }
    }
}
