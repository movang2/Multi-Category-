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

    data class RoadmapIntentAnalysis(
        val detectedType: RoadmapPlanType,
        val isShortEvent: Boolean,
        val isTimeBound: Boolean,
        val isMultiDay: Boolean,
        val isHybrid: Boolean,
        val detectedEventDate: String? = null,
        val detectedEventTime: String? = null,
        val detectedDurationHours: Double? = null,
        val detectedPrepDays: Int? = null,
        val detectedFollowUpDays: Int? = null,
        val detectedOrigin: String? = null,
        val detectedDestination: String? = null,
        val detectedTimezone: String? = null,
        val isShortEventConflictWith90Days: Boolean = false,
        val suggestedDurationDays: Int = 30,
        val conflictReason: String = ""
    )

    fun analyzeRoadmapIntent(
        topic: String,
        currentDurationDays: Int = 90,
        selectedPlanType: RoadmapPlanType = RoadmapPlanType.AUTO
    ): RoadmapIntentAnalysis {
        val lower = topic.lowercase().trim()

        // 1. Phân tích thực thể: Ngày giờ, thời lượng, địa điểm
        val dateRegex = "(?:ngày\\s*)?(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{4})?)".toRegex()
        val timeRegex = "(\\b[0-2]?\\d[:h][0-5]\\d\\b|\\b[0-2]?\\d\\s*(?:giờ|h|g)\\b)".toRegex()
        val durationRegex = "(\\d+(?:\\.\\d+)?)\\s*(?:tiếng|giờ|h|hour)".toRegex()
        val prepRegex = "chuẩn bị\\s*(\\d+)?\\s*(?:ngày|tháng)?".toRegex()
        val followUpRegex = "(?:sau\\s*(?:khi\\s*)?(?:về|sự kiện)|ổn định)\\s*(\\d+)?\\s*ngày".toRegex()

        val detectedDateMatch = dateRegex.find(lower)?.value
        val detectedTimeMatch = timeRegex.find(lower)?.value
        val detectedDurationMatch = durationRegex.find(lower)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val detectedPrepMatch = prepRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val detectedFollowUpMatch = followUpRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()

        var origin: String? = null
        var destination: String? = null
        if (lower.contains("busan")) origin = "Busan, Hàn Quốc"
        if (lower.contains("việt nam") || lower.contains("vn") || lower.contains("về nước")) destination = "Việt Nam"

        val timezone = when {
            lower.contains("giờ hàn") || lower.contains("hàn quốc") || lower.contains("kst") -> "KST (UTC+9)"
            lower.contains("giờ việt") || lower.contains("ict") -> "ICT (UTC+7)"
            else -> null
        }

        // 2. Nhận diện các loại sự kiện
        val shortKeywords = listOf(
            "bay", "chuyến bay", "vé máy bay", "flight", "máy bay",
            "khám", "bác sĩ", "bệnh viện", "phỏng vấn", "cuộc hẹn",
            "buổi thi", "thi cử", "lấy giấy tờ", "tiệc", "đám cưới",
            "họp", "hội thảo", "lễ", "nhận bằng"
        )
        val timeBoundKeywords = listOf(
            "chuyển nhà", "dọn nhà", "hoàn thành dự án", "làm hồ sơ",
            "thủ tục trong", "dự án trong", "xong trong 7 ngày", "xong trong 10 ngày",
            "xong trong 14 ngày", "hoàn tất trong"
        )
        val multiDayKeywords = listOf(
            "trồng", "canh tác", "nuôi", "chăm sóc cây", "học",
            "tiếng hàn", "tiếng anh", "ielts", "tập gym", "thể hình",
            "marathon", "du lịch", "phượt", "100 ngày", "90 ngày", "60 ngày"
        )

        val hasShort = shortKeywords.any { lower.contains(it) }
        val hasTimeBound = timeBoundKeywords.any { lower.contains(it) }
        val hasMultiDay = multiDayKeywords.any { lower.contains(it) }
        val hasPrep = lower.contains("chuẩn bị") || detectedPrepMatch != null
        val hasFollowUp = lower.contains("ổn định") || lower.contains("sau khi") || detectedFollowUpMatch != null

        val autoType = when {
            hasShort && (hasPrep || hasFollowUp) -> RoadmapPlanType.HYBRID
            hasShort -> RoadmapPlanType.SHORT_EVENT
            hasTimeBound -> RoadmapPlanType.TIME_BOUND
            hasMultiDay -> RoadmapPlanType.MULTI_DAY
            else -> RoadmapPlanType.MULTI_DAY
        }

        val effectiveType = if (selectedPlanType != RoadmapPlanType.AUTO) selectedPlanType else autoType

        // 3. Xung đột giữa sự kiện ngắn và số ngày 90
        val isConflict = (effectiveType == RoadmapPlanType.SHORT_EVENT || (effectiveType == RoadmapPlanType.AUTO && hasShort && !hasPrep)) &&
                currentDurationDays >= 60

        val suggestedDays = when (effectiveType) {
            RoadmapPlanType.SHORT_EVENT -> if (lower.contains("qua đêm") || (detectedTimeMatch?.contains("23") == true)) 2 else 1
            RoadmapPlanType.TIME_BOUND -> 7
            RoadmapPlanType.HYBRID -> (detectedPrepMatch ?: 60) + (detectedFollowUpMatch ?: 3) + 2
            RoadmapPlanType.MULTI_DAY -> currentDurationDays
            RoadmapPlanType.AUTO -> if (hasShort) 2 else currentDurationDays
        }

        val conflictReason = if (isConflict) {
            "Yêu cầu này có vẻ là một sự kiện ngắn (${if (detectedDurationMatch != null) "${detectedDurationMatch.toInt()} tiếng" else "vài tiếng/1-2 ngày"}), nhưng bạn đang chọn kế hoạch $currentDurationDays ngày. Bạn muốn tạo lịch trình sự kiện hay giữ kế hoạch chuẩn bị $currentDurationDays ngày?"
        } else ""

        return RoadmapIntentAnalysis(
            detectedType = effectiveType,
            isShortEvent = effectiveType == RoadmapPlanType.SHORT_EVENT,
            isTimeBound = effectiveType == RoadmapPlanType.TIME_BOUND,
            isMultiDay = effectiveType == RoadmapPlanType.MULTI_DAY,
            isHybrid = effectiveType == RoadmapPlanType.HYBRID,
            detectedEventDate = detectedDateMatch,
            detectedEventTime = detectedTimeMatch,
            detectedDurationHours = detectedDurationMatch,
            detectedPrepDays = detectedPrepMatch,
            detectedFollowUpDays = detectedFollowUpMatch,
            detectedOrigin = origin,
            detectedDestination = destination,
            detectedTimezone = timezone,
            isShortEventConflictWith90Days = isConflict,
            suggestedDurationDays = suggestedDays,
            conflictReason = conflictReason
        )
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
                Bạn là Trợ lý Lập lộ trình & Kế hoạch AI chuyên nghiệp (AI Multi-Month & Event Roadmap Specialist) bằng tiếng Việt.
                Ngày bắt đầu kế hoạch: $startDateStr.

                QUY TẮC PHÂN LOẠI YÊU CẦU THÀNH 4 LOẠI:
                A. SỰ KIỆN NGẮN (SHORT_EVENT): Chuyến bay, đi khám bác sĩ, cuộc hẹn, thi cử, phỏng vấn, lấy giấy tờ (vài tiếng đến 1-2 ngày).
                   - Tạo 5-12 nhiệm vụ thực tế quanh sự kiện.
                   - KHÔNG trải dài thành 30 hay 90 ngày.
                B. CÔNG VIỆC CÓ THỜI HẠN (TIME_BOUND): Chuyển nhà, hoàn tất hồ sơ dự án trong 3 - 14 ngày.
                C. KẾ HOẠCH DÀI HẠN (MULTI_DAY): Trồng cà chua 100 ngày, học tiếng Hàn 90 ngày, rèn luyện 30-60 ngày. Phân bổ các giai đoạn phát triển.
                D. LỊCH TRÌNH HỖN HỢP (HYBRID): Tách riêng Thời gian chuẩn bị trước + Thời lượng sự kiện + Thời gian theo dõi/ổn định sau sự kiện.

                QUY TẮC THỜI GIAN VÀ QUA ĐÊM:
                - Nếu sự kiện bắt đầu đêm (ví dụ 23:00) và kéo dài qua nửa đêm (ví dụ đến 03:00 sáng hôm sau): các nhiệm vụ sau 00:00 PHẢI có dayOffset = 1 (hoặc mốc ngày kế tiếp).
                - Ghi rõ múi giờ nếu có chênh lệch (ví dụ Hàn Quốc KST UTC+9 sang Việt Nam ICT UTC+7).
                - Không tự bịa số hiệu chuyến bay / sân bay chưa xác nhận, ghi chú "cần đối chiếu vé thật".

                BẮT BUỘC trả về khối JSON trong ```json ... ```:
                ```json
                {
                  "roadmapTitle": "Tiêu đề lộ trình",
                  "category": "Nông nghiệp", // Hoặc: Du lịch, Học tập, Sức khỏe, Đời sống, Sự kiện
                  "icon": "🌱",
                  "totalDays": 90, // Số ngày thực tế (1-2 ngày đối với sự kiện ngắn)
                  "planType": "SHORT_EVENT", // SHORT_EVENT, TIME_BOUND, MULTI_DAY, HYBRID
                  "eventDate": "29/12/2026", // Nếu có
                  "eventTime": "23:00", // Nếu có
                  "eventDurationMinutes": 240, // Nếu có
                  "timezone": "KST / ICT", // Nếu có
                  "description": "Tóm tắt lộ trình",
                  "tasks": [
                    {
                      "dayOffset": 0,
                      "scheduledTime": "19:00",
                      "phase": "Giai đoạn 1: Chuẩn bị",
                      "title": "Tên nhiệm vụ",
                      "instruction": "Chi tiết hướng dẫn",
                      "priority": "Cao",
                      "hasLoudAlarm": false,
                      "timezone": "KST"
                    }
                  ]
                }
                ```
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray()

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
                var totalDays = planJson.optInt("totalDays", 30)
                val desc = planJson.optString("description", "")
                val planType = planJson.optString("planType", "MULTI_DAY")
                val eventDate = if (planJson.has("eventDate")) planJson.optString("eventDate") else null
                val eventTime = if (planJson.has("eventTime")) planJson.optString("eventTime") else null
                val durationMin = if (planJson.has("eventDurationMinutes")) planJson.optInt("eventDurationMinutes") else null
                val timezone = if (planJson.has("timezone")) planJson.optString("timezone") else null
                val prepDays = if (planJson.has("preparationDays")) planJson.optInt("preparationDays") else null
                val followUpDays = if (planJson.has("followUpDays")) planJson.optInt("followUpDays") else null

                val tasksArray = planJson.optJSONArray("tasks")
                val tasksList = mutableListOf<AiRoadmapTaskStep>()
                if (tasksArray != null) {
                    var prevTimeMinutes = -1
                    var currentDayOffsetAccum = 0

                    for (i in 0 until tasksArray.length()) {
                        val tObj = tasksArray.optJSONObject(i) ?: continue
                        var dayOffset = tObj.optInt("dayOffset", i)
                        val rawTime = tObj.optString("scheduledTime", "08:00")
                        val time = normalizeTime(rawTime)
                        val phase = tObj.optString("phase", "Giai đoạn ${i / 4 + 1}")
                        val taskTitle = tObj.optString("title", "Công việc ${i + 1}")
                        val instr = tObj.optString("instruction", "")
                        val priority = tObj.optString("priority", "Bình thường")
                        val hasLoudAlarm = tObj.optBoolean("hasLoudAlarm", false)
                        val tTimezone = if (tObj.has("timezone")) tObj.optString("timezone") else timezone

                        // Kiểm tra nếu sự kiện kéo dài qua đêm (ví dụ 23:00 -> 02:00) mà dayOffset chưa tăng
                        val timeParts = time.split(":")
                        val curMinutes = (timeParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (timeParts.getOrNull(1)?.toIntOrNull() ?: 0)
                        if (prevTimeMinutes >= 20 * 60 && curMinutes <= 6 * 60 && dayOffset == 0 && currentDayOffsetAccum == 0) {
                            currentDayOffsetAccum = 1
                        }
                        if (currentDayOffsetAccum > 0 && dayOffset == 0) {
                            dayOffset += currentDayOffsetAccum
                        }
                        prevTimeMinutes = curMinutes

                        tasksList.add(
                            AiRoadmapTaskStep(
                                dayOffset = dayOffset,
                                scheduledTime = time,
                                phase = phase,
                                title = taskTitle,
                                instruction = instr,
                                priority = priority,
                                hasLoudAlarm = hasLoudAlarm,
                                timezone = tTimezone
                            )
                        )
                    }
                }

                if (tasksList.isNotEmpty()) {
                    val maxOffset = tasksList.maxOfOrNull { it.dayOffset } ?: 0
                    // Chuẩn hóa totalDays nếu là sự kiện ngắn
                    if (planType == "SHORT_EVENT" || (maxOffset <= 1 && totalDays >= 30)) {
                        totalDays = maxOffset + 1
                    } else {
                        totalDays = totalDays.coerceAtLeast(maxOffset + 1)
                    }

                    val roadmapPlan = AiRoadmapPlan(
                        title = title,
                        category = category,
                        icon = icon,
                        totalDays = totalDays,
                        description = desc,
                        tasks = tasksList,
                        planType = planType,
                        eventDate = eventDate,
                        eventTime = eventTime,
                        eventDurationMinutes = durationMin,
                        timezone = timezone,
                        preparationDays = prepDays,
                        followUpDays = followUpDays
                    )
                    return Pair(chatText.ifBlank { "Dưới đây là lộ trình chi tiết theo loại hình đã nhận diện:" }, roadmapPlan)
                }
            }
        } catch (e: Exception) {
            Log.e("AiScheduleService", "Failed to parse roadmap json: ${e.message}", e)
        }
        return generateSmartLocalRoadmap(prompt, startDateStr)
    }

    private fun generateSmartLocalRoadmap(prompt: String, startDateStr: String): Pair<String, AiRoadmapPlan?> {
        val p = prompt.lowercase()
        val analysis = analyzeRoadmapIntent(prompt)

        return when {
            // Case 1: Chuyến bay Busan -> Việt Nam (Sự kiện ngắn hoặc Hỗn hợp)
            p.contains("bay") || p.contains("chuyến bay") || p.contains("máy bay") || p.contains("busan") -> {
                val isHybrid = p.contains("chuẩn bị") || p.contains("về nước") || p.contains("60 ngày") || analysis.isHybrid

                if (isHybrid) {
                    val tasks = listOf(
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "09:00",
                            phase = "Giai đoạn 1: Lập kế hoạch chuẩn bị (Trước 60 ngày)",
                            title = "Rà soát thủ tục về nước & Giấy tờ tùy thân",
                            instruction = "Kiểm tra hạn hộ chiếu, hạn visa xuất cảnh Hàn Quốc, lập danh mục đồ đạc cần mang về hoặc gửi hàng trước.",
                            priority = "Cao"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 15,
                            scheduledTime = "14:00",
                            phase = "Giai đoạn 1: Lập kế hoạch chuẩn bị (Trước 45 ngày)",
                            title = "Thanh lý đồ dùng & Thông báo trả phòng trọ",
                            instruction = "Thông báo chủ nhà về thời điểm trả phòng, thanh lý đồ điện tử, bàn ghế và gửi các kiện hàng cồng kềnh qua bưu điện/đường biển.",
                            priority = "Bình thường"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 30,
                            scheduledTime = "10:00",
                            phase = "Giai đoạn 2: Xác nhận lịch trình & Vé máy bay",
                            title = "Kiểm tra xác nhận vé máy bay & Quy định hành lý",
                            instruction = "Xác nhận giờ khởi hành 23:00 ngày 29/12/2026 tại sân bay Gimhae (Busan) đi Việt Nam. Cần đối chiếu vé thật để xác nhận sân bay đến.",
                            priority = "Cao"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 55,
                            scheduledTime = "15:00",
                            phase = "Giai đoạn 2: Đóng gói & Hoàn tất thủ tục tại Hàn",
                            title = "Đóng gói hành lý ký gửi & Đổi tiền mặt",
                            instruction = "Cân hành lý đúng tiêu chuẩn hãng, đóng gói kỹ quà biếu, đổi tiền Won sang VND/USD phòng thân.",
                            priority = "Cao"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 61,
                            scheduledTime = "19:00",
                            phase = "Giai đoạn 3: Sự kiện chính - Chuyến bay (29/12/2026)",
                            title = "Khởi hành ra sân bay Gimhae Busan",
                            instruction = "Có mặt tại sân bay trước 3 tiếng. Kiểm tra kỹ hộ chiếu, vé điện tử và CCCD.",
                            priority = "Cao",
                            hasLoudAlarm = true,
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 61,
                            scheduledTime = "20:30",
                            phase = "Giai đoạn 3: Sự kiện chính - Chuyến bay (29/12/2026)",
                            title = "Check-in tại quầy & Gửi hành lý ký gửi",
                            instruction = "Cân hành lý, nhận thẻ lên tàu bay (boarding pass) và kiểm tra cửa ra máy bay (gate).",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 61,
                            scheduledTime = "23:00",
                            phase = "Giai đoạn 3: Sự kiện chính - Chuyến bay (29/12/2026)",
                            title = "Chuyến bay cất cánh từ Busan đi Việt Nam",
                            instruction = "Thời gian bay dự kiến khoảng 4 tiếng. Cài dây an toàn và nghỉ ngơi trên chuyến bay.",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        // Nhiệm vụ qua đêm sang ngày hôm sau
                        AiRoadmapTaskStep(
                            dayOffset = 62,
                            scheduledTime = "02:00",
                            phase = "Giai đoạn 3: Sự kiện chính - Hạ cánh (30/12/2026)",
                            title = "Hạ cánh an toàn tại sân bay Việt Nam",
                            instruction = "Máy bay hạ cánh dự kiến 02:00 sáng (giờ Việt Nam ICT, chênh lệch -2h so với Hàn Quốc). Bật điện thoại di động.",
                            priority = "Cao",
                            timezone = "ICT (UTC+7)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 62,
                            scheduledTime = "03:00",
                            phase = "Giai đoạn 3: Sự kiện chính - Hạ cánh (30/12/2026)",
                            title = "Nhập cảnh & Nhận hành lý tại băng chuyền",
                            instruction = "Xuất trình hộ chiếu qua cổng kiểm soát xuất nhập cảnh, lấy hành lý ký gửi và qua cửa hải quan.",
                            priority = "Bình thường",
                            timezone = "ICT (UTC+7)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 62,
                            scheduledTime = "04:30",
                            phase = "Giai đoạn 4: Theo dõi & Ổn định sau sự kiện (Ngày 1)",
                            title = "Về đến nơi lưu trú / nhà riêng & Nghỉ ngơi",
                            instruction = "Về đến nhà an toàn, báo tin cho người thân và ngủ nghỉ hồi phục sức khỏe sau chuyến bay đêm.",
                            priority = "Bình thường",
                            timezone = "ICT (UTC+7)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 63,
                            scheduledTime = "10:00",
                            phase = "Giai đoạn 4: Theo dõi & Ổn định sau sự kiện (Ngày 2)",
                            title = "Mở kiện hành lý & Lắp SIM điện thoại nội địa",
                            instruction = "Sắp xếp lại tư trang, kiểm tra quà tặng mang về, kích hoạt gói cước data SIM tại Việt Nam.",
                            priority = "Bình thường"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 64,
                            scheduledTime = "09:00",
                            phase = "Giai đoạn 4: Theo dõi & Ổn định sau sự kiện (Ngày 3)",
                            title = "Đăng ký cư trú & Hoàn tất ổn định sinh hoạt",
                            instruction = "Làm thủ tục khai báo tạm trú/thường trú tại địa phương nếu cần, hoàn tất lộ trình về nước thành công.",
                            priority = "Bình thường"
                        )
                    )
                    val plan = AiRoadmapPlan(
                        title = "Lộ trình Chuẩn bị về nước & Chuyến bay Busan - Việt Nam",
                        category = "Du lịch",
                        icon = "✈️",
                        totalDays = 65,
                        description = "Lịch trình hỗn hợp kết hợp 60 ngày chuẩn bị kỹ lưỡng tại Hàn Quốc, chuyến bay 4 tiếng qua đêm 29-30/12/2026 và 3 ngày ổn định sau khi về Việt Nam.",
                        tasks = tasks,
                        planType = "HYBRID",
                        eventDate = "29/12/2026",
                        eventTime = "23:00",
                        eventDurationMinutes = 240,
                        timezone = "KST (UTC+9) -> ICT (UTC+7)",
                        preparationDays = 60,
                        followUpDays = 3
                    )
                    Pair("Tôi đã thiết lập Lịch trình Hỗn hợp (Chuẩn bị 60 ngày + Chuyến bay 4h + Ổn định 3 ngày) cho bạn:", plan)
                } else {
                    // Sự kiện ngắn (Short Event) tập trung quanh chuyến bay
                    val tasks = listOf(
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "18:00",
                            phase = "Trước chuyến bay (29/12/2026)",
                            title = "Kiểm tra túi giấy tờ & Hộ chiếu lần cuối",
                            instruction = "Đảm bảo mang theo Hộ chiếu, CCCD, vé điện tử, sạc dự phòng không để trong hành lý ký gửi.",
                            priority = "Cao",
                            hasLoudAlarm = true,
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "19:30",
                            phase = "Trước chuyến bay (29/12/2026)",
                            title = "Di chuyển đến Sân bay Quốc tế Gimhae (Busan)",
                            instruction = "Đi tàu điện ngầm hoặc taxi đến sân bay Gimhae, tính toán trừ hao kẹt xe giờ cao điểm.",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "20:30",
                            phase = "Tại sân bay Busan (29/12/2026)",
                            title = "Làm thủ tục check-in & Ký gửi hành lý",
                            instruction = "Đến quầy hãng bay làm thủ tục, gửi kiện hành lý, lấy vé lên máy bay và kiểm tra số cổng xuất cảnh.",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "21:30",
                            phase = "Tại sân bay Busan (29/12/2026)",
                            title = "Qua cửa kiểm tra an ninh & Hải quan xuất cảnh",
                            instruction = "Tháo thắt lưng, bỏ áo khoác và thiết bị điện tử ra khay soi chiếu. Vào khu vực phòng chờ.",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "22:30",
                            phase = "Lên tàu bay (Boarding)",
                            title = "Lên máy bay & Cài dây an toàn",
                            instruction = "Di chuyển vào cửa lên tàu bay trước 30 phút, cất hành lý xách tay vào khoang trên đầu.",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 0,
                            scheduledTime = "23:00",
                            phase = "Sự kiện: Chuyến bay Busan - Việt Nam",
                            title = "Máy bay cất cánh từ Busan đi Việt Nam",
                            instruction = "Thời gian bay dự kiến 4 tiếng. Điểm đến cụ thể cần xác nhận theo vé thật. Chuyển điện thoại sang chế độ máy bay.",
                            priority = "Cao",
                            timezone = "KST (UTC+9)"
                        ),
                        // Nhiệm vụ qua đêm sang ngày hôm sau (dayOffset = 1)
                        AiRoadmapTaskStep(
                            dayOffset = 1,
                            scheduledTime = "02:00",
                            phase = "Hạ cánh tại Việt Nam (30/12/2026)",
                            title = "Máy bay hạ cánh an toàn tại Việt Nam",
                            instruction = "Giờ Việt Nam (ICT, chậm hơn Hàn Quốc 2 tiếng). Ngồi yên cho đến khi đèn báo hiệu cài dây an toàn tắt.",
                            priority = "Cao",
                            timezone = "ICT (UTC+7)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 1,
                            scheduledTime = "02:45",
                            phase = "Sau chuyến bay",
                            title = "Nhập cảnh & Lấy hành lý tại băng chuyền",
                            instruction = "Đến quầy nhập cảnh trình hộ chiếu, đi theo bảng chỉ dẫn đến băng chuyền nhận kiện hành lý ký gửi.",
                            priority = "Cao",
                            timezone = "ICT (UTC+7)"
                        ),
                        AiRoadmapTaskStep(
                            dayOffset = 1,
                            scheduledTime = "03:45",
                            phase = "Sau chuyến bay",
                            title = "Di chuyển về nơi lưu trú & Nghỉ ngơi",
                            instruction = "Đón taxi hoặc xe gia đình đón, về đến nhà và nghỉ ngơi phục hồi sức khỏe sau chuyến bay đêm.",
                            priority = "Bình thường",
                            timezone = "ICT (UTC+7)"
                        )
                    )
                    val plan = AiRoadmapPlan(
                        title = "Lịch trình Chuyến bay Busan - Việt Nam (4 tiếng qua đêm)",
                        category = "Du lịch",
                        icon = "✈️",
                        totalDays = 2,
                        description = "Sự kiện ngắn có thời điểm cố định: Khởi hành 23:00 (giờ Hàn Quốc) ngày 29/12/2026 từ Busan, bay 4 tiếng và hạ cánh 02:00 sáng hôm sau tại Việt Nam (giờ ICT).",
                        tasks = tasks,
                        planType = "SHORT_EVENT",
                        eventDate = "29/12/2026",
                        eventTime = "23:00",
                        eventDurationMinutes = 240,
                        timezone = "KST (UTC+9) / ICT (UTC+7)"
                    )
                    Pair("Tôi đã thiết lập Lịch trình Sự kiện Ngắn tập trung đúng vào chuyến bay qua đêm của bạn:", plan)
                }
            }

            // Case 2: Cà chua 100 ngày
            p.contains("cà chua") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "07:30", phase = "Giai đoạn 1: Chuẩn bị giá thể & Ươm giống", title = "Ngâm ủ hạt giống cà chua & Làm đất bầu", instruction = "Ngâm hạt nước ấm 50°C trong 4-6 giờ, ủ vải ẩm. Đất trộn tro trấu, xơ dừa, phân chuồng hoai mục."),
                    AiRoadmapTaskStep(dayOffset = 5, scheduledTime = "08:00", phase = "Giai đoạn 1: Chuẩn bị giá thể & Ươm giống", title = "Gieo hạt vào khay ươm & Tưới phun sương", instruction = "Gieo sâu 0.5cm, tưới phun sương nhẹ giữ ẩm, che lưới đen tránh nắng gắt."),
                    AiRoadmapTaskStep(dayOffset = 20, scheduledTime = "16:30", phase = "Giai đoạn 2: Cấy cây con ra luống & Bón lót", title = "Trồng cây con ra luống đất chính", instruction = "Chọn cây 4-5 lá thật mập mạp. Bón lót lân và phân hữu cơ vi sinh, trồng cách nhau 50cm, tưới đẫm nước."),
                    AiRoadmapTaskStep(dayOffset = 35, scheduledTime = "08:00", phase = "Giai đoạn 3: Cắm cọc giàn & Tỉa chồi nách", title = "Làm giàn chữ A & Tỉa bỏ toàn bộ chồi nách", instruction = "Cắm cọc tre cố định thân cây chống đổ. Tỉa sạch các chồi phụ mọc ở nách lá, chỉ giữ lại thân chính."),
                    AiRoadmapTaskStep(dayOffset = 50, scheduledTime = "08:00", phase = "Giai đoạn 4: Ra hoa rộ & Bón thúc nuôi hoa", title = "Bón thúc đợt 1 & Rung cây trợ thụ phấn", instruction = "Bón NPK cân đối, rung nhẹ giàn cây vào lúc 8-9h sáng để tăng tỷ lệ đậu quả."),
                    AiRoadmapTaskStep(dayOffset = 65, scheduledTime = "08:30", phase = "Giai đoạn 5: Quả non phát triển & Phòng nứt quả", title = "Bổ sung Canxi-Bo & Phòng nấm mốc sương mai", instruction = "Phun phân bón lá Canxi-Bo để vỏ dày chống nứt quả và thối đít. Cắt bỏ lá già sát gốc tạo độ thông thoáng."),
                    AiRoadmapTaskStep(dayOffset = 80, scheduledTime = "08:00", phase = "Giai đoạn 6: Quả già & Chuyển màu chín", title = "Bón tăng cường Kali & Giảm lượng nước tưới", instruction = "Bón kali sulphate giúp quả ngọt đậm, màu đỏ bóng đẹp. Giảm nước tưới tránh nứt vỏ khi chín."),
                    AiRoadmapTaskStep(dayOffset = 100, scheduledTime = "07:00", phase = "Giai đoạn 7: Thu hoạch cà chua chín rộ", title = "Thu hoạch chùm quả cà chua tươi mọng", instruction = "Cắt cuống nhẹ nhàng lúc quả chín đỏ đều 90%. Xếp vào sọt có lót giấy mềm để bảo quản.")
                )
                val plan = AiRoadmapPlan(
                    title = "Quy trình Trồng Cà chua Hữu cơ Năng suất cao (100 ngày)",
                    category = "Nông nghiệp",
                    icon = "🍅",
                    totalDays = 100,
                    description = "Lộ trình 100 ngày từ gieo ươm, làm giàn cọc, tỉa chồi nách, nuôi hoa thụ phấn đến thu hoạch quả mọng ngọt.",
                    tasks = tasks,
                    planType = "MULTI_DAY"
                )
                Pair("Tôi đã thiết lập lộ trình 100 ngày trồng cà chua chuẩn nông nghiệp cho bạn:", plan)
            }

            // Case 3: Học tiếng Hàn 90 ngày
            p.contains("tiếng hàn") || p.contains("hàn ngữ") || p.contains("topik") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(dayOffset = 0, scheduledTime = "19:00", phase = "Tháng 1: Nhập môn & Bảng chữ cái Hangeul", title = "Học thuộc 21 nguyên âm và 19 phụ âm Hangeul", instruction = "Luyện viết chuẩn nét chữ Hangeul, học cách ghép âm tiết và quy tắc âm đệm cơ bản."),
                    AiRoadmapTaskStep(dayOffset = 10, scheduledTime = "19:30", phase = "Tháng 1: Nhập môn & Bảng chữ cái Hangeul", title = "Nắm vững 7 quy tắc biến âm & Nối âm", instruction = "Luyện quy tắc nối âm (연음), nhũ hóa (유음화), biến âm mũi (비음화) để phát âm chuẩn xác."),
                    AiRoadmapTaskStep(dayOffset = 25, scheduledTime = "20:00", phase = "Tháng 1: Nhập môn & Bảng chữ cái Hangeul", title = "Học 300 từ vựng căn bản & Đuôi câu -ㅂ/습니다", instruction = "Học từ vựng đồ vật, gia đình, nghề nghiệp. Cách dùng câu trần thuật và nghi vấn trang trọng."),
                    AiRoadmapTaskStep(dayOffset = 40, scheduledTime = "19:30", phase = "Tháng 2: Ngữ pháp Giao tiếp Sơ cấp", title = "Luyện đuôi câu thân mật -아/어요 & Thì quá khứ", instruction = "Nắm vững cách chia nguyên âm dương/âm, thì quá khứ -았/었어요 và trợ từ chủ ngữ/tân ngữ."),
                    AiRoadmapTaskStep(dayOffset = 55, scheduledTime = "20:00", phase = "Tháng 2: Ngữ pháp Giao tiếp Sơ cấp", title = "Học số đếm Thuần Hàn, Hán Hàn & Mua sắm", instruction = "Phân biệt dùng số đếm giờ giấc, tuổi tác, giá tiền. Thực hành hội thoại hỏi đường, mua đồ."),
                    AiRoadmapTaskStep(dayOffset = 70, scheduledTime = "19:30", phase = "Tháng 3: Phản xạ Hội thoại & Đời sống Hàn", title = "Luyện nghe Podcast tiếng Hàn & Shadowing ngắn", instruction = "Nghe các đoạn hội thoại thực tế về công việc xưởng/công ty, nhà hàng, bệnh viện tại Hàn."),
                    AiRoadmapTaskStep(dayOffset = 90, scheduledTime = "09:00", phase = "Tổng kết & Đánh giá năng lực", title = "Giải đề thi thử TOPIK I & Kiểm tra phản xạ", instruction = "Làm trọn vẹn 1 đề thi TOPIK cấp độ 1-2, đánh giá điểm nghe hiểu và đọc hiểu sau 90 ngày nỗ lực.")
                )
                val plan = AiRoadmapPlan(
                    title = "Lộ trình Tự học Tiếng Hàn Giao tiếp & TOPIK I (90 ngày)",
                    category = "Học tập",
                    icon = "🇰🇷",
                    totalDays = 90,
                    description = "Kế hoạch 3 tháng toàn diện từ bảng chữ Hangeul, ngữ pháp sơ cấp đến phản xạ nghe nói đời sống tại Hàn Quốc.",
                    tasks = tasks,
                    planType = "MULTI_DAY"
                )
                Pair("Tôi đã thiết lập lộ trình học tiếng Hàn 90 ngày từng giai đoạn vững chắc cho bạn:", plan)
            }

            // Case 4: Đi khám bác sĩ (Sự kiện ngắn 2 tiếng)
            p.contains("khám") || p.contains("bác sĩ") || p.contains("bệnh viện") -> {
                val tasks = listOf(
                    AiRoadmapTaskStep(
                        dayOffset = 0,
                        scheduledTime = "07:30",
                        phase = "Trước buổi khám (Chuẩn bị)",
                        title = "Chuẩn bị hồ sơ khám, BHYT & Nhịn ăn sáng",
                        instruction = "Mang theo CCCD, thẻ BHYT, sổ khám cũ và đơn thuốc đang dùng. Nhịn ăn sáng nếu cần làm xét nghiệm máu/nước tiểu.",
                        priority = "Cao",
                        hasLoudAlarm = true
                    ),
                    AiRoadmapTaskStep(
                        dayOffset = 0,
                        scheduledTime = "08:30",
                        phase = "Tại bệnh viện / phòng khám",
                        title = "Lấy số thứ tự & Đăng ký phòng khám chuyên khoa",
                        instruction = "Đến quầy tiếp đón, đo huyết áp, nhiệt độ ban đầu và nhận số vào phòng khám chuyên khoa.",
                        priority = "Bình thường"
                    ),
                    AiRoadmapTaskStep(
                        dayOffset = 0,
                        scheduledTime = "09:00",
                        phase = "Sự kiện chính: Khám bệnh (Thời lượng ~2 tiếng)",
                        title = "Bác sĩ thăm khám lâm sàng trực tiếp",
                        instruction = "Trình bày rõ các triệu chứng, thời gian xuất hiện và thói quen sinh hoạt. Nhận chỉ định cận lâm sàng.",
                        priority = "Cao"
                    ),
                    AiRoadmapTaskStep(
                        dayOffset = 0,
                        scheduledTime = "09:40",
                        phase = "Sự kiện chính: Khám bệnh (Thời lượng ~2 tiếng)",
                        title = "Lấy mẫu xét nghiệm, siêu âm / chụp chiếu",
                        instruction = "Di chuyển đến phòng xét nghiệm và chẩn đoán hình ảnh theo chỉ dẫn của y tá, chờ kết quả.",
                        priority = "Cao"
                    ),
                    AiRoadmapTaskStep(
                        dayOffset = 0,
                        scheduledTime = "10:30",
                        phase = "Sự kiện chính: Khám bệnh (Thời lượng ~2 tiếng)",
                        title = "Nghe bác sĩ kết luận chẩn đoán & Kê đơn thuốc",
                        instruction = "Quay lại phòng khám ban đầu, nghe bác sĩ giải thích kết quả xét nghiệm, tư vấn hướng điều trị và nhận đơn thuốc.",
                        priority = "Cao"
                    ),
                    AiRoadmapTaskStep(
                        dayOffset = 0,
                        scheduledTime = "11:00",
                        phase = "Sau buổi khám",
                        title = "Thanh toán viện phí & Lĩnh thuốc tại quầy dược",
                        instruction = "Quyết toán viện phí BHYT, nhận thuốc và nghe dược sĩ hướng dẫn liều uống chi tiết. Ghi nhớ lịch hẹn tái khám.",
                        priority = "Bình thường"
                    )
                )
                val plan = AiRoadmapPlan(
                    title = "Lịch trình Đi khám bệnh chuyên khoa (~2 tiếng)",
                    category = "Sức khỏe",
                    icon = "🏥",
                    totalDays = 1,
                    description = "Sự kiện ngắn có thời điểm cố định: Đi khám bệnh lúc 09:00 sáng, thời lượng dự kiến 2 tiếng bao gồm chuẩn bị, thăm khám và nhận thuốc.",
                    tasks = tasks,
                    planType = "SHORT_EVENT",
                    eventDate = "Hôm nay / Ngày mai",
                    eventTime = "09:00",
                    eventDurationMinutes = 120
                )
                Pair("Tôi đã thiết lập Lịch trình Sự kiện Ngắn đi khám bác sĩ cho bạn:", plan)
            }

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
                    tasks = tasks,
                    planType = "MULTI_DAY"
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
                    tasks = tasks,
                    planType = "MULTI_DAY"
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
                    tasks = tasks,
                    planType = "MULTI_DAY"
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
                    tasks = tasks,
                    planType = "MULTI_DAY"
                )
                Pair("Tôi đã thiết lập lộ trình học tập 60 ngày dành cho bạn:", plan)
            }
            else -> {
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
                    tasks = tasks,
                    planType = "MULTI_DAY"
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
        startDate: String = "2026-10-29",
        durationDays: Int = 90,
        category: String = "Nông nghiệp",
        planType: RoadmapPlanType = RoadmapPlanType.AUTO
    ): String {
        val cleanTopic = topic.trim().ifBlank { "Lộ trình kế hoạch chi tiết" }
        val analysis = analyzeRoadmapIntent(cleanTopic, durationDays, planType)
        val lower = cleanTopic.lowercase()

        val categoryEmoji = when {
            category.contains("Nông nghiệp") || lower.contains("ngô") || lower.contains("lúa") || lower.contains("cà chua") -> "🌱"
            category.contains("Du lịch") || lower.contains("phượt") || lower.contains("bay") || lower.contains("chuyến bay") -> "✈️"
            category.contains("Học tập") || lower.contains("học") || lower.contains("thi") || lower.contains("tiếng") -> "📚"
            category.contains("Sức khỏe") || lower.contains("khám") || lower.contains("chạy") || lower.contains("gym") -> "🏥"
            else -> "🎯"
        }

        val typeDescription = when (analysis.detectedType) {
            RoadmapPlanType.SHORT_EVENT -> "SỰ KIỆN NGẮN CÓ THỜI ĐIỂM CỐ ĐỊNH (vài tiếng đến 1-2 ngày, ví dụ chuyến bay, khám bệnh, thi cử, phỏng vấn)"
            RoadmapPlanType.TIME_BOUND -> "CÔNG VIỆC CÓ THỜI HẠN (vài ngày đến 2 tuần, ví dụ chuyển nhà, hoàn tất dự án)"
            RoadmapPlanType.HYBRID -> "LỊCH TRÌNH HỖN HỢP (Thời gian chuẩn bị trước + Sự kiện chính + Thời gian theo dõi/ổn định sau sự kiện)"
            RoadmapPlanType.MULTI_DAY -> "KẾ HOẠCH DÀI HẠN ĐA THÁNG (trồng trọt, canh tác, học ngoại ngữ, rèn luyện 30-100 ngày)"
            RoadmapPlanType.AUTO -> "TỰ ĐỘNG PHÂN LOẠI THEO NỘI DUNG (Sự kiện ngắn, Có thời hạn, Đa tháng hoặc Hỗn hợp)"
        }

        val eventDetailsBuilder = StringBuilder()
        if (analysis.detectedEventDate != null) eventDetailsBuilder.append("- Ngày diễn ra sự kiện: ${analysis.detectedEventDate}\n")
        if (analysis.detectedEventTime != null) eventDetailsBuilder.append("- Giờ sự kiện: ${analysis.detectedEventTime}\n")
        if (analysis.detectedDurationHours != null) eventDetailsBuilder.append("- Thời lượng sự kiện: ${analysis.detectedDurationHours.toInt()} tiếng\n")
        if (analysis.detectedOrigin != null) eventDetailsBuilder.append("- Nơi xuất phát: ${analysis.detectedOrigin}\n")
        if (analysis.detectedDestination != null) eventDetailsBuilder.append("- Điểm đến: ${analysis.detectedDestination}\n")
        if (analysis.detectedTimezone != null) eventDetailsBuilder.append("- Múi giờ: ${analysis.detectedTimezone}\n")
        if (analysis.detectedPrepDays != null) eventDetailsBuilder.append("- Thời gian chuẩn bị trước sự kiện: ${analysis.detectedPrepDays} ngày\n")
        if (analysis.detectedFollowUpDays != null) eventDetailsBuilder.append("- Thời gian theo dõi sau sự kiện: ${analysis.detectedFollowUpDays} ngày\n")

        val eventDetailsStr = eventDetailsBuilder.toString().trim()

        return """
Hãy đóng vai trò là chuyên gia thiết kế lộ trình & kế hoạch AI hàng đầu. Hãy giúp tôi tạo một Lộ trình chuẩn xác cho yêu cầu sau:

NỘI DUNG YÊU CẦU:
"$cleanTopic"

THÔNG TIN ĐẦU VÀO:
- Ngày bắt đầu kế hoạch: $startDate
- Tổng số ngày người dùng đang chọn trên giao diện: $durationDays ngày
- Phân loại lộ trình mong muốn: ${analysis.detectedType.displayName} ($typeDescription)
- Danh mục: $category $categoryEmoji
${if (eventDetailsStr.isNotBlank()) "THÔNG TIN SỰ KIỆN ĐÃ NHẬN DIỆN ĐƯỢC:\n$eventDetailsStr" else ""}

QUY TẮC BẮT BUỘC KHI LẬP LỊCH TRÌNH:
1. “Trước khi tạo lịch trình, hãy xác định người dùng đang yêu cầu một sự kiện ngắn, một công việc có thời hạn hay một kế hoạch dài hạn. Không được mặc định rằng thời lượng của sự kiện bằng số ngày trong trường totalDays. Nếu người dùng yêu cầu một chuyến bay kéo dài 4 tiếng, hãy lập lịch theo các mốc cần thiết quanh chuyến bay, không tạo công việc cho đủ 90 ngày chỉ vì trường số ngày đang đặt là 90. Chỉ tạo kế hoạch 90 ngày nếu người dùng thực sự yêu cầu thời gian chuẩn bị hoặc theo dõi 90 ngày.”
2. Với SỰ KIỆN NGẮN (như chuyến bay, đi khám, thi cử): Tạo từ 5 đến 12 nhiệm vụ thực tế và hợp lý quanh sự kiện (chuẩn bị trước sự kiện, lúc diễn ra, và sau sự kiện). Đặt totalDays đúng với thực tế (1 đến 2 ngày). KHÔNG tạo việc lặp lại vô nghĩa để lấp đầy 90 ngày.
3. Với LỊCH TRÌNH HỖN HỢP: Tách rõ 3 loại thời gian:
   - Thời gian chuẩn bị trước sự kiện
   - Thời lượng sự kiện diễn ra
   - Thời gian theo dõi/ổn định sau sự kiện
4. XỬ LÝ SỰ KIỆN QUA ĐÊM & MÚI GIỜ:
   - Nếu sự kiện bắt đầu đêm (ví dụ 23:00) và kéo dài qua nửa đêm hôm sau (ví dụ hạ cánh lúc 02:00 - 04:00 sáng): các nhiệm vụ sau 00:00 PHẢI được gán dayOffset tăng thêm 1 (ngày hôm sau).
   - Nếu có chênh lệch múi giờ (ví dụ Hàn Quốc KST UTC+9 sang Việt Nam ICT UTC+7), hãy ghi chú rõ múi giờ ở từng mốc.
   - Không tự ý bịa đặt số hiệu chuyến bay, nhà ga hoặc thông tin đặt chỗ khi người dùng chưa cung cấp (ghi chú 'cần đối chiếu vé thật').

CẤU TRÚC JSON BẮT BUỘC TRẢ VỀ:
BẮT BUỘC chỉ trả về duy nhất một khối JSON hợp lệ nằm trong cặp ```json ... ``` để tôi sao chép và dán trực tiếp vào ứng dụng Android:

```json
{
  "title": "$cleanTopic",
  "category": "$category",
  "icon": "$categoryEmoji",
  "totalDays": ${if (analysis.isShortEvent) analysis.suggestedDurationDays else durationDays},
  "planType": "${analysis.detectedType.code}",
  ${if (analysis.detectedEventDate != null) "\"eventDate\": \"${analysis.detectedEventDate}\"," else ""}
  ${if (analysis.detectedEventTime != null) "\"eventTime\": \"${analysis.detectedEventTime}\"," else ""}
  ${if (analysis.detectedDurationHours != null) "\"eventDurationMinutes\": ${(analysis.detectedDurationHours * 60).toInt()}," else ""}
  ${if (analysis.detectedTimezone != null) "\"timezone\": \"${analysis.detectedTimezone}\"," else ""}
  "description": "Mô tả ngắn gọn mục tiêu và tổng quan các mốc chính của lộ trình.",
  "tasks": [
    {
      "dayOffset": 0,
      "scheduledTime": "19:00",
      "phase": "Giai đoạn 1: Chuẩn bị",
      "title": "Tên nhiệm vụ cụ thể",
      "instruction": "Hướng dẫn chi tiết, giấy tờ cần chuẩn bị hoặc lưu ý an toàn",
      "priority": "Cao",
      "hasLoudAlarm": true,
      "timezone": "KST"
    }
  ]
}
```
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
                planJson.optString("roadmapTitle", "Lộ trình AI").ifBlank { "Lộ trình Kế hoạch" }
            }
            val category = planJson.optString("category", "Đời sống")
            val icon = planJson.optString("icon", "🌱")
            var totalDays = planJson.optInt("totalDays", 0)
            val description = planJson.optString("description", "")
            val planType = planJson.optString("planType", "MULTI_DAY")
            val eventDate = if (planJson.has("eventDate")) planJson.optString("eventDate") else null
            val eventTime = if (planJson.has("eventTime")) planJson.optString("eventTime") else null
            val durationMin = if (planJson.has("eventDurationMinutes")) planJson.optInt("eventDurationMinutes") else null
            val timezone = if (planJson.has("timezone")) planJson.optString("timezone") else null
            val prepDays = if (planJson.has("preparationDays")) planJson.optInt("preparationDays") else null
            val followUpDays = if (planJson.has("followUpDays")) planJson.optInt("followUpDays") else null

            val tasksArray = planJson.optJSONArray("tasks")
                ?: planJson.optJSONArray("steps")
                ?: planJson.optJSONArray("roadmap")

            if (tasksArray == null || tasksArray.length() == 0) {
                return Pair(null, "Khối JSON không chứa danh sách các mốc công việc ('tasks').")
            }

            val tasksList = mutableListOf<AiRoadmapTaskStep>()
            var maxOffset = 0
            var prevTimeMinutes = -1
            var overnightDayAdded = 0

            for (i in 0 until tasksArray.length()) {
                val tObj = tasksArray.optJSONObject(i) ?: continue

                // Check dayOffset or day (if external AI used 1-based "day" or "dayNumber")
                var dayOffset = when {
                    tObj.has("dayOffset") -> tObj.optInt("dayOffset", 0)
                    tObj.has("day") -> maxOf(0, tObj.optInt("day", 1) - 1)
                    tObj.has("dayNumber") -> maxOf(0, tObj.optInt("dayNumber", 1) - 1)
                    else -> i * 5
                }

                val rawTime = tObj.optString("scheduledTime", "08:00").ifBlank {
                    tObj.optString("time", "08:00")
                }
                val normalizedTime = normalizeTime(rawTime)
                val phase = tObj.optString("phase").ifBlank { "Giai đoạn ${i + 1}" }
                val taskTitle = tObj.optString("title").ifBlank { "Công việc mốc ngày ${dayOffset + 1}" }
                val instr = tObj.optString("instruction", "")
                val priority = tObj.optString("priority", "Bình thường")
                val hasLoudAlarm = tObj.optBoolean("hasLoudAlarm", false)
                val tTimezone = if (tObj.has("timezone")) tObj.optString("timezone") else timezone

                // Tự động xử lý nếu sự kiện kéo dài qua nửa đêm (ví dụ từ 23:00 chuyển sang 02:00) mà AI chưa tăng dayOffset
                val parts = normalizedTime.split(":")
                val curMinutes = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                if (prevTimeMinutes >= 20 * 60 && curMinutes <= 6 * 60 && dayOffset == 0 && overnightDayAdded == 0) {
                    overnightDayAdded = 1
                }
                if (overnightDayAdded > 0 && dayOffset == 0) {
                    dayOffset += overnightDayAdded
                }
                prevTimeMinutes = curMinutes

                if (dayOffset > maxOffset) {
                    maxOffset = dayOffset
                }

                tasksList.add(
                    AiRoadmapTaskStep(
                        dayOffset = dayOffset,
                        scheduledTime = normalizedTime,
                        phase = phase,
                        title = taskTitle,
                        instruction = instr,
                        priority = priority,
                        hasLoudAlarm = hasLoudAlarm,
                        timezone = tTimezone
                    )
                )
            }

            if (tasksList.isEmpty()) {
                return Pair(null, "Không có mốc công việc hợp lệ nào trong danh sách.")
            }

            // Sắp xếp các tasks theo thứ tự dayOffset tăng dần
            tasksList.sortBy { it.dayOffset }

            // Chuẩn hóa totalDays nếu là sự kiện ngắn hoặc external AI đặt 90 ngày cho 1-2 ngày sự kiện
            val isShortOrEvent = planType == "SHORT_EVENT" || (maxOffset <= 1 && totalDays >= 30)
            if (isShortOrEvent) {
                totalDays = maxOffset + 1
            } else if (totalDays <= 0) {
                totalDays = maxOffset + 1
            }

            val plan = AiRoadmapPlan(
                title = title,
                category = category,
                icon = icon.ifBlank { "🌱" },
                totalDays = totalDays,
                description = description,
                tasks = tasksList,
                planType = planType,
                eventDate = eventDate,
                eventTime = eventTime,
                eventDurationMinutes = durationMin,
                timezone = timezone,
                preparationDays = prepDays,
                followUpDays = followUpDays
            )
            return Pair(plan, null)

        } catch (e: Exception) {
            Log.e("AiScheduleService", "Error parsing external roadmap JSON: ${e.message}", e)
            return Pair(null, "Lỗi phân tích JSON lộ trình: ${e.localizedMessage ?: "Cấu trúc không hợp lệ"}")
        }
    }
}

