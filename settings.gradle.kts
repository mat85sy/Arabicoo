rootProject.name = 'CloudStreamExtensions'

// Dynamically include all provider directories that contain build.gradle.kts
File(rootDir).listFiles()?.forEach { file ->
    if (file.isDirectory && 
        File(file, "build.gradle.kts").exists() &&
        !file.name.equals("build") &&
        !file.name.equals("cloudstream_repo")) {
        include(":${file.name}")
    }
}