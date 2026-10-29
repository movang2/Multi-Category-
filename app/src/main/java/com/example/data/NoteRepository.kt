package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val quickNoteDao: QuickNoteDao,
    private val shiftDao: ShiftDao,
    private val roadmapDao: RoadmapDao
) {

    val categories: Flow<List<Category>> = noteDao.getCategories()
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allQuickNotes: Flow<List<QuickNote>> = quickNoteDao.getAllQuickNotesFlow()
    val allRoadmaps: Flow<List<Roadmap>> = roadmapDao.getAllRoadmaps()

    // --- ROADMAP & LONG-TERM SCHEDULE MANAGEMENT ---
    fun getTasksForRoadmap(roadmapId: String): Flow<List<RoadmapTask>> {
        return roadmapDao.getTasksForRoadmap(roadmapId)
    }

    suspend fun insertRoadmap(roadmap: Roadmap) {
        roadmapDao.insertRoadmap(roadmap)
    }

    suspend fun updateRoadmap(roadmap: Roadmap) {
        roadmapDao.updateRoadmap(roadmap)
    }

    suspend fun deleteRoadmap(roadmap: Roadmap) {
        roadmapDao.deleteTasksForRoadmap(roadmap.id)
        roadmapDao.deleteRoadmap(roadmap)
    }

    suspend fun insertRoadmapTask(task: RoadmapTask) {
        roadmapDao.insertTask(task)
    }

    suspend fun insertRoadmapTasks(tasks: List<RoadmapTask>) {
        roadmapDao.insertTasks(tasks)
    }

    suspend fun updateRoadmapTask(task: RoadmapTask) {
        roadmapDao.updateTask(task)
    }

    suspend fun deleteRoadmapTask(task: RoadmapTask) {
        roadmapDao.deleteTask(task)
    }

    suspend fun deleteTasksForRoadmap(roadmapId: String) {
        roadmapDao.deleteTasksForRoadmap(roadmapId)
    }

    suspend fun getAllRoadmapsDirect(): List<Roadmap> = roadmapDao.getAllRoadmapsDirect()
    suspend fun getAllRoadmapTasksDirect(): List<RoadmapTask> = roadmapDao.getAllTasksDirect()

    // --- SHIFT MANAGEMENT ---
    val allShiftStations: Flow<List<ShiftStation>> = shiftDao.getAllStations()
    val allShiftTasks: Flow<List<ShiftTask>> = shiftDao.getAllTasks()
    val allShiftHandovers: Flow<List<ShiftHandover>> = shiftDao.getAllHandovers()

    fun getTasksForStation(stationId: String): Flow<List<ShiftTask>> {
        return shiftDao.getTasksForStation(stationId)
    }

    suspend fun getTasksForStationDirect(stationId: String): List<ShiftTask> {
        return shiftDao.getTasksForStationDirect(stationId)
    }

    suspend fun getTaskById(taskId: String): ShiftTask? {
        return shiftDao.getTaskById(taskId)
    }

    fun getHandoversForStation(stationId: String): Flow<List<ShiftHandover>> {
        return shiftDao.getHandoversForStation(stationId)
    }

    suspend fun getStationById(id: String): ShiftStation? {
        return shiftDao.getStationById(id)
    }

    suspend fun getStationByQrCode(qrCode: String): ShiftStation? {
        return shiftDao.getStationByQrCode(qrCode)
    }

    suspend fun insertStation(station: ShiftStation) {
        shiftDao.insertStation(station)
    }

    suspend fun deleteStation(station: ShiftStation) {
        shiftDao.deleteStation(station)
    }

    suspend fun insertTask(task: ShiftTask) {
        shiftDao.insertTask(task)
    }

    suspend fun updateTask(task: ShiftTask) {
        shiftDao.updateTask(task)
    }

    suspend fun deleteTask(task: ShiftTask) {
        shiftDao.deleteTask(task)
    }

    suspend fun insertHandover(handover: ShiftHandover) {
        shiftDao.insertHandover(handover)
    }

    suspend fun deleteHandover(handover: ShiftHandover) {
        shiftDao.deleteHandover(handover)
    }

    suspend fun resetTasksForStation(stationId: String) {
        val tasks = shiftDao.getTasksForStation(stationId)
    }

    suspend fun getCategoriesDirect(): List<Category> = noteDao.getCategoriesDirect()
    suspend fun getAllNotesDirect(): List<Note> = noteDao.getAllNotesDirect()
    suspend fun getAllQuickNotesDirect(): List<QuickNote> = quickNoteDao.getAllQuickNotesDirect()
    suspend fun getAllShiftStationsDirect(): List<ShiftStation> = shiftDao.getAllStationsDirect()
    suspend fun getAllShiftTasksDirect(): List<ShiftTask> = shiftDao.getAllTasksDirect()
    suspend fun getAllShiftHandoversDirect(): List<ShiftHandover> = shiftDao.getAllHandoversDirect()

    suspend fun deleteStationAndRelatedData(station: ShiftStation) {
        shiftDao.deleteTasksForStation(station.id)
        shiftDao.deleteHandoversForStation(station.id)
        shiftDao.deleteStation(station)
    }

    suspend fun removeLegacySampleData() {
        val legacyIds = setOf("station_may_a", "station_may_b", "station_dong_goi", "station_ve_sinh", "station_kiem_tra")
        val legacyNames = setOf("máy a", "máy b", "đóng gói", "vệ sinh", "kiểm tra đầu ca")
        val existingStations = shiftDao.getAllStationsDirect()
        for (st in existingStations) {
            if (st.id in legacyIds || st.name.trim().lowercase() in legacyNames) {
                deleteStationAndRelatedData(st)
            }
        }
        val allHandovers = shiftDao.getAllHandoversDirect()
        for (h in allHandovers) {
            shiftDao.deleteHandover(h)
        }
    }

    suspend fun seedDefaultShiftDataIfEmpty(alreadySeeded: Boolean) {
        removeLegacySampleData()

        val existingStations = shiftDao.getAllStationsDirect()
        if (existingStations.isEmpty()) {
            val stationDay = ShiftStation("station_ca_ngay", "ca ngày", "🏭", "Ca làm việc ban ngày", "STATION_CA_NGAY")
            val stationNight = ShiftStation("station_ca_dem", "ca đêm", "🏭", "Ca làm việc ban đêm", "STATION_CA_DEM")
            val stationOff = ShiftStation("station_ngay_nghi", "ngày nghỉ", "🏭", "Ngày nghỉ ngơi", "STATION_NGAY_NGHI")

            shiftDao.insertStation(stationDay)
            shiftDao.insertStation(stationNight)
            shiftDao.insertStation(stationOff)
        }
    }

    fun getNotesByCategory(categoryName: String): Flow<List<Note>> {
        return noteDao.getNotesByCategory(categoryName)
    }

    suspend fun insertCategory(category: Category) {
        noteDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        noteDao.deleteNotesByCategory(category.name)
        noteDao.deleteCategory(category)
    }

    suspend fun renameCategory(oldName: String, newName: String) {
        noteDao.renameCategoryTransaction(oldName, newName)
    }

    suspend fun insertNote(note: Note) {
        noteDao.insertNote(note)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun getNoteById(id: String): Note? {
        return noteDao.getNoteById(id)
    }

    // --- QUICK NOTES ---
    suspend fun insertQuickNote(quickNote: QuickNote) {
        quickNoteDao.insertQuickNote(quickNote)
    }

    suspend fun deleteQuickNote(quickNote: QuickNote) {
        quickNoteDao.deleteQuickNote(quickNote)
    }

    suspend fun getQuickNoteById(id: String): QuickNote? {
        return quickNoteDao.getQuickNoteById(id)
    }
}
