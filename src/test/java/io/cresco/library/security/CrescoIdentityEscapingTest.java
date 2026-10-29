package io.cresco.library.security;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.*;

/** toX500Name escapes so that any parser reads back exactly the names it was given; the strict grammar. */
class CrescoIdentityEscapingTest {

    private static void roundTrips(String agent) {
        CrescoIdentity id = CrescoIdentity.of("t1", "rgA", agent, null);
        String dn = id.toX500Name();
        // JDK parser (what certificates are read with), via the canonical RFC 2253 form
        String rfc = new X500Principal(dn).getName();
        assertEquals(agent, CrescoIdentity.fromDN(rfc).getAgent(), "JDK read back a different name from " + dn);
        // the DN string itself, parsed directly
        assertEquals(agent, CrescoIdentity.fromDN(dn).getAgent(), "LdapName read back a different name from " + dn);
    }

    @Test
    void leadingAndTrailingSpacesAndALeadingHashSurviveARoundTrip() {
        roundTrips(" rc");
        roundTrips("rc ");
        roundTrips("#0c027263");
        roundTrips("a,b+c=d;e\"f<g>h\\\\i");
        roundTrips("plain-name");
    }

    @Test
    void theNameGrammar() {
        for (String ok : new String[]{"a", "rc", "agent-0b1c", "global-region", "rgn01", "a.b", "A9"}) assertTrue(CrescoIdentity.isValidName(ok), ok);
        for (String bad : new String[]{null, "", " rc", "rc ", "#rc", "a_b", "-a", "a-", "a,b", "a b", "\u0000", "x".repeat(65)})
            assertFalse(CrescoIdentity.isValidName(bad), String.valueOf(bad));
    }
}
