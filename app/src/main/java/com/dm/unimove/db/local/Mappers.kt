package com.dm.unimove.db.local

import com.dm.unimove.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

// ── Ride ──────────────────────────────────────────────────────────────────────

fun Ride.toEntity(id: String): RideEntity = RideEntity(
    id = id,
    driver_id = driver_ref?.id ?: "",
    starting_point_name = starting_point.name,
    starting_point_lat = starting_point.coordinates.latitude,
    starting_point_lng = starting_point.coordinates.longitude,
    destination_name = destination.name,
    destination_lat = destination.coordinates.latitude,
    destination_lng = destination.coordinates.longitude,
    date_time = date_time?.toDate()?.time,
    occasion = occasion.name,
    payment_type = payment_type.name,
    ride_value = ride_value,
    status = status.name,
    total_seats = total_seats,
    vehicle_model = vehicle_model,
    description = description,
    sincronizado = true
)

fun RideEntity.toRide(): Ride = Ride(
    starting_point = Location(
        name = starting_point_name,
        coordinates = GeoPoint(starting_point_lat, starting_point_lng)
    ),
    destination = Location(
        name = destination_name,
        coordinates = GeoPoint(destination_lat, destination_lng)
    ),
    date_time = date_time?.let { Timestamp(it / 1000, 0) },
    occasion = runCatching { Occasion.valueOf(occasion) }.getOrDefault(Occasion.ONE_WAY),
    payment_type = runCatching { PaymentType.valueOf(payment_type) }.getOrDefault(PaymentType.FREE),
    ride_value = ride_value,
    status = runCatching { RideStatus.valueOf(status) }.getOrDefault(RideStatus.AVAILABLE),
    total_seats = total_seats,
    vehicle_model = vehicle_model,
    description = description
)

// ── User ──────────────────────────────────────────────────────────────────────

fun User.toEntity(id: String): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
    is_busy = is_busy,
    sincronizado = true
)

fun UserEntity.toUser(): User = User(
    name = name,
    email = email,
    is_busy = is_busy
)

// ── Solicitation ──────────────────────────────────────────────────────────────

fun Solicitation.toEntity(id: String): SolicitationEntity = SolicitationEntity(
    id = id,
    ride_id = ride_ref?.id ?: "",
    passenger_id = passenger_ref?.id ?: "",
    driver_id = driver_ref?.id ?: "",
    requested_seat = requested_seat.name,
    status = status.name,
    timestamp = timestamp?.toDate()?.time,
    sincronizado = true
)