package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcAudioOutputTest {
    private val supported = VlcDirectAudioSupport(true, "지원 확인")
    private val unsupported = VlcDirectAudioSupport(false, "현재 HDMI 경로 지원 미확인")

    @Test
    fun preferenceKeysAreStableAndUnknownValuesDefaultToAutomatic() {
        assertEquals(listOf("auto", "pcm", "passthrough"), VlcAudioOutputMode.entries.map { it.preferenceKey })
        VlcAudioOutputMode.entries.forEach { mode ->
            assertEquals(mode, VlcAudioOutputMode.fromPreferenceKey(mode.preferenceKey))
            assertTrue(mode.label.isNotBlank())
            assertTrue(mode.detail.isNotBlank())
        }
        assertEquals(VlcAudioOutputMode.AUTO, VlcAudioOutputMode.fromPreferenceKey(null))
        assertEquals(VlcAudioOutputMode.AUTO, VlcAudioOutputMode.fromPreferenceKey("unknown"))
        assertEquals(VlcAudioOutputMode.AUTO, VlcAudioOutputMode.fromPreferenceKey("PASSTHROUGH"))
    }

    @Test
    fun recognizesExactTrueHdAliasesWithoutDependingOnLocale() {
        listOf(
            "truehd", "TRUE-HD", " Dolby TrueHD ", "mlpfb", "A_TRUEHD",
            "audio/true-hd", "audio/vnd.dolby.mlp",
        ).forEach { codec ->
            assertTrue(codec, isVlcTrueHdAudioCodec(codec))
            assertTrue(
                codec,
                decideVlcAudioOutput(VlcAudioOutputMode.AUTO, codec, 8, 48_000, supported).passthrough,
            )
        }
    }

    @Test
    fun genericMlpOrAtmosLabelDoesNotClaimTrueHd() {
        listOf(null, "", "mlp", "Dolby Atmos", "eac3", "truehd atmos", "not-truehd").forEach { codec ->
            assertFalse(codec, isVlcTrueHdAudioCodec(codec))
        }
    }

    @Test
    fun forcedPcmNeverEnablesPassthroughEvenWithSupportedHdmi() {
        val decision = decideVlcAudioOutput(VlcAudioOutputMode.PCM, "truehd", 8, 48_000, supported)

        assertFalse(decision.passthrough)
        assertEquals("PCM 디코딩", decision.label)
    }

    @Test
    fun automaticAndPassthroughModesUseConfirmedSupport() {
        listOf(VlcAudioOutputMode.AUTO, VlcAudioOutputMode.PASSTHROUGH).forEach { mode ->
            val decision = decideVlcAudioOutput(mode, "truehd", 8, 48_000, supported)

            assertTrue(decision.passthrough)
            assertEquals("HDMI 패스스루", decision.label)
            assertTrue(decision.reason.contains("Atmos"))
        }
    }

    @Test
    fun requestedPassthroughStillFallsBackWhenCurrentOutputSupportIsUnknown() {
        listOf(VlcAudioOutputMode.AUTO, VlcAudioOutputMode.PASSTHROUGH).forEach { mode ->
            val decision = decideVlcAudioOutput(mode, "truehd", 8, 48_000, unsupported)

            assertFalse(decision.passthrough)
            assertEquals(unsupported.reason, decision.reason)
        }
    }

    @Test
    fun supportedOutputDoesNotOverrideUnknownOrNonEncodedSource() {
        listOf(null, "", "aac", "flac", "pcm", "mlp", "Atmos", "unknown").forEach { codec ->
            assertFalse(
                codec,
                decideVlcAudioOutput(VlcAudioOutputMode.PASSTHROUGH, codec, 8, 48_000, supported).passthrough,
            )
        }
    }

    @Test
    fun knownDolbyDigitalAndDtsAliasesCanUseConfirmedSupport() {
        listOf("ac3", "AC-3", "a52 ", "A_AC3", "e-ac-3", "ec-3", "A_EAC3", "dts", "dca").forEach { codec ->
            assertTrue(
                codec,
                decideVlcAudioOutput(VlcAudioOutputMode.AUTO, codec, 6, 48_000, supported).passthrough,
            )
        }
    }

    @Test
    fun unknownInvalidOrAmbiguousChannelLayoutsRemainPcm() {
        listOf(null, 0, -1, 3, 4, 5, 7, 9, 16).forEach { channels ->
            assertFalse(
                "channels=" + channels,
                decideVlcAudioOutput(VlcAudioOutputMode.AUTO, "truehd", channels, 48_000, supported).passthrough,
            )
        }
    }

    @Test
    fun commonMonoStereoFivePointOneAndSevenPointOneCanUseConfirmedSupport() {
        listOf(1, 2, 6, 8).forEach { channels ->
            assertTrue(
                "channels=" + channels,
                decideVlcAudioOutput(VlcAudioOutputMode.AUTO, "truehd", channels, 48_000, supported).passthrough,
            )
        }
    }

    @Test
    fun unknownOrInvalidSampleRateNeverAssumes48Khz() {
        listOf(null, 0, -48_000).forEach { rate ->
            assertFalse(
                decideVlcAudioOutput(VlcAudioOutputMode.AUTO, "truehd", 8, rate, supported).passthrough,
            )
        }
        assertFalse(
            decideVlcAudioOutput(
                mode = VlcAudioOutputMode.AUTO,
                audioCodec = "truehd",
                directSupport = supported,
            ).passthrough,
        )
    }

    @Test
    fun api33RequiresCurrentHdmiRouteAndBitstreamSupport() {
        val evidence = VlcAudioRouteEvidence(33, currentRouteIsHdmi = true, directBitstreamSupported = true)

        assertTrue(evaluateVlcDirectAudioSupport(evidence).supported)
        assertFalse(evaluateVlcDirectAudioSupport(evidence.copy(currentRouteIsHdmi = false)).supported)
        assertFalse(evaluateVlcDirectAudioSupport(evidence.copy(directBitstreamSupported = false)).supported)
    }

    @Test
    fun connectedHdmiIsNotEvidenceOfCurrentHdmiOnApi33() {
        val evidence = legacyHdmiEvidence(33).copy(currentRouteIsHdmi = false)

        assertFalse(evaluateVlcDirectAudioSupport(evidence).supported)
    }

    @Test
    fun api33CurrentRouteQueryTakesPrecedenceOverMerelyConnectedOtherOutputs() {
        val evidence = VlcAudioRouteEvidence(
            sdkInt = 33,
            currentRouteIsHdmi = true,
            hdmiOutputCount = 1,
            nonHdmiOutputCount = 2,
            directBitstreamSupported = true,
        )

        assertTrue(evaluateVlcDirectAudioSupport(evidence).supported)
    }

    @Test
    fun legacyAllowsOnlyConfirmedSingleHdmiWithExactFormatSupport() {
        listOf(29, 30, 31, 32).forEach { sdk ->
            assertTrue(evaluateVlcDirectAudioSupport(legacyHdmiEvidence(sdk)).supported)
        }
    }

    @Test
    fun legacyCannotUseAllOutputDirectResultWhileBluetoothWiredUsbOrSpeakersAreAvailable() {
        val evidence = legacyHdmiEvidence().copy(nonHdmiOutputCount = 1)

        assertFalse(evaluateVlcDirectAudioSupport(evidence).supported)
    }

    @Test
    fun legacyDisconnectedHdmiNeverEnablesPassthrough() {
        assertFalse(evaluateVlcDirectAudioSupport(legacyHdmiEvidence().copy(hdmiPlugged = false)).supported)
        assertFalse(evaluateVlcDirectAudioSupport(legacyHdmiEvidence().copy(hdmiOutputCount = 0)).supported)
    }

    @Test
    fun legacyMultipleHdmiRoutesRemainAmbiguous() {
        assertFalse(evaluateVlcDirectAudioSupport(legacyHdmiEvidence().copy(hdmiOutputCount = 2)).supported)
    }

    @Test
    fun legacyNeedsBothAdvertisedProfileAndExactDirectSupport() {
        assertFalse(evaluateVlcDirectAudioSupport(legacyHdmiEvidence().copy(hdmiProfileSupported = false)).supported)
        assertFalse(evaluateVlcDirectAudioSupport(legacyHdmiEvidence().copy(directBitstreamSupported = false)).supported)
    }

    @Test
    fun versionsWithoutExactDirectFormatQueryFallBackEvenWhenHdmiIsAdvertised() {
        listOf(23, 26, 27, 28).forEach { sdk ->
            val decision = evaluateVlcDirectAudioSupport(legacyHdmiEvidence(sdk))
            assertFalse(decision.supported)
            assertTrue(decision.reason.contains("확인"))
        }
    }

    private fun legacyHdmiEvidence(sdk: Int = 32) = VlcAudioRouteEvidence(
        sdkInt = sdk,
        hdmiOutputCount = 1,
        nonHdmiOutputCount = 0,
        hdmiPlugged = true,
        hdmiProfileSupported = true,
        directBitstreamSupported = true,
    )
}

