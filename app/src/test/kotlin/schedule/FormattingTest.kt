package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {
    private fun message(reason: RejectReason, line: Int = 7) =
        formatRejection(ParseResult.Rejected(line, reason))

    @Test
    fun `mandatory messages`() {
        assertEquals("line 7: invalid date \"2026-13-45\"", message(RejectReason.InvalidDate("2026-13-45")))
        assertEquals("line 7: empty required field \"date\"", message(RejectReason.EmptyField("date")))
        assertEquals("line 7: invalid field separator", message(RejectReason.InvalidSeparator))
        assertEquals("line 7: unknown session type \"SEMINAR\"", message(RejectReason.UnknownType("SEMINAR")))
        assertEquals(
            "line 7: type \"EXAM\" requires at least 2 teachers, got 1",
            message(RejectReason.NotEnoughTeachers("EXAM", 2, 1)),
        )
        assertEquals("line 7: got 6 fields, expected 5", message(RejectReason.WrongFieldCount(6)))
    }

    @Test
    fun `escaping`() {
        assertEquals("a\\\\b", escapeValue("a\\b"))
        assertEquals("say \\\"hi\\\"", escapeValue("say \"hi\""))
        assertEquals("a\\u000Ab", escapeValue("a\nb"))
        assertEquals("\\u041F", escapeValue("П"))
        assertEquals("\\uD83D\\uDE00", escapeValue("\uD83D\uDE00"))
        assertEquals("plain text 123", escapeValue("plain text 123"))
    }

    @Test
    fun `values in messages are escaped`() {
        assertEquals(
            "line 1: unknown session type \"A\\u000AB\"",
            message(RejectReason.UnknownType("A\nB"), 1),
        )
    }

    @Test
    fun `period and type lines`() {
        val date = LocalDate.of(2026, 10, 15)
        val exam = Exam(date, "Oral exam 1", "Room 401", listOf("Portnov", "Stepanova"))
        assertEquals("2026-10-15 | EXAM | Oral exam 1 | Room 401", formatPeriodLine(exam))
        assertEquals("2026-10-15 | EXAM | Oral exam 1 | Room 401 | Portnov, Stepanova", formatTypeLine(exam))

        val cancelled = Cancelled(LocalDate.of(2026, 10, 19), "Lecture postponed")
        assertEquals("2026-10-19 | CANCELLED | Lecture postponed | -", formatPeriodLine(cancelled))
        assertEquals("2026-10-19 | CANCELLED | Lecture postponed | - | -", formatTypeLine(cancelled))
    }
}
