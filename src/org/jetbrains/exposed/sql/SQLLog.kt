package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.statements.*
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.util.*

interface SqlLogger {
    fun log (context: StatementContext);
}

val exposedLogger = LoggerFactory.getLogger("Exposed")!!

inline fun <R> logTimeSpent(message: String, block: ()->R) : R {
    val start = System.currentTimeMillis()
    val answer = block()
    exposedLogger.info(message + " took " + (System.currentTimeMillis() - start) + "ms")
    return answer
}

fun expandArgs (sql: String, args: List<Pair<ColumnType, Any?>>) : String {
    if (args.isEmpty())
        return sql

    val result = StringBuilder()
    val quoteStack = Stack<Char>()
    var argIndex = 0
    var lastPos = 0
    for (i in 0..sql.length -1) {
        val char = sql[i]
        if (char == '?') {
            if (quoteStack.isEmpty()) {
                result.append(sql.substring(lastPos, i))
                lastPos = i+1
                result.append(args[argIndex].first.valueToString(args[argIndex].second))
                ++argIndex
            }
            continue
        }

        if (char == '\'' || char == '\"') {
            if (quoteStack.isEmpty()) {
                quoteStack.push(char)
            } else {
                val currentQuote = quoteStack.peek()
                if (currentQuote == char)
                    quoteStack.pop()
                else
                    quoteStack.push(char)
            }
        }
    }

    if (lastPos < sql.length)
        result.append(sql.substring(lastPos))

    return result.toString()
}

class StdOutSqlLogger : SqlLogger {

    override fun log(context: StatementContext) {
        System.out.println(context.expandArgs())
    }
}

class Slf4jSqlLogger(): SqlLogger {

    override fun log(context: StatementContext) {
        exposedLogger.debug(context.expandArgs())
    }
}

class CompositeSqlLogger() : SqlLogger, StatementInterceptor {
    private val loggers: ArrayList<SqlLogger> = ArrayList()

    fun addLogger (logger: SqlLogger) {
        loggers.add(logger)
    }

    fun removeLogger (logger: SqlLogger) {
        loggers.remove(logger)
    }

    override fun log(context: StatementContext) {
        for (logger in loggers) {
            logger.log(context)
        }
    }

    override fun beforeExecution(transaction: Transaction, context: StatementContext) { }

    override fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatement) {
        contexts.forEach {
            log(it)
        }
    }
}