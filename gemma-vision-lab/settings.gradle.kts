pluginManagement { repositories { google(); maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }; mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { exclusiveContent { forRepository { maven { url = uri(rootDir.resolve("hybrid-maven")); metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() } } }; filter { includeModule("com.google.ai.edge.litertlm", "litertlm-android") } }; google(); maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }; mavenCentral() } }
rootProject.name = "BlueStarGemmaLab"
include(":app")
