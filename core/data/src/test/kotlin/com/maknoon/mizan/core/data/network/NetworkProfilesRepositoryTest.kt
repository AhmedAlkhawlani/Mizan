package com.maknoon.mizan.core.data.network

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.NetworkProfileDao
import com.maknoon.mizan.core.data.db.NetworkProfileEntity
import com.maknoon.mizan.core.domain.network.NetworkProfile
import com.maknoon.mizan.core.domain.network.NetworkSnapshot
import com.maknoon.mizan.core.domain.network.NetworkTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkProfilesRepositoryTest {

    private val dao = FakeNetworkProfileDao()
    private val repository = NetworkProfilesRepository(dao)

    private val wifiAtHome = NetworkSnapshot(
        connected = true,
        transport = NetworkTransport.WIFI,
        systemMetered = false,
    )

    private fun profile(
        name: String = "البيت",
        transport: NetworkTransport = NetworkTransport.WIFI,
        systemMetered: Boolean = false,
        metered: Boolean = false,
        monthlyBudgetBytes: Long? = null,
        blockedPackages: List<String> = emptyList(),
    ) = NetworkProfile(
        name = name,
        transport = transport,
        systemMetered = systemMetered,
        metered = metered,
        monthlyBudgetBytes = monthlyBudgetBytes,
        blockedPackages = blockedPackages,
        createdAt = 100L,
    )

    @Test
    fun `saving a profile stores it with a trimmed name`() = runTest {
        repository.save(profile(name = "  البيت  "))

        assertThat(dao.rows.single().name).isEqualTo("البيت")
        assertThat(dao.rows.single().transport).isEqualTo(NetworkTransport.WIFI)
    }

    @Test
    fun `blocked packages are trimmed and deduplicated`() = runTest {
        repository.save(
            profile(blockedPackages = listOf(" com.a ", "com.b", "com.a", "")),
        )

        assertThat(dao.rows.single().blockedPackages).containsExactly("com.a", "com.b").inOrder()
    }

    @Test
    fun `a blank name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(profile(name = "   ")) }
        }
    }

    @Test
    fun `a non-positive monthly budget is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(profile(monthlyBudgetBytes = 0L)) }
        }
    }

    @Test
    fun `an absent monthly budget is allowed`() = runTest {
        repository.save(profile(monthlyBudgetBytes = null))

        assertThat(dao.rows.single().monthlyBudgetBytes).isNull()
    }

    @Test
    fun `all maps stored rows to domain profiles`() = runTest {
        dao.rows += NetworkProfileEntity(
            id = 3,
            name = "الباقة",
            transport = NetworkTransport.CELLULAR,
            systemMetered = true,
            metered = true,
            monthlyBudgetBytes = 50_000_000_000L,
            blockedPackages = listOf("com.example.app"),
            createdAt = 200L,
        )

        val profiles = repository.all()

        assertThat(profiles).hasSize(1)
        assertThat(profiles.single().id).isEqualTo(3)
        assertThat(profiles.single().monthlyBudgetBytes).isEqualTo(50_000_000_000L)
        assertThat(profiles.single().blockedPackages).containsExactly("com.example.app")
    }

    @Test
    fun `the stored profile overrides the system metered flag`() = runTest {
        dao.rows += profile(metered = true).toEntity(id = 1)

        val resolved = repository.resolve(wifiAtHome)

        assertThat(resolved.metered).isTrue()
        assertThat(resolved.profile?.id).isEqualTo(1)
    }

    @Test
    fun `without a stored profile resolution falls back to the system`() = runTest {
        val resolved = repository.resolve(wifiAtHome)

        assertThat(resolved.metered).isFalse()
        assertThat(resolved.profile).isNull()
    }

    @Test
    fun `delete forwards the id to the dao`() = runTest {
        dao.rows += profile().toEntity(id = 7)

        repository.delete(7)

        assertThat(dao.rows).isEmpty()
    }

    private fun NetworkProfile.toEntity(id: Long) = NetworkProfileEntity(
        id = id,
        name = name,
        transport = transport,
        systemMetered = systemMetered,
        metered = metered,
        monthlyBudgetBytes = monthlyBudgetBytes,
        blockedPackages = blockedPackages,
        createdAt = createdAt,
    )

    private class FakeNetworkProfileDao : NetworkProfileDao {
        val rows = mutableListOf<NetworkProfileEntity>()

        override fun observeAll(): Flow<List<NetworkProfileEntity>> = flowOf(rows.toList())

        override suspend fun all(): List<NetworkProfileEntity> = rows.toList()

        override suspend fun boundTo(
            transport: NetworkTransport,
            systemMetered: Boolean,
        ): NetworkProfileEntity? = rows
            .firstOrNull { it.transport == transport && it.systemMetered == systemMetered }

        override suspend fun upsert(entity: NetworkProfileEntity): Long {
            rows.removeAll { it.transport == entity.transport && it.systemMetered == entity.systemMetered }
            val id = if (entity.id == 0L) (rows.maxOfOrNull { it.id } ?: 0L) + 1 else entity.id
            rows += entity.copy(id = id)
            return id
        }

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }
    }
}
