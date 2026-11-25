pluginManagement { repositories { google(); maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }; maven { url = uri("https://repo1.maven.org/maven2/") }; mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }; maven { url = uri("https://repo1.maven.org/maven2/") }; mavenCentral() } }
rootProject.name = "BlueStarNpuLab"
include(":app")
