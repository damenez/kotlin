/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirIrAbstractElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtProperty

class FirIrField(
    psi: KtProperty,
    override var parent: IrDeclarationParent,
    override val descriptor: PropertyDescriptor,
    override var correspondingProperty: IrProperty?,
    override val visibility: Visibility,
    override val name: Name,
    override val isFinal: Boolean,
    override val isExternal: Boolean,
    override val isStatic: Boolean,
    override val type: IrType
) : FirIrAbstractElement(psi), IrField {

    override var initializer: IrExpressionBody? = null

    override val metadata: MetadataSource.Property? = null

    override var origin: IrDeclarationOrigin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD

    override val symbol: IrFieldSymbol = IrFieldSymbolImpl(descriptor).apply {
        bind(this@FirIrField)
    }

    override val annotations = mutableListOf<IrCall>()

    override val overriddenSymbols = mutableListOf<IrFieldSymbol>()

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        return visitor.visitField(this, data)
    }

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        initializer?.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        initializer = initializer?.transform(transformer, data)
    }
}