package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RepositoryTest {
    private val date = LocalDate.of(2026, 10, 5)
    private fun cancelled(topic: String) = Cancelled(date, topic)

    private class FailingRepository : SessionRepository {
        override fun add(session: Session) { throw IllegalStateException("boom") }
        override fun addAll(sessions: List<Session>) { throw IllegalStateException("boom") }
        override fun all(): List<Session> = emptyList()
    }

    @Test
    fun `add and addAll keep order and duplicates`() {
        val repo = InMemorySessionRepository()
        val a = cancelled("A")
        val b = cancelled("B")
        repo.add(a)
        repo.addAll(listOf(b, a))
        val all = repo.all()
        assertEquals(3, all.size)
        assertSame(a, all[0])
        assertSame(b, all[1])
        assertSame(a, all[2])
    }

    @Test
    fun `snapshot does not change after later adds`() {
        val repo = InMemorySessionRepository()
        repo.add(cancelled("A"))
        val snapshot = repo.all()
        repo.add(cancelled("B"))
        assertEquals(1, snapshot.size)
        assertEquals(2, repo.all().size)
    }

    @Test
    fun `snapshot cannot be used to change the repository`() {
        val repo = InMemorySessionRepository()
        repo.add(cancelled("A"))
        assertFailsWith<UnsupportedOperationException> {
            (repo.all() as MutableList<Session>).add(cancelled("B"))
        }
        assertEquals(1, repo.all().size)
    }

    @Test
    fun `changing the list passed to addAll does not affect the repository`() {
        val repo = InMemorySessionRepository()
        val source = mutableListOf<Session>(cancelled("A"), cancelled("B"))
        repo.addAll(source)
        source.clear()
        assertEquals(2, repo.all().size)
    }

    @Test
    fun `audit counts single and batch additions`() {
        val audited = AuditedSessionRepository(InMemorySessionRepository())
        audited.add(cancelled("A"))
        audited.addAll(listOf(cancelled("B"), cancelled("C"), cancelled("D")))

        assertEquals(4, audited.all().size)
        assertEquals(4, audited.totalAdded)
        assertEquals(listOf(AuditEvent("add", 1), AuditEvent("addAll", 3)), audited.events)
    }

    @Test
    fun `empty batch creates an event with count zero`() {
        val audited = AuditedSessionRepository(InMemorySessionRepository())
        audited.addAll(emptyList())
        assertEquals(listOf(AuditEvent("addAll", 0)), audited.events)
        assertEquals(0, audited.totalAdded)
    }

    @Test
    fun `all does not create events`() {
        val audited = AuditedSessionRepository(InMemorySessionRepository())
        audited.add(cancelled("A"))
        audited.all()
        audited.all()
        assertEquals(1, audited.events.size)
    }

    @Test
    fun `failed operations are not logged`() {
        val audited = AuditedSessionRepository(FailingRepository())
        assertFailsWith<IllegalStateException> { audited.add(cancelled("A")) }
        assertFailsWith<IllegalStateException> { audited.addAll(listOf(cancelled("B"))) }
        assertEquals(0, audited.events.size)
        assertEquals(0, audited.totalAdded)
    }

    @Test
    fun `journal returned to callers cannot be modified`() {
        val audited = AuditedSessionRepository(InMemorySessionRepository())
        audited.add(cancelled("A"))
        assertFailsWith<UnsupportedOperationException> {
            (audited.events as MutableList<AuditEvent>).clear()
        }
        assertEquals(1, audited.events.size)
    }
}
