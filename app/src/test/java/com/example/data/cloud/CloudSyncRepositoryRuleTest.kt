package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.UUID

@Config(sdk = [34], manifest = Config.NONE)
class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndGetFavorite_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)
        val itemId = "m_${UUID.randomUUID()}"

        val addRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addFavorite(itemId, "match", "IND vs AUS")
        }
        assertTrue(addRes.isSuccess)

        val listRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getUserFavorites()
        }
        assertTrue(listRes.isSuccess)
        assertTrue(listRes.getOrThrow().any { it.itemId == itemId && it.userId == uid })
    }

    @Test
    fun getFavoriteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        val itemId = "m_${UUID.randomUUID()}"
        val favId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addFavorite(itemId, "match", "ENG vs RSA").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore, auth)
        val bobRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getFavoriteById(aliceUid, favId)
        }
        assertTrue(bobRes.isFailure)
        val fsEx = bobRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observePublicCatalogItems_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore, auth)

        val caught = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePublicCatalogItems().first()
            }
        }.exceptionOrNull()

        val fsEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    @Test
    fun upsertAndObservePublicCatalogItem_authenticatedUser_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)
        val rawId = "live_${UUID.randomUUID()}"

        val upsertRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertPublicCatalogItem(rawId, "match", "LIV vs MCI", "{\"id\":\"$rawId\"}")
        }
        assertTrue(upsertRes.isSuccess)
        val docId = upsertRes.getOrThrow()

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePublicCatalogItems().first { list -> list.any { it.id == docId } }
        }
        assertTrue(items.any { it.id == docId && it.title == "LIV vs MCI" })
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
