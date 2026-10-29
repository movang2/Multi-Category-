package com.example.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class AiScheduleService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateSchedule(
        userPrompt: String,
        history: List<AiChatMessage> = emptyList()
    ): AiScheduleResponse = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        val hasValidKey = apiKey.isNotBlank() &&
                apiKey != "MY_GEMINI_API_KEY" &&
                apiKey != "YOUR_GEMINI_API_KEY"

        if (!hasValidKey) {
            Log.d("AiScheduleService", "Gemini API key is placeholder or empty. Using smart local generator.")
            return@withContext generateSmartLocalSchedule(userPrompt)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val cal = Calendar.getInstance()
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val currentTimeStr = timeFormat.format(cal.time)

            val systemInstructionText = """
                Bạn là Trợ lý Lập lịch trình & Chia bước thông minh bằng tiếng Việt (AI Schedule Planner).
                Thời gian hiện tại của người dùng là $currentTimeStr.
                
                Nhiệm vụ của bạn:
                1. Hiểu mục tiêu của người dùng (tạo lịch trình công việc, hoặc lập chu kỳ xoay ca làm việc tuần hoàn giữa ca ngày/ca đêm/ngày nghỉ).
                2. Đưa ra lời tư vấn ngắn gọn, hữu ích và thân thiện bằng tiếng Việt.
                3. NẾU người dùng yêu cầu xoay ca tuần hoàn (ví dụ: tuần ca ngày, tuần ca đêm, làm T2-T6, T7-CN nghỉ...):
                   BẮT BUỘC cung cấp khối JSON trong cặp ```json ... ```:
                   ```json
                   {
                     "rotationPlan": {
                       "title": "Chu kỳ xoay ca 2 tuần (Ca ngày / Ca đêm)",
                       "cycleWeeks": 2,
                       "description": "Tuần 1: T2-T6 Ca ngày, T7-CN Nghỉ. Tuần 2: T2-T6 Ca đêm, T7-CN Nghỉ.",
                       "weeks": [
                         {
                           "weekNumber": 1,
                           "weekTitle": "Tuần 1: Ca ngày",
                           "days": [
                             { "dayOfWeek": 1, "dayName": "Thứ 2", "stationName": "ca ngày" },
                             { "dayOfWeek": 2, "dayName": "Thứ 3", "stationName": "ca ngày" },
                             { "dayOfWeek": 3, "dayName": "Thứ 4", "stationName": "ca ngày" },
                             { "dayOfWeek": 4, "dayName": "Thứ 5", "stationName": "ca ngày" },
                             { "dayOfWeek": 5, "dayName": "Thứ 6", "stationName": "ca ngày" },
                             { "dayOfWeek": 6, "dayName": "Thứ 7", "stationName": "ngày nghỉ" },
                             { "dayOfWeek": 7, "dayName": "Chủ nhật", "stationName": "ngày nghỉ" }
                           ]
                         },
                         {
                           "weekNumber": 2,
                           "weekTitle": "Tuần 2: Ca đêm",
                           "days": [
                             { "dayOfWeek": 1, "dayName": "Thứ 2", "stationName": "ca đêm" },
                             { "dayOfWeek": 2, "dayName": "Thứ 3", "stationName": "ca đêm" },
                             { "dayOfWeek": 3, "dayName": "Thứ 4", "stationName": "ca đêm" },
                             { "dayOfWeek": 4, "dayName": "Thứ 5", "stationName": "ca đêm" },
                             { "dayOfWeek": 5, "dayName": "Thứ 6", "stationName": "ca đêm" },
                             { "dayOfWeek": 6, "dayName": "Thứ 7", "stationName": "ngày nghỉ" },
                             { "dayOfWeek": 7, "dayName": "Chủ nhật", "stationName": "ngày nghỉ" }
                           ]
                         }
                       ]
                     }
                   }
                   ```
                4. NẾU người dùng lập lịch trình các bước công việc 1 ngày hoặc quy trình nấu nướng:
                   BẮT BUỘC cung cấp khối JSON trong cặp ```json ... ```:
                   ```json
                   {
                     "scheduleTitle": "Tiêu đề lịch trình",
                     "suggestedStationName": "Tên ngắn gọn cho vị trí/kế hoạch",
                     "suggestedIcon": "Biểu tượng cảm xúc phù hợp",
                     "steps": [
                       {
                         "timeScheduled": "HH:mm",
                         "title": "Tên bước/công việc",
                         "instruction": "Hướng dẫn thực hiện",
                         "priority": "Bình thường",
                         "hasLoudAlarm": false
                       }
                     ]
                   }
                   ```
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray()

                // Add recent history for context
                history.takeLast(6).forEach { msg ->
                    val contentObj = JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        val partsArr = JSONArray().apply {
                            put(JSONObject().apply { put("text", msg.text) })
                        }
                        put("parts", partsArr)
                    }
                    contentsArray.put(contentObj)
                }

                // Add current user prompt
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("AiScheduleService", "API error: ${response.code} - $responseBody")
                return@withContext generateSmartLocalSchedule(userPrompt)
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts")
            val text = partsArr?.optJSONObject(0)?.optString("text") ?: ""

            if (text.isBlank()) {
                return@withContext generateSmartLocalSchedule(userPrompt)
            }

            parseAiResponse(text, userPrompt)
        } catch (e: Exception) {
            Log.e("AiScheduleService", "Call failed, falling back to local engine: ${e.message}", e)
            generateSmartLocalSchedule(userPrompt)
        }
    }

    private fun parseAiResponse(rawText: String, prompt: String): AiScheduleResponse {
        try {
            var jsonString: String? = null
            var chatText = rawText

            val jsonBlockRegex = "```(?:json)?\\s*([\\s\\S]*?)\\s*```".toRegex()
            val match = jsonBlockRegex.find(rawText)
            if (match != null) {
                jsonString = match.groupValues[1].trim()
                chatText = rawText.replace(match.value, "").trim()
            } else if (rawText.contains("{") && rawText.contains("}")) {
                val start = rawText.indexOf("{")
                val end = rawText.lastIndexOf("}")
                if (start in 0 until end) {
                    jsonString = rawText.substring(start, end + 1)
                    chatText = (rawText.substring(0, start) + "\n" + rawText.substring(end + 1)).trim()
                }
            }

            if (jsonString != null) {
                val planJson = JSONObject(jsonString)

                if (planJson.has("rotationPlan")) {
                    val rObj = planJson.getJSONObject("rotationPlan")
                    val title = rObj.optString("title", "Chu kỳ xoay ca")
                    val cycleWeeks = rObj.optInt("cycleWeeks", 2)
                    val switchTime = rObj.optString("switchTime", "06:00")
                    val desc = rObj.optString("description", "")
                    val weeksArr = rObj.optJSONArray("weeks")
                    val weeksList = mutableListOf<AiRotationWeek>()
                    if (weeksArr != null) {
                        for (w in 0 until weeksArr.length()) {
                            val wObj = weeksArr.getJSONObject(w)
                            val wNum = wObj.optInt("weekNumber", w + 1)
                            val wTitle = wObj.optString("weekTitle", "Tuần $wNum")
                            val daysArr = wObj.optJSONArray("days")
                            val daysList = mutableListOf<AiRotationDay>()
                            if (daysArr != null) {
                                for (d in 0 until daysArr.length()) {
                                    val dObj = daysArr.getJSONObject(d)
                                    daysList.add(
                                        AiRotationDay(
                                            dayOfWeek = dObj.optInt("dayOfWeek", d + 1),
                                            dayName = dObj.optString("dayName", "Thứ ${d + 2}"),
                                            stationName = dObj.optString("stationName", "ca ngày")
                                        )
                                    )
                                }
                            }
                            weeksList.add(AiRotationWeek(wNum, wTitle, daysList))
                        }
                    }
                    val rotPlan = AiRotationPlan(
                        title = title,
                        cycleWeeks = cycleWeeks,
                        description = desc,
                        weeks = weeksList,
                        switchTime = switchTime
                    )
                    return AiScheduleResponse(chatText.ifBlank { "Tôi đã lập kế hoạch chu kỳ xoay ca theo yêu cầu của bạn:" }, null, rotPlan)
                }

                val title = planJson.optString("scheduleTitle", "Lịch trình đề xuất")
                val suggestedStation = planJson.optString("suggestedStationName", "Kế hoạch AI")
                val suggestedIcon = planJson.optString("suggestedIcon", "✨")
                val stepsArray = planJson.optJSONArray("steps")

                val stepsList = mutableListOf<AiScheduleStep>()
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        val sObj = stepsArray.optJSONObject(i) ?: continue
                        val time = sObj.optString("timeScheduled", "08:00")
                        val stepTitle = sObj.optString("title", "Bước ${i + 1}")
                        val instr = sObj.optString("instruction", "")
                        val priority = sObj.optString("priority", "Bình thường")
                        val isCookingOrFish = (prompt + title).lowercase().let { p ->
                            p.contains("kho cá") || p.contains("cá") || p.contains("nấu") || p.contains("bếp") || p.contains("nướng") || p.contains("báo thức")
                        }
                        val hasLoudAlarm = if (sObj.has("hasLoudAlarm")) sObj.optBoolean("hasLoudAlarm") else isCookingOrFish
                        stepsList.add(
                            AiScheduleStep(
                                timeScheduled = normalizeTime(time),
                                title = stepTitle,
                                instruction = instr,
                                priority = priority,
                                hasLoudAlarm = hasLoudAlarm
                            )
                        )
                    }
                }

                if (stepsList.isNotEmpty()) {
                    val plan = AiSchedulePlan(
                        title = title,
                        suggestedStationName = suggestedStation,
                        suggestedIcon = suggestedIcon,
                        steps = stepsList
                    )
                    return AiScheduleResponse(chatText.ifBlank { "Dưới đây là lịch trình chi tiết tôi đã lập riêng cho bạn:" }, plan, null)
                }
            }
        } catch (e: Exception) {
            Log.e("AiScheduleService", "Failed to parse json block: ${e.message}", e)
        }

        // If JSON wasn't cleanly extracted, fall back to local plan for the prompt
        return generateSmartLocalSchedule(prompt)
    }

    private fun normalizeTime(timeStr: String): String {
        val clean = timeStr.trim().replace("h", ":").replace(".", ":").replace("-", ":")
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toIntOrNull() ?: 8
            val m = parts[1].trim().toIntOrNull() ?: 0
            return String.format(Locale.getDefault(), "%02d:%02d", h.coerceIn(0, 23), m.coerceIn(0, 59))
        }
        return "08:00"
    }

    private fun calculateTimeFromNow(addMinutes: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, addMinutes)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(cal.time)
    }

    /**
     * Smart local schedule generator when offline or before Gemini API key is configured.
     * Accurately supports recipes (kho cá), day planning (vui chơi, học tập), shifts, and custom activities.
     */
    fun generateSmartLocalSchedule(prompt: String): AiScheduleResponse {
        val p = prompt.lowercase(Locale.getDefault())

        if (p.contains("xoay ca") || p.contains("tuần hoàn") || (p.contains("ca ngày") && (p.contains("ca đêm") || p.contains("tuần"))) || (p.contains("bật tắt") && p.contains("ca")) || (p.contains("thứ 2") && p.contains("thứ 6") && p.contains("ca"))) {
            val rotPlan = AiRotationPlan(
                title = "Chu kỳ xoay ca 2 tuần (Ca ngày / Ca đêm)",
                cycleWeeks = 2,
                description = "Tuần 1: Thứ 2 đến Thứ 6 bật 'ca ngày', Thứ 7 & CN bật 'ngày nghỉ'. Tuần 2: Thứ 2 đến Thứ 6 bật 'ca đêm', Thứ 7 & CN bật 'ngày nghỉ'.",
                weeks = listOf(
                    AiRotationWeek(
                        weekNumber = 1,
                        weekTitle = "Tuần 1: Ca ngày (T2-T6 làm ngày, T7-CN nghỉ)",
                        days = listOf(
                            AiRotationDay(1, "Thứ 2", "ca ngày"),
                            AiRotationDay(2, "Thứ 3", "ca ngày"),
                            AiRotationDay(3, "Thứ 4", "ca ngày"),
                            AiRotationDay(4, "Thứ 5", "ca ngày"),
                            AiRotationDay(5, "Thứ 6", "ca ngày"),
                            AiRotationDay(6, "Thứ 7", "ngày nghỉ"),
                            AiRotationDay(7, "Chủ nhật", "ngày nghỉ")
                        )
                    ),
                    AiRotationWeek(
                        weekNumber = 2,
                        weekTitle = "Tuần 2: Ca đêm (T2-T6 làm đêm, T7-CN nghỉ)",
                        days = listOf(
                            AiRotationDay(1, "Thứ 2", "ca đêm"),
                            AiRotationDay(2, "Thứ 3", "ca đêm"),
                            AiRotationDay(3, "Thứ 4", "ca đêm"),
                            AiRotationDay(4, "Thứ 5", "ca đêm"),
                            AiRotationDay(5, "Thứ 6", "ca đêm"),
                            AiRotationDay(6, "Thứ 7", "ngày nghỉ"),
                            AiRotationDay(7, "Chủ nhật", "ngày nghỉ")
                        )
                    )
                )
            )
            val msg = "Tôi đã lập kế hoạch chu kỳ xoay ca 2 tuần tuần hoàn theo đúng yêu cầu:\n" +
                    "• Tuần 1: Thứ 2 đến Thứ 6 tự động bắt đầu 'ca ngày', Thứ 7 & CN tự động bật 'ngày nghỉ'.\n" +
                    "• Tuần 2: Thứ 2 đến Thứ 6 tự động bắt đầu 'ca đêm', Thứ 7 & CN tự động bật 'ngày nghỉ'.\n" +
                    "• Chu kỳ tự động tuần hoàn giữa 2 tuần, đổi ca vào 06:00 sáng mỗi ngày.\n\n" +
                    "Bạn hãy kiểm tra bảng kế hoạch bên dưới và nhấn 'Áp dụng chu kỳ xoay ca' để kích hoạt ngay!"
            return AiScheduleResponse(msg, null, rotPlan)
        }

        return when {
            p.contains("kho cá") || p.contains("cá") || p.contains("nấu") || p.contains("kho") -> {
                val t1 = calculateTimeFromNow(5)
                val t2 = calculateTimeFromNow(20)
                val t3 = calculateTimeFromNow(35)
                val t4 = calculateTimeFromNow(55)
                val t5 = calculateTimeFromNow(70)

                val message = """
                    Tôi đã chia quy trình Kho cá chuẩn vị thành 5 bước với mốc thời gian tăng dần từ lúc này.
                    Mỗi bước tương ứng một mốc chuông báo để bạn yên tâm không bị khét cá hay quên tắt bếp!
                    Bạn có thể chỉnh sửa lại số phút hoặc đổi hướng dẫn trước khi bấm Bắt đầu ca.
                """.trimIndent()

                val plan = AiSchedulePlan(
                    title = "Quy trình Kho cá ngon chuẩn vị",
                    suggestedStationName = "Kho cá 🐟",
                    suggestedIcon = "🐟",
                    steps = listOf(
                        AiScheduleStep(
                            timeScheduled = t1,
                            title = "1. Sơ chế & Ướp cá",
                            instruction = "Cá rửa sạch, ướp với nước mắm, đường, hành tỏi băm, tiêu, ớt và chút nước màu trong 15 phút.",
                            priority = "Cao",
                            hasLoudAlarm = true
                        ),
                        AiScheduleStep(
                            timeScheduled = t2,
                            title = "2. Bật lửa lớn đun sôi bùng",
                            instruction = "Bật bếp lửa to để nồi cá sôi bùng lên trong 3-5 phút cho thịt cá săn chắc lại.",
                            priority = "Cao",
                            hasLoudAlarm = true
                        ),
                        AiScheduleStep(
                            timeScheduled = t3,
                            title = "3. Thêm nước sôi & Hạ nhỏ lửa",
                            instruction = "Chế thêm nước sôi ngập xâm xấp mặt cá, đậy hé vung và hạ lửa nhỏ liu riu để cá ngấm gia vị.",
                            priority = "Bình thường",
                            hasLoudAlarm = true
                        ),
                        AiScheduleStep(
                            timeScheduled = t4,
                            title = "4. Nêm lại & Kiểm tra độ sánh",
                            instruction = "Mở vung, nêm lại gia vị vừa ăn, rưới nước kho lên mặt cá để tạo độ bóng và sánh kẹo.",
                            priority = "Cao",
                            hasLoudAlarm = true
                        ),
                        AiScheduleStep(
                            timeScheduled = t5,
                            title = "5. Rắc hành tiêu & Tắt bếp",
                            instruction = "Thêm hành lá cắt khúc, tiêu xay và vài lát ớt tươi. Tắt bếp và thưởng thức món cá kho đậm đà!",
                            priority = "Bình thường",
                            hasLoudAlarm = true
                        )
                    )
                )
                AiScheduleResponse(message, plan, null)
            }

            p.contains("vui chơi") || p.contains("học tập") || p.contains("giải trí") || p.contains("1 ngày") || p.contains("ngày") -> {
                val message = """
                    Tôi đã lên lịch trình cân bằng hoàn hảo cho 1 ngày trọn vẹn: kết hợp giữa năng suất học tập, vận động thể chất và thư giãn giải trí.
                    Các mốc thời gian được phân bổ khoa học để bạn duy trì sự hứng khởi cả ngày!
                """.trimIndent()

                val plan = AiSchedulePlan(
                    title = "Lịch trình 1 ngày Học tập, Vui chơi & Giải trí",
                    suggestedStationName = "Kế hoạch Ngày 🎯",
                    suggestedIcon = "🎯",
                    steps = listOf(
                        AiScheduleStep(
                            timeScheduled = "07:00",
                            title = "Khởi động ngày mới & Ăn sáng",
                            instruction = "Uống 1 ly nước ấm, tập thể dục nhẹ 15 phút và ăn bữa sáng đầy đủ năng lượng.",
                            priority = "Bình thường"
                        ),
                        AiScheduleStep(
                            timeScheduled = "08:30",
                            title = "Phiên học tập & Làm việc sâu (Deep Work)",
                            instruction = "Tập trung giải quyết các bài tập hoặc nhiệm vụ khó nhất trong không gian yên tĩnh.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = "11:30",
                            title = "Ăn trưa & Nghỉ ngơi ngắn",
                            instruction = "Thưởng thức bữa trưa và chợp mắt 20-30 phút để nạp lại năng lượng cho buổi chiều.",
                            priority = "Bình thường"
                        ),
                        AiScheduleStep(
                            timeScheduled = "14:00",
                            title = "Học kỹ năng mới hoặc Ôn tập nhẹ",
                            instruction = "Đọc tài liệu, luyện tiếng Anh hoặc nghiên cứu thêm chủ đề bạn yêu thích.",
                            priority = "Bình thường"
                        ),
                        AiScheduleStep(
                            timeScheduled = "16:30",
                            title = "Hoạt động thể thao & Vui chơi ngoài trời",
                            instruction = "Chạy bộ, đi dạo, chơi thể thao hoặc giao lưu bạn bè để thư giãn gân cốt.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = "19:00",
                            title = "Bữa tối ấm cúng cùng gia đình",
                            instruction = "Tận hưởng bữa cơm ngon và trò chuyện thân mật, ngắt kết nối với màn hình công việc.",
                            priority = "Bình thường"
                        ),
                        AiScheduleStep(
                            timeScheduled = "20:30",
                            title = "Giải trí tự do & Nghe nhạc / Xem phim",
                            instruction = "Xem bộ phim yêu thích, chơi game hoặc đọc sách giải trí thư giãn.",
                            priority = "Thấp"
                        ),
                        AiScheduleStep(
                            timeScheduled = "22:30",
                            title = "Chuẩn bị giấc ngủ & Viết nhật ký",
                            instruction = "Tổng kết những điều tốt đẹp trong ngày, tắt thiết bị điện tử và đi ngủ đúng giờ.",
                            priority = "Bình thường"
                        )
                    )
                )
                AiScheduleResponse(message, plan, null)
            }

            p.contains("ca") || p.contains("làm") || p.contains("máy") || p.contains("trực") || p.contains("bảo dưỡng") -> {
                val message = """
                    Tôi đã chuẩn bị danh mục công việc theo các khung giờ trọng tâm cho ca làm việc vận hành tại vị trí máy.
                    Khi bạn bấm 'Bắt đầu ca', hệ thống sẽ thiết lập chuông nhắc nhở chuẩn xác theo từng mốc kiểm tra.
                """.trimIndent()

                val plan = AiSchedulePlan(
                    title = "Lịch trình Ca làm việc Vận hành & Kiểm tra",
                    suggestedStationName = "Ca trực Máy 🏭",
                    suggestedIcon = "🏭",
                    steps = listOf(
                        AiScheduleStep(
                            timeScheduled = "08:00",
                            title = "Nhận ca & Kiểm tra an toàn lao động",
                            instruction = "Kiểm tra trang bị bảo hộ, kiểm tra tình trạng máy móc và ghi nhận thông số ban đầu.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = "10:00",
                            title = "Kiểm tra nhiệt độ & Áp suất định kỳ",
                            instruction = "Đo nhiệt độ vòng bi, kiểm tra mức dầu bôi trơn và hệ thống làm mát.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = "12:00",
                            title = "Nghỉ giữa ca & Bàn giao tạm",
                            instruction = "Nghỉ ăn trưa, đảm bảo máy duy trì chế độ tự động an toàn.",
                            priority = "Bình thường"
                        ),
                        AiScheduleStep(
                            timeScheduled = "14:30",
                            title = "Đo kiểm chất lượng thành phẩm",
                            instruction = "Lấy mẫu ngẫu nhiên kiểm tra sai số và độ hoàn thiện bề mặt.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = "16:30",
                            title = "Vệ sinh vị trí & Viết bàn giao ca",
                            instruction = "Lau dọn máy móc, thu gom phế liệu và ghi chép nhật ký bàn giao cho ca tiếp theo.",
                            priority = "Cao"
                        )
                    )
                )
                AiScheduleResponse(message, plan, null)
            }

            else -> {
                val t1 = calculateTimeFromNow(15)
                val t2 = calculateTimeFromNow(60)
                val t3 = calculateTimeFromNow(120)
                val t4 = calculateTimeFromNow(180)

                val message = """
                    Tôi đã phác thảo các mốc thời gian và hành động cụ thể dựa trên yêu cầu: "$prompt".
                    Bạn có thể chỉnh sửa lại thời gian và nội dung từng bước trước khi áp dụng vào ca làm việc!
                """.trimIndent()

                val plan = AiSchedulePlan(
                    title = "Kế hoạch thực hiện: $prompt",
                    suggestedStationName = "Kế hoạch AI ✨",
                    suggestedIcon = "✨",
                    steps = listOf(
                        AiScheduleStep(
                            timeScheduled = t1,
                            title = "1. Chuẩn bị & Bắt đầu",
                            instruction = "Chuẩn bị đầy đủ công cụ, nguyên vật liệu và không gian cần thiết.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = t2,
                            title = "2. Thực hiện giai đoạn chính",
                            instruction = "Tập trung triển khai nội dung quan trọng nhất theo kế hoạch.",
                            priority = "Cao"
                        ),
                        AiScheduleStep(
                            timeScheduled = t3,
                            title = "3. Rà soát & Đánh giá tiến độ",
                            instruction = "Kiểm tra chất lượng công việc đã làm, chỉnh sửa điểm chưa đạt.",
                            priority = "Bình thường"
                        ),
                        AiScheduleStep(
                            timeScheduled = t4,
                            title = "4. Hoàn tất & Tổng kết",
                            instruction = "Dọn dẹp, lưu trữ kết quả và ghi nhận hoàn thành mục tiêu.",
                            priority = "Bình thường"
                        )
                    )
                )
                AiScheduleResponse(message, plan, null)
            }
        }
    }

    /**
     * Sinh đoạn văn bản Prompt hoàn chỉnh để người dùng copy đi nhờ AI khác (ChatGPT, Claude, DeepSeek...)
     * trả về đúng khối JSON để dán lại vào app.
     */
    fun generateExternalPrompt(
        topic: String,
        startTime: String = "07:30",
        endTime: String = "22:00"
    ): String {
        val cleanTopic = topic.trim().ifBlank { "lịch trình đi chơi 1 ngày ở Đà Nẵng" }
        val lower = cleanTopic.lowercase()

        val activitiesList = when {
            lower.contains("đà nẵng") || lower.contains("du lịch") || lower.contains("đi chơi") || lower.contains("nghỉ mát") || lower.contains("tham quan") -> """
Tôi muốn lịch trình có:
- Ăn sáng
- Tham quan một số địa điểm nổi tiếng ở ${if (lower.contains("đà nẵng")) "Đà Nẵng" else "địa phương"}
- Ăn trưa
- Nghỉ ngơi
- Tham quan/đi chơi buổi chiều
- Ăn tối
- Hoạt động buổi tối
- Có thời gian di chuyển hợp lý giữa các địa điểm
            """.trimIndent()

            lower.contains("kho cá") || lower.contains("nấu") || lower.contains("ẩm thực") || lower.contains("bếp") -> """
Tôi muốn lịch trình có:
- Sơ chế nguyên liệu và ướp gia vị
- Bật bếp đun sôi và khóa vị
- Canh lửa và hạ nhỏ lửa liu riu
- Kiểm tra cạn nước, nêm nếm hoàn thiện và tắt bếp
- Thời gian giữa các bước hợp lý chuẩn vị
            """.trimIndent()

            lower.contains("ca làm") || lower.contains("xưởng") || lower.contains("máy") || lower.contains("trực") -> """
Tôi muốn lịch trình có:
- Nhận ca làm và kiểm tra an toàn vận hành thiết bị
- Các mốc kiểm tra thông số kỹ thuật định kỳ
- Xử lý các công đoạn trọng tâm trong ca
- Vệ sinh máy móc, ghi nhật ký và chuẩn bị bàn giao ca
            """.trimIndent()

            else -> """
Tôi muốn lịch trình có:
- Chia nhỏ thành các bước rõ ràng từ đầu đến cuối
- Có thời gian nghỉ ngơi hoặc giãn cách hợp lý
- Các mốc giờ phù hợp từ khi bắt đầu đến khi kết thúc
            """.trimIndent()
        }

        val isCooking = lower.contains("kho cá") || lower.contains("nấu") || lower.contains("bếp") || lower.contains("cháy")

        val promptLead = if (cleanTopic.startsWith("lập lịch", ignoreCase = true) || cleanTopic.startsWith("hãy", ignoreCase = true)) {
            cleanTopic
        } else {
            "lập $cleanTopic"
        }

        return """
Hãy giúp tôi $promptLead.

Thời gian bắt đầu: $startTime
Thời gian kết thúc dự kiến: $endTime

$activitiesList

Hãy tự sắp xếp thời gian hợp lý và tránh xếp lịch quá sát nhau.

Sau khi lập lịch, BẮT BUỘC trả về một khối JSON để tôi có thể copy toàn bộ và dán trực tiếp vào một ứng dụng Android để tạo lịch trình.

Không thay đổi cấu trúc JSON sau:

```json
{
  "scheduleTitle": "Tiêu đề lịch trình",
  "suggestedStationName": "Tên ngắn gọn cho lịch trình",
  "suggestedIcon": "1 emoji phù hợp",
  "steps": [
    {
      "timeScheduled": "HH:mm",
      "title": "Tên công việc/hoạt động",
      "instruction": "Mô tả chi tiết cần làm",
      "priority": "Bình thường",
      "hasLoudAlarm": ${if (isCooking) "true" else "false"}
    }
  ]
}
```

Quy tắc bắt buộc:

1. "timeScheduled" phải là giờ 24h dạng HH:mm.
2. Các bước phải được sắp xếp theo thứ tự thời gian tăng dần.
3. "priority" chỉ được dùng một trong:
   - "Cao"
   - "Bình thường"
   - "Thấp"
4. "hasLoudAlarm":
   - true nếu là việc cần chuông lớn để tránh bỏ lỡ hoặc cần chú ý ngay.
   - false đối với lịch đi chơi, tham quan, ăn uống và các hoạt động thông thường.
5. "suggestedStationName" phải ngắn gọn.
6. "suggestedIcon" chỉ chứa một emoji.
7. Không được thêm trường dữ liệu mới.
8. Không được đổi tên các trường.
9. JSON phải hợp lệ, không có dấu phẩy thừa.
10. Sau khi đã lập lịch, hãy đặt JSON trong đúng một khối ```json ... ```.
11. Phần JSON phải chứa đầy đủ tất cả các bước trong lịch trình.
12. Không đưa bình luận hoặc giải thích bên trong JSON.

Tôi muốn có thể copy nguyên khối JSON và dán trực tiếp vào app mà không cần chỉnh sửa.
        """.trimIndent()
    }

    /**
     * Bóc tách câu trả lời từ AI khác hoặc khối JSON dán vào ứng dụng.
     */
    fun parseExternalSchedule(rawInput: String): Pair<AiSchedulePlan?, String?> {
        val rawText = rawInput.trim()
        if (rawText.isBlank()) {
            return Pair(null, "Nội dung dán vào đang trống. Vui lòng dán câu trả lời hoặc khối JSON từ AI khác.")
        }

        try {
            var jsonString: String? = null
            val jsonBlockRegex = "```(?:json)?\\s*([\\s\\S]*?)\\s*```".toRegex()
            val match = jsonBlockRegex.find(rawText)
            if (match != null) {
                jsonString = match.groupValues[1].trim()
            } else if (rawText.contains("{") && rawText.contains("}")) {
                val start = rawText.indexOf("{")
                val end = rawText.lastIndexOf("}")
                if (start in 0 until end) {
                    jsonString = rawText.substring(start, end + 1).trim()
                }
            }

            if (jsonString.isNullOrBlank()) {
                val fallbackPlan = parsePlainTextSchedule(rawText)
                if (fallbackPlan != null && fallbackPlan.steps.isNotEmpty()) {
                    return Pair(fallbackPlan, null)
                }
                return Pair(null, "Không tìm thấy khối JSON hợp lệ. Vui lòng đảm bảo nội dung chứa khối { \"scheduleTitle\": ... } hoặc đặt trong ```json ... ```")
            }

            val planJson = JSONObject(jsonString)
            val title = planJson.optString("scheduleTitle", "Lịch trình từ AI ngoài")
            val suggestedStation = planJson.optString("suggestedStationName", "Lịch trình AI")
            val suggestedIcon = planJson.optString("suggestedIcon", "📅")
            val stepsArray = planJson.optJSONArray("steps")

            if (stepsArray == null || stepsArray.length() == 0) {
                return Pair(null, "Khối JSON không chứa danh sách các bước ('steps').")
            }

            val stepsList = mutableListOf<AiScheduleStep>()
            for (i in 0 until stepsArray.length()) {
                val sObj = stepsArray.optJSONObject(i) ?: continue
                val rawTime = sObj.optString("timeScheduled", "08:00")
                val stepTitle = sObj.optString("title", "Bước ${i + 1}")
                val instr = sObj.optString("instruction", "")
                val priority = sObj.optString("priority", "Bình thường")
                val hasLoudAlarm = sObj.optBoolean("hasLoudAlarm", false)

                stepsList.add(
                    AiScheduleStep(
                        timeScheduled = normalizeTime(rawTime),
                        title = stepTitle,
                        instruction = instr,
                        priority = priority,
                        hasLoudAlarm = hasLoudAlarm
                    )
                )
            }

            if (stepsList.isEmpty()) {
                return Pair(null, "Không có bước công việc nào hợp lệ trong khối JSON.")
            }

            val plan = AiSchedulePlan(
                title = title,
                suggestedStationName = suggestedStation,
                suggestedIcon = suggestedIcon.ifBlank { "📅" },
                steps = stepsList
            )
            return Pair(plan, null)

        } catch (e: Exception) {
            Log.e("AiScheduleService", "Error parsing external JSON: ${e.message}", e)
            return Pair(null, "Lỗi phân tích JSON: ${e.localizedMessage ?: "Cấu trúc không hợp lệ"}")
        }
    }

    private fun parsePlainTextSchedule(text: String): AiSchedulePlan? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val timeRegex = """(?:^|\b)(\d{1,2}[:h]\d{2})\b""".toRegex()
        val steps = mutableListOf<AiScheduleStep>()

        for (line in lines) {
            val match = timeRegex.find(line)
            if (match != null) {
                val timeStr = match.groupValues[1].replace('h', ':')
                val normalizedTime = normalizeTime(timeStr)
                val remaining = line.replace(match.value, "").trim(' ', '-', ':', '•', '*', '\t')
                val parts = remaining.split(" - ", " : ", " | ")
                val title = parts.firstOrNull()?.trim() ?: "Công việc"
                val instr = if (parts.size > 1) parts.drop(1).joinToString(" - ").trim() else ""
                val isCooking = line.lowercase().let { it.contains("cá") || it.contains("nấu") || it.contains("kho") }

                steps.add(
                    AiScheduleStep(
                        timeScheduled = normalizedTime,
                        title = title.ifBlank { "Hoạt động" },
                        instruction = instr,
                        priority = "Bình thường",
                        hasLoudAlarm = isCooking
                    )
                )
            }
        }

        if (steps.size >= 2) {
            return AiSchedulePlan(
                title = "Lịch trình từ văn bản",
                suggestedStationName = "Lịch trình 📅",
                suggestedIcon = "📅",
                steps = steps
            )
        }
        return null
    }

    suspend fun generateRoadmapPlan(
        userPrompt: String,
        startDateStr: String,
        history: List<AiRoadmapChatMessage> = emptyList()
    ): Pair<String, AiRoadmapPlan?> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        val hasValidKey = apiKey.isNotBlank() &&
                apiKey != "MY_GEMINI_API_KEY" &&
                apiKey != "YOUR_GEMINI_API_KEY"

        if (!hasValidKey) {
            Log.d("AiScheduleService", "Gemini API key is placeholder or empty. Using smart local roadmap generator.")
            return@withContext generateSmartLocalRoadmap(userPrompt, startDateStr)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val systemInstructionText = """
                Bạn là Trợ lý Lập lộ trình & Kế hoạch Đa tháng thông minh (AI Multi-Month Roadmap & Planning Specialist) bằng tiếng Việt.
                Người dùng muốn tạo kế hoạch / lịch trình trải dài theo tuần hoặc tháng (ví dụ: quy trình trồng trọt nông nghiệp như trồng cây ngô/lúa/rau màu 2-4 tháng, du lịch phượt xuyên Việt 1 tháng, kế hoạch học tập 3 tháng, tập gym 60 ngày, chuẩn bị đám cưới, xây nhà...).
                Ngày bắt đầu của kế hoạch là: $startDateStr.

                Nhiệm vụ của bạn:
                1. Hiểu mục tiêu và thời lượng của người dùng.
                2. Đưa ra lời tư vấn chuyên môn ngắn gọn, thực tế, thân thiện bằng tiếng Việt.
                3. BẮT BUỘC cung cấp một khối JSON định dạng lộ trình chính xác nằm trong cặp ```json ... ``` với cấu trúc:
                ```json
                {
                  "roadmapTitle": "Tiêu đề lộ trình (ví dụ: Kế hoạch Trồng ngô lai 90 ngày hoặc Hành trình Du lịch Xuyên Việt 30 ngày)",
                  "category": "Nông nghiệp", // Một trong các loại: "Nông nghiệp", "Du lịch", "Học tập", "Thể thao", "Đời sống", "Khác"
                  "icon": "🌽", // Icon emoji phù hợp (ví dụ: 🌽, 🌾, 🛵, 🗺️, 📚, 🏋️, 🏡)
                  "totalDays": 90, // Tổng số ngày của toàn bộ kế hoạch
                  "description": "Mô tả tóm tắt mục tiêu và các mốc chính",
                  "tasks": [
                    {
                      "dayOffset": 0, // Ngày thứ mấy tính từ ngày bắt đầu (0 = Ngày bắt đầu, 4 = Ngày thứ 5, 14 = Ngày thứ 15...)
                      "scheduledTime": "08:00", // Giờ thực hiện (HH:mm)
                      "phase": "Giai đoạn 1: Làm đất & Gieo hạt", // Tên chặng hoặc giai đoạn
                      "title": "Cày bừa đất & Bón phân lót",
                      "instruction": "Cày sâu 20-25cm, bón lót phân chuồng hoai mục và NPK, lên luống thoát nước.",
                      "priority": "Cao", // "Cao", "Bình thường", "Thấp"
                      "hasLoudAlarm": false
                    }
                  ]
                }
                ```
                QUY TẮC PHÂN BỔ THỜI GIAN:
                - `dayOffset`: Bắt đầu từ 0 (ngày đầu tiên), tăng dần theo các mốc thực tế (ví dụ: ngày 0, ngày 3, ngày 10, ngày 20, ngày 35, ngày 60, ngày 90...).
                - Các công việc phải chia đều qua các tuần/tháng theo chu kỳ thực tế, có hướng dẫn cụ thể dễ làm theo.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray()

                // Add recent history for context
                history.takeLast(6).forEach { msg ->
                    val contentObj = JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        val partsArr = JSONArray().apply {
                            put(JSONObject().apply { put("text", msg.text) })
                        }
                        put("parts", partsArr)
                    }
                    contentsArray.put(contentObj)
                }

                // Add current user prompt
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("AiScheduleService", "Roadmap API error: ${response.code} - $responseBody")
                return@withContext generateSmartLocalRoadmap(userPrompt, startDateStr)
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts")
            val text = partsArr?.optJSONObject(0)?.optString("text") ?: ""

            if (text.isBlank()) {
                return@withContext generateSmartLocalRoadmap(userPrompt, startDateStr)
            }

            val (chatMessage, plan) = parseAiRoadmapResponse(text, userPrompt, startDateStr)
            Pair(chatMessage, plan)
        } catch (e: Exception) {
            Log.e("AiScheduleService", "Roadmap AI call failed: ${e.message}", e)
            generateSmartLocalRoadmap(userPrompt, startDateStr)
        }
    }

    private fun parseAiRoadmapResponse(rawText: String, prompt: String, startDateStr: String): Pair<String, AiRoadmapPlan?> {
        try {
            var jsonString: String? = null
            var chatText = rawText

            val jsonBlockRegex = "```(?:json)?\\s*([\\s\\S]*?)\\s*```".toRegex()
            val match = jsonBlockRegex.find(rawText)
            if (match != null) {
                jsonString = match.groupValues[1].trim()
                chatText = rawText.replace(match.value, "").trim()
            } else if (rawText.contains("{") && rawText.contains("}")) {
                val start = rawText.indexOf("{")
                val end = rawText.lastIndexOf("}")
                if (start in 0 until end) {
                    jsonString = rawText.substring(start, end + 1)
                    chatText = (rawText.substring(0, start) + "\n" + rawText.substring(end + 1)).trim()
                }
            }

            if (jsonString != null) {
                val planJson = JSONObject(jsonString)
                val title = planJson.optString("roadmapTitle", "Lộ trình đề xuất")
                val category = planJson.optString("category", "Đời sống")
                val icon = planJson.optString("icon", "🌱")
                val totalDays = planJson.optInt("totalDays", 30)
                val desc = planJson.optString("description", "")
                val tasksArray = planJson.optJSONArray("tasks")

                val tasksList = mutableListOf<AiRoadmapTaskStep>()
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val tObj = tasksArray.optJSONObject(i) ?: continue
                        val dayOffset = tObj.optInt("dayOffset", i)
                        val time = tObj.optString("scheduledTime", "08:00")
                        val phase = tObj.optString("phase", "Giai đoạn ${i / 4 + 1}")
                        val taskTitle = tObj.optString("title", "Công việc ${i + 1}")
                        val instr = tObj.optString("instruction", "")
                        val priority = tObj.optString("priority", "Bình thường")
                        val hasLoudAlarm = tObj.optBoolean("hasLoudAlarm", false)

                        tasksList.add(
                            AiRoadmapTaskStep(
                                dayOffset = dayOffset,
                                scheduledTime = normalizeTime(time),
                                phase = phase,
                                title = taskTitle,
                                instruction = instr,
                                priority = priority,
                                hasLoudAlarm = hasLoudAlarm
                            )
                        )
                    }
                }

                if (tasksList.isNotEmpty()) {
                    val roadmapPlan = AiRoadmapPlan(
                        title = title,
                        category = category,
                        icon = icon,
                        totalDays = totalDays.coerceAtLeast(tasksList.maxOfOrNull { it.dayOffset }?.plus(1) ?: 30),
                        description = desc,
                        tasks = tasksList
                    )
                    return Pair(chatText.ifBlank { "Dưới đây là lộ trình chi tiết theo ngày tôi đã thiết kế cho bạn:" }, roadmapPlan)
                }
            }
        } catch (e: Exception) {
            Log.e("AiScheduleService", "Failed to parse roadmap json: ${e.message}", e)
        }
        return generateSmartLocalRoadmap(prompt, startDateStr)
    }

    private fun generateSmartLocalRoadmap(prompt: String, startDateStr: String): Pair<String, AiRoadmapPlan?> {
        val p = prompt.lowercase()
        return when {
            p.contains("ngô") || p.contains("bắp") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "07:30", phase = "Giai đoạn 1: Chuẩn bị & Gieo hạt", title = "Làm đất & Bón phân lót", instruction = "Cày bừa tơi xốp, bón lót phân chuồng hoai mục và NPK, lên luống rộng 1.2m, rãnh thoát nước tốt."),
                    AiRoadmapTaskStep(dayOffset = 1, scheduledTime = "08:00", phase = "Giai đoạn 1: Chuẩn bị & Gieo hạt", title = "Gieo hạt ngô giống & Tưới ẩm", instruction = "Tra hạt cách nhau 25-30cm, phủ lớp đất mỏng 2-3cm, tưới ẩm giữ nước cho hạt nảy mầm."),
                    AiRoadmapTaskStep(dayOffset = 5, scheduledTime = "08:00", phase = "Giai đoạn 1: Chuẩn bị & Gieo hạt", title = "Kiểm tra nảy mầm & Dặm cây", instruction = "Kiểm tra tỷ lệ nảy mầm, dặm lại các hốc hạt không lên để mật độ đồng đều."),
                    AiRoadmapTaskStep(dayOffset = 15, scheduledTime = "08:00", phase = "Giai đoạn 2: Cây con & Bón thúc đợt 1", title = "Làm cỏ, tỉa định cây & Bón thúc đợt 1", instruction = "Tỉa mỗi hốc để lại 1 cây khỏe nhất. Bón thúc đạm urê và kali quanh gốc, tưới nước ngay sau khi bón."),
                    AiRoadmapTaskStep(dayOffset = 30, scheduledTime = "08:00", phase = "Giai đoạn 3: Sinh trưởng mạnh & Vun xới", title = "Bón thúc đợt 2 & Vun gốc cao", instruction = "Bón thúc NPK lượng nhiều hơn, xới đất vun cao vào gốc để chống đổ ngã khi mưa gió."),
                    AiRoadmapTaskStep(dayOffset = 45, scheduledTime = "08:30", phase = "Giai đoạn 3: Sinh trưởng mạnh & Vun xới", title = "Kiểm tra sâu keo mùa thu & Phun phòng", instruction = "Quan sát nõn ngô xem có sâu keo cắn lá không. Phun chế phẩm sinh học phòng ngừa sâu đục thân."),
                    AiRoadmapTaskStep(dayOffset = 60, scheduledTime = "08:00", phase = "Giai đoạn 4: Trỗ cờ & Phun râu thụ phấn", title = "Tưới nước đẫm & Bón đón bắp", instruction = "Giai đoạn trỗ cờ thụ phấn cần đủ nước. Tưới ẩm đều, bón bổ sung kali để hạt ngô mẩy chắc."),
                    AiRoadmapTaskStep(dayOffset = 75, scheduledTime = "08:00", phase = "Giai đoạn 5: Vào hạt & Chín sữa", title = "Kiểm tra độ chín sữa & Phòng chuột", instruction = "Bắp bắt đầu vào giai đoạn chín sữa ngọt. Đặt bẫy sinh học phòng chuột cắn phá bắp."),
                    AiRoadmapTaskStep(dayOffset = 90, scheduledTime = "07:00", phase = "Giai đoạn 6: Thu hoạch", title = "Thu hoạch ngô bắp tươi", instruction = "Râu bắp khô đen, hạt căng bóng đầy đặn. Bẻ bắp sáng sớm để giữ độ ngọt tươi ngon nhất.")
                )
                val plan = AiRoadmapPlan(
                    title = "Quy trình Trồng ngô lai năng suất cao (90 ngày)",
                    category = "Nông nghiệp",
                    icon = "🌽",
                    totalDays = 90,
                    description = "Lộ trình chi tiết từng mốc từ làm đất, gieo hạt, bón lót, tỉa dặm, bón thúc, phòng sâu bệnh đến khi bẻ bắp thu hoạch.",
                    tasks = tasks
                )
                Pair("Tôi đã thiết lập lộ trình 90 ngày chuẩn nông nghiệp cho vụ ngô của bạn:", plan)
            }
            p.contains("du lịch") || p.contains("xuyên việt") || p.contains("phượt") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "07:00", phase = "Chặng 1: Miền Bắc (Hà Nội - Ninh Bình)", title = "Khởi hành từ Hà Nội -> Khám phá Ninh Bình", instruction = "Xuất phát sớm, tham quan Quần thể danh thắng Tràng An, check-in Hang Múa, thưởng thức dê núi."),
                    AiRoadmapTaskStep(dayOffset = 2, scheduledTime = "08:00", phase = "Chặng 1: Miền Bắc (Hà Nội - Ninh Bình)", title = "Khám phá Vịnh Hạ Long & Ngủ đêm tàu", instruction = "Di chuyển ra Quảng Ninh, lên du thuyền vịnh Hạ Long, chèo thuyền kayak qua hang Luồn."),
                    AiRoadmapTaskStep(dayOffset = 5, scheduledTime = "08:00", phase = "Chặng 2: Bắc Trung Bộ (Quảng Bình - Huế)", title = "Thám hiểm Động Phong Nha & Hang Tối", instruction = "Đến Quảng Bình, trải nghiệm đu dây zipline và chèo thuyền khám phá hang động kỳ vĩ."),
                    AiRoadmapTaskStep(dayOffset = 8, scheduledTime = "08:30", phase = "Chặng 2: Bắc Trung Bộ (Quảng Bình - Huế)", title = "Cố đô Huế - Đại Nội & Ca Huế sông Hương", instruction = "Tham quan Hoàng thành Huế, lăng Tự Đức, chiều tối đi thuyền rồng nghe ca Huế trên sông Hương."),
                    AiRoadmapTaskStep(dayOffset = 12, scheduledTime = "09:00", phase = "Chặng 3: Miền Trung (Đà Nẵng - Hội An)", title = "Vượt đèo Hải Vân -> Đà Nẵng & Phố cổ Hội An", instruction = "Check-in cầu Rồng, bán đảo Sơn Trà. Tối thả hoa đăng lung linh trên sông Hoài phố cổ Hội An."),
                    AiRoadmapTaskStep(dayOffset = 16, scheduledTime = "08:00", phase = "Chặng 4: Duyên hải Nam Trung Bộ (Quy Nhơn - Phú Yên)", title = "Eo Gió Quy Nhơn & Ghềnh Đá Đĩa Phú Yên", instruction = "Ngắm bình minh Kỳ Co - Eo Gió, ghé thăm Ghềnh Đá Đĩa độc nhất vô nhị và hải đăng Đại Lãnh."),
                    AiRoadmapTaskStep(dayOffset = 20, scheduledTime = "08:00", phase = "Chặng 5: Biển đảo & Cao nguyên (Nha Trang - Đà Lạt)", title = "Thành phố biển Nha Trang -> Thành phố sương mù Đà Lạt", instruction = "Tắm biển vịnh Nha Trang, leo đèo Khánh Lê lên Đà Lạt mộng mơ, săn mây đồi chè Cầu Đất."),
                    AiRoadmapTaskStep(dayOffset = 25, scheduledTime = "08:30", phase = "Chặng 6: Đông Nam Bộ (TP. Hồ Chí Minh)", title = "Khám phá Sài Gòn phồn hoa", instruction = "Tham quan Dinh Độc Lập, Nhà thờ Đức Bà, Bưu điện trung tâm, dạo phố đi bộ Nguyễn Huệ."),
                    AiRoadmapTaskStep(dayOffset = 28, scheduledTime = "07:30", phase = "Chặng 7: Miền Tây sông nước (Cần Thơ - Cà Mau)", title = "Chợ nổi Cái Răng & Rừng tràm Trà Sư", instruction = "Đi thuyền chợ nổi Cái Răng sáng sớm, ngắm rừng tràm xanh mướt, thưởng thức lẩu mắm miền Tây."),
                    AiRoadmapTaskStep(dayOffset = 30, scheduledTime = "09:00", phase = "Chặng 7: Miền Tây sông nước (Cần Thơ - Cà Mau)", title = "Chạm mốc Đất Mũi Cà Mau - Hoàn thành chuyến đi", instruction = "Chụp ảnh lưu niệm tại cột mốc tọa độ quốc gia GPS 0001 Đất Mũi Cà Mau. Kết thúc hành trình tuyệt vời!")
                )
                val plan = AiRoadmapPlan(
                    title = "Hành trình Phượt Xuyên Việt 30 ngày (Bắc - Trung - Nam)",
                    category = "Du lịch",
                    icon = "🛵",
                    totalDays = 30,
                    description = "Lộ trình xuyên suốt chiều dài đất nước qua các điểm đến biểu tượng từ Thủ đô Hà Nội qua Cố đô Huế, Hội An, Đà Lạt đến Cột mốc Đất Mũi Cà Mau.",
                    tasks = tasks
                )
                Pair("Tôi đã lên lịch trình chi tiết 30 ngày phượt Xuyên Việt từ Bắc vào Nam cho bạn:", plan)
            }
            p.contains("lúa") || p.contains("gạo") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "07:00", phase = "Giai đoạn 1: Chuẩn bị giống & Làm đất", title = "Ngâm ủ hạt giống lúa & Cày bừa ruộng", instruction = "Xử lý giống bằng nước ấm 3 sôi 2 lạnh, ngâm ủ nứt nanh. Cày bừa trục phẳng mặt ruộng."),
                    AiRoadmapTaskStep(dayOffset = 3, scheduledTime = "07:30", phase = "Giai đoạn 1: Chuẩn bị giống & Làm đất", title = "Gieo mạ / Gieo sạ hàng", instruction = "Sạ đều tay trên mặt luống có rãnh thoát nước, giữ ẩm mặt ruộng cho rễ mạ bám sâu."),
                    AiRoadmapTaskStep(dayOffset = 15, scheduledTime = "07:30", phase = "Giai đoạn 2: Cây con & Đẻ nhánh", title = "Bón thúc đợt 1 (Thúc đẻ nhánh)", instruction = "Bón phân đạm và lân kích thích đẻ nhánh sớm, giữ mức nước nông 2-3cm."),
                    AiRoadmapTaskStep(dayOffset = 35, scheduledTime = "07:30", phase = "Giai đoạn 3: Phân hóa đòng", title = "Bón đón đòng & Rút nước phơi ruộng", instruction = "Rút nước phơi ruộng nứt chân chim để hãm nhánh vô hiệu, bón đón đòng đủ kali."),
                    AiRoadmapTaskStep(dayOffset = 55, scheduledTime = "08:00", phase = "Giai đoạn 4: Trỗ bông & Phòng sâu rầy", title = "Phòng ngừa đạo ôn cổ bông & Rầy nâu", instruction = "Kiểm tra sâu cuốn lá và rầy nâu trước khi lúa trỗ, phun thuốc bảo vệ đòng lúa."),
                    AiRoadmapTaskStep(dayOffset = 75, scheduledTime = "08:00", phase = "Giai đoạn 5: Ngậm sữa & Chắc hạt", title = "Bơm nước ngọt nuôi hạt & Giữ lá đòng", instruction = "Giữ lá đòng xanh bền để quang hợp nuôi hạt mẩy vàng, phòng chuột cắn phá."),
                    AiRoadmapTaskStep(dayOffset = 100, scheduledTime = "06:30", phase = "Giai đoạn 6: Thu hoạch", title = "Gặt lúa mùa chín vàng rộ", instruction = "Lúa chín trên 85% diện tích bông, điều máy gặt đập liên hợp thu hoạch nhanh gọn.")
                )
                val plan = AiRoadmapPlan(
                    title = "Kế hoạch Canh tác Lúa nước chất lượng cao (100 ngày)",
                    category = "Nông nghiệp",
                    icon = "🌾",
                    totalDays = 100,
                    description = "Quy trình chăm sóc lúa từng giai đoạn từ ngâm giống, sạ mạ, đẻ nhánh, bón đón đòng đến gặt lúa vàng.",
                    tasks = tasks
                )
                Pair("Tôi đã thiết lập lộ trình canh tác lúa 100 ngày cho bạn:", plan)
            }
            p.contains("học") || p.contains("tiếng anh") || p.contains("thi") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "08:00", phase = "Tháng 1: Củng cố Nền tảng", title = "Đánh giá trình độ & Lập danh sách 1000 từ vựng cốt lõi", instruction = "Làm bài test đầu vào, ghi chú từ vựng theo chủ đề gia đình, công việc, du lịch."),
                    AiRoadmapTaskStep(dayOffset = 10, scheduledTime = "19:30", phase = "Tháng 1: Củng cố Nền tảng", title = "Chuẩn hóa bảng phiên âm IPA & Nghe cơ bản", instruction = "Luyện phát âm chuẩn 44 âm IPA, nghe podcast tiếng Anh 20 phút mỗi ngày."),
                    AiRoadmapTaskStep(dayOffset = 25, scheduledTime = "19:30", phase = "Tháng 1: Củng cố Nền tảng", title = "Nắm vững 6 thì thông dụng trong giao tiếp", instruction = "Luyện tập thì Hiện tại đơn, Quá khứ đơn, Tương lai đơn, Hiện tại tiếp diễn và Hoàn thành."),
                    AiRoadmapTaskStep(dayOffset = 40, scheduledTime = "19:30", phase = "Tháng 2: Phát triển Kỹ năng Phản xạ", title = "Thực hành phương pháp Shadowing (Nói nhại)", instruction = "Chọn video ngắn 3-5 phút, nói nhại theo ngữ điệu người bản xứ để tăng độ trôi chảy."),
                    AiRoadmapTaskStep(dayOffset = 50, scheduledTime = "20:00", phase = "Tháng 2: Phát triển Kỹ năng Phản xạ", title = "Tập viết nhật ký tiếng Anh ngắn & Tự thuyết trình", instruction = "Viết 1 đoạn văn 100 từ kể về ngày hôm nay, quay video tự nói lại bằng tiếng Anh."),
                    AiRoadmapTaskStep(dayOffset = 60, scheduledTime = "09:00", phase = "Tổng kết & Đánh giá", title = "Kiểm tra tiến bộ & Giao tiếp thực chiến", instruction = "Làm bài kiểm tra đánh giá sau 2 tháng, tham gia câu lạc bộ tiếng Anh giao tiếp.")
                )
                val plan = AiRoadmapPlan(
                    title = "Lộ trình Tự học Tiếng Anh Giao tiếp bứt phá (60 ngày)",
                    category = "Học tập",
                    icon = "📚",
                    totalDays = 60,
                    description = "Phương pháp học từ vựng, phát âm IPA, ngữ pháp nền tảng đến phản xạ nói nhại và giao tiếp tự tin.",
                    tasks = tasks
                )
                Pair("Tôi đã thiết lập lộ trình học tập 60 ngày dành cho bạn:", plan)
            }
            else -> {
                // Adaptive default roadmap for custom request
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "08:00", phase = "Chặng 1: Khởi động & Chuẩn bị", title = "Khởi đầu kế hoạch: $prompt", instruction = "Chuẩn bị nguồn lực, dụng cụ và tài liệu cần thiết để bắt đầu hành trình thuận lợi."),
                    AiRoadmapTaskStep(dayOffset = 5, scheduledTime = "08:30", phase = "Chặng 1: Khởi động & Chuẩn bị", title = "Thực hiện bước tiền đề & Đánh giá sơ bộ", instruction = "Tiến hành các bước cơ bản ban đầu, ghi chép lại kết quả để điều chỉnh kịp thời."),
                    AiRoadmapTaskStep(dayOffset = 12, scheduledTime = "09:00", phase = "Chặng 2: Tăng tốc & Triển khai chính", title = "Đẩy mạnh các hoạt động trọng tâm", instruction = "Tập trung triển khai khối lượng công việc chính theo kế hoạch đề ra."),
                    AiRoadmapTaskStep(dayOffset = 20, scheduledTime = "08:30", phase = "Chặng 2: Tăng tốc & Triển khai chính", title = "Kiểm tra tiến độ & Khắc phục trở ngại", instruction = "Rà soát lại những việc đã làm được và các điểm cần tối ưu cải thiện."),
                    AiRoadmapTaskStep(dayOffset = 30, scheduledTime = "09:00", phase = "Chặng 3: Hoàn thiện & Đích đến", title = "Về đích & Tổng kết thành quả", instruction = "Hoàn tất các bước cuối cùng, ăn mừng thành công và đúc rút kinh nghiệm giá trị.")
                )
                val plan = AiRoadmapPlan(
                    title = "Lộ trình Kế hoạch: ${prompt.take(40)}",
                    category = "Đời sống",
                    icon = "✨",
                    totalDays = 30,
                    description = "Kế hoạch được tùy chỉnh từng mốc thời gian giúp bạn hoàn thành mục tiêu suôn sẻ.",
                    tasks = tasks
                )
                Pair("Tôi đã phác thảo lộ trình thực tế cho mục tiêu của bạn:", plan)
            }
        }
    }

    /**
     * Sinh văn bản Prompt tối ưu để nhờ AI ngoài (ChatGPT, Claude, DeepSeek...)
     * lập Lộ trình & Kế hoạch Đa tháng kèm khuôn mẫu JSON chuẩn.
     */
    fun generateExternalRoadmapPrompt(
        topic: String,
        startDate: String = "2026-10-21",
        durationDays: Int = 30,
        category: String = "Nông nghiệp"
    ): String {
        val cleanTopic = topic.trim().ifBlank { "Lộ trình kế hoạch chi tiết đa tháng" }
        val lower = cleanTopic.lowercase()

        val categoryEmoji = when {
            category.contains("Nông nghiệp") || lower.contains("ngô") || lower.contains("lúa") || lower.contains("trồng") -> "🌱"
            category.contains("Du lịch") || lower.contains("phượt") || lower.contains("du lịch") || lower.contains("tour") -> "✈️"
            category.contains("Học tập") || lower.contains("học") || lower.contains("thi") || lower.contains("ielts") -> "📚"
            category.contains("Sức khỏe") || lower.contains("chạy") || lower.contains("gym") || lower.contains("giảm cân") -> "🏃"
            else -> "🎯"
        }

        val requirements = when {
            lower.contains("ngô") || lower.contains("bắp") || (category.contains("Nông nghiệp") && (lower.contains("rau") || lower.contains("trồng") || lower.contains("lúa") || lower.contains("cây"))) -> """
Các giai đoạn cần có trong lộ trình nông nghiệp/canh tác:
- Giai đoạn 1: Chuẩn bị giống, xử lý đất và bón lót
- Giai đoạn 2: Gieo hạt / cấy sạ / trồng cây con và tưới ẩm
- Giai đoạn 3: Chăm sóc cây con, tỉa dặm và bón thúc đợt 1
- Giai đoạn 4: Giai đoạn phát triển mạnh, vun xới, bón thúc đợt 2
- Giai đoạn 5: Phòng trừ sâu bệnh sinh học, tưới nước nuôi hoa/bắp/đòng
- Giai đoạn 6: Vào hạt, dưỡng quả và chuẩn bị thu hoạch
- Giai đoạn 7: Thu hoạch đúng thời điểm và bảo quản sau thu hoạch
            """.trimIndent()

            lower.contains("du lịch") || lower.contains("phượt") || lower.contains("xuyên việt") || lower.contains("tour") -> """
Các giai đoạn/chặng cần có trong lộ trình du lịch:
- Phân chia các chặng/tỉnh thành theo hành trình địa lý hợp lý
- Mỗi chặng nêu rõ các điểm tham quan biểu tượng, đặc sản địa phương, lưu trú
- Có mốc ngày di chuyển và mốc ngày nghỉ dưỡng/trải nghiệm
- Kết thúc bằng điểm đến trọn vẹn và tổng kết chuyến đi
            """.trimIndent()

            lower.contains("học") || lower.contains("thi") || lower.contains("ielts") || lower.contains("tiếng") -> """
Các giai đoạn cần có trong lộ trình học tập/rèn luyện:
- Chặng 1: Kiểm tra trình độ, củng cố kiến thức nền tảng và phương pháp
- Chặng 2: Nâng cao chuyên sâu, thực hành giải đề/kỹ năng phản xạ
- Chặng 3: Thi thử áp lực thời gian, khắc phục điểm yếu và về đích tự tin
            """.trimIndent()

            else -> """
Các giai đoạn cần có trong kế hoạch đa tháng:
- Chặng 1: Khởi động, chuẩn bị nguồn lực và triển khai bước đầu
- Chặng 2: Đẩy mạnh các hoạt động cốt lõi và kiểm tra định kỳ
- Chặng 3: Tăng tốc, hoàn thiện các hạng mục trọng điểm
- Chặng 4: Tổng kết, đánh giá thành quả và bước tiếp theo
            """.trimIndent()
        }

        return """
Hãy đóng vai trò là một chuyên gia lập kế hoạch và lịch trình chi tiết. Hãy giúp tôi lập Lộ trình & Kế hoạch Đa tháng: "$cleanTopic".

Thông tin cơ bản:
- Ngày bắt đầu: $startDate
- Tổng thời lượng dự kiến: $durationDays ngày
- Phân loại: $category $categoryEmoji

Yêu cầu chi tiết:
$requirements

Hãy phân bổ các công việc/nhiệm vụ trải đều theo các mốc ngày (dayOffset từ 0 đến ${durationDays - 1}, trong đó dayOffset = 0 là Ngày bắt đầu $startDate).

Sau khi phân tích, BẮT BUỘC bạn phải trả về một khối JSON chuẩn duy nhất (đặt trong ```json ... ```) để tôi có thể sao chép và dán trực tiếp vào ứng dụng Android quản lý lịch trình:

```json
{
  "title": "$cleanTopic",
  "category": "$category",
  "icon": "$categoryEmoji",
  "totalDays": $durationDays,
  "description": "Mô tả tổng quan, mục tiêu đạt được sau $durationDays ngày của lộ trình.",
  "tasks": [
    {
      "dayOffset": 0,
      "scheduledTime": "07:30",
      "phase": "Giai đoạn 1: Chuẩn bị & Khởi động",
      "title": "Tên công việc / hoạt động cụ thể",
      "instruction": "Hướng dẫn chi tiết cách thực hiện, thông số kỹ thuật hoặc lưu ý an toàn",
      "priority": "Cao",
      "hasLoudAlarm": false
    }
  ]
}
```

Quy tắc bắt buộc:
1. "dayOffset": Số ngày tính từ ngày bắt đầu (0 là ngày đầu tiên, 1 là ngày thứ hai...). Các công việc phải được sắp xếp theo "dayOffset" tăng dần (tối đa dayOffset <= ${durationDays - 1}).
2. "scheduledTime": Giờ dự kiến thực hiện trong ngày, dạng 24h "HH:mm" (ví dụ: "07:30", "08:00", "14:00", "19:30").
3. "phase": Tên giai đoạn hoặc chặng (ví dụ: "Giai đoạn 1: Làm đất", "Chặng 1: Miền Bắc", "Tháng 1: Nền tảng").
4. "priority": Chỉ được dùng một trong 3 giá trị: "Cao", "Bình thường", "Thấp".
5. "hasLoudAlarm": true nếu là công việc cực kỳ quan trọng cần chuông báo thức lớn để không bao giờ quên, ngược lại là false.
6. Hãy tạo ít nhất 6 đến 15 mốc công việc chi tiết, thực tế, khoa học và khả thi để người dùng dễ dàng theo dõi.
        """.trimIndent()
    }

    /**
     * Phân tích cú pháp văn bản hoặc chuỗi JSON do AI ngoài (ChatGPT, Claude...) sinh ra
     * để tạo thành một đối tượng AiRoadmapPlan hoàn chỉnh.
     */
    fun parseExternalRoadmap(rawInput: String): Pair<AiRoadmapPlan?, String?> {
        val rawText = rawInput.trim()
        if (rawText.isBlank()) {
            return Pair(null, "Nội dung dán vào đang trống. Vui lòng dán câu trả lời hoặc khối JSON từ AI khác.")
        }

        try {
            var jsonString: String? = null
            val jsonBlockRegex = "```(?:json)?\\s*([\\s\\S]*?)\\s*```".toRegex()
            val match = jsonBlockRegex.find(rawText)
            if (match != null) {
                jsonString = match.groupValues[1].trim()
            } else if (rawText.contains("{") && rawText.contains("}")) {
                val start = rawText.indexOf("{")
                val end = rawText.lastIndexOf("}")
                if (start in 0 until end) {
                    jsonString = rawText.substring(start, end + 1).trim()
                }
            }

            if (jsonString.isNullOrBlank()) {
                return Pair(null, "Không tìm thấy khối JSON hợp lệ. Vui lòng đảm bảo nội dung chứa khối { \"title\": ... } hoặc đặt trong ```json ... ```")
            }

            val planJson = JSONObject(jsonString)
            val title = planJson.optString("title").ifBlank {
                planJson.optString("roadmapTitle", "Lộ trình AI").ifBlank { "Lộ trình Kế hoạch Đa tháng" }
            }
            val category = planJson.optString("category", "Nông nghiệp")
            val icon = planJson.optString("icon", "🌱")
            var totalDays = planJson.optInt("totalDays", 0)
            val description = planJson.optString("description", "")

            val tasksArray = planJson.optJSONArray("tasks")
                ?: planJson.optJSONArray("steps")
                ?: planJson.optJSONArray("roadmap")

            if (tasksArray == null || tasksArray.length() == 0) {
                return Pair(null, "Khối JSON không chứa danh sách các mốc công việc ('tasks').")
            }

            val tasksList = mutableListOf<AiRoadmapTaskStep>()
            var maxOffset = 0

            for (i in 0 until tasksArray.length()) {
                val tObj = tasksArray.optJSONObject(i) ?: continue

                // Check dayOffset or day (if external AI used 1-based "day" or "dayNumber")
                val dayOffset = when {
                    tObj.has("dayOffset") -> tObj.optInt("dayOffset", 0)
                    tObj.has("day") -> maxOf(0, tObj.optInt("day", 1) - 1)
                    tObj.has("dayNumber") -> maxOf(0, tObj.optInt("dayNumber", 1) - 1)
                    else -> i * 5
                }
                if (dayOffset > maxOffset) {
                    maxOffset = dayOffset
                }

                val rawTime = tObj.optString("scheduledTime", "08:00").ifBlank {
                    tObj.optString("time", "08:00")
                }
                val phase = tObj.optString("phase").ifBlank { "Giai đoạn ${i + 1}" }
                val taskTitle = tObj.optString("title").ifBlank { "Công việc mốc ngày ${dayOffset + 1}" }
                val instr = tObj.optString("instruction", "")
                val priority = tObj.optString("priority", "Bình thường")
                val hasLoudAlarm = tObj.optBoolean("hasLoudAlarm", false)

                tasksList.add(
                    AiRoadmapTaskStep(
                        dayOffset = dayOffset,
                        scheduledTime = normalizeTime(rawTime),
                        phase = phase,
                        title = taskTitle,
                        instruction = instr,
                        priority = priority,
                        hasLoudAlarm = hasLoudAlarm
                    )
                )
            }

            if (tasksList.isEmpty()) {
                return Pair(null, "Không có mốc công việc hợp lệ nào trong danh sách.")
            }

            // Sắp xếp các tasks theo thứ tự dayOffset tăng dần
            tasksList.sortBy { it.dayOffset }

            if (totalDays <= 0) {
                totalDays = maxOffset + 1
            }

            val plan = AiRoadmapPlan(
                title = title,
                category = category,
                icon = icon.ifBlank { "🌱" },
                totalDays = totalDays,
                description = description,
                tasks = tasksList
            )
            return Pair(plan, null)

        } catch (e: Exception) {
            Log.e("AiScheduleService", "Error parsing external roadmap JSON: ${e.message}", e)
            return Pair(null, "Lỗi phân tích JSON lộ trình: ${e.localizedMessage ?: "Cấu trúc không hợp lệ"}")
        }
    }
}
