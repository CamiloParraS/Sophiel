package dev.sophiel.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildPairingTest {
    private val kv = HashMap<String, String>()
    private fun store() = ChildStore({ kv[it] }, { m -> m.forEach { (k, v) -> if (v == null) kv.remove(k) else kv[k] = v } })

    @Test fun `no role until chosen, then it sticks`() {
        assertNull(store().role)
        store().role = Role.Padre
        assertEquals(Role.Padre, store().role)
    }

    @Test fun `key is made once and survives a new claim`() {
        val k = store().key()
        assertEquals(32, k.size)
        assertArrayEquals(k, store().key())
    }

    @Test fun `paired stores token and id, clear drops them and the key`() {
        val s = store()
        assertFalse(s.paired)
        s.key()
        s.savePaired("p1", "tok")
        assertTrue(store().paired)
        assertEquals("p1", store().pairingId)
        assertEquals("tok", store().deviceToken)
        s.clear()
        assertFalse(store().paired)
        assertNull(store().deviceToken)
        assertEquals(32, store().key().size) // a later pairing gets a fresh key
    }

    @Test fun `relay frames parse, anything else is ignored`() {
        assertEquals(ChildFrame.Code("ab12"), ChildFrame.parse("""{"t":"code","claimCode":"ab12"}"""))
        assertEquals(ChildFrame.Paired("p", "d"), ChildFrame.parse("""{"t":"paired","pairingId":"p","deviceToken":"d"}"""))
        assertEquals(ChildFrame.Unpaired, ChildFrame.parse("""{"t":"unpaired"}"""))
        assertNull(ChildFrame.parse("""{"t":"req","rid":"x"}"""))
        assertNull(ChildFrame.parse("""{"t":"paired"}"""))
        assertNull(ChildFrame.parse("nope"))
    }

    @Test fun `ws url follows RELAY_URL scheme`() {
        assertEquals("ws://localhost:3000/child", ChildFrame.wsUrl("http://localhost:3000"))
        assertEquals("wss://x.fly.dev/child", ChildFrame.wsUrl("https://x.fly.dev"))
    }
}
