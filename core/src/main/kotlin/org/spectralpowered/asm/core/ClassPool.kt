package org.spectralpowered.asm.core

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.V1_6
import org.objectweb.asm.tree.ClassNode
import org.spectralpowered.asm.core.util.JarUtil
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ClassPool {

    private val classMap = mutableMapOf<String, ClassFile>()

    private val sharedClassMap = mutableMapOf<String, ClassFile>()

    private val featureProcessor = ClassFeatureProcessor(this)

    fun addClass(element: ClassFile) {
        classMap[element.name] = element
    }

    fun removeClass(element: ClassFile) {
        classMap.remove(element.name)
    }

    fun addSharedClass(element: ClassFile) {
        sharedClassMap[element.name] = element
    }

    fun removeSharedClass(element: ClassFile) {
        sharedClassMap.remove(element.name)
    }

    val size: Int get() = classMap.values.size

    fun findClass(name: String): ClassFile? = classMap[name]

    fun findSharedClass(name: String): ClassFile? = sharedClassMap[name]

    /**
     * Initializes and extracts features / references from the current class
     * pool entries.
     */
    fun init() {
        featureProcessor.processAll()
    }

    fun findOrCreateClass(name: String): ClassFile {
        var ret = findClass(name) ?: findSharedClass(name)
        if(ret != null) return ret

        if(name.startsWith('[')) {
            val elementClass = findArrayClass(name)
            ret = loadMissingClass(name)
            ret.elementClass = elementClass
            addSharedClass(ret)
        } else {
            ret = loadMissingClass(name)
        }

        return ret
    }

    private fun findArrayClass(name: String): ClassFile {
        var elementName = name.substring(name.lastIndexOf('[') + 1)
        if(elementName.startsWith('L')) {
            elementName = elementName.substring(1, elementName.length - 1)
        }

        return findOrCreateClass(elementName)
    }

    private fun loadMissingClass(name: String): ClassFile {
        if(name.length > 1) {
            var file: Path? = null
            val url = ClassLoader.getSystemResource("$name.class")

            if(url != null) {
                file = resolveClasspath(url)
            }

            if(file != null) {
                val cls = readClass(file)
                addSharedClass(cls)

                /*
                 * Process shared class A
                 */
                featureProcessor.processClassA(cls)

                return cls
            }
        }

        /*
         * Create unknown class
         */
        val node = ClassNode()
        node.version = V1_6
        node.access = ACC_PUBLIC
        node.name = name
        node.superName = "java/lang/Object"

        val ret = ClassFile(this, node)
        addSharedClass(ret)

        return ret
    }

    private fun resolveClasspath(url: URL): Path? {
        return try {
            val uri = url.toURI()
            var ret = Paths.get(uri)

            if(uri.scheme == "jrt" && !Files.exists(ret)) {
                ret = Paths.get(URI(uri.scheme, uri.userInfo, uri.host, uri.port, "/modules".plus(uri.path), uri.query, uri.fragment))
            }

            ret
        } catch(e : Exception) {
            null
        }
    }

    fun toList(): List<ClassFile> = classMap.values.toList()

    fun forEach(action: (ClassFile) -> Unit) = toList().forEach(action)

    fun first(predicate: (ClassFile) -> Boolean) = toList().first(predicate)

    fun firstOrNull(predicate: (ClassFile) -> Boolean) = toList().firstOrNull(predicate)

    fun readClass(bytes: ByteArray): ClassFile {
        val node = ClassNode()
        val reader = ClassReader(bytes)
        reader.accept(node, ClassReader.SKIP_FRAMES)
        return ClassFile(this, node)
    }

    fun readClass(archive: Path): ClassFile {
        return readClass(Files.newInputStream(archive).readAllBytes())
    }

    fun insertJar(archive: Path) {
        JarUtil.iterateJar(archive) { entry ->
            val bytes = Files.newInputStream(entry).readAllBytes()
            val classFile = readClass(bytes)
            addClass(classFile)
        }
    }
}