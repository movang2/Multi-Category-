package com.example

import com.example.ai.AiScheduleService
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testAiScheduleServiceFishCookingPlan() {
    val service = AiScheduleService()
    val (message, plan) = service.generateSmartLocalSchedule("kho cá ngon chuẩn vị")
    assertNotNull(plan)
    assertTrue(plan.steps.size >= 5)
    assertTrue(plan.title.contains("Kho cá", ignoreCase = true))
    // Each step should have a valid time format HH:mm and non-empty title & instructions
    plan.steps.forEach { step ->
      assertTrue(step.timeScheduled.contains(":"))
      assertTrue(step.title.isNotBlank())
      assertTrue(step.instruction.isNotBlank())
      // For cooking fish, hasLoudAlarm should be true to prevent burning or sleeping
      assertTrue("Cooking fish steps should have loud alarm", step.hasLoudAlarm)
    }
  }

  @Test
  fun testAiScheduleServiceDayPlan() {
    val service = AiScheduleService()
    val (message, plan) = service.generateSmartLocalSchedule("Lịch trình 1 ngày vui chơi học tập và giải trí")
    assertNotNull(plan)
    assertTrue(plan.steps.size >= 6)
    assertTrue(plan.title.contains("1 ngày", ignoreCase = true) || plan.title.contains("Học tập", ignoreCase = true))
    val firstStep = plan.steps.first()
    assertEquals("07:00", firstStep.timeScheduled)
    // Regular study/work day should have standard quiet/normal notifications
    assertFalse("Normal day plan should have standard quiet notification", firstStep.hasLoudAlarm)
  }

  @Test
  fun testShiftScheduleSteps() {
    val steps = listOf(
      com.example.ai.AiScheduleStep(timeScheduled = "11:44", title = "Sơ chế cá"),
      com.example.ai.AiScheduleStep(timeScheduled = "12:00", title = "Đun sôi bùng"),
      com.example.ai.AiScheduleStep(timeScheduled = "12:15", title = "Kho nhỏ lửa")
    )
    val parsedNewStart = Pair(12, 0)
    val newStartMinutes = 12 * 60 + 0
    val firstMinutes = 11 * 60 + 44

    val shifted = steps.map { step ->
      val parts = step.timeScheduled.split(":")
      val curMinutes = parts[0].toInt() * 60 + parts[1].toInt()
      val diff = (curMinutes - firstMinutes).let { if (it < 0) it + 1440 else it }
      val targetMin = (newStartMinutes + diff) % 1440
      val h = targetMin / 60
      val m = targetMin % 60
      step.copy(timeScheduled = String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m))
    }

    assertEquals("12:00", shifted[0].timeScheduled)
    assertEquals("12:16", shifted[1].timeScheduled)
    assertEquals("12:31", shifted[2].timeScheduled)
  }

  @Test
  fun testExternalAiPromptGeneration() {
    val service = AiScheduleService()
    val prompt = service.generateExternalPrompt("lịch trình đi chơi 1 ngày ở Đà Nẵng", "07:30", "22:00")
    assertTrue(prompt.contains("Hãy giúp tôi"))
    assertTrue(prompt.contains("Thời gian bắt đầu: 07:30"))
    assertTrue(prompt.contains("Thời gian kết thúc dự kiến: 22:00"))
    assertTrue(prompt.contains("Ăn sáng"))
    assertTrue(prompt.contains("Ăn trưa"))
    assertTrue(prompt.contains("scheduleTitle"))
    assertTrue(prompt.contains("hasLoudAlarm"))
    assertTrue(prompt.contains("```json"))
  }

  @Test
  fun testShiftTaskAutomation() {
    val taskOpenApp = com.example.data.ShiftTask(
      stationId = "station_1",
      timeScheduled = "06:00",
      title = "Mở TikTok",
      actionType = "OPEN_APP",
      targetPackageName = "com.zhiliaoapp.musically",
      targetAppName = "TikTok"
    )
    assertEquals("OPEN_APP", taskOpenApp.actionType)
    assertEquals("com.zhiliaoapp.musically", taskOpenApp.targetPackageName)
    assertEquals("TikTok", taskOpenApp.targetAppName)

    val taskLock = com.example.data.ShiftTask(
      stationId = "station_1",
      timeScheduled = "22:15",
      title = "Tắt app đi ngủ",
      actionType = "LOCK_SCREEN"
    )
    assertEquals("LOCK_SCREEN", taskLock.actionType)
  }

  @Test
  fun testRoadmapExternalPromptGeneration() {
    val service = AiScheduleService()
    val prompt = service.generateExternalRoadmapPrompt(
      topic = "Canh tác ngô lai 90 ngày",
      startDate = "2026-10-21",
      durationDays = 90,
      category = "Nông nghiệp"
    )
    assertTrue(prompt.contains("Lộ trình & Kế hoạch Đa tháng"))
    assertTrue(prompt.contains("2026-10-21"))
    assertTrue(prompt.contains("90 ngày"))
    assertTrue(prompt.contains("Nông nghiệp"))
    assertTrue(prompt.contains("dayOffset"))
    assertTrue(prompt.contains("scheduledTime"))
    assertTrue(prompt.contains("```json"))
  }

  @Test
  fun testRoadmapExternalJsonParsing() {
    val service = AiScheduleService()
    val sampleJson = """
      ```json
      {
        "title": "Kế hoạch Canh tác Ngô lai 90 ngày",
        "category": "Nông nghiệp",
        "icon": "🌽",
        "totalDays": 90,
        "description": "Lộ trình chi tiết chăm sóc ngô lai năng suất cao",
        "tasks": [
          {
            "dayOffset": 0,
            "scheduledTime": "07:30",
            "phase": "Giai đoạn 1: Chuẩn bị đất & Gieo hạt",
            "title": "Làm đất và bón phân lót",
            "instruction": "Cày bừa tơi xốp, bón phân chuồng hoai mục",
            "priority": "Cao",
            "hasLoudAlarm": false
          },
          {
            "dayOffset": 15,
            "scheduledTime": "08:00",
            "phase": "Giai đoạn 2: Cây con & Bón thúc 1",
            "title": "Tỉa dặm định cây và bón thúc đạm",
            "instruction": "Tỉa giữ cây khỏe, bón đạm urê quanh gốc",
            "priority": "Bình thường",
            "hasLoudAlarm": false
          },
          {
            "dayOffset": 90,
            "scheduledTime": "07:00",
            "phase": "Giai đoạn 3: Thu hoạch",
            "title": "Bẻ bắp ngô chín vàng",
            "instruction": "Thu hoạch buổi sáng khi trời mát",
            "priority": "Cao",
            "hasLoudAlarm": true
          }
        ]
      }
      ```
    """.trimIndent()

    val (plan, error) = service.parseExternalRoadmap(sampleJson)
    assertNull(error)
    assertNotNull(plan)
    assertEquals("Kế hoạch Canh tác Ngô lai 90 ngày", plan!!.title)
    assertEquals("Nông nghiệp", plan.category)
    assertEquals("🌽", plan.icon)
    assertEquals(90, plan.totalDays)
    assertEquals(3, plan.tasks.size)
    assertEquals(0, plan.tasks[0].dayOffset)
    assertEquals("07:30", plan.tasks[0].scheduledTime)
    assertEquals("Làm đất và bón phân lót", plan.tasks[0].title)
    assertEquals(15, plan.tasks[1].dayOffset)
    assertEquals(90, plan.tasks[2].dayOffset)
    assertTrue(plan.tasks[2].hasLoudAlarm)
  }
}


