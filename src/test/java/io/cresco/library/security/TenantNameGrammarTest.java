package io.cresco.library.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A tenant name has no '.': T.t1. must not be a prefix of another tenant's namespace (T.t1.x.). */
class TenantNameGrammarTest {

    @Test
    void aDottedTenantGetsNoNamespace() {
        assertTrue(CrescoIdentity.isValidTenant("t1"));
        assertFalse(CrescoIdentity.isValidTenant("t1.x"));
        assertTrue(CrescoIdentity.isValidName("t1.x"), "other names keep '.'");
        assertEquals("T.t1.inbox", TenantNamespace.qualify("t1", "inbox"));
        assertThrows(IllegalArgumentException.class, () -> TenantNamespace.qualify("t1.x", "inbox"));
        assertThrows(IllegalArgumentException.class, () -> TenantNamespace.prefix("t1.x"));
        assertEquals("inbox", TenantNamespace.qualify(null, "inbox"), "no tenant: unchanged, as before");
    }
}
