package org.spectralpowered.asm.core

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode

class Field(val owner: ClassFile, val node: FieldNode) {

    var access by node::access

    var name by node::name

    var desc by node::desc

    var value by node::value

    val type: Type get() = Type.getType(name)

    /*
     * Reference features
     */

    val readRefs = mutableListOf<Method>()

    val writeRefs = mutableListOf<Method>()

    fun toAsmNode(): FieldNode {
        return node
    }

    fun accept(visitor: ClassVisitor) {
        toAsmNode().accept(visitor)
    }

    override fun toString(): String {
        return "$owner.$name"
    }
}