package top.wkbin.taixu.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEndpointPolicyTest {
    @Test
    fun acceptsHttpsAndExactLoopbackHttpHosts() {
        assertTrue(ProviderEndpointPolicy.isSafeBaseUrl("https://api.example.com/v1"))
        assertTrue(ProviderEndpointPolicy.isSafeBaseUrl("http://localhost:8080/v1"))
        assertTrue(ProviderEndpointPolicy.isSafeBaseUrl("http://127.0.0.1:8080"))
        assertTrue(ProviderEndpointPolicy.isSafeBaseUrl("http://[::1]:8080"))
    }

    @Test
    fun rejectsLookalikeHostsAndUnsafeSchemes() {
        assertFalse(ProviderEndpointPolicy.isSafeBaseUrl("http://localhost.evil/v1"))
        assertFalse(ProviderEndpointPolicy.isSafeBaseUrl("http://127.0.0.1.evil/v1"))
        assertFalse(ProviderEndpointPolicy.isSafeBaseUrl("file:///data/local/tmp/api"))
        assertFalse(ProviderEndpointPolicy.isSafeBaseUrl("https://user:pass@example.com/v1"))
    }
}
