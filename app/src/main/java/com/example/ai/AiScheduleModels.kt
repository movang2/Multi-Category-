package com.example.ai

import java.util.UUID

data class AiScheduleStep(
    val id: String = UUID.randomUUID().toString(),
    var timeScheduled: String = "08:00",
    var title: String = "",
    var instruction: String = "",
    var priority: String = "Bình thường", // "Cao", "Bình thường", "Thấp"
    var hasLoudAlarm: Boolean = false // Chuông báo thức kêu to (kho cá, việc khẩn cấp)
)

data class AiSchedulePlan(
    val title: String,
    val suggestedStationName: String,
    val suggestedIcon: String = "✨",
    val steps: List<AiScheduleStep>
)

data class AiRotationDay(
    val dayOfWeek: Int, // 1 = T2, 2 = T3, ..., 7 = CN
    val dayName: String, // "Thứ 2", "Thứ 3", ...
    val stationName: String // "ca ngày", "ca đêm", "ngày nghỉ"
)

data class AiRotationWeek(
    val weekNumber: Int, // 1, 2, ...
    val weekTitle: String, // "Tuần 1: Ca ngày", "Tuần 2: Ca đêm"
    val days: List<AiRotationDay>
) {
    val weekLabel: String get() = weekTitle
}

data class AiRotationPlan(
    val title: String,
    val cycleWeeks: Int = 2,
    val description: String = "",
    val weeks: List<AiRotationWeek>,
    val switchTime: String = "06:00"
)

data class AiScheduleResponse(
    val text: String,
    val plan: AiSchedulePlan? = null,
    val rotationPlan: AiRotationPlan? = null
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val plan: AiSchedulePlan? = null,
    val rotationPlan: AiRotationPlan? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiRoadmapTaskStep(
    val id: String = UUID.randomUUID().toString(),
    var dayOffset: Int = 0,
    var scheduledTime: String = "08:00",
    var phase: String = "Giai đoạn 1",
    var title: String = "",
    var instruction: String = "",
    var priority: String = "Bình thường",
    var hasLoudAlarm: Boolean = false,
    var timezone: String? = null
)

enum class RoadmapPlanType(val code: String, val displayName: String, val icon: String, val desc: String) {
    AUTO("AUTO", "Tự động nhận diện bằng AI", "🤖", "Tự động phân loại: Sự kiện ngắn, Công việc có hạn hay Kế hoạch đa tháng"),
    SHORT_EVENT("SHORT_EVENT", "Sự kiện ngắn", "⚡", "Chuyến bay, đi khám, cuộc hẹn, thi cử (vài tiếng đến 1-2 ngày)"),
    TIME_BOUND("TIME_BOUND", "Công việc có thời hạn", "⏱️", "Chuyển nhà, hoàn tất giấy tờ, dự án trong 3 - 14 ngày"),
    MULTI_DAY("MULTI_DAY", "Kế hoạch nhiều ngày", "📅", "Trồng trọt, canh tác, học ngoại ngữ, tập gym (30 - 100 ngày)"),
    HYBRID("HYBRID", "Lịch trình hỗn hợp", "🔀", "Chuẩn bị trước + Sự kiện chính + Ổn định/theo dõi sau sự kiện")
}

data class AiRoadmapPlan(
    val title: String,
    val category: String = "Nông nghiệp",
    val icon: String = "🌱",
    val totalDays: Int = 30,
    val description: String = "",
    val tasks: List<AiRoadmapTaskStep>,
    val planType: String = "MULTI_DAY",
    val eventDate: String? = null,
    val eventTime: String? = null,
    val eventDurationMinutes: Int? = null,
    val timezone: String? = null,
    val preparationDays: Int? = null,
    val followUpDays: Int? = null
)

data class AiRoadmapChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val roadmapPlan: AiRoadmapPlan? = null,
    val timestamp: Long = System.currentTimeMillis()
)

