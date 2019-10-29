package com.henry.batchtaskcheck.check

import org.springframework.aop.framework.Advised
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronSequenceGenerator
import java.lang.reflect.Method
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

object TaskCheckUtil {
    fun getSchedules(beans: List<Any>): Pair<Int, String> {
        val limit = 30 // input

        val allBatchDateTimes = lookupAllBatchDateTimes(beans, limit)
        val allBatchNames = allBatchDateTimes.values.flatten().toSet()
        val executingBatches = lookupExecutingBatches(allBatchNames)

        val now = LocalDateTime.now()

        // lookupAllBatchDateTimes 함수에서 startDate ~ endDate(현재 + limit시간)까지 구했으므로
        // 여기서 limit시간 안에 실행될 배치 작업이 있다면 futureDateTimes에 속하게 된다.
        val futureDateTimes = allBatchDateTimes.keys.filter { it.isAfter(now) }.sorted()

        val ok = executingBatches.isEmpty() && futureDateTimes.isEmpty()
        val executingInfo = executingBatches.joinToString("\n")
        val futureInfo = futureDateTimes.joinToString("\n") { x ->
            "{$x}: ${allBatchDateTimes[x]?.joinToString(", ")}"
        }

        val statusCode = if (ok) 200 else 403
        val body = """
            |${if (statusCode == 200) "OK" else "FAIL"}
            |
            |Info: $limit Minutes, ${allBatchNames.size} Job Inspected
            |
            |Executing:
            |$executingInfo
            |
            |Future:
            |$futureInfo
            """.trimMargin()

        return Pair(statusCode, body)
    }

    fun getRealMethod(bean: Any, method: Method): Method {
        try {
            val declaringClass = method.declaringClass

            if ("CGLIB" in declaringClass.name) {
                return (bean as Advised).targetSource.target!!.javaClass.getMethod(method.name)
            }
        } catch (e: Exception) {
            // do nothing; simply ignore
        }

        return method
    }

    // 배치 작업중에 현재 실행되고 있는 작업이 있는지 확인한다.
    private fun lookupExecutingBatches(allBatchNames: Set<String>) =
        Thread.getAllStackTraces().values.flatMap { stacks ->
            stacks.map { "${it.className}.${it.methodName}" }.filter { name -> allBatchNames.any { it == name } }
        }

    private fun lookupAllBatchDateTimes(beans: List<Any>, limit: Int): Map<LocalDateTime, List<String>> {
        val allBatchTimes = mutableMapOf<LocalDateTime, MutableList<String>>()

        // fetch all Scheduled annotation method
        val batchMethods = beans
            .flatMap { bean ->
                bean.javaClass.declaredMethods
                    .map { getRealMethod(bean, it) }
                    .filter { it.isAnnotationPresent(Scheduled::class.java) }
            }

        batchMethods.forEach { method ->
            val annotation = method.getAnnotation(Scheduled::class.java)
            val expressionFragments = annotation.cron.trim().split(" ")
            if (expressionFragments.size < 5) {
                return@forEach
            }

            val batchName = "${method.declaringClass.name}.${method.name}"
            for (localDateTime in getAllTimeFromCronExpression(annotation.cron, limit)) {
                if (localDateTime !in allBatchTimes) {
                    allBatchTimes[localDateTime] = mutableListOf()
                }
                allBatchTimes[localDateTime]!!.add(batchName)
            }
        }
        return allBatchTimes
    }

    // cron expression을 보고 startDateTime부터 endDateTime까지 언제 실행되었는지 LocalDateTime 리스트에 담는다.
    private fun getAllTimeFromCronExpression(cronExpression: String, limit: Int): List<LocalDateTime> {
        val cronSequenceGenerator = CronSequenceGenerator(cronExpression)

        val startDateTime = LocalDateTime.now().minusDays(1)
        val endDateTime = LocalDateTime.now().plusMinutes(limit.toLong())
        var iterateDate = Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant())

        val localDateTimes = ArrayList<LocalDateTime>()
        while (true) {
            iterateDate = cronSequenceGenerator.next(iterateDate)
            val executedDateTime = LocalDateTime.ofInstant(iterateDate.toInstant(), ZoneId.systemDefault())
            if (executedDateTime.isAfter(endDateTime)) {
                break
            }
            localDateTimes.add(executedDateTime)
        }
        return localDateTimes
    }
}