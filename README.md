# «Ничего не падает» — расписание занятий на Kotlin

Консольное приложение для работы с расписанием. Оно читает файл с занятиями, отбирает корректные по дате или типу и сообщает об ошибках во входных данных. Главная идея работы: **модель нельзя испортить через публичный API**: ни при чтении файла, ни при прямом вызове конструктора из кода.

Лабораторная работа курса Kotlin, Академия МИФИ. Условие: [`docs/assignment.md`](docs/assignment.md).

## Содержание

1. [Требования и сборка](#требования-и-сборка)
2. [Запуск](#запуск)
3. [Структура проекта](#структура-проекта)
4. [Модель занятий](#модель-занятий)
5. [Реестр и журналирование через делегирование](#реестр-и-журналирование-через-делегирование)
6. [Разбор файла: ошибки как данные](#разбор-файла-ошибки-как-данные)
7. [Команды и коды завершения](#команды-и-коды-завершения)
8. [Проверка «сломай свою модель»](#проверка-сломай-свою-модель)
9. [Тесты и проверка результата](#тесты-и-проверка-результата)

---

## Требования и сборка

- **JDK 25** (указан в `JAVA_HOME` и выбран для Gradle в IntelliJ IDEA);
- **Python 3.10+** (только для `tools/verify.py` и генератора примеров);
- Kotlin 2.4.10, Gradle 9.5.1 и JUnit 6.1.3 отдельно ставить не нужно: используется Gradle Wrapper. Первая сборка скачивает зависимости из интернета.

Команды выполняются **из корня проекта**, где лежат `gradlew`, `settings.gradle.kts` и `samples/`.

Windows PowerShell:

```powershell
.\gradlew.bat build :app:installDist
```

Linux/macOS:

```bash
bash ./gradlew build :app:installDist
```

`build` компилирует приложение и запускает тесты, `installDist` создаёт скрипты запуска в `app/build/install/schedule/bin/`.

## Запуск

`schedule` в примерах из условия — это имя приложения, а не встроенная команда терминала.

Windows PowerShell:

```powershell
.\app\build\install\schedule\bin\schedule.bat period --file samples/clean.txt --from 2026-09-28 --to 2027-05-13
.\app\build\install\schedule\bin\schedule.bat type --file samples/dirty.txt --type EXAM
.\app\build\install\schedule\bin\schedule.bat errors --file samples/dirty.txt
```

Linux/macOS:

```bash
./app/build/install/schedule/bin/schedule period --file samples/clean.txt --from 2026-09-28 --to 2027-05-13
./app/build/install/schedule/bin/schedule type --file samples/dirty.txt --type EXAM
./app/build/install/schedule/bin/schedule errors --file samples/dirty.txt
```

Во время разработки можно запускать напрямую через Gradle:

```powershell
.\gradlew.bat :app:run --args="period --file samples/clean.txt --from 2026-09-28 --to 2027-05-13"
```

Путь к файлу отсчитывается от текущей рабочей папки. Путь с пробелами нужно брать в кавычки. Имя файла, начинающееся с `--`, указывается как `./--name`.

### Формат входного файла

Каждая строка состоит из пяти полей, разделённых `|`:

```
date|type|topic|location|teachers
2026-10-05|LECTURE|Classes and delegation|Room 312|Portnov
2026-10-08|PRACTICE|Domain modeling|Room 312|Anokhin
2026-10-15|EXAM|Oral exam 1|Room 401|Portnov,Stepanova
2026-10-19|CANCELLED|Lecture postponed|-|-
```

Допустимые типы: `LECTURE`, `PRACTICE`, `EXAM`, `CANCELLED` (регистр значим). Преподаватели разделяются запятой, отсутствующее значение записывается как `-`.

## Структура проекта

```
app/src/main/kotlin/schedule/
├── Cli.kt                          # (шаблон) разбор аргументов командной строки
├── Main.kt                         # (шаблон) точка входа, коды 2 при ошибках
├── FileLines.kt                    # (шаблон) построчное чтение файла с номером строки
├── Session.kt                      # интерфейс Session и четыре типа занятий, проверка инвариантов
├── Repository.kt                   # SessionRepository и InMemorySessionRepository
├── AuditedSessionRepository.kt     # обёртка с журналом (делегирование)
├── Parser.kt                       # parseLine, ParseResult, RejectReason
├── Formatting.kt                   # причины отказов → английские сообщения, форматы вывода
└── Commands.kt                     # runPeriod, runType, runErrors
app/src/test/kotlin/schedule/       # тесты модели, реестра, парсера, форматтера, CLI
samples/                            # входные файлы и эталоны *.expected
tools/                              # verify.py, generate_samples.py, check_samples.py
```

---

## Модель занятий

```kotlin
sealed interface Session {
    val date: LocalDate
    val topic: String
}
```

| Тип | Свойства | Ограничения |
|---|---|---|
| `Lecture` | `date`, `topic`, `location`, `teachers` | не менее 1 преподавателя |
| `Practice` | `date`, `topic`, `location`, `teachers` | не менее 1 преподавателя |
| `Exam` | `date`, `topic`, `location`, `teachers` | не менее 2 **разных** преподавателей |
| `Cancelled` | `date`, `topic` | **нет** свойств места и преподавателей, даже nullable |

Общие правила для всех типов (проверяются в `init` каждого класса):

- год в диапазоне `1..9999`;
- тема непустая, не состоит только из пробелов, без пробелов по краям и без управляющих символов;
- место действующего занятия непустое и не равно `-`;
- имена преподавателей: непустые, не `-`, без повторов, регистр значим (`Ivanov` ≠ `ivanov`), порядок сохраняется;
- внутренние пробелы разрешены в теме, месте и имени;
- **ничего не исправляется автоматически**: пробелы не обрезаются, регистр не меняется, дубликаты не удаляются, а выбрасывается `IllegalArgumentException` с понятным сообщением.

### Почему `sealed interface`, а не `open`/`abstract`

Набор типов занятий конечен и известен заранее. `sealed` даёт два преимущества:

1. никто снаружи не может добавить пятый тип и обойти правила модели;
2. `when (session)` в `Formatting.kt` (`typeLabel`, `locationOrDash`, `teachersOrDash`) проверяется компилятором на полноту без ветки `else`. Если появится новый тип, код перестанет компилироваться, а не сломается молча.

### Почему обычный `class`, а не `data class`

Выбран **обычный `class`** для всех четырёх типов:

- **`copy()` — это лазейка.** У `data class` автоматически генерируется `copy(...)`, а для публичного основного конструктора он принимает уже «сырые» значения. Приходится проверять, что он проходит через `init` (он проходит, но это нужно держать в голове и тестировать). Обычный класс не создаёт такой поверхности атаки вообще.
- **Защитная копия списка не совмещается с `data class`.** В основном конструкторе `data class` каждый параметр обязан быть `val`/`var` и становится публичным свойством. Мне же нужно принять изменяемый список (`teachersInput`) и хранить его защитную копию. Поэтому параметр конструктора не равен хранимому свойству, и это естественно выражается только в обычном классе.
- **Автогенерируемые `equals`/`hashCode`/`toString` здесь не нужны.** По условию одинаковые занятия допускаются и хранятся отдельно, а сравнение занятий по значению нигде не требуется.
- **Меньше неочевидного поведения.** `componentN()` и `copy()` — это дополнительный публичный API, который нужно поддерживать и защищать.

### Защита коллекций

```kotlin
class Lecture( teachersInput: List<String>) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachersInput)
}
```

`teachersInput` — это просто параметр конструктора, а не свойство, поэтому снаружи он недоступен. `java.util.List.copyOf` делает сразу два дела:

1. **копирует** содержимое: последующее `names.clear()` у вызывающего кода не затрагивает объект;
2. возвращает **неизменяемый** список: приведение `lecture.teachers as MutableList<String>` и вызов `add`/`clear` бросает `UnsupportedOperationException`.

Проверки (`requireValidTeachers`) выполняются уже над копией `teachers`, а не над исходным списком, поэтому между проверкой и сохранением список подменить нельзя.

---

## Реестр и журналирование через делегирование

```kotlin
interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}
```

`InMemorySessionRepository` хранит занятия во внутреннем `MutableList`:

- `add` и `addAll` сохраняют порядок и допускают одинаковые занятия;
- `addAll` копирует переданный список (`sessions.toList()`), поэтому его последующее изменение не затрагивает реестр;
- `all()` возвращает **снимок**: `java.util.List.copyOf(items)`. Позднее добавление в реестр не меняет ранее полученный снимок, а изменить внутреннее состояние через снимок невозможно.

### `AuditedSessionRepository`

```kotlin
class AuditedSessionRepository(
    private val delegate: SessionRepository,
) : SessionRepository by delegate {
    override fun add(session: Session)         { delegate.add(session);       record("add", 1) }
    override fun addAll(sessions: List<Session>) { val b = sessions.toList(); delegate.addAll(b); record("addAll", b.size) }
    // all() не переопределён: его целиком подставляет `by delegate`
}
```

- хранение выполняет внутренний реестр (`delegate`);
- журнал (`events: List<AuditEvent>`, `AuditEvent(operation, count)`) и счётчик `totalAdded` ведутся в памяти и наружу отдаются только как неизменяемая копия;
- запись в журнал делается **после** успешного вызова делегата: если делегат бросил исключение, события нет (тест `failed operations are not logged`);
- `all()` не создаёт событий;
- `addAll(emptyList())` создаёт событие с количеством `0`.

Пример: один `add` и один `addAll` из трёх занятий дают `totalAdded = 4` и `events.size = 2`.

### Вопрос: почему переопределить только `add` недостаточно для учёта `addAll`?

Делегирование Kotlin (`by delegate`) **не** превращает обёртку в наследника делегата. Компилятор просто генерирует методы-пересылки вида `override fun addAll(s) = delegate.addAll(s)`. Вызов идёт целиком во внутренний объект, и если `InMemorySessionRepository.addAll` внутри делает `items.addAll(...)`, то метод `add` обёртки **не вызывается ни разу**: внутренние вызовы `delegate` не знают про обёртку (`this` внутри делегата — это сам делегат, а не `AuditedSessionRepository`).

Поэтому, если переопределить только `add`, пакетное добавление окажется для журнала невидимым: добавятся 3 занятия, а событий и счётчика не прибавится. Каждый метод, который нужно учитывать, переопределяется явно (`add` и `addAll`), а `all()` остаётся на автоматической пересылке.

Обратная сторона: раз `addAll` записывает **одно** событие со своим размером, а `delegate.addAll` не вызывает `add` обёртки, двойного журналирования не возникает.

---

## Разбор файла: ошибки как данные

Результат разбора одной строки — sealed-иерархия, а не исключение и не готовая строка:

```kotlin
sealed interface ParseResult {
    data class Parsed(val session: Session) : ParseResult
    data class Rejected(val lineNumber: Int, val reason: RejectReason) : ParseResult
}

sealed interface RejectReason {   // структурированная причина, а не текст
    data object InvalidSeparator
    data class WrongFieldCount(val actual: Int)
    data class EmptyField(val field: String)
    data class InvalidDate(val raw: String)
    data class UnknownType(val raw: String)
    data class NotEnoughTeachers(val type: String, val required: Int, val actual: Int)
    data class ControlCharacter(val field: String)
    data class OuterWhitespace(val field: String)
    data class CancelledFieldNotDash(val field: String)
    data object DashLocation
    data class InvalidTeacher(val raw: String)
    data class DuplicateTeacher(val name: String)
}
```

Причина хранит **данные** (поле, сырое значение, числа), а преобразование в английское сообщение выполняет отдельная функция `describe(reason)` в `Formatting.kt` (`formatRejection` добавляет префикс `line N: `). Благодаря `sealed` `when` в `describe` проверяется на полноту. Некорректная строка даёт `Rejected`, и обработка следующих строк продолжается.

`parseLine` сначала сам проверяет формат и правила домена и только потом вызывает конструкторы моделей, поэтому конструкторы внутри парсера не бросают исключений. Исключения остаются для ошибок программирования, и их обработчик команд не перехватывает.

### Порядок проверок (один отказ на строку)

1. Нет `|` → неверный разделитель.
2. Полей не пять → неверное количество полей (конечные пустые поля считаются).
3. Первое пустое или полностью пробельное поле.
4. Неверный формат или значение даты (`YYYY-MM-DD`, год `0001..9999`, дата должна существовать).
5. Неизвестный тип.
6. У `EXAM` менее двух непустых элементов списка, отличных от `-`.
7. Остальное по порядку: управляющие символы → внешние пробелы → неверные поля `CANCELLED` → недопустимое место → недопустимый элемент списка преподавателей → повторное имя.

При нарушениях в нескольких полях выбирается первое поле в порядке следования, а в списке преподавателей — первый недопустимый элемент.

Примеры нескольких дефектов одновременно:

| Строка | Результат |
|---|---|
| `2026-13-45\|SEMINAR\|Topic\|Room 1\|Ivanov` | ошибка **даты** (приоритет 4 выше типа) |
| `2026-10-05\|EXAM\|Topic\|Room 1\|Ivanov,Ivanov` | ошибка **повторного имени** |

### Сообщения об ошибках

Обязательные (из условия):

| Причина | Сообщение |
|---|---|
| `InvalidDate` | `line N: invalid date "2026-13-45"` |
| `EmptyField` | `line N: empty required field "date"` |
| `InvalidSeparator` | `line N: invalid field separator` |
| `UnknownType` | `line N: unknown session type "SEMINAR"` |
| `NotEnoughTeachers` | `line N: type "EXAM" requires at least 2 teachers, got 1` |
| `WrongFieldCount` | `line N: got 6 fields, expected 5` |

Собственные (для остальных причин):

| Причина | Сообщение |
|---|---|
| `ControlCharacter` | `line N: control character in field "topic"` |
| `OuterWhitespace` | `line N: field "location" has leading or trailing whitespace` |
| `CancelledFieldNotDash` | `line N: field "teachers" must be "-" for CANCELLED` |
| `DashLocation` | `line N: location must not be "-"` |
| `InvalidTeacher` | `line N: invalid teacher name "-"` |
| `DuplicateTeacher` | `line N: duplicate teacher "Ivanov"` |

### Экранирование значений в сообщениях

Значения, подставляемые в сообщения (`escapeValue`), экранируются: `\` → `\\`, `"` → `\"`, управляющие и не-ASCII символы (всё вне `0x20..0x7E`) → `\uXXXX` (четыре шестнадцатеричные цифры на каждый UTF-16 символ). Так повреждённый вход не добавит в вывод перевод строки или кириллицу. Сами значения модели при этом не изменяются.

---

## Команды и коды завершения

```
schedule period --file PATH --from YYYY-MM-DD --to YYYY-MM-DD
schedule type   --file PATH --type LECTURE
schedule errors --file PATH
```

Разбор аргументов и диспетчеризация (`Cli.kt`, `Main.kt`) уже были в шаблоне; в `Commands.kt` реализованы `runPeriod`, `runType`, `runErrors`.

Пример `period`:

```
2026-10-05 | LECTURE | Classes and delegation | Room 312
Total: 1
```

`type` дополнительно печатает преподавателей через запятую с пробелом:

```
2026-10-15 | EXAM | Oral exam 1 | Room 401 | Portnov, Stepanova
Total: 1
```

`errors` печатает отказы в порядке строк и итог:

```
line 7: invalid date "2026-13-45"
Rejected: 1 of 10
```

Общие правила для всех команд:

- порядок файла сохраняется; файл проверяется **целиком**, даже если занятие не попало в фильтр;
- ошибочные строки в `period` и `type` пропускаются, но учитываются в коде завершения;
- итоговая строка печатается всегда, в том числе при нулевом результате;
- результат печатается через `println`, без заголовков и сообщений журнала;
- журнал `AuditedSessionRepository` в консоль не выводится.

| Код | Значение |
|---|---|
| `0` | файл обработан полностью, отклонённых строк нет |
| `1` | файл обработан полностью, есть отклонённые строки |
| `2` | неверные аргументы (`Invalid arguments`) или ошибка чтения файла (`Cannot read file`) |

Ошибка чтения файла не перехватывается в обработчиках: она доходит до готовой точки входа, которая печатает `Cannot read file` и возвращает `2`. Итог успешного завершения при этом не печатается.

---

## Проверка «сломай свою модель»

Для каждой попытки нарушить инварианты указано, что именно защищает модель. Тесты лежат в `app/src/test/kotlin/schedule/SessionTest.kt` и `RepositoryTest.kt`.

| # | Попытка | Что защищает | Тест |
|---|---|---|---|
| 1 | Занятие с пустой темой (`""`, `"   "`) | **Проверка при создании** (`requireValidText` в `init`) | `blank topic is rejected` |
| 2 | Год `0` или `10000` | **Проверка при создании** (`requireValidDate`) | `year 0 is rejected`, `year 10000 is rejected` (и граничные `1` и `9999` принимаются) |
| 3 | Действующее занятие без преподавателей | **Проверка при создании** (`requireValidTeachers`, `minCount = 1`) | `but nobody came`, `practice without teachers is rejected` |
| 4 | Сдача с одним преподавателем | **Проверка при создании** (`minCount = 2` у `Exam`) | `one teacher on exam` |
| 5 | Сдача с двумя одинаковыми преподавателями | **Проверка при создании** (множество `seen`, поиск дубликатов) | `two same teachers on exam` |
| 6 | Изменить исходный список (`names.clear()`) после создания занятия | **Защита коллекции** (`List.copyOf` при создании: хранится копия) | `changing the source list does not affect the session` |
| 7 | Изменить состояние через выданный список (`teachers as MutableList`) | **Защита коллекции** (хранится неизменяемый список) | `teachers list from the object cannot be modified` |
| 8 | Обойти проверки через `copy()` | **Устройство типа**: классы не `data class`, `copy()` не существует, других способов создать объект без `init` нет | проверяется компилятором: такого метода нет |
| 9 | Создать `Cancelled` с местом или преподавателями | **Устройство типа**: у `Cancelled` нет таких свойств, конструктор их не принимает | не компилируется |
| 10 | Создать пятый тип занятия снаружи | **Устройство типа**: `sealed interface` | не компилируется |
| 11 | Изменить реестр через снимок `all()` | **Защита коллекции** (`List.copyOf`) | `snapshot cannot be used to change the repository` |
| 12 | Изменить список, переданный в `addAll`, после вызова | **Защита коллекции** (`toList()` в реестре) | `changing the list passed to addAll does not affect the repository` |
| 13 | Изменить журнал через `events` | **Защита коллекции** (`List.copyOf`) | `journal returned to callers cannot be modified` |

Дополнительно проверено: внешние пробелы и управляющие символы в теме, `-` и пустое место, `-` и пустое имя преподавателя, регистр имён, сохранение порядка преподавателей, внутренние пробелы, создание всех четырёх типов.

---

## Тесты и проверка результата

```powershell
.\gradlew.bat build                 # компиляция и все тесты
python tools/verify.py              # сравнение с эталонами, Linux/macOS: python3
```

| Файл тестов | Что проверяет |
|---|---|
| `SessionTest.kt` | инварианты модели, защита коллекций, создание всех типов |
| `RepositoryTest.kt` | порядок, дубликаты, снимки, журнал для `add`/`addAll`/пустого пакета, `all()` без событий, неуспешные операции |
| `ParserTest.kt` | приоритет проверок, номера строк, несколько дефектов в одной строке, регистр типа, преподаватели |
| `FormattingTest.kt` | обязательные сообщения, экранирование, форматы строк `period` и `type` |
| `CliTest.kt` | (шаблон) разбор аргументов и чтение файла |

Примеры из `samples/`:

| Файл | Содержимое |
|---|---|
| `clean.txt` | 200 корректных строк |
| `dirty.txt` | 500 строк, 164 отклоняются |
| `empty.txt` | пустой файл |

Эталоны `samples/*.expected` взяты из шаблона, под своё решение их менять нельзя. Дополнительная проверка большого файла: `python tools/generate_samples.py --huge`, затем `python tools/verify.py --huge`.

Автоматические проверки (тесты, `verify.py`, GitHub Actions) служат для самопроверки и не заменяют ревью кода преподавателем.

## Задание со звёздочкой

Перенос занятия на другую дату в этой версии **не реализован**.