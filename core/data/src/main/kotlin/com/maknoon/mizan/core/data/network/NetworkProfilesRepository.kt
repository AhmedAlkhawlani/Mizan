package com.maknoon.mizan.core.data.network

import com.maknoon.mizan.core.data.db.NetworkProfileDao
import com.maknoon.mizan.core.data.db.toDomain
import com.maknoon.mizan.core.data.db.toEntity
import com.maknoon.mizan.core.domain.network.NetworkProfile
import com.maknoon.mizan.core.domain.network.NetworkProfileResolver
import com.maknoon.mizan.core.domain.network.NetworkSnapshot
import com.maknoon.mizan.core.domain.network.ResolvedNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores the user's network profiles and resolves the current connection against them.
 *
 * The repository owns the validation the UI would otherwise repeat: a profile must have a name,
 * a non-positive budget is not a budget, and a package may be listed once. Resolution delegates
 * to the pure [NetworkProfileResolver] so the metered rule is tested on the JVM.
 */
class NetworkProfilesRepository(
    private val dao: NetworkProfileDao,
    private val resolver: NetworkProfileResolver = NetworkProfileResolver(),
) {

    fun observeAll(): Flow<List<NetworkProfile>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<NetworkProfile> = dao.all().map { it.toDomain() }

    suspend fun save(profile: NetworkProfile): Long {
        require(profile.name.isNotBlank()) { "profile name must not be blank" }
        val monthlyBudget = profile.monthlyBudgetBytes
        require(monthlyBudget == null || monthlyBudget > 0L) {
            "monthly budget must be positive but was $monthlyBudget"
        }

        val normalized = profile.copy(
            name = profile.name.trim(),
            blockedPackages = profile.blockedPackages
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
        )
        return dao.upsert(normalized.toEntity())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun resolve(snapshot: NetworkSnapshot): ResolvedNetwork =
        resolver.resolve(snapshot, all())
}
