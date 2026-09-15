package io.mirr.plexplay.data

import org.junit.Assert.*
import org.junit.Test

class SeriesFolderBrowsingTest {
    private val show = PlexItem("10", "/library/metadata/10/children", "show", "같은 시리즈")
    private val aggregate = PlexItem("/library/metadata/10/allLeaves", "/library/metadata/10/allLeaves", "directory", "All Episodes")
    private fun season(id: String, title: String = "시즌 1") =
        PlexItem(id, "/library/metadata/$id/children", "season", title)

    @Test fun hidesAggregateOnlyWhenTwoDifferentFoldersExist() {
        val first = season("11")
        val second = season("12", "시즌 1 4K")
        assertEquals(listOf(first, second), visibleSeriesChildren(show, listOf(aggregate, first, second)))
    }

    @Test fun keepsAggregateForOneFolder() {
        val rows = listOf(aggregate, season("11"))
        assertSame(rows, visibleSeriesChildren(show, rows))
    }

    @Test fun keepsAggregateWhenItIsTheOnlyAvailableRoute() {
        val rows = listOf(aggregate)
        assertSame(rows, visibleSeriesChildren(show, rows))
    }

    @Test fun sameFolderRepeatedWithDifferentKeyShapeIsNotTwoFolders() {
        val rows = listOf(aggregate, season("11"), season("11").copy(key = "/library/metadata/11"))
        assertSame(rows, visibleSeriesChildren(show, rows))
    }

    @Test fun keyFallbackIdsFromXmlAreNormalizedBeforeCounting() {
        val first = season("11").copy(ratingKey = "/library/metadata/11")
        val duplicate = first.copy(ratingKey = "/library/metadata/11/children", key = "/library/metadata/11/children?sort=title")
        val rows = listOf(aggregate, first, duplicate)
        assertSame(rows, visibleSeriesChildren(show, rows))
    }

    @Test fun translatedAggregateUsesRouteRatherThanTitle() {
        val rows = listOf(aggregate.copy(title = "모든 에피소드"), season("11"), season("12"))
        assertEquals(listOf("11", "12"), visibleSeriesChildren(show, rows).map { it.ratingKey })
    }

    @Test fun realSeasonAndVideoNamedAllEpisodesArePreserved() {
        val namedSeason = season("11", "All Episodes")
        val episode = PlexItem("14", "/library/metadata/14", "episode", "All Episodes")
        val other = season("12")
        assertEquals(listOf(namedSeason, episode, other), visibleSeriesChildren(show, listOf(aggregate, namedSeason, episode, other)))
    }

    @Test fun anotherShowsAggregateIsNotRemovedOrCountedAsFolder() {
        val otherAggregate = aggregate.copy(key = "/library/metadata/90/allLeaves")
        val oneFolder = listOf(aggregate, season("11"), otherAggregate)
        assertSame(oneFolder, visibleSeriesChildren(show, oneFolder))
        assertEquals(listOf(season("11"), otherAggregate, season("12")),
            visibleSeriesChildren(show, listOf(aggregate, season("11"), otherAggregate, season("12"))))
    }

    @Test fun doesNotFilterArtistCollectionOrSeasonChildren() {
        val rows = listOf(aggregate, season("11"), season("12"))
        listOf("artist", "collection", "season", "directory").forEach {
            assertSame(rows, visibleSeriesChildren(show.copy(type = it), rows))
        }
    }

    @Test fun specialsCountAsARealSeasonAndOrderIsPreserved() {
        val special = season("13", "스페셜")
        val regular = season("11")
        assertEquals(listOf(special, regular), visibleSeriesChildren(show, listOf(special, aggregate, regular)))
    }

    @Test fun untypedXmlDirectoriesCountAsFoldersWithDistinctIds() {
        val xml = """
            <MediaContainer size="3">
              <Directory key="/library/metadata/10/allLeaves" title="All Episodes"/>
              <Directory key="/library/metadata/11/children" title="같은 폴더"/>
              <Directory key="/library/metadata/12/children" title="같은 폴더"/>
            </MediaContainer>
        """.trimIndent()
        val rows = PlexXmlParser.items(xml.byteInputStream())
        assertEquals(listOf("/library/metadata/11/children", "/library/metadata/12/children"),
            visibleSeriesChildren(show, rows).map { it.key })
    }

    @Test fun countersAndEpisodesAreNotFolderCounts() {
        val episode = PlexItem("14", "/library/metadata/14", "episode", "회차")
        val rows = listOf(aggregate, season("11"), episode)
        assertSame(rows, visibleSeriesChildren(show.copy(childCount = 100, leafCount = 500), rows))
    }

    @Test fun trailingSlashAndQueryDoNotHideRealFoldersOrRetainAggregate() {
        val queried = aggregate.copy(key = "/library/metadata/10/allLeaves/?sort=title#fragment")
        assertEquals(listOf(season("11"), season("12")),
            visibleSeriesChildren(show, listOf(queried, season("11"), season("12"))))
    }

    @Test fun childrenAndAllLeavesRemainListEndpoints() {
        assertEquals("/library/metadata/10/allLeaves", plexChildrenPath(aggregate))
        assertEquals("/library/metadata/10/allLeaves/?sort=title",
            plexChildrenPath(aggregate.copy(key = "/library/metadata/10/allLeaves/?sort=title")))
        assertEquals("/library/metadata/11/children", plexChildrenPath(season("11")))
        assertEquals("/library/metadata/11/children", plexChildrenPath(season("11").copy(key = "/library/metadata/11/")))
        assertEquals("/library/metadata/11/children", plexChildrenPath(season("11").copy(key = "")))
        assertEquals("/library/metadata/11/children?sort=title",
            plexChildrenPath(season("11").copy(key = "/library/metadata/11?sort=title")))
    }
}
