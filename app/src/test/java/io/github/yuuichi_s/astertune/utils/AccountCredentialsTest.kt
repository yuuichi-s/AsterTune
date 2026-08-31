package io.github.yuuichi_s.astertune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundaries of [normalizeDataSyncId] and [isSameAccount]: the two decisions that keep a fetched
 * result attached to the account it was fetched for.
 */
class AccountCredentialsTest {

    private val cookie = "SAPISID=abc; __Secure-3PAPISID=def"
    private val otherCookie = "SAPISID=xyz; __Secure-3PAPISID=uvw"

    @Test
    fun normalize_keepsPlainId() {
        assertEquals("id1", normalizeDataSyncId("id1"))
    }

    @Test
    fun normalize_keepsIdBeforeTrailingSeparator() {
        assertEquals("id1", normalizeDataSyncId("id1||"))
    }

    @Test
    fun normalize_keepsIdAfterLeadingSeparator() {
        assertEquals("id2", normalizeDataSyncId("id1||id2"))
    }

    @Test
    fun normalize_passesNullThrough() {
        assertNull(normalizeDataSyncId(null))
    }

    @Test
    fun sameCredentials_stores() {
        assertTrue(isSameAccount(cookie, "id1", cookie, "id1"))
    }

    @Test
    fun changedCookie_doesNotStore() {
        assertFalse(isSameAccount(cookie, "id1", otherCookie, "id1"))
    }

    @Test
    fun changedDataSyncId_doesNotStore() {
        assertFalse(isSameAccount(cookie, "id1", cookie, "id2"))
    }

    @Test
    fun signedOut_doesNotStore() {
        assertFalse(isSameAccount(cookie, "id1", null, null))
        assertFalse(isSameAccount(null, null, null, null))
    }

    @Test
    fun cookieWithoutSapisid_doesNotStore() {
        val noSapisid = "__Secure-3PAPISID=def"
        assertFalse(isSameAccount(noSapisid, "id1", noSapisid, "id1"))
    }

    @Test
    fun unparseableCookie_doesNotStore() {
        val malformed = "not-a-cookie"
        assertFalse(isSameAccount(malformed, "id1", malformed, "id1"))
    }
}
