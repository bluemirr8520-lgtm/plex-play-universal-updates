package io.mirr.plexplay.data

import org.junit.Assert.*
import org.junit.Test

class PlexResourcesOwnershipTest {
    @Test fun ownershipIsScopedToEachServerAndEveryEndpoint() {
        val servers = parse("""
            <MediaContainer>
              <Device name="Owner" provides="player, server" owned="1" accessToken="owner-fixture">
                <Connection uri="https://owner/" local="0" relay="0"/>
                <Connection uri="http://local/" local="1" relay="0"/>
              </Device>
              <Device name="Shared" provides="server" owned="0" accessToken="shared-fixture">
                <Connection uri="https://shared/" local="0" relay="1"/>
              </Device>
              <Device name="Legacy" provides="server">
                <Connection uri="https://legacy/" local="0" relay="0"/>
              </Device>
            </MediaContainer>
        """)
        assertEquals(4, servers.size)
        assertEquals(listOf(true, true), servers.filter { it.serverName == "Owner" }.map { it.isOwned })
        assertEquals("owner-fixture", servers.first { it.serverName == "Owner" }.token)
        assertEquals(false, servers.single { it.serverName == "Shared" }.isOwned)
        assertEquals("shared-fixture", servers.single { it.serverName == "Shared" }.token)
        assertNull(servers.single { it.serverName == "Legacy" }.isOwned)
        assertEquals("account-fixture", servers.single { it.serverName == "Legacy" }.token)
        assertEquals("Shared", servers.last().serverName)
        assertFalse(servers.any { it.uri.endsWith('/') })
    }

    @Test fun unknownOwnershipIsNotMistakenForShared() {
        for (attribute in listOf("", "owned=\"\"", "owned=\"unexpected\"")) {
            assertNull(parse("""<MediaContainer><Device provides="server" $attribute><Connection uri="https://fixture"/></Device></MediaContainer>""").single().isOwned)
        }
    }

    @Test fun nonServersAndOrphanConnectionsDoNotInheritOwnership() {
        val servers = parse("""
            <MediaContainer>
              <Device provides="server" owned="0"><Connection uri="https://shared"/></Device>
              <Connection uri="https://orphan"/>
              <Device provides="player" owned="1"><Connection uri="https://player"/></Device>
              <Device provides="server" owned="1"><Connection uri="https://owner"/></Device>
            </MediaContainer>
        """)
        assertEquals(setOf("https://shared", "https://owner"), servers.map { it.uri }.toSet())
        assertTrue(servers.single { it.uri == "https://owner" }.isOwned!!)
    }

    private fun parse(xml: String): List<PlexServerConnection> =
        xml.trimIndent().byteInputStream().use { parsePlexServerConnections(it, "account-fixture") }
}
