package io.mirr.plexplay.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PlexXmlParserTest {
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
