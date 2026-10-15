package schedule

data class AuditEvent(val operation: String, val count: Int)

class AuditedSessionRepository(
    private val delegate: SessionRepository,
) : SessionRepository by delegate {

    private val journal = mutableListOf<AuditEvent>()

    var totalAdded: Int = 0
        private set

    val events: List<AuditEvent>
        get() = java.util.List.copyOf(journal)

    override fun add(session: Session) {
        delegate.add(session)
        record("add", 1)
    }

    override fun addAll(sessions: List<Session>) {
        val batch = sessions.toList()
        delegate.addAll(batch)
        record("addAll", batch.size)
    }

    private fun record(operation: String, count: Int) {
        journal.add(AuditEvent(operation, count))
        totalAdded += count
    }
}
