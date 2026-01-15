package com.example.vinculacion.data.local.room

import androidx.room.TypeConverter
import com.example.vinculacion.data.local.room.entities.MediaRecordType
import com.example.vinculacion.data.local.room.entities.MediaSyncStatus
import com.example.vinculacion.data.local.room.entities.SyncTaskState
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.local.room.entities.TourStatus
import com.example.vinculacion.data.model.UserRole

/**
 * Conversores compartidos para almacenar enums y tipos complejos en Room.
 */
class RoomTypeConverters {

    @TypeConverter
    fun fromMediaRecordType(value: MediaRecordType?): String? = value?.name

    @TypeConverter
    fun toMediaRecordType(value: String?): MediaRecordType? = value?.let(MediaRecordType::valueOf)

    @TypeConverter
    fun fromMediaSyncStatus(value: MediaSyncStatus?): String? = value?.name

    @TypeConverter
    fun toMediaSyncStatus(value: String?): MediaSyncStatus? = value?.let(MediaSyncStatus::valueOf)

    @TypeConverter
    fun fromTourStatus(value: TourStatus?): String? = value?.name

    @TypeConverter
    fun toTourStatus(value: String?): TourStatus? = value?.let(TourStatus::valueOf)

    @TypeConverter
    fun fromTourParticipantStatus(value: TourParticipantStatus?): String? = value?.name

    @TypeConverter
    fun toTourParticipantStatus(value: String?): TourParticipantStatus? = value?.let(TourParticipantStatus::valueOf)

    @TypeConverter
    fun fromSyncTaskState(value: SyncTaskState?): String? = value?.name

    @TypeConverter
    fun toSyncTaskState(value: String?): SyncTaskState? = value?.let(SyncTaskState::valueOf)

    @TypeConverter
    fun fromUserRole(value: UserRole?): String? = value?.name

    @TypeConverter
    fun toUserRole(value: String?): UserRole? = value?.let(UserRole::valueOf)
}
