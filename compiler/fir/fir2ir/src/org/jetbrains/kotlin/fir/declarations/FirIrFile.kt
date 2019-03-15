/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.fir.FirIrAbstractElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi2ir.PsiSourceManager

class FirIrFile(
    psi: PsiFile,
    override val packageFragmentDescriptor: PackageFragmentDescriptor,
    override val fqName: FqName
) : FirIrAbstractElement(psi), IrFile {
    override val fileEntry = PsiSourceManager.PsiFileEntry(psi)

    override val symbol = IrFileSymbolImpl(packageFragmentDescriptor).apply {
        bind(this@FirIrFile)
    }

    override val annotations = mutableListOf<IrCall>()

    override val declarations = mutableListOf<IrDeclaration>()

    override val fileAnnotations: MutableList<AnnotationDescriptor>
        get() = mutableListOf()

    override val metadata: MetadataSource.File?
        get() = null

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitFile(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        declarations.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        declarations.forEachIndexed { i, irDeclaration ->
            declarations[i] = irDeclaration.transform(transformer, data) as IrDeclaration
        }
    }
}
