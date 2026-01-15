package com.example.vinculacion.data.local.room.entities

/**
 * Enumeraciones compartidas por las entidades Room para describir estados sin depender de strings mágicas.
 */
enum class MediaRecordType { PHOTO, AUDIO }

enum class MediaSyncStatus { PENDING, SYNCED, FAILED }

enum class TourStatus { DRAFT, PUBLISHED, IN_PROGRESS, COMPLETED, CANCELLED }

enum class TourParticipantStatus { PENDING, APPROVED, DECLINED, CANCELLED }

enum class SyncTaskState { PENDING, RUNNING, COMPLETED, FAILED }
