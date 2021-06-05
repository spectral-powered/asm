package org.spectralpowered.asm.core.util

import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

object JarUtil {

    fun iterateJar(archive: Path, action: (Path) -> Unit): FileSystem {
        val fs = FileSystems.newFileSystem(URI("jar:${archive.toUri()}"), mutableMapOf<String, String>())

        Files.walkFileTree(fs.getPath("/"), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if(file.toString().endsWith(".class")) {
                    action(file)
                }

                return FileVisitResult.CONTINUE
            }
        })

        fs.close()
        return fs
    }
}