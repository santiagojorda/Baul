package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.DriveMetadata
import com.santiagojorda.baul.domain.model.GooglePhotosMetadata
import com.santiagojorda.baul.domain.model.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMapperTest {

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
    fun `toEntity con driveMetadata nulo deja photosAlbumId y photosAlbumName como null`() {
        val rule = Rule(
            folderUri = "content://tree/foo",
            folderDisplayName = "Foo",
            destinationType = DestinationType.DRIVE,
            googleAccountEmail = "user@example.com",
            driveMetadata = DriveMetadata(destinationFolderId = "folder-1"),
            createdAt = 1000,
        )

        val entity = rule.toEntity()

        assertEquals("folder-1", entity.driveFolderId)
        assertNull(entity.photosAlbumId)
        assertNull(entity.photosAlbumName)
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
            destinationType = DestinationType.DRIVE,
            googleAccountEmail = "user@example.com",
            driveMetadata = DriveMetadata(destinationFolderId = "folder-1"),
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
