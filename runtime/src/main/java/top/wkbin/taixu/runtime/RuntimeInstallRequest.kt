package top.wkbin.taixu.runtime

data class RuntimeInstallRequest(
    val distributionId: String,
    val registryRoute: RegistryRoute = RegistryRoute.AUTO,
)

enum class RegistryRoute { AUTO, OFFICIAL, CHINA_ACCELERATED }

data class DistributionSpec(
    val id: String,
    val displayName: String,
    val imageReference: String,
)

object DistributionCatalog {
    val supported = listOf(
        DistributionSpec("ubuntu", "Ubuntu 24.04 LTS (推荐 · 全功能开发版)", "buildpack-deps:noble-scm"),
        DistributionSpec("debian", "Debian 12 Bookworm (全功能开发版)", "buildpack-deps:bookworm-scm"),
        DistributionSpec("kali", "Kali Linux Rolling", "kalilinux/kali-rolling:latest"),
        DistributionSpec("arch", "Arch Linux", "archlinux:latest"),
        DistributionSpec("fedora", "Fedora 40", "fedora:40"),
        DistributionSpec("alpine", "Alpine Linux 3.19", "alpine:3.19"),
        DistributionSpec("almalinux", "AlmaLinux 9", "almalinux:9"),
        DistributionSpec("opensuse", "openSUSE Tumbleweed", "opensuse/tumbleweed:latest"),
    )

    fun require(id: String): DistributionSpec = supported.firstOrNull { it.id.equals(id, ignoreCase = true) }
        ?: supported.first()
}
