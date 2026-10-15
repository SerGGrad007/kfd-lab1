package schedule

import kotlin.test.Test

import java.time.LocalDate

import kotlin.test.assertFailsWith

import kotlin.test.assertEquals

import kotlin.test.assertTrue

class SessionTest {
    @Test
    fun `blank topic is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Lecture(LocalDate.of(2026, 10, 5), "", "Room 312", listOf("Portnov"))
        }
    }
    @Test
    fun `invalid year`() {
        assertFailsWith<IllegalArgumentException> {
            Lecture(LocalDate.of(10001, 9, 25), "VibeCoding", "Room 67", listOf("Portnoy"))
        }
    }
    @Test
    fun `but nobody came`() {
        assertFailsWith<IllegalArgumentException> {
            Lecture(LocalDate.of(2007, 10, 20), "HobbyHorsing", "Canteen", listOf())
        }
    }
    @Test
    fun `one teacher on exam`() {
        assertFailsWith<IllegalArgumentException> {
            Exam(LocalDate.of(2025, 4, 15), "?", "Room 404", listOf("Walter White"))
        }
    }
    @Test
    fun `two same teachers on exam`() {
        assertFailsWith<IllegalArgumentException> {
            Exam(LocalDate.of(2023, 8, 27), "Calulus-2", "Room 117", listOf("Irina Vyacheslavovna", "Irina Vyacheslavovna"))
        }
    }

    @Test
    fun `year 0 is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Cancelled(LocalDate.of(0, 1, 1), "Topic")
        }
    }

    @Test
    fun `year 10000 is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Cancelled(LocalDate.of(10000, 1, 1), "Topic")
        }
    }

    @Test
    fun `boundary years 1 and 9999 are accepted`() {
        Cancelled(LocalDate.of(1, 1, 1), "Topic")
        Cancelled(LocalDate.of(9999, 12, 31), "Topic")
    }

    @Test
    fun `topic with outer whitespace is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Cancelled(LocalDate.of(2026, 10, 5), " Topic")
        }
    }

    @Test
    fun `topic with control character is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Cancelled(LocalDate.of(2026, 10, 5), "Top\nic")
        }
    }

    @Test
    fun `inner spaces are allowed`() {
        val lecture = Lecture(LocalDate.of(2026, 10, 5), "Classes and delegation", "Room 312", listOf("Ivan Ivanov"))
        assertEquals("Classes and delegation", lecture.topic)
    }

    @Test
    fun `dash location is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Lecture(LocalDate.of(2026, 10, 5), "Topic", "-", listOf("Ivanov"))
        }
    }

    @Test
    fun `blank location is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Practice(LocalDate.of(2026, 10, 5), "Topic", "  ", listOf("Ivanov"))
        }
    }

    @Test
    fun `practice without teachers is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Practice(LocalDate.of(2026, 10, 5), "Topic", "Room 1", emptyList())
        }
    }

    @Test
    fun `dash teacher is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Lecture(LocalDate.of(2026, 10, 5), "Topic", "Room 1", listOf("-"))
        }
    }

    @Test
    fun `blank teacher is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Lecture(LocalDate.of(2026, 10, 5), "Topic", "Room 1", listOf(""))
        }
    }

    @Test
    fun `teacher names are case sensitive`() {
        val exam = Exam(LocalDate.of(2026, 10, 5), "Topic", "Room 1", listOf("Ivanov", "ivanov"))
        assertEquals(2, exam.teachers.size)
    }

    @Test
    fun `teacher order is preserved`() {
        val exam = Exam(LocalDate.of(2026, 10, 5), "Topic", "Room 1", listOf("Stepanova", "Portnov"))
        assertEquals(listOf("Stepanova", "Portnov"), exam.teachers)
    }

    @Test
    fun `all four session types can be created`() {
        val date = LocalDate.of(2026, 10, 5)
        val lecture = Lecture(date, "L", "Room 1", listOf("A"))
        val practice = Practice(date, "P", "Room 2", listOf("B"))
        val exam = Exam(date, "E", "Room 3", listOf("C", "D"))
        val cancelled = Cancelled(date, "X")

        assertEquals("Room 1", lecture.location)
        assertEquals(listOf("B"), practice.teachers)
        assertEquals(listOf("C", "D"), exam.teachers)
        assertEquals("X", cancelled.topic)
    }

    @Test
    fun `changing the source list does not affect the session`() {
        val names = mutableListOf("Ivanov", "Petrov")
        val exam = Exam(LocalDate.of(2026, 10, 5), "Topic", "Room 1", names)

        names.clear()

        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
    }

    @Test
    fun `teachers list from the object cannot be modified`() {
        val exam = Exam(LocalDate.of(2026, 10, 5), "Topic", "Room 1", listOf("Ivanov", "Petrov"))

        assertFailsWith<UnsupportedOperationException> {
            (exam.teachers as MutableList<String>).add("Sidorov")
        }
        assertEquals(2, exam.teachers.size)
    }

}