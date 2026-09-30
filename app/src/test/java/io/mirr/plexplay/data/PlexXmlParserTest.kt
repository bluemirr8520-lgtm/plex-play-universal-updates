package io.mirr.plexplay.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PlexXmlParserTest {
    @Test fun seriesCompletionSurvivesFreshMetadataWithoutViewCount() {
        val xml = """<MediaContainer librarySectionID="7">
            <Directory ratingKey="10" key="/library/metadata/10" type="show" leafCount="12" viewedLeafCount="12"/>
            <Directory ratingKey="11" key="/library/metadata/11" type="season" leafCount="6" viewedLeafCount="6"/>
            <Directory ratingKey="12" key="/library/metadata/12" type="show" leafCount="12" viewedLeafCount="11"/>
            </MediaContainer>"""
        val items = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(listOf(true, true, false), items.map { it.isWatched })
        assertEquals(listOf(0, 0, 1), items.map { it.unwatchedEpisodeCount })
        assertEquals(listOf(0, 0, 0), items.map { it.viewCount })
    }

    @Test fun allPartPathsAreRetainedWithoutChangingSelectedPlaybackPart() {
        val xml = """
            <MediaContainer librarySectionID="7">
              <Video ratingKey="42" type="movie">
                <Media><Part key="/a" file="/video/a.mkv"/><Part key="/b" file="/video/b.mkv"/></Media>
                <Media><Part key="/c" file="/video/c.mkv"/></Media>
                <Extras><Video ratingKey="43"><Media><Part key="/extra" file="/extra/ignored.mkv"/></Media></Video></Extras>
              </Video>
              <Video ratingKey="44" type="movie"><Media><Part key="/d" file="/video/d.mkv"/></Media></Video>
            </MediaContainer>
        """.trimIndent()
        val items = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(listOf("/video/a.mkv", "/video/b.mkv", "/video/c.mkv"), items[0].mediaFilePaths)
        assertEquals("/video/c.mkv", items[0].filePath)
        assertEquals("/c", items[0].partKey)
        assertEquals(listOf("/video/d.mkv"), items[1].mediaFilePaths)
    }

    @Test fun unresolvedVersionPathIsRetainedAsACollectionSafetyMarker() {
        val xml = """<MediaContainer><Video ratingKey="42" type="movie">
            <Media><Part key="/missing"/></Media>
            <Media><Part key="/valid" file="/video/a.mkv"/></Media>
            </Video></MediaContainer>"""
        val item = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray())).single()
        assertEquals(listOf("", "/video/a.mkv"), item.mediaFilePaths)
        assertEquals("/video/a.mkv", item.filePath)
    }

    @Test
    fun collectionsPreserveFirstOccurrenceOrderAndIgnoreEmptyTags() {
        val xml = """
            <MediaContainer size="2">
              <Video ratingKey="1" key="/library/metadata/1" type="movie" title="Collections">
                <Collection tag="Favorites" />
                <Collection tag="KILL" />
                <Collection tag="" />
                <Collection />
                <Collection tag="   " />
                <Collection tag="123" />
                <Collection tag="KILL" />
                <Collection tag="Favorites" />
                <Collection tag="kill" />
              </Video>
              <Video ratingKey="2" key="/library/metadata/2" type="movie" title="No collections" />
            </MediaContainer>
        """.trimIndent()

        val items = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(listOf("Favorites", "KILL", "123", "kill"), items[0].collections)
        assertEquals(emptyList<String>(), items[1].collections)
    }

    @Test
    fun collectionTagsRetainTheirActualNames() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="1" key="/library/metadata/1" type="movie" title="Collections">
                <Collection tag="Action &amp; Adventure" />
                <Collection tag=" My Collection " />
                <Collection tag="Action &amp; Adventure" />
              </Video>
            </MediaContainer>
        """.trimIndent()

        val item = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray())).single()

        assertEquals(listOf("Action & Adventure", " My Collection "), item.collections)
    }

    @Test
    fun containerLibrarySectionIsUsedOnlyWhenItemSectionIsMissingOrBlank() {
        val xml = """
            <MediaContainer size="3" librarySectionID="10">
              <Video ratingKey="1" key="/library/metadata/1" type="movie" title="Inherited" />
              <Video ratingKey="2" key="/library/metadata/2" type="movie" title="Explicit"
                  librarySectionID="20" />
              <Video ratingKey="3" key="/library/metadata/3" type="movie" title="Blank"
                  librarySectionID=" " />
            </MediaContainer>
        """.trimIndent()

        val items = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(listOf("10", "20", "10"), items.map { it.librarySectionId })
    }

    @Test
    fun containerSectionDoesNotLeakBetweenParseCalls() {
        val withSection = """
            <MediaContainer size="1" librarySectionID="10">
              <Video ratingKey="1" key="/library/metadata/1" type="movie" title="First" />
            </MediaContainer>
        """.trimIndent()
        val withoutSection = """
            <MediaContainer size="1">
              <Video ratingKey="2" key="/library/metadata/2" type="movie" title="Second" />
            </MediaContainer>
        """.trimIndent()

        val first = PlexXmlParser.items(ByteArrayInputStream(withSection.toByteArray())).single()
        val second = PlexXmlParser.items(ByteArrayInputStream(withoutSection.toByteArray())).single()

        assertEquals("10", first.librarySectionId)
        assertEquals(null, second.librarySectionId)
    }

    @Test
    fun nestedRelatedItemsCannotDonateCollectionsOrOverrideContainerSection() {
        val xml = """
            <MediaContainer size="2" librarySectionID="10">
              <Video ratingKey="1" key="/library/metadata/1" type="movie" title="Outer">
                <Media videoCodec="hevc">
                  <Part key="/library/parts/outer-first/file.mkv" file="/video/Allowed/first.mkv" />
                  <Part key="/library/parts/outer-last/file.mkv" file="/video/Allowed/last.mkv">
                    <Stream id="701" streamType="3" codec="srt" external="0" />
                  </Part>
                </Media>
                <Role tag="Outer actor" />
                <Genre tag="Outer genre" />
                <Collection tag="KILL" />
                <Related>
                  <Collection tag="Not a direct child" />
                  <MediaContainer librarySectionID="99">
                    <Video ratingKey="nested" key="/library/metadata/nested" type="movie" title="Related">
                      <Media videoCodec="av1">
                        <Part key="/library/parts/related/file.mkv" file="/different/path/related.mkv">
                          <Stream id="999" streamType="3" codec="ass" external="0" />
                        </Part>
                      </Media>
                      <Role tag="Related actor" />
                      <Genre tag="Related genre" />
                      <Collection tag="Related collection" />
                    </Video>
                  </MediaContainer>
                </Related>
                <Collection tag="123" />
              </Video>
              <Directory ratingKey="2" key="/library/metadata/2" type="show" title="Second">
                <Collection tag="Second item's own collection" />
              </Directory>
            </MediaContainer>
        """.trimIndent()

        val items = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(listOf("1", "2"), items.map { it.ratingKey })
        assertEquals(listOf("KILL", "123"), items[0].collections)
        assertEquals(listOf("Second item's own collection"), items[1].collections)
        assertEquals(listOf("10", "10"), items.map { it.librarySectionId })
        assertEquals("/library/parts/outer-last/file.mkv", items[0].partKey)
        assertEquals("/video/Allowed/last.mkv", items[0].filePath)
        assertEquals(0, items[0].selectedMediaIndex)
        assertEquals(1, items[0].selectedPartIndex)
        assertEquals("hevc", items[0].videoCodec)
        assertEquals(listOf("701"), items[0].subtitles.map { it.streamId })
        assertEquals(listOf("Outer actor"), items[0].actors.map { it.tag })
        assertEquals(listOf("Outer genre"), items[0].genres.map { it.tag })
    }

    @Test
    fun missingFileOnLastPartDoesNotReuseAnotherPartsFilePath() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="42" key="/library/metadata/42" type="movie" title="Demo">
                <Media>
                  <Part key="/library/parts/first/file.mkv" file="/video/First/movie.mkv" />
                  <Part key="/library/parts/last/file.mkv" />
                </Media>
              </Video>
            </MediaContainer>
        """.trimIndent()

        val item = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray())).single()

        assertEquals("/library/parts/last/file.mkv", item.partKey)
        assertEquals(null, item.filePath)
        assertEquals(0, item.selectedMediaIndex)
        assertEquals(1, item.selectedPartIndex)
    }

    @Test
    fun filePathAndPartKeyComeFromTheSameLastPart() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="42" key="/library/metadata/42" type="movie" title="Demo">
                <Media>
                  <Part key="/library/parts/first/file.mkv" file="/video/First/movie.mkv" />
                </Media>
                <Media>
                  <Part key="/library/parts/last/file.mkv" file="/video/Last/movie.mkv" />
                </Media>
              </Video>
            </MediaContainer>
        """.trimIndent()

        val item = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray())).single()

        assertEquals("/library/parts/last/file.mkv", item.partKey)
        assertEquals("/video/Last/movie.mkv", item.filePath)
        assertEquals(1, item.selectedMediaIndex)
        assertEquals(0, item.selectedPartIndex)
    }

    @Test
    fun selectedIndexesFollowTheActualPartAndResetForTheNextItem() {
        val xml = """
            <MediaContainer size="2">
              <Video ratingKey="42" key="/library/metadata/42" type="movie" title="Multi version">
                <Media>
                  <Part key="/library/parts/first/file.mkv" file="/video/Other/first.mkv" />
                </Media>
                <Media>
                  <Part key="/library/parts/second/file.mkv" file="/video/Other/second.mkv" />
                  <Part key="/library/parts/selected/file.mkv" file="/video/Allowed/selected.mkv" />
                </Media>
              </Video>
              <Video ratingKey="43" key="/library/metadata/43" type="movie" title="Single version">
                <Media>
                  <Part key="/library/parts/next/file.mkv" file="/video/Allowed/next.mkv" />
                </Media>
              </Video>
            </MediaContainer>
        """.trimIndent()

        val items = PlexXmlParser.items(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("/library/parts/selected/file.mkv", items[0].partKey)
        assertEquals("/video/Allowed/selected.mkv", items[0].filePath)
        assertEquals(1, items[0].selectedMediaIndex)
        assertEquals(1, items[0].selectedPartIndex)
        assertEquals("/library/parts/next/file.mkv", items[1].partKey)
        assertEquals(0, items[1].selectedMediaIndex)
        assertEquals(0, items[1].selectedPartIndex)
    }

    @Test
    fun parsesEmbeddedAndExternalSubtitleStreams() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="42" key="/library/metadata/42" type="movie" title="Demo">
                <Media>
                  <Part key="/library/parts/42/file.mkv">
                    <Stream id="701" streamType="3" codec="srt" languageCode="kor"
                        displayTitle="한국어 내장" external="0" />
                    <Stream id="702" streamType="3" key="/library/streams/702"
                        codec="ass" languageCode="eng" displayTitle="English External"
                        external="1" />
                  </Part>
                </Media>
              </Video>
            </MediaContainer>
        """.trimIndent()

        val subtitles = PlexXmlParser.items(
            ByteArrayInputStream(xml.toByteArray()),
        ).single().subtitles

        assertEquals(2, subtitles.size)
        assertEquals("701", subtitles[0].streamId)
        assertEquals(true, subtitles[0].isEmbedded)
        assertEquals(null, subtitles[0].key)
        assertEquals("/library/parts/42/file.mkv", subtitles[0].partKey)
        assertEquals(0, subtitles[0].mediaIndex)
        assertEquals(0, subtitles[0].partIndex)
        assertEquals("/library/streams/702", subtitles[1].key)
        assertEquals(false, subtitles[1].isEmbedded)
    }

    @Test
    fun keepsSubtitleMediaAndPartIndexes() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="42" key="/library/metadata/42" type="movie" title="Demo">
                <Media>
                  <Part key="/library/parts/first/file.mkv">
                    <Stream id="701" streamType="3" codec="srt" external="0" />
                  </Part>
                  <Part key="/library/parts/second/file.mkv">
                    <Stream id="702" streamType="3" codec="ass" external="0" />
                  </Part>
                </Media>
                <Media>
                  <Part key="/library/parts/third/file.mkv">
                    <Stream id="703" streamType="3" codec="vtt" external="0" />
                  </Part>
                </Media>
              </Video>
            </MediaContainer>
        """.trimIndent()

        val subtitles = PlexXmlParser.items(
            ByteArrayInputStream(xml.toByteArray()),
        ).single().subtitles

        assertEquals(listOf(0, 0, 1), subtitles.map { it.mediaIndex })
        assertEquals(listOf(0, 1, 0), subtitles.map { it.partIndex })
        assertEquals(
            listOf(
                "/library/parts/first/file.mkv",
                "/library/parts/second/file.mkv",
                "/library/parts/third/file.mkv",
            ),
            subtitles.map { it.partKey },
        )
    }

    @Test
    fun parsesHdrDolbyVisionAv1AndDolbyAudioMetadata() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="42" key="/library/metadata/42" type="movie" title="Demo">
                <Media videoCodec="av1" videoResolution="4k" videoProfile="main 10"
                    videoBitDepth="10" videoDynamicRange="DOVI" audioCodec="eac3"
                    audioChannels="6" audioProfile="atmos">
                  <Part key="/library/parts/42/file.mkv" container="mkv">
                    <Stream streamType="1" codec="av1" profile="main 10" bitDepth="10"
                        colorPrimaries="bt2020" colorTrc="smpte2084" DOVIPresent="1"
                        DOVIProfile="8" width="3840" height="2160" />
                    <Stream streamType="2" codec="eac3" channels="6" profile="atmos"
                        extendedDisplayTitle="한국어 (EAC3 5.1 Dolby Atmos)" selected="1" />
                  </Part>
                </Media>
              </Video>
            </MediaContainer>
        """.trimIndent()

        val item = PlexXmlParser.items(
            ByteArrayInputStream(xml.toByteArray()),
        ).single()

        assertEquals("av1", item.videoCodec)
        assertEquals("4k", item.videoResolution)
        assertEquals("main 10", item.videoProfile)
        assertEquals(10, item.videoBitDepth)
        assertEquals("DOVI", item.videoDynamicRange)
        assertEquals("bt2020", item.videoColorPrimaries)
        assertEquals("smpte2084", item.videoColorTransfer)
        assertEquals(8, item.dolbyVisionProfile)
        assertEquals("eac3", item.audioCodec)
        assertEquals(6, item.audioChannels)
        assertEquals("atmos", item.audioProfile)
        assertEquals(
            "한국어 (EAC3 5.1 Dolby Atmos)",
            item.audioDisplayTitle,
        )
    }
}
