package com.maknoon.mizan.core.domain.saver

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.domain.usage.InstalledApp
import org.junit.Test

class VpnTunnelPlanTest {

    private val self = "com.maknoon.mizan"

    private fun app(packageName: String, uid: Int = 10_000) = InstalledApp(
        packageName = packageName,
        uid = uid,
        label = packageName,
    )

    private val installed = listOf(
        app("com.example.beta"),
        app("com.example.alpha"),
        app(self),
    )

    @Test
    fun `an empty blocklist never produces a tunnel`() {
        assertThat(planVpnTunnel(emptySet(), installed, self)).isNull()
    }

    @Test
    fun `blocking only Mizan never produces a tunnel`() {
        assertThat(planVpnTunnel(setOf(self), installed, self)).isNull()
    }

    @Test
    fun `uninstalled and blank entries never produce a tunnel on their own`() {
        val plan = planVpnTunnel(
            setOf("com.example.gone", "   "),
            installed,
            self,
        )

        assertThat(plan).isNull()
    }

    @Test
    fun `only installed packages survive, sorted and without duplicates`() {
        val plan = planVpnTunnel(
            listOf("com.example.gone", "com.example.beta", "com.example.alpha", "com.example.beta"),
            installed,
            self,
        )

        assertThat(plan?.packageNames).containsExactly("com.example.alpha", "com.example.beta").inOrder()
    }

    @Test
    fun `unblocked installed apps are not admitted`() {
        val plan = planVpnTunnel(setOf("com.example.alpha"), installed, self)

        assertThat(plan?.packageNames).containsExactly("com.example.alpha")
    }

    @Test
    fun `Mizan is excluded even when it is selected beside another app`() {
        val plan = planVpnTunnel(setOf(self, "com.example.beta"), installed, self)

        assertThat(plan?.packageNames).containsExactly("com.example.beta")
    }
}
