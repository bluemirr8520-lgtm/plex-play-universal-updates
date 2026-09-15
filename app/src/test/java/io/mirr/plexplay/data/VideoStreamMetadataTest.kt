package io.mirr.plexplay.data

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class VideoStreamMetadataTest {
    private fun item(media: String): PlexItem = PlexXmlParser.items(ByteArrayInputStream(
        ("""<MediaContainer><Video ratingKey="1" key="/library/metadata/1" type="movie" title="Video">""" +
            media + "</Video></MediaContainer>").toByteArray(),
    )).single()

    @Test fun dimensionsAndBitDepthRequirePositiveIntegers() {
        assertEquals(3840, parsePositiveVideoInt(" 3840 "))
        assertEquals(10, parsePositiveVideoInt("10"))
        listOf(null, "", "0", "-1", "2160p", "4k", "3840x2160", "3.5", "NaN", "2147483648").forEach {
            assertNull(it, parsePositiveVideoInt(it))
        }
    }

    @Test fun frameRateAcceptsFiniteDecimalAndFractionalMeasurements() {
        assertEquals(23.976, parseVideoFrameRate("23.976")!!, 0.000001)
        assertEquals(24000.0 / 1001.0, parseVideoFrameRate(" 24000 / 1001 ")!!, 0.000001)
        assertEquals(60000.0 / 1001.0, parseVideoFrameRate("60000/1001")!!, 0.000001)
    }

    @Test fun invalidRatesAndDisplayLabelsRemainUnknown() {
        listOf(null, "", "0", "-24", "NaN", "Infinity", "1e309", "0/0", "24/0", "-24/-1", "24/Infinity", "1/2/3", "24p", "NTSC").forEach {
            assertNull(it, parseVideoFrameRate(it))
        }
    }

    @Test fun omittedMeasurementsCanUseBaselineButExplicitInvalidValuesClearIt() {
        assertEquals(1920, parsePositiveVideoInt(null, 1920))
        assertNull(parsePositiveVideoInt("0", 1920))
        assertEquals(24.0, parseVideoFrameRate(null, 24.0)!!, 0.0)
        assertNull(parseVideoFrameRate("0/0", 24.0))
    }

    @Test fun unknownTransformedOutputDoesNotClaimOriginalMeasurements() {
        val original = VideoStreamMetadata(3840, 2160, 24000.0 / 1001.0, 10)
        assertEquals(original, original.forPlaybackOutput(true))
        assertEquals(VideoStreamMetadata(), original.forPlaybackOutput(false))
    }

    @Test fun itemMeasurementAdapterDoesNotTrustInvalidStoredValues() {
        val media = PlexItem("1", "/1", "movie", "Video", videoWidth = 0, videoHeight = -1,
            videoFrameRate = Double.NaN, videoBitDepth = 0)
        assertEquals(VideoStreamMetadata(), media.videoStreamMetadata())
    }

    @Test fun readsActualMediaDimensionsAndNumericFrameRate() {
        val parsed = item("""<Media width="3840" height="2160" videoFrameRate="24000/1001" videoBitDepth="10"><Part key="/p" /></Media>""")
        assertEquals(3840, parsed.videoWidth)
        assertEquals(2160, parsed.videoHeight)
        assertEquals(24000.0 / 1001.0, parsed.videoFrameRate!!, 0.000001)
        assertEquals(10, parsed.videoBitDepth)
    }

    @Test fun streamMeasurementsOverrideSameMediaBaseline() {
        val parsed = item("""<Media width="1920" height="1080" frameRate="24" videoBitDepth="8">
            <Part key="/p"><Stream streamType="1" width="3840" height="2160" frameRate="60000/1001" bitDepth="10" /></Part>
            </Media>""")
        assertEquals(3840, parsed.videoWidth)
        assertEquals(2160, parsed.videoHeight)
        assertEquals(60000.0 / 1001.0, parsed.videoFrameRate!!, 0.000001)
        assertEquals(10, parsed.videoBitDepth)
    }

    @Test fun resolutionAndFrameRateLabelsDoNotInventMeasurements() {
        val parsed = item("""<Media videoResolution="4k" videoFrameRate="24p"><Part key="/p" /></Media>""")
        assertEquals("4k", parsed.videoResolution)
        assertNull(parsed.videoWidth)
        assertNull(parsed.videoHeight)
        assertNull(parsed.videoFrameRate)
    }

    @Test fun siblingPartCannotInheritAnotherPartsStreamMeasurementsOrCodecs() {
        val parsed = item("""<Media>
            <Part key="/first"><Stream streamType="1" codec="hevc" width="3840" height="2160" frameRate="24" bitDepth="10" colorTrc="smpte2084" />
                <Stream streamType="2" codec="truehd" selected="1" /></Part>
            <Part key="/last" />
            </Media>""")
        assertEquals("/last", parsed.partKey)
        assertEquals(1, parsed.selectedPartIndex)
        assertEquals(VideoStreamMetadata(), parsed.videoStreamMetadata())
        assertNull(parsed.videoCodec)
        assertNull(parsed.videoColorTransfer)
        assertNull(parsed.audioCodec)
    }

    @Test fun siblingPartUsesOnlyItsOwnMediaBaseline() {
        val parsed = item("""<Media width="1920" height="1080" videoCodec="h264" audioCodec="aac">
            <Part key="/first"><Stream streamType="1" width="3840" height="2160" codec="hevc" />
                <Stream streamType="2" codec="truehd" selected="1" /></Part>
            <Part key="/last"><Stream streamType="2" codec="ac3" /></Part>
            </Media>""")
        assertEquals(1920, parsed.videoWidth)
        assertEquals(1080, parsed.videoHeight)
        assertEquals("h264", parsed.videoCodec)
        assertEquals("ac3", parsed.audioCodec)
    }

    @Test fun anotherMediaCannotInheritCodecHdrOrMeasurements() {
        val parsed = item("""<Media width="3840" height="2160" videoCodec="hevc" videoBitDepth="10" videoDynamicRange="HDR10" audioCodec="truehd">
            <Part key="/first" /></Media><Media><Part key="/last" /></Media>""")
        assertEquals("/last", parsed.partKey)
        assertEquals(1, parsed.selectedMediaIndex)
        assertEquals(VideoStreamMetadata(), parsed.videoStreamMetadata())
        assertNull(parsed.videoCodec)
        assertNull(parsed.videoDynamicRange)
        assertNull(parsed.audioCodec)
    }

    @Test fun trailingPartlessMediaCannotReplaceActualSelectedPartMetadata() {
        val parsed = item("""<Media width="1920" height="1080" videoCodec="h264"><Part key="/actual" /></Media>
            <Media width="3840" height="2160" videoCodec="hevc" />""")
        assertEquals("/actual", parsed.partKey)
        assertEquals(0, parsed.selectedMediaIndex)
        assertEquals(1920, parsed.videoWidth)
        assertEquals("h264", parsed.videoCodec)
    }

    @Test fun partlessMediaCanStillExposeItsOwnDisplayMetadata() {
        val parsed = item("""<Media width="3840" height="2160" videoCodec="hevc" />""")
        assertNull(parsed.partKey)
        assertEquals(3840, parsed.videoWidth)
        assertEquals(2160, parsed.videoHeight)
    }

    @Test fun selectedVideoStreamDoesNotInheritUnselectedStreamMeasurements() {
        val parsed = item("""<Media><Part key="/p">
            <Stream streamType="1" width="3840" height="2160" codec="hevc" frameRate="24" />
            <Stream streamType="1" selected="1" codec="h264" />
            </Part></Media>""")
        assertEquals("h264", parsed.videoCodec)
        assertNull(parsed.videoWidth)
        assertNull(parsed.videoHeight)
        assertNull(parsed.videoFrameRate)
    }

    @Test fun invalidStreamMeasurementsClearMediaBaselineInsteadOfGuessing() {
        val parsed = item("""<Media width="3840" height="2160" frameRate="24" videoBitDepth="10"><Part key="/p">
            <Stream streamType="1" width="0" height="-1" frameRate="0/0" bitDepth="0" />
            </Part></Media>""")
        assertEquals(VideoStreamMetadata(), parsed.videoStreamMetadata())
    }
}
