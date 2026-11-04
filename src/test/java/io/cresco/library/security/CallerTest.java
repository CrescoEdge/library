package io.cresco.library.security;

import io.cresco.library.messaging.MsgEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CallerTest {

    private static MsgEvent msg() {
        return new MsgEvent(MsgEvent.Type.EXEC, "r", "a", null, "r", "a", null, false, false);
    }

    @Test
    void theFactoriesProduceTheContractForms() {
        Caller c = Caller.controller("t1", "r1", "a1");
        assertEquals("controller:t1/r1/a1", c.getPrincipal());
        assertEquals("t1", c.getTenant());
        assertEquals(Collections.singleton("infra"), c.getRoles());
        assertEquals("r1", c.getRegion());
        assertEquals("a1", c.getAgent());

        Caller p = Caller.plugin("t1", "r1", "a1", "plugin-1b2c-3d");
        assertEquals("plugin:t1/r1/a1/plugin-1b2c-3d", p.getPrincipal());
        assertEquals("plugin-1b2c-3d", p.getPluginId());
        assertTrue(p.hasRole("plugin"));
        assertFalse(p.hasRole("infra"));

        Caller u = Caller.api("t1", "alice@example", Arrays.asList("user", "admin"));
        assertEquals("api:t1/alice@example", u.getPrincipal());
        assertEquals("alice@example", u.getUser());
        assertEquals("admin,user", u.getRolesCsv());
        assertNull(u.getRegion());
    }

    @Test
    void anApiCallerCannotHoldInfraOrPlugin() {
        assertThrows(IllegalArgumentException.class, () -> Caller.api("t1", "bob", Collections.singleton("infra")));
        assertThrows(IllegalArgumentException.class, () -> Caller.api("t1", "bob", Collections.singleton("plugin")));
        assertThrows(IllegalArgumentException.class, () -> Caller.api("t1", "bob", Collections.emptySet()));
    }

    @Test
    void stampStripAndReadRoundTrip() {
        MsgEvent m = msg();
        Caller c = Caller.plugin("t1", "r1", "a1", "p1");
        c.stamp(m);
        assertEquals(c, Caller.fromMsgEvent(m));
        assertTrue(Caller.hasAny(m));
        Caller.strip(m);
        assertFalse(Caller.hasAny(m));
        assertNull(Caller.fromMsgEvent(m));
    }

    @Test
    void stampingOverwritesAForgedValue() {
        MsgEvent m = msg();
        m.setParam(Caller.PARAM_CALLER, "controller:t1/r1/a1");
        m.setParam(Caller.PARAM_TENANT, "t1");
        m.setParam(Caller.PARAM_ROLES, "infra");
        Caller.plugin("t1", "r1", "a1", "p1").stamp(m);
        assertEquals("plugin", m.getParam(Caller.PARAM_ROLES));
        assertEquals(Caller.Kind.PLUGIN, Caller.fromMsgEvent(m).getKind());
    }

    @Test
    void malformedOrInconsistentValuesAreNotACaller() {
        assertNull(Caller.parse("controller:t1/r1/a1", "t2", "infra"), "tenant mismatch");
        assertNull(Caller.parse("controller:t1/r1", "t1", "infra"), "too few parts");
        assertNull(Caller.parse("controller:t1/r1/a1/x", "t1", "infra"), "too many parts");
        assertNull(Caller.parse("root:t1/r1/a1", "t1", "infra"), "unknown kind");
        assertNull(Caller.parse("controller:t1/r1/a1", "t1", "infra,god"), "unknown role");
        assertNull(Caller.parse("controller:t1/r1/a1", "t1", ""), "no role");
        assertNull(Caller.parse("controller:t.1/r1/a1", "t.1", "infra"), "dotted tenant");
        assertNull(Caller.parse("api:t1/a/b", "t1", "user"), "user with separator");
        assertNull(Caller.parse("api:t1/a b", "t1", "user"), "user with space");
        assertNull(Caller.parse(null, "t1", "user"));
        assertNull(Caller.parse("controller:t1/r1/a1", null, "infra"));
    }

    @Test
    void anonymousHasEveryRole() {
        Caller a = Caller.anonymous();
        assertTrue(a.isAnonymous());
        assertEquals(Caller.ALL_ROLES, a.getRoles());
        MsgEvent m = msg();
        a.stamp(m);
        assertTrue(Caller.fromMsgEvent(m).isAnonymous());
    }
}
