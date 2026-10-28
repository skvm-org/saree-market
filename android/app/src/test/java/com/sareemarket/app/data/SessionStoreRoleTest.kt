package com.sareemarket.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreRoleTest {

    @Test
    fun legacyResellerRoleIsReadAsCustomer() {
        assertEquals(Role.CUSTOMER, roleFromStored("RESELLER"))
    }

    @Test
    fun currentRolesAreReadAsTheyAre() {
        assertEquals(Role.CUSTOMER, roleFromStored("CUSTOMER"))
        assertEquals(Role.SELLER, roleFromStored("SELLER"))
        assertEquals(Role.ADMIN, roleFromStored("ADMIN"))
    }

    @Test
    fun unknownOrMissingRoleMeansLoggedOut() {
        assertNull(roleFromStored("WHOLESALER"))
        assertNull(roleFromStored(null))
    }
}
