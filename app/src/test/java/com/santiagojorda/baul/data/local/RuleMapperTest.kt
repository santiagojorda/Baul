package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.DriveMetadata
import com.santiagojorda.baul.domain.model.GooglePhotosMetadata
import com.santiagojorda.baul.domain.model.Rule
import com.santiagojorda.baul.domain.model.YouTubeMetadata
import com.santiagojorda.baul.domain.model.YouTubePrivacyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMapperTest {

    @Test
    fun `entidad YouTube con channelId se mapea a YouTubeMetadata y el resto queda nulo`() {
        val entity = RuleEntity(
            id = 1,
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.YOUTUBE,
            googleAccountEmail = "user@example.com",
            youTubeChannelId = "channel-1",
            youTubePlaylistId = "playlist-1",
            youTubePrivacyStatus = YouTubePrivacyStatus.UNLISTED,
            youTubeTags = listOf("tag1", "tag2"),
        )

        val domain = entity.toDomain()

        assertEquals(
            YouTubeMetadata(
                channelId = "channel-1",
                playlistId = "playlist-1",
                privacyStatus = YouTubePrivacyStatus.UNLISTED,
                tags = listOf("tag1", "tag2"),
            ),
            domain.youTubeMetadata,
        )
        assertNull(domain.googlePhotosMetadata)
        assertNull(domain.driveMetadata)
    }

    @Test
    fun `entidad YouTube sin channelId no genera metadata (fila inconsistente)`() {
        val entity = RuleEntity(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.YOUTUBE,
            googleAccountEmail = "user@example.com",
            youTubeChannelId = null,
        )

        assertNull(entity.toDomain().youTubeMetadata)
    }

    @Test
    fun `youTubePrivacyStatus nulo en la entidad cae a PRIVATE por default en el dominio`() {
        val entity = RuleEntity(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.YOUTUBE,
            googleAccountEmail = "user@example.com",
            youTubeChannelId = "channel-1",
            youTubePrivacyStatus = null,
        )

        assertEquals(YouTubePrivacyStatus.PRIVATE, entity.toDomain().youTubeMetadata?.privacyStatus)
    }

    @Test
    fun `entidad GooglePhotos se mapea a GooglePhotosMetadata aunque no tenga albumId`() {
        val entity = RuleEntity(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.GOOGLE_PHOTOS,
            googleAccountEmail = "user@example.com",
            photosAlbumId = null,
            photosAlbumName = "Vacaciones",
        )

        val domain = entity.toDomain()

        assertEquals(GooglePhotosMetadata(albumId = null, albumName = "Vacaciones"), domain.googlePhotosMetadata)
        assertNull(domain.youTubeMetadata)
        assertNull(domain.driveMetadata)
    }

    @Test
    fun `entidad Drive con driveFolderId se mapea a DriveMetadata`() {
        val entity = RuleEntity(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.DRIVE,
            googleAccountEmail = "user@example.com",
            driveFolderId = "folder-1",
        )

        assertEquals(DriveMetadata(destinationFolderId = "folder-1"), entity.toDomain().driveMetadata)
    }

    @Test
    fun `entidad Drive sin driveFolderId no genera metadata`() {
        val entity = RuleEntity(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.DRIVE,
            googleAccountEmail = "user@example.com",
            driveFolderId = null,
        )

        assertNull(entity.toDomain().driveMetadata)
    }

    @Test
    fun `campos comunes se preservan en toDomain`() {
        val entity = RuleEntity(
            id = 7,
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.GOOGLE_PHOTOS,
            googleAccountEmail = "user@example.com",
            deleteSourceAfterUpload = false,
            wifiOnly = false,
            isActive = false,
            createdAt = 1234L,
            folderRelativePath = "DCIM/Foo/",
            isAutoCreated = true,
        )

        val domain = entity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals(false, domain.deleteSourceAfterUpload)
        assertEquals(false, domain.wifiOnly)
        assertEquals(false, domain.isActive)
        assertEquals(1234L, domain.createdAt)
        assertEquals("DCIM/Foo/", domain.folderRelativePath)
        assertEquals(true, domain.isAutoCreated)
    }

    @Test
    fun `Rule con YouTubeMetadata se mapea a entidad con las columnas youtube pobladas y el resto nulas`() {
        val rule = Rule(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.YOUTUBE,
            googleAccountEmail = "user@example.com",
            youTubeMetadata = YouTubeMetadata(
                channelId = "channel-1",
                playlistId = "playlist-1",
                privacyStatus = YouTubePrivacyStatus.PUBLIC,
                tags = listOf("a"),
            ),
            createdAt = 1000,
        )

        val entity = rule.toEntity()

        assertEquals("channel-1", entity.youTubeChannelId)
        assertEquals("playlist-1", entity.youTubePlaylistId)
        assertEquals(YouTubePrivacyStatus.PUBLIC, entity.youTubePrivacyStatus)
        assertEquals(listOf("a"), entity.youTubeTags)
        assertNull(entity.photosAlbumId)
        assertNull(entity.photosAlbumName)
        assertNull(entity.driveFolderId)
    }

    @Test
    fun `toEntity con youTubeMetadata nulo deja youTubeTags como lista vacia, no null`() {
        val rule = Rule(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.DRIVE,
            googleAccountEmail = "user@example.com",
            driveMetadata = DriveMetadata(destinationFolderId = "folder-1"),
            createdAt = 1000,
        )

        val entity = rule.toEntity()

        assertEquals(emptyList<String>(), entity.youTubeTags)
        assertEquals("folder-1", entity.driveFolderId)
        assertNull(entity.youTubeChannelId)
    }

    @Test
    fun `Rule con GooglePhotosMetadata se mapea a entidad con photosAlbumId y photosAlbumName`() {
        val rule = Rule(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.GOOGLE_PHOTOS,
            googleAccountEmail = "user@example.com",
            googlePhotosMetadata = GooglePhotosMetadata(albumId = "album-1", albumName = "Vacaciones"),
            createdAt = 1000,
        )

        val entity = rule.toEntity()

        assertEquals("album-1", entity.photosAlbumId)
        assertEquals("Vacaciones", entity.photosAlbumName)
    }

    @Test
    fun `round trip toEntity-toDomain preserva la regla completa`() {
        val rule = Rule(
            id = 3,
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.YOUTUBE,
            googleAccountEmail = "user@example.com",
            youTubeMetadata = YouTubeMetadata(channelId = "channel-1", tags = listOf("x", "y")),
            deleteSourceAfterUpload = false,
            wifiOnly = false,
            isActive = true,
            createdAt = 999,
            folderRelativePath = null,
            isAutoCreated = false,
        )

        assertEquals(rule, rule.toEntity().toDomain())
    }
}
