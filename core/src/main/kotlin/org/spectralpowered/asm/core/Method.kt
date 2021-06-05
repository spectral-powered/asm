package org.spectralpowered.asm.core

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode

class Method(val owner: ClassFile, val node: MethodNode) {

    val pool: ClassPool get() = owner.pool

    var access by node::access

    var name by node::name

    var desc by node::desc

    var instructions by node::instructions

    var maxStack by node::maxStack

    var maxLocals by node::maxLocals

    val type: Type get() = Type.getMethodType(desc)

    val returnType: Type get() = type.returnType

    val argumentTypes: List<Type> get() = type.argumentTypes.toList()

    /*
     * Reference features
     */

    val refsIn = mutableListOf<Method>()

    val refsOut = mutableListOf<Method>()

    val fieldReadRefs = mutableListOf<Field>()

    val fieldWriteRefs = mutableListOf<Field>()

    val classRefs = mutableListOf<ClassFile>()

    var overrides = mutableSetOf<Method>()

    fun toAsmNode(): MethodNode {
        return node
    }

    fun accept(visitor: ClassVisitor) {
        toAsmNode().accept(visitor)
    }

    fun accept(visitor: MethodVisitor) {
        toAsmNode().accept(visitor)
    }

    override fun toString(): String {
        return "$owner.$name$desc"
    }
}