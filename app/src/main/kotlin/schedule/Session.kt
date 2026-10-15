package schedule

import java.time.LocalDate

sealed interface Session {
    val date : LocalDate
    val topic: String
}

class Lecture(
    override val date: LocalDate,
    override val topic: String,
    val location : String,
    teachersInput : List<String>) : Session {

    val teachers : List<String> = java.util.List.copyOf(teachersInput)

    init {
        requireValidDate(date)
        requireValidText(topic, "topic")
        requireValidLocation(location, "location")
        requireValidTeachers(teachers)
    }
}

class Practice(
    override val date: LocalDate,
    override val topic: String,
    val location : String,
    teachersInput : List<String>) : Session {

    val teachers : List<String> = java.util.List.copyOf(teachersInput)

    init {
        requireValidDate(date)
        requireValidText(topic, "topic")
        requireValidLocation(location, "location")
        requireValidTeachers(teachers)
    }
}

class Exam(
    override val date: LocalDate,
    override val topic: String,
    val location : String,
    teachersInput : List<String>
) : Session {
    val teachers : List<String> = java.util.List.copyOf(teachersInput)
    init {
        requireValidDate(date)
        requireValidText(topic, "topic")
        requireValidLocation(location, "location")
        requireValidTeachers(teachers, 2)
    }
}

class Cancelled(
    override val date: LocalDate,
    override val topic: String) : Session {
    init {
        requireValidDate(date)
        requireValidText(topic, "topic")
    }
}

private fun requireValidDate(date: LocalDate) {
    require(date.year > 0 && date.year <= 9999){"year must be in 1..9999"}
}
private fun requireValidText(value: String, field: String) {
    require(value.isNotBlank()){ "$field must not be blank" }
    require(value.trim() == value){ "$field must not have leading or trailing whitespace" }
    require(value.none { it.isISOControl()}){ "$field must not have control symbol" }
}

private fun requireValidLocation(value: String, field: String) {
    requireValidText(value, field)
    require(value != "-"){ "$field must not be \"-\""}
}

private fun requireValidTeachers(teachers: List<String>, minCount: Int = 1) {
    require(teachers.size >= minCount) {
        "at least $minCount teacher(s) required, got ${teachers.size}"
    }
    val seen = mutableSetOf<String>()
    for (name in teachers) {
        requireValidText(name, "teacher")
        require(name != "-") { "teacher must not be \"-\"" }
        require(seen.add(name)) { "duplicate teacher \"$name\"" }
    }
}
