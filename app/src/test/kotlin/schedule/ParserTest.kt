package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTest {
    private fun assertRejected(expected: RejectReason, line: String) {
        assertEquals(ParseResult.Rejected(1, expected), parseLine(1, line), "line: $line")
    }

    @Test
    fun `valid lecture`() {
        val result = parseLine(1, "2026-10-05|LECTURE|Classes and delegation|Room 312|Portnov")
        assertIs<ParseResult.Parsed>(result)
        val lecture = assertIs<Lecture>(result.session)
        assertEquals(LocalDate.of(2026, 10, 5), lecture.date)
        assertEquals("Classes and delegation", lecture.topic)
        assertEquals("Room 312", lecture.location)
        assertEquals(listOf("Portnov"), lecture.teachers)
    }

    @Test
    fun `valid practice exam and cancelled`() {
        assertIs<Practice>(assertIs<ParseResult.Parsed>(
            parseLine(1, "2026-10-08|PRACTICE|Domain modeling|Room 312|Anokhin")).session)
        val exam = assertIs<Exam>(assertIs<ParseResult.Parsed>(
            parseLine(1, "2026-10-15|EXAM|Oral exam 1|Room 401|Portnov,Stepanova")).session)
        assertEquals(listOf("Portnov", "Stepanova"), exam.teachers)
        assertIs<Cancelled>(assertIs<ParseResult.Parsed>(
            parseLine(1, "2026-10-19|CANCELLED|Lecture postponed|-|-")).session)
    }

    @Test
    fun `line number is kept`() {
        assertEquals(ParseResult.Rejected(42, RejectReason.InvalidSeparator), parseLine(42, "bad"))
    }

    @Test
    fun `separator and field count`() {
        assertRejected(RejectReason.InvalidSeparator, "hello")
        assertRejected(RejectReason.InvalidSeparator, "")
        assertRejected(RejectReason.WrongFieldCount(6), "a|b|c|d|e|f")
        assertRejected(RejectReason.WrongFieldCount(4), "a|b|c|d")
        assertRejected(RejectReason.WrongFieldCount(3), "||")
    }

    @Test
    fun `first empty field wins and trailing empty field is counted`() {
        assertRejected(RejectReason.EmptyField("topic"), "2026-10-05|LECTURE||Room 1|Ivanov")
        assertRejected(RejectReason.EmptyField("location"), "2026-10-05|LECTURE|Topic|  |")
        assertRejected(RejectReason.EmptyField("teachers"), "2026-10-05|LECTURE|Topic|Room 1|")
    }

    @Test
    fun `date errors come before type errors`() {
        assertRejected(RejectReason.InvalidDate("2026-13-45"), "2026-13-45|SEMINAR|Topic|Room 1|Ivanov")
        assertRejected(RejectReason.InvalidDate("2026-02-30"), "2026-02-30|LECTURE|Topic|Room 1|Ivanov")
        assertRejected(RejectReason.InvalidDate("0000-01-01"), "0000-01-01|LECTURE|Topic|Room 1|Ivanov")
        assertRejected(RejectReason.InvalidDate("26-10-05"), "26-10-05|LECTURE|Topic|Room 1|Ivanov")
        assertRejected(RejectReason.InvalidDate("2026-1-5"), "2026-1-5|LECTURE|Topic|Room 1|Ivanov")
    }

    @Test
    fun `unknown type is case sensitive`() {
        assertRejected(RejectReason.UnknownType("SEMINAR"), "2026-10-05|SEMINAR|Topic|Room 1|Ivanov")
        assertRejected(RejectReason.UnknownType("lecture"), "2026-10-05|lecture|Topic|Room 1|Ivanov")
    }

    @Test
    fun `exam needs two usable teachers before other domain checks`() {
        assertRejected(RejectReason.NotEnoughTeachers("EXAM", 2, 1), "2026-10-05|EXAM|Topic|Room 1|Ivanov")
        assertRejected(RejectReason.NotEnoughTeachers("EXAM", 2, 1), "2026-10-05|EXAM|Topic|Room 1|Ivanov,-")
        assertRejected(RejectReason.NotEnoughTeachers("EXAM", 2, 0), "2026-10-05|EXAM|Topic|Room 1|-")
    }

    @Test
    fun `duplicate teachers`() {
        assertRejected(RejectReason.DuplicateTeacher("Ivanov"), "2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov")
    }

    @Test
    fun `control characters come before outer whitespace`() {
        assertRejected(RejectReason.ControlCharacter("topic"), "2026-10-05|LECTURE|To\u0007pic|Room 1|Ivanov")
        assertRejected(RejectReason.ControlCharacter("location"), "2026-10-05|LECTURE| Topic|Ro\u0007om|Ivanov")
    }

    @Test
    fun `outer whitespace`() {
        assertRejected(RejectReason.OuterWhitespace("topic"), "2026-10-05|LECTURE| Topic|Room 1|Ivanov")
        assertRejected(RejectReason.OuterWhitespace("location"), "2026-10-05|LECTURE|Topic|Room 1 |Ivanov")
        assertRejected(RejectReason.OuterWhitespace("teachers"), "2026-10-05|EXAM|Topic|Room 1|Ivanov, Petrov")
    }

    @Test
    fun `cancelled must have dashes`() {
        assertRejected(RejectReason.CancelledFieldNotDash("location"), "2026-10-19|CANCELLED|Topic|Room 1|-")
        assertRejected(RejectReason.CancelledFieldNotDash("teachers"), "2026-10-19|CANCELLED|Topic|-|Ivanov")
    }

    @Test
    fun `active session domain rules`() {
        assertRejected(RejectReason.DashLocation, "2026-10-05|LECTURE|Topic|-|Ivanov")
        assertRejected(RejectReason.InvalidTeacher("-"), "2026-10-05|LECTURE|Topic|Room 1|-")
        assertRejected(RejectReason.InvalidTeacher(""), "2026-10-05|LECTURE|Topic|Room 1|Ivanov,,Petrov")
    }

    @Test
    fun `teacher names are case sensitive`() {
        assertIs<ParseResult.Parsed>(parseLine(1, "2026-10-05|EXAM|Topic|Room 1|Ivanov,ivanov"))
    }
}
