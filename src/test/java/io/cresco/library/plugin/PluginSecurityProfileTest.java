package io.cresco.library.plugin;

import io.cresco.library.agent.AgentService;
import io.cresco.library.security.SecurityProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/** A plugin reads the node's security profile from its controller ({@link PluginBuilder#getSecurityProfile()}), failing closed. */
class PluginSecurityProfileTest {

    /** An AgentService that answers {@code getSecurityProfile} with {@code answer} (or throws {@code fail}) and nothing else. */
    private static AgentService agent(SecurityProfile answer, RuntimeException fail) {
        InvocationHandler h = (proxy, m, args) -> {
            if (m.getName().equals("getSecurityProfile")) {
                if (fail != null) throw fail;
                return answer;
            }
            if (m.getName().equals("isSecurityEnabled")) return true;
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class || r == long.class || r == short.class || r == byte.class) return 0;
            if (r == double.class || r == float.class) return 0.0;
            return null;
        };
        return (AgentService) Proxy.newProxyInstance(AgentService.class.getClassLoader(), new Class<?>[]{AgentService.class}, h);
    }

    private static PluginBuilder plugin(AgentService as) {
        return new PluginBuilder(as, "io.cresco.test.Plugin", null, new HashMap<>());
    }

    @Test
    void theControllersProfileIsThePlugins() {
        assertEquals(SecurityProfile.PRODUCTION, plugin(agent(SecurityProfile.PRODUCTION, null)).getSecurityProfile());
        assertEquals(SecurityProfile.STANDARD, plugin(agent(SecurityProfile.STANDARD, null)).getSecurityProfile());
        assertEquals(SecurityProfile.INSECURE, plugin(agent(SecurityProfile.INSECURE, null)).getSecurityProfile());
    }

    @Test
    void noAnswerFailsClosed() {
        assertEquals(SecurityProfile.PRODUCTION, plugin(agent(null, null)).getSecurityProfile(), "no answer");
        assertEquals(SecurityProfile.PRODUCTION, plugin(agent(null, new IllegalStateException("controller stopping"))).getSecurityProfile(),
                "the controller could not be asked");
    }

    @Test
    void aPluginsOwnConfigurationCannotChangeIt() {
        HashMap<String, Object> cfg = new HashMap<>();
        cfg.put(SecurityProfile.KEY, "standard");
        PluginBuilder pb = new PluginBuilder(agent(SecurityProfile.PRODUCTION, null), "io.cresco.test.Plugin", null, cfg);
        assertEquals(SecurityProfile.PRODUCTION, pb.getSecurityProfile());
    }
}
