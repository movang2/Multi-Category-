package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    // Stations
    @Query("SELECT * FROM shift_stations ORDER BY name ASC")
    fun getAllStations(): Flow<List<ShiftStation>>

    @Query("SELECT * FROM shift_stations ORDER BY name ASC")
    suspend fun getAllStationsDirect(): List<ShiftStation>

    @Query("SELECT * FROM shift_stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: String): ShiftStation?

    @Query("SELECT * FROM shift_stations WHERE qrCode = :qrCode LIMIT 1")
    suspend fun getStationByQrCode(qrCode: String): ShiftStation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: ShiftStation)

    @Delete
    suspend fun deleteStation(station: ShiftStation)

    // Tasks
    @Query("SELECT * FROM shift_tasks WHERE stationId = :stationId ORDER BY timeScheduled ASC")
    fun getTasksForStation(stationId: String): Flow<List<ShiftTask>>

    @Query("SELECT * FROM shift_tasks WHERE stationId = :stationId ORDER BY timeScheduled ASC")
    suspend fun getTasksForStationDirect(stationId: String): List<ShiftTask>

    @Query("SELECT * FROM shift_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): ShiftTask?

    @Query("SELECT * FROM shift_tasks ORDER BY timeScheduled ASC")
    fun getAllTasks(): Flow<List<ShiftTask>>

    @Query("SELECT * FROM shift_tasks ORDER BY timeScheduled ASC")
    suspend fun getAllTasksDirect(): List<ShiftTask>

    @Query("SELECT * FROM shift_tasks WHERE isCompleted = 0")
    suspend fun getUncompletedTasksDirect(): List<ShiftTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ShiftTask)

    @Update
    suspend fun updateTask(task: ShiftTask)

    @Delete
    suspend fun deleteTask(task: ShiftTask)

    @Query("DELETE FROM shift_tasks WHERE stationId = :stationId")
    suspend fun deleteTasksForStation(stationId: String)

    @Query("UPDATE shift_tasks SET isCompleted = 0")
    suspend fun resetAllTasks()

    // Handovers
    @Query("SELECT * FROM shift_handovers WHERE stationId = :stationId ORDER BY timestamp DESC")
    fun getHandoversForStation(stationId: String): Flow<List<ShiftHandover>>

    @Query("SELECT * FROM shift_handovers ORDER BY timestamp DESC")
    fun getAllHandovers(): Flow<List<ShiftHandover>>

    @Query("SELECT * FROM shift_handovers ORDER BY timestamp DESC")
    suspend fun getAllHandoversDirect(): List<ShiftHandover>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandover(handover: ShiftHandover)

    @Delete
    suspend fun deleteHandover(handover: ShiftHandover)

    @Query("DELETE FROM shift_handovers WHERE stationId = :stationId")
    suspend fun deleteHandoversForStation(stationId: String)
}
