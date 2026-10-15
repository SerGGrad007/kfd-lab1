package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

private fun exitCodeFor(rejected: Int): Int = if (rejected == 0) 0 else 1

fun runPeriod(command: Command.Period): Int {
    var rejected = 0
    var shown = 0
    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseLine(number.toInt(), line)) {
            is ParseResult.Rejected -> {
                rejected++
            }
            is ParseResult.Parsed -> {
                if (result.session.date in command.from..command.to) {
                    println(formatPeriodLine(result.session))
                    shown++
                }
            }
        }
    }
    println("Total: $shown")
    return exitCodeFor(rejected)
}

fun runType(command: Command.ByType): Int {

    val wanted = command.type.toString()
    var rejected = 0
    var shown = 0
    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseLine(number.toInt(), line)) {
            is ParseResult.Rejected -> {
                rejected++
            }
            is ParseResult.Parsed -> {
                if (result.session.typeLabel() == wanted) {
                    println(formatTypeLine(result.session))
                    shown++
                }
            }
        }
    }
    println("Total: $shown")
    return exitCodeFor(rejected)
}

fun runErrors(command: Command.Errors): Int {
    var total = 0
    var rejected = 0
    forEachScheduleLine(command.file) { number, line ->
        total++
        val result = parseLine(number.toInt(), line)
        if (result is ParseResult.Rejected) {
            println(formatRejection(result))
            rejected++
        }
    }
    println("Rejected: $rejected of $total")
    return exitCodeFor(rejected)
}
