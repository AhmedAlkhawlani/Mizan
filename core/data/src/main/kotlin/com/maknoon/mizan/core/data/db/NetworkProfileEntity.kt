package com.maknoon.mizan.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maknoon.mizan.core.domain.network.NetworkProfile
import com.maknoon.mizan.core.domain.network.NetworkTransport

/**
 * One user-named network profile (docs/03 §7).
 *
 * The unique index on (transport, systemMetered) mirrors what the app can actually distinguish
 * without the SSID: home Wi-Fi and a hotspot are both Wi-Fi, and only the platform's metered
 * flag tells them apart. One profile per kind keeps resolution deterministic.
 */
@Entity(
    tableName = "network_profiles",
    indices = [Index(value = ["transport", "systemMetered"], unique = true)],
)
data class NetworkProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val transport: NetworkTransport,
    val systemMetered: Boolean,
    val metered: Boolean,
    val monthlyBudgetBytes: Long?,
    val blockedPackages: List<String>,
    val createdAt: Long,
)

fun NetworkProfileEntity.toDomain(): NetworkProfile = NetworkProfile(
    id = id,
    name = name,
    transport = transport,
    systemMetered = systemMetered,
    metered = metered,
    monthlyBudgetBytes = monthlyBudgetBytes,
    blockedPackages = blockedPackages,
    createdAt = createdAt,
)

fun NetworkProfile.toEntity(): NetworkProfileEntity = NetworkProfileEntity(
    id = id,
    name = name,
    transport = transport,
    systemMetered = systemMetered,
    metered = metered,
    monthlyBudgetBytes = monthlyBudgetBytes,
    blockedPackages = blockedPackages,
    createdAt = createdAt,
)
