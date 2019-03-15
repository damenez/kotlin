/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.descriptors.WrappedClassDescriptor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.ConeClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.IrErrorType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrErrorTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.types.Variance

private fun createErrorType(): IrErrorType = IrErrorTypeImpl(null, emptyList(), Variance.INVARIANT)

fun FirTypeRef.toIrType(session: FirSession): IrType {
    if (this !is FirResolvedTypeRef) {
        return createErrorType()
    }
    return type.toIrType(session)
}

fun ConeKotlinType.toIrType(session: FirSession): IrType {
    when (this) {
        is ConeKotlinErrorType -> return createErrorType()
        is ConeFlexibleType -> TODO()
        is ConeLookupTagBasedType -> {
            val firSymbol = this.lookupTag.toSymbol(session) ?: return createErrorType()
            val irSymbol = firSymbol.toIrSymbol()
            // TODO: arguments, annotations
            return IrSimpleTypeImpl(irSymbol, this.isMarkedNullable, emptyList(), emptyList())
        }
    }
}

fun ConeClassifierSymbol.toIrSymbol(): IrClassifierSymbol {
    when (this) {
        is FirTypeParameterSymbol -> TODO()
        is FirTypeAliasSymbol -> TODO()
        is FirClassSymbol -> {
            // TODO: at some later stage we should bind symbol to its IR
            return IrClassSymbolImpl(WrappedClassDescriptor())
        }
        else -> throw AssertionError("Should not be here: $this")
    }
}
