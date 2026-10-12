package com.example.homeops.data.repo

import com.example.homeops.data.db.RoomDao
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.data.db.ZoneDao
import com.example.homeops.data.db.ZoneEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Rooms and zones (the spatial hierarchy, DESIGN.md Section 4.1). `@Inject constructor` lets Hilt build it. */
@Singleton
class HomeRepository
    @Inject
    constructor(
        private val roomDao: RoomDao,
        private val zoneDao: ZoneDao,
    ) {
        fun observeRooms(): Flow<List<RoomEntity>> = roomDao.observeAll()

        fun observeRoom(id: Long): Flow<RoomEntity?> = roomDao.observeById(id)

        fun observeZones(roomId: Long): Flow<List<ZoneEntity>> = zoneDao.observeForRoom(roomId)

        /** Inserts when id == 0, otherwise updates. New rooms go to the end of the list. */
        suspend fun saveRoom(room: RoomEntity): Long =
            if (room.id == 0L) {
                roomDao.insert(room.copy(sortOrder = roomDao.nextSortOrder()))
            } else {
                roomDao.update(room)
                room.id
            }

        /** Cascades to the room's zones, tasks, and their completion history. */
        suspend fun deleteRoom(room: RoomEntity) = roomDao.delete(room)

        suspend fun saveZone(zone: ZoneEntity): Long =
            if (zone.id == 0L) {
                zoneDao.insert(zone)
            } else {
                zoneDao.update(zone)
                zone.id
            }

        /** Tasks in the zone are kept and become whole-room tasks (FK ON DELETE SET NULL). */
        suspend fun deleteZone(zone: ZoneEntity) = zoneDao.delete(zone)
    }
