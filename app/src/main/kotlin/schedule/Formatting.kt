package schedule

fun escapeValue(value: String): String = buildString {
    for (ch in value) {
        when {
            ch == '\\' -> append("\\\\")
            ch == '"' -> append("\\\"")
            ch.code in 0x20..0x7E -> append(ch)
            else -> append("\\u").append(Integer.toHexString(ch.code).uppercase().padStart(4, '0'))
        }
    }
}

fun describe(reason: RejectReason): String = when (reason) {
    RejectReason.InvalidSeparator -> "invalid field separator"
    is RejectReason.WrongFieldCount -> "got ${reason.actual} fields, expected 5"
    is RejectReason.EmptyField -> "empty required field \"${reason.field}\""
    is RejectReason.InvalidDate -> "invalid date \"${escapeValue(reason.raw)}\""
    is RejectReason.UnknownType -> "unknown session type \"${escapeValue(reason.raw)}\""
    is RejectReason.NotEnoughTeachers ->
        "type \"${reason.type}\" requires at least ${reason.required} teachers, got ${reason.actual}"
    is RejectReason.ControlCharacter -> "control character in field \"${reason.field}\""
    is RejectReason.OuterWhitespace -> "field \"${reason.field}\" has leading or trailing whitespace"
    is RejectReason.CancelledFieldNotDash -> "field \"${reason.field}\" must be \"-\" for CANCELLED"
    RejectReason.DashLocation -> "location must not be \"-\""
    is RejectReason.InvalidTeacher -> "invalid teacher name \"${escapeValue(reason.raw)}\""
    is RejectReason.DuplicateTeacher -> "duplicate teacher \"${escapeValue(reason.name)}\""
}

fun formatRejection(rejected: ParseResult.Rejected): String =
    "line ${rejected.lineNumber}: ${describe(rejected.reason)}"

fun Session.typeLabel(): String = when (this) {
    is Lecture -> "LECTURE"
    is Practice -> "PRACTICE"
    is Exam -> "EXAM"
    is Cancelled -> "CANCELLED"
}

fun Session.locationOrDash(): String = when (this) {
    is Lecture -> location
    is Practice -> location
    is Exam -> location
    is Cancelled -> "-"
}

fun Session.teachersOrDash(): String = when (this) {
    is Lecture -> teachers.joinToString(", ")
    is Practice -> teachers.joinToString(", ")
    is Exam -> teachers.joinToString(", ")
    is Cancelled -> "-"
}

fun formatPeriodLine(session: Session): String =
    "${session.date} | ${session.typeLabel()} | ${session.topic} | ${session.locationOrDash()}"

fun formatTypeLine(session: Session): String =
    "${formatPeriodLine(session)} | ${session.teachersOrDash()}"
