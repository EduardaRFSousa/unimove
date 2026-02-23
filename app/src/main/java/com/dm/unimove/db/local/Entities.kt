package com.dm.unimove.db.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val email: String = "",
    val is_busy: Boolean = false,
    val sincronizado: Boolean = false
)

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey val id: String,
    val driver_id: String = "",
    val starting_point_name: String = "",
    val starting_point_lat: Double = 0.0,
    val starting_point_lng: Double = 0.0,
    val destination_name: String = "",
    val destination_lat: Double = 0.0,
    val destination_lng: Double = 0.0,
    val date_time: Long? = null,
    val occasion: String = "ONE_WAY",
    val payment_type: String = "FREE",
    val ride_value: Double = 0.0,
    val status: String = "AVAILABLE",
    val total_seats: Int = 0,
    val vehicle_model: String = "",
    val description: String = "",
    val sincronizado: Boolean = false
)

@Entity(tableName = "solicitations")
data class SolicitationEntity(
    @PrimaryKey val id: String,
    val ride_id: String = "",
    val passenger_id: String = "",
    val driver_id: String = "",
    val requested_seat: String = "FRONT",
    val status: String = "PENDING",
    val timestamp: Long? = null,
    val sincronizado: Boolean = false
)