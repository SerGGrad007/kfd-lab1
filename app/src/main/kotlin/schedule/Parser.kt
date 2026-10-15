package schedule

import java.time.DateTimeException
import java.time.LocalDate

sealed interface RejectReason {
    data object InvalidSeparator : RejectReason
    data class WrongFieldCount(val actual: Int) : RejectReason
    data class EmptyField(val field: String) : RejectReason
    data class InvalidDate(val raw: String) : RejectReason
    data class UnknownType(val raw: String) : RejectReason
    data class NotEnoughTeachers(val type: String, val required: Int, val actual: Int) : RejectReason
    data class ControlCharacter(val field: String) : RejectReason
    data class OuterWhitespace(val field: String) : RejectReason
    data class CancelledFieldNotDash(val field: String) : RejectReason
    data object DashLocation : RejectReason
    data class InvalidTeacher(val raw: String) : RejectReason
    data class DuplicateTeacher(val name: String) : RejectReason
}

sealed interface ParseResult {
    data class Parsed(val session: Session) : ParseResult
    data class Rejected(val lineNumber: Int, val reason: RejectReason) : ParseResult
}

private val FIELD_NAMES = listOf("date", "type", "topic", "location", "teachers")
private val KNOWN_TYPES = setOf("LECTURE", "PRACTICE", "EXAM", "CANCELLED")
private val DATE_SHAPE = Regex("""\d{4}-\d{2}-\d{2}""")
private const val EXAM_MIN_TEACHERS = 2


fun parseLine(lineNumber: Int, line: String): ParseResult {
    fun reject(reason: RejectReason) = ParseResult.Rejected(lineNumber, reason)

    if ('|' !in line) return reject(RejectReason.InvalidSeparator)


    val fields = line.split('|')
    if (fields.size != FIELD_NAMES.size) return reject(RejectReason.WrongFieldCount(fields.size))

    for ((index, value) in fields.withIndex()) {
        if (value.isBlank()) return reject(RejectReason.EmptyField(FIELD_NAMES[index]))
    }
    val (rawDate, type, topic, location, teachersField) = fields


    val date = parseDate(rawDate) ?: return reject(RejectReason.InvalidDate(rawDate))


    if (type !in KNOWN_TYPES) return reject(RejectReason.UnknownType(type))

    val teacherNames = teachersField.split(',')

    if (type == "EXAM") {
        val usable = teacherNames.count { it.isNotBlank() && it != "-" }
        if (usable < EXAM_MIN_TEACHERS) {
            return reject(RejectReason.NotEnoughTeachers(type, EXAM_MIN_TEACHERS, usable))
        }
    }


    val checked = listOf("topic" to topic, "location" to location, "teachers" to teachersField)
    for ((name, value) in checked) {
        if (value.any { it.isISOControl() }) return reject(RejectReason.ControlCharacter(name))
    }

    if (topic.trim() != topic) return reject(RejectReason.OuterWhitespace("topic"))
    if (location.trim() != location) return reject(RejectReason.OuterWhitespace("location"))
    if (teacherNames.any { it.isNotBlank() && it.trim() != it }) {
        return reject(RejectReason.OuterWhitespace("teachers"))
    }

    if (type == "CANCELLED") {
        if (location != "-") return reject(RejectReason.CancelledFieldNotDash("location"))
        if (teachersField != "-") return reject(RejectReason.CancelledFieldNotDash("teachers"))
        return ParseResult.Parsed(Cancelled(date, topic))
    }

    if (location == "-") return reject(RejectReason.DashLocation)

    teacherNames.firstOrNull { it.isBlank() || it == "-" }?.let {
        return reject(RejectReason.InvalidTeacher(it))
    }

    val seen = HashSet<String>()
    for (name in teacherNames) {
        if (!seen.add(name)) return reject(RejectReason.DuplicateTeacher(name))
    }

    val session: Session = when (type) {
        "LECTURE" -> Lecture(date, topic, location, teacherNames)
        "PRACTICE" -> Practice(date, topic, location, teacherNames)
        else -> Exam(date, topic, location, teacherNames)
    }
    return ParseResult.Parsed(session)
}

private fun parseDate(raw: String): LocalDate? {
    if (!DATE_SHAPE.matches(raw)) return null
    val year = raw.substring(0, 4).toInt()
    val month = raw.substring(5, 7).toInt()
    val day = raw.substring(8, 10).toInt()
    if (year !in 1..9999) return null
    return try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }
}
