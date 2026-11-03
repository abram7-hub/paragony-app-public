package pl.paragony.reading

import java.io.File

/** Anonymized reading samples in `testdata/reading/expected` (shared with the Python prototype). */
object TestData {
    val dir: File by lazy {
        val candidates = listOfNotNull(
            System.getProperty("testdata.dir")?.let(::File),
            File("../testdata"), // Gradle runs unit tests from the module directory (app/)
            File("testdata"),
        )
        candidates.firstOrNull { File(it, "reading/expected").isDirectory }
            ?: error("testdata not found; tried ${candidates.map { it.absolutePath }}")
    }

    val expectedFiles: List<File>
        get() = File(dir, "reading/expected").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

    fun load(name: String): ReadDocument =
        ReadDocumentJson.parse(File(dir, "reading/expected/$name.json").readText(Charsets.UTF_8))
}
