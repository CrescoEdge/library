package io.cresco.library.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityProfileTest {
    @Test
    void parsesTheTwoProfilesAndDefaultsToStandard() {
        assertEquals(SecurityProfile.STANDARD, SecurityProfile.parse(null));
        assertEquals(SecurityProfile.STANDARD, SecurityProfile.parse(" "));
        assertEquals(SecurityProfile.PRODUCTION, SecurityProfile.parse("Production"));
        assertEquals(SecurityProfile.STANDARD, SecurityProfile.parse("standard"));
        assertNull(SecurityProfile.parse("insecure"), "insecure is never a profile name: it is broker_security_enabled=false");
        assertNull(SecurityProfile.parse("prod"));
        assertEquals("production", SecurityProfile.PRODUCTION.id());
    }
}
