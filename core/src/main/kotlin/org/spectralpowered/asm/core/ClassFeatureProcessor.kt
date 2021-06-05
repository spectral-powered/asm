package org.spectralpowered.asm.core

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.util.ArrayDeque

class ClassFeatureProcessor(private val pool: ClassPool) {

    fun processAll() {
        pool.findOrCreateClass("java/lang/Object")

        val processingClasses = pool.toList().toMutableList()
        processingClasses.forEach { cls ->
            processClassA(cls)
        }

        processingClasses.clear()
        processingClasses.addAll(pool.toList())

        processingClasses.forEach { cls ->
            processClassB(cls)
        }

        processingClasses.clear()
        processingClasses.addAll(pool.toList())

        processingClasses.forEach { cls ->
            processClassC(cls)
        }
    }

    fun processClassA(cls: ClassFile) {
        val strings = cls.strings

        cls.methods.forEach { method ->
            extractStrings(method.instructions.iterator(), strings)
        }

        cls.fields.forEach { field ->
            if(field.value is String) {
                strings.add(field.value as String)
            }
        }

        if(cls.node.superName != null && cls.superClass == null) {
            cls.superClass = pool.findOrCreateClass(cls.node.superName)
            cls.superClass!!.subClasses.add(cls)
        }

        cls.node.interfaces.forEach { itfName ->
            val itf = pool.findOrCreateClass(itfName)
            if(cls.interfaces.add(itf)) itf.implementers.add(cls)
        }
    }

    private fun processClassB(cls: ClassFile) {
        cls.methods.forEach { method ->
            processMethodInsns(method)
        }
    }

    private fun processClassC(c: ClassFile) {
        val methods = hashMapOf<String, Method>()
        val queue = ArrayDeque<ClassFile>()

        queue.add(c)

        var cls: ClassFile?
        while(queue.poll().also { cls = it } != null) {
            for(method in cls!!.methods) {
                val prev = methods[method.toString()]
                if(isHierarchyBarrier(method)) {
                    if(method.overrides.isEmpty()) {
                        method.overrides.add(method)
                    }
                } else if(prev != null) {
                    if(method.overrides.isEmpty()) {
                        method.overrides = prev.overrides
                        method.overrides.add(method)
                    } else if(method.overrides != prev.overrides) {
                        prev.overrides.forEach { member ->
                            method.overrides.add(member)
                            member.overrides = method.overrides
                        }
                    }
                } else {
                    methods[method.toString()] = method
                    if(method.overrides.isEmpty()) {
                        method.overrides.add(method)
                    }
                }
            }

            if(cls!!.superClass != null) queue.add(cls!!.superClass!!)
            queue.addAll(cls!!.interfaces)
        }
    }

    private fun isHierarchyBarrier(method: Method): Boolean {
        return (method.access and (ACC_PRIVATE or ACC_STATIC)) != 0
    }

    private fun processMethodInsns(method: Method) {
        val insns = method.instructions.iterator()
        while(insns.hasNext()) {
            when(val insn = insns.next()) {
                /*
                 * Handle method invocation instructions
                 */
                is MethodInsnNode -> {
                    val c = pool.findOrCreateClass(insn.owner)
                    val dst = c.resolveMethod(insn.name, insn.desc, insn.itf) ?: continue

                    dst.refsIn.add(method)
                    method.refsOut.add(dst)
                    dst.owner.methodTypeRefs.add(method)
                    method.classRefs.add(dst.owner)
                }

                /*
                 * Handle field read / write instructions
                 */
                is FieldInsnNode -> {
                    val owner = pool.findOrCreateClass(insn.owner)
                    val dst = owner.resolveField(insn.name, insn.desc) ?: continue

                    if(insn.opcode == GETSTATIC || insn.opcode == GETFIELD) {
                        dst.readRefs.add(method)
                        method.fieldReadRefs.add(dst)
                    } else {
                        dst.writeRefs.add(method)
                        method.fieldWriteRefs.add(dst)
                    }

                    dst.owner.methodTypeRefs.add(method)
                    method.classRefs.add(dst.owner)
                }

                /*
                 * Handle type instructions
                 */
                is TypeInsnNode -> {
                    val dst = pool.findOrCreateClass(insn.desc)

                    dst.methodTypeRefs.add(method)
                    method.classRefs.add(dst)
                }
            }
        }
    }

    private fun extractStrings(it: Iterator<AbstractInsnNode>, out: MutableList<String>) {
        while(it.hasNext()) {
            val insn = it.next()

            if(insn is LdcInsnNode) {
                if(insn.cst is String) {
                    out.add(insn.cst as String)
                }
            }
        }
    }
}