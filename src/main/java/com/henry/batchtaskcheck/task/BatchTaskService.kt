package com.henry.batchtaskcheck.task

import mu.KLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class BatchTaskService {

    companion object : KLogging()

    @Scheduled(cron = "0 0/5 * * * *")
    fun logFor5Min() {

        logger.info("log for 5 min.......")
    }
}