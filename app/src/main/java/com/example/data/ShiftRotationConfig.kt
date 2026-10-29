package com.example.data

data class ShiftDayAssignment(
    val weekIndex: Int, // 0 = Tuần 1, 1 = Tuần 2, ...
    val dayOfWeek: Int, // 1 = Thứ 2, 2 = Thứ 3, ..., 6 = Thứ 7, 7 = Chủ nhật
    val stationId: String? = null,
    val targetStationName: String = "" // "ca ngày", "ca đêm", "ngày nghỉ"
)

data class ShiftRotationConfig(
    val isEnabled: Boolean = false,
    val cycleWeeks: Int = 2,
    val anchorMondayMillis: Long = 0L,
    val switchTimeStr: String = "06:00", // Giờ kích hoạt ca hàng ngày
    val notifyOnSwitch: Boolean = true,
    val assignments: List<ShiftDayAssignment> = emptyList()
) {
    val enabled: Boolean get() = isEnabled
}
