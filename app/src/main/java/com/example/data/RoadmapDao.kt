package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoadmapDao {
    @Query("SELECT * FROM roadmaps ORDER BY createdAt DESC")
    fun getAllRoadmaps(): Flow<List<Roadmap>>

    @Query("SELECT * FROM roadmaps ORDER BY createdAt DESC")
    suspend fun getAllRoadmapsDirect(): List<Roadmap>

    @Query("SELECT * FROM roadmaps WHERE id = :id")
    fun getRoadmapById(id: String): Flow<Roadmap?>

    @Query("SELECT * FROM roadmap_tasks WHERE roadmapId = :roadmapId ORDER BY dayOffset ASC, scheduledTime ASC")
    fun getTasksForRoadmap(roadmapId: String): Flow<List<RoadmapTask>>

    @Query("SELECT * FROM roadmap_tasks ORDER BY scheduledDate ASC, scheduledTime ASC")
    suspend fun getAllTasksDirect(): List<RoadmapTask>

    @Query("SELECT * FROM roadmap_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): RoadmapTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoadmap(roadmap: Roadmap)

    @Update
    suspend fun updateRoadmap(roadmap: Roadmap)

    @Delete
    suspend fun deleteRoadmap(roadmap: Roadmap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: RoadmapTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<RoadmapTask>)

    @Update
    suspend fun updateTask(task: RoadmapTask)

    @Delete
    suspend fun deleteTask(task: RoadmapTask)

    @Query("DELETE FROM roadmap_tasks WHERE roadmapId = :roadmapId")
    suspend fun deleteTasksForRoadmap(roadmapId: String)
}
