package io.mirr.plexplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCodecCatalogTest {
    @Test
    fun recognizesCommonUhdVideoAliases() {
        mapOf(
            MediaVideoCodec.HEVC to listOf("HEVC", "h.265", "hvc1", "hev1", "V_MPEGH/ISO/HEVC", "video/hevc"),
            MediaVideoCodec.AVC to listOf("H.264", "AVC1", "avc3", "V_MPEG4/ISO/AVC", "video/avc"),
            MediaVideoCodec.AV1 to listOf("AV1", "av01", "V_AV1", "video/av01"),
            MediaVideoCodec.VP9 to listOf("VP09", "VP9", "V_VP9", "video/x-vnd.on2.vp9"),
        ).forEach { (family, aliases) ->
            aliases.forEach { assertEquals(it, family, mediaVideoCodec(it)) }
        }
    }

    @Test
    fun recognizesLegacyDiscVideoWithoutMisclassifyingWmv3AsVc1() {
        assertEquals(MediaVideoCodec.MPEG2, mediaVideoCodec("mpeg2video"))
        assertEquals(MediaVideoCodec.MPEG2, mediaVideoCodec("V_MPEG2"))
        assertEquals(MediaVideoCodec.VC1, mediaVideoCodec("VC-1"))
        assertEquals(MediaVideoCodec.VC1, mediaVideoCodec("WVC1"))
        assertEquals(MediaVideoCodec.WMV3, mediaVideoCodec("wmv3"))
        assertEquals("video/x-ms-wmv", mediaVideoMimeType("wmv3"))
    }

    @Test
    fun recognizesDolbyVisionCodecTagsWithoutClaimingAnOrdinaryHevcProfile() {
        listOf("dvhe", "dvh1", "dvav", "dva1", "video/dolby-vision").forEach {
            assertEquals(MediaVideoCodec.DOLBY_VISION, mediaVideoCodec(it))
        }
    }

    @Test
    fun trueHdAndGenericMlpRemainDifferentCodecFamilies() {
        listOf("true-hd", "Dolby TrueHD", "trhd", "mlpfb", "A_TRUEHD", "audio/vnd.dolby.mlp").forEach {
            assertEquals(it, MediaAudioCodec.TRUEHD, mediaAudioCodec(it))
        }
        assertEquals(MediaAudioCodec.MLP, mediaAudioCodec("mlp"))
        assertEquals(MediaAudioCodec.MLP, mediaAudioCodec("audio/mlp"))
        assertEquals("MLP", mediaAudioCodec("mlp")?.featureLabel)
    }

    @Test
    fun dtsCoreAndHdVariantsRemainSeparate() {
        listOf("dts", "dca", "A_DTS").forEach { assertEquals(MediaAudioCodec.DTS, mediaAudioCodec(it)) }
        listOf("DTS-HD", "DTS-HD MA", "DTS-HD HRA", "dtsl", "dtsh", "A_DTS/LOSSLESS", "A_DTS/EXPRESS").forEach {
            assertEquals(it, MediaAudioCodec.DTS_HD, mediaAudioCodec(it))
        }
    }

    @Test
    fun dolbyDigitalAndJocAliasesAreExact() {
        listOf("ac-3", "a52 ", "A_AC3").forEach { assertEquals(MediaAudioCodec.AC3, mediaAudioCodec(it)) }
        listOf("E-AC-3", "EC-3", "A_EAC3").forEach { assertEquals(MediaAudioCodec.EAC3, mediaAudioCodec(it)) }
        listOf("eac3-joc", "ec3_joc", "audio/eac3-joc").forEach {
            assertEquals(MediaAudioCodec.EAC3_JOC, mediaAudioCodec(it))
        }
        assertNull(mediaAudioCodec("Dolby Atmos"))
    }

    @Test
    fun losslessAndDiscPcmVariantsAreRecognized() {
        assertEquals(MediaAudioCodec.PCM_S16LE, mediaAudioCodec("pcm_s16le"))
        listOf("PCM", "LPCM", "pcm_bluray", "pcm_dvd", "pcm_s24le", "pcm_s24be", "pcm_s32le", "pcm_f32le", "A_PCM/INT/LIT").forEach {
            assertEquals(it, MediaAudioCodec.PCM, mediaAudioCodec(it))
            assertTrue(it, requiresLocalAudioDecoding(it))
        }
        assertEquals(MediaAudioCodec.FLAC, mediaAudioCodec("A_FLAC"))
        assertEquals(MediaAudioCodec.ALAC, mediaAudioCodec("audio/alac"))
    }

    @Test
    fun commonCompressedAudioRemainsRecognized() {
        listOf("aac", "aac_latm", "HE-AAC", "mp4a.40.2", "mp4a.40.5", "mp4a.40.29", "A_AAC/MPEG4/LC").forEach {
            assertEquals(it, MediaAudioCodec.AAC, mediaAudioCodec(it))
        }
        assertEquals(MediaAudioCodec.MP3, mediaAudioCodec("A_MPEG/L3"))
        assertEquals(MediaAudioCodec.MP2, mediaAudioCodec("A_MPEG/L2"))
        assertEquals(MediaAudioCodec.OPUS, mediaAudioCodec("A_OPUS"))
        assertEquals(MediaAudioCodec.VORBIS, mediaAudioCodec("audio/vorbis"))
    }

    @Test
    fun unknownAndDescriptiveStringsNeverBecomeKnownCodecsBySubstring() {
        listOf(null, "", "not-hevc", "HEVC Atmos Dolby Vision", "hvc1.unknown", "futurecodec").forEach {
            assertNull(it, mediaVideoCodec(it))
        }
        listOf(null, "", "not-ac3", "aac commentary", "DTS unknown", "truehd atmos", "mpga").forEach {
            assertNull(it, mediaAudioCodec(it))
        }
    }

    @Test
    fun onlyAudioFamiliesNeedingLocalDecodePreferVlc() {
        listOf("truehd", "mlp", "dts", "dts-hd", "lpcm", "pcm_s24le", "mp2", "alac").forEach {
            assertTrue(it, requiresLocalAudioDecoding(it))
        }
        listOf("aac", "ac3", "eac3", "eac3-joc", "flac", "pcm_s16le", "mp3", "opus", "vorbis", "unknown").forEach {
            assertFalse(it, requiresLocalAudioDecoding(it))
        }
    }
}
