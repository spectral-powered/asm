package org.spectralpowered.asm.core

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.*

class ClassFile(val pool: ClassPool, val node: ClassNode) {

    var version by node::version

    var access by node::access

    var name by node::name

    var superClass: ClassFile? = null

    var subClasses = mutableListOf<ClassFile>()

    var interfaces = mutableListOf<ClassFile>()

    var implementers = mutableListOf<ClassFile>()

    val type: Type get() = Type.getObjectType(name)

    var methods = node.methods.map { Method(this, it) }.toMutableList()

    var fields = node.fields.map { Field(this, it) }.toMutableList()

    /*
     * Reference features
     */

    val strings = mutableListOf<String>()

    val methodTypeRefs = mutableListOf<Method>()

    val fieldTypeRefs = mutableListOf<Field>()

    var elementClass: ClassFile? = null

    fun findMethod(name: String, desc: String): Method? {
        return methods.firstOrNull { it.name == name && it.desc == desc }
    }

    fun findField(name: String, desc: String): Field? {
        return fields.firstOrNull { it.name == name && it.desc == desc }
    }

    fun resolveMethod(name: String, desc: String, toInterface: Boolean): Method? {
        if(!toInterface) {
            var ret = findMethod(name, desc)
            if(ret != null) return ret

            var cls = this.superClass
            while(cls != null) {
                ret = cls.findMethod(name, desc)
                if(ret != null) return ret
                cls = cls.superClass
            }

            return resolveInterfaceMethod(name, desc)
        } else {
            var ret = findMethod(name, desc)
            if(ret != null) return ret

            if(superClass != null) {
                ret = superClass!!.findMethod(name, desc)
                if(ret != null && (ret.access and (ACC_PUBLIC or ACC_STATIC)) == ACC_PUBLIC) return ret
            }

            return resolveInterfaceMethod(name, desc)
        }
    }

    private fun resolveInterfaceMethod(name: String, desc: String): Method? {
        val queue = ArrayDeque<ClassFile>()
        val visited = mutableSetOf<ClassFile>()

        var cls: ClassFile? = this
        do {
            cls!!.interfaces.forEach { itf ->
                if(visited.add(itf)) queue.add(itf)
            }
        } while(cls!!.superClass.also { cls = it } != null)

        if(queue.isEmpty()) return null

        val matches = mutableSetOf<Method>()
        var foundNonAbstract = false

        while(queue.poll().also { cls = it } != null) {
            var ret = cls!!.findMethod(name, desc)

            if(ret != null &&
                (ret.access and (ACC_PRIVATE or ACC_STATIC)) == 0) {
                matches.add(ret)

                if((ret.access and ACC_ABSTRACT) == 0) {
                    foundNonAbstract = true
                }
            }

            cls!!.interfaces.forEach { itf ->
                if(visited.add(itf)) queue.add(itf)
            }
        }

        if(matches.isEmpty()) return null
        if(matches.size == 1) return matches.iterator().next()

        if(foundNonAbstract) {
            val it = matches.iterator()
            while(it.hasNext()) {
                val m = it.next()

                if((m.access and ACC_ABSTRACT) != 0) {
                    it.remove()
                }
            }

            if(matches.size == 1) return matches.iterator().next()
        }

        val it = matches.iterator()
        while(it.hasNext()) {
            val m = it.next()

            cmpLoop@ for(m2 in matches) {
                if(m2 == m) continue

                if(m2.owner.interfaces.contains(m.owner)) {
                    it.remove()
                    break
                }

                queue.addAll(m2.owner.interfaces)

                while(queue.poll().also { cls = it } != null) {
                    if(cls!!.interfaces.contains(m.owner)) {
                        it.remove()
                        queue.clear()
                        break@cmpLoop
                    }

                    queue.addAll(cls!!.interfaces)
                }
            }
        }

        return matches.iterator().next()
    }

    fun resolveField(name: String, desc: String): Field? {
        var ret = findField(name, desc)
        if(ret != null) return ret

        if(interfaces.isNotEmpty()) {
            val queue = ArrayDeque<ClassFile>()
            queue.addAll(interfaces)

            var cls: ClassFile?
            while(queue.pollFirst().also { cls = it } != null) {
                ret = cls!!.findField(name, desc)
                if(ret != null) return ret

                cls!!.interfaces.forEach { itf ->
                    queue.addFirst(itf)
                }
            }
        }

        var cls = superClass
        while(cls != null) {
            ret = cls!!.findField(name, desc)
            if(ret != null) return ret

            cls = cls!!.superClass
        }

        return null
    }

    fun toAsmNode(): ClassNode {
        node.methods.clear()
        node.fields.clear()
        node.superName = superClass?.name
        node.interfaces = interfaces.map { it.name }.toList()
        node.methods = methods.map { it.toAsmNode() }
        node.fields = fields.map { it.toAsmNode() }
        return node
    }

    fun accept(visitor: ClassVisitor) {
        toAsmNode().accept(visitor)
    }

    override fun toString(): String {
        return name
    }
}