package com.henry.batchtaskcheck.check

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/check/batch-task")
class TaskCheckApiController {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @GetMapping
    fun viewSchedules(
        response: HttpServletResponse
    ): String {
        val beans = applicationContext.beanDefinitionNames.map { x -> applicationContext.getBean(x) }
        val result = TaskCheckUtil.getSchedules(beans)

        response.status = result.first

        return result.second
    }
}