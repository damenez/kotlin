package org.jetbrains.jet.codegen;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lexer.JetTokens;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.commons.Method;

/**
 * @author yole
 */
public abstract class StackValue {
    protected final Type type;

    public StackValue(Type type) {
        this.type = type;
    }

    public static void valueOf(InstructionAdapter instructionAdapter, final Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            return;
        }
        if (type == Type.VOID_TYPE) {
            instructionAdapter.aconst(null);
        } else {
            Type boxed = JetTypeMapper.getBoxedType(type);
            instructionAdapter.invokestatic(boxed.getInternalName(), "valueOf", "(" + type.getDescriptor() + ")" + boxed.getDescriptor());
        }
    }

    public abstract void put(Type type, InstructionAdapter v);

    public void store(InstructionAdapter v) {
        throw new UnsupportedOperationException("cannot store to value " + this);
    }

    public void dupReceiver(InstructionAdapter v, int below) {
    }

    public void condJump(Label label, boolean jumpIfFalse, InstructionAdapter v) {
        if (this.type == Type.BOOLEAN_TYPE) {
            put(Type.BOOLEAN_TYPE, v);
            if (jumpIfFalse) {
                v.ifeq(label);
            }
            else {
                v.ifne(label);
            }
        }
        else {
            throw new UnsupportedOperationException("can't generate a cond jump for a non-boolean value");
        }
    }

    public static StackValue local(int index, Type type) {
        return new Local(index, type);
    }

    public static StackValue onStack(Type type) {
        return type == Type.VOID_TYPE ? none() : new OnStack(type);
    }

    public static StackValue constant(Object value, Type type) {
        return new Constant(value, type);
    }

    public static StackValue cmp(IElementType opToken, Type type) {
        return type.getSort() == Type.OBJECT ? new ObjectCompare(opToken, type) : new NumberCompare(opToken, type);
    }

    public static StackValue not(StackValue stackValue) {
        return new Invert(stackValue);
    }

    public static StackValue arrayElement(Type type) {
        return new ArrayElement(type);
    }

    public static StackValue collectionElement(Type type, CallableMethod getter, CallableMethod setter) {
        return new CollectionElement(type, getter, setter);
    }

    public static StackValue field(Type type, String owner, String name, boolean isStatic) {
        return new Field(type, owner, name, isStatic);
    }

    public static StackValue instanceField(Type type, String owner, String name) {
        return new InstanceField(type, owner, name);
    }

    public static StackValue property(String name, String owner, Type type, boolean isStatic, boolean isInterface, Method getter, Method setter) {
        return new Property(name, owner, getter, setter, isStatic, isInterface, type);
    }

    public static StackValue expression(Type type, JetExpression expression, ExpressionCodegen generator) {
        return new Expression(type, expression, generator);
    }

    private static void box(final Type type, final Type toType, InstructionAdapter v) {
        // TODO handle toType correctly
        if (type == Type.INT_TYPE || (JetTypeMapper.isIntPrimitive(type) && toType.getInternalName().equals("java/lang/Integer"))) {
            v.invokestatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
        }
        else if (type == Type.BOOLEAN_TYPE) {
            v.invokestatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
        }
        else if (type == Type.CHAR_TYPE) {
            v.invokestatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
        }
        else if (type == Type.SHORT_TYPE) {
            v.invokestatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
        }
        else if (type == Type.LONG_TYPE) {
            v.invokestatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
        }
        else if (type == Type.BYTE_TYPE) {
            v.invokestatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
        }
        else if (type == Type.FLOAT_TYPE) {
            v.invokestatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
        }
        else if (type == Type.DOUBLE_TYPE) {
            v.invokestatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
        }
    }

    private static void unbox(final Type type, InstructionAdapter v) {
        if (type == Type.INT_TYPE) {
            v.invokevirtual("java/lang/Number", "intValue", "()I");
        }
        else if (type == Type.BOOLEAN_TYPE) {
            v.invokevirtual("java/lang/Boolean", "booleanValue", "()Z");
        }
        else if (type == Type.CHAR_TYPE) {
            v.invokevirtual("java/lang/Character", "charValue", "()C");
        }
        else if (type == Type.SHORT_TYPE) {
            v.invokevirtual("java/lang/Number", "shortValue", "()S");
        }
        else if (type == Type.LONG_TYPE) {
            v.invokevirtual("java/lang/Number", "longValue", "()J");
        }
        else if (type == Type.BYTE_TYPE) {
            v.invokevirtual("java/lang/Number", "byteValue", "()B");
        }
        else if (type == Type.FLOAT_TYPE) {
            v.invokevirtual("java/lang/Number", "floatValue", "()F");
        }
        else if (type == Type.DOUBLE_TYPE) {
            v.invokevirtual("java/lang/Number", "doubleValue", "()D");
        }
    }

    public void upcast(Type type, InstructionAdapter v) {
        if (type.equals(this.type)) return;

        if (type.getSort() == Type.OBJECT && this.type.getSort() == Type.OBJECT) {
            v.checkcast(type);
        }
        else {
            coerce(type, v);
        }
    }

    protected void coerce(Type type, InstructionAdapter v) {
        if (type.equals(this.type)) return;

        if (type.getSort() == Type.VOID && this.type.getSort() != Type.VOID) {
            if(this.type.getSize() == 1)
                v.pop();
            else
                v.pop2();
        }
        else if (type.getSort() != Type.VOID && this.type.getSort() == Type.VOID) {
            if(type.getSort() == Type.OBJECT)
                v.visitFieldInsn(Opcodes.GETSTATIC, "jet/Tuple0", "INSTANCE", "Ljet/Tuple0;");
            else if(type == Type.LONG_TYPE)
                v.lconst(0);
            else if(type == Type.FLOAT_TYPE)
                v.fconst(0);
            else if(type == Type.DOUBLE_TYPE)
                v.dconst(0);
            else
                v.iconst(0);
        }
        else if (type.getSort() == Type.OBJECT && this.type.equals(JetTypeMapper.TYPE_OBJECT)) {
                v.checkcast(type);
        }
        else if (type.getSort() == Type.OBJECT) {
            box(this.type, type, v);
        }
        else if (this.type.getSort() == Type.OBJECT && type.getSort() <= Type.DOUBLE) {
            if (this.type.equals(JetTypeMapper.TYPE_OBJECT)) {
                if (type.getSort() == Type.BOOLEAN) {
                    v.checkcast(JetTypeMapper.JL_BOOLEAN_TYPE);
                }
                else {
                    v.checkcast(JetTypeMapper.JL_NUMBER_TYPE);
                }
            }
            unbox(type, v);
        }
        else {
            v.cast(this.type, type);
        }
    }

    protected void putAsBoolean(InstructionAdapter v) {
        Label ifTrue = new Label();
        Label end = new Label();
        condJump(ifTrue, false, v);
        v.iconst(0);
        v.goTo(end);
        v.mark(ifTrue);
        v.iconst(1);
        v.mark(end);
    }

    public static StackValue none() {
        return None.INSTANCE;
    }
    
    private static class None extends StackValue {
        public static None INSTANCE = new None();
        private None() {
            super(Type.VOID_TYPE);
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            coerce(type, v);
        }
    }

    public static class Local extends StackValue {
        private final int index;

        public Local(int index, Type type) {
            super(type);
            this.index = index;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            v.load(index, this.type);
            coerce(type, v);
            // TODO unbox
        }

        @Override
        public void store(InstructionAdapter v) {
            v.store(index, this.type);
        }
    }

    public static class OnStack extends StackValue {
        public OnStack(Type type) {
            super(type);
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            if (type == Type.VOID_TYPE && this.type != Type.VOID_TYPE) {
                if (this.type.getSize() == 2) {
                    v.pop2();
                }
                else {
                    v.pop();
                }
            }
            else {
                coerce(type, v);
            }
        }
    }

    public static class Constant extends StackValue {
        private final Object value;

        public Constant(Object value, Type type) {
            super(type);
            this.value = value;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            if(value instanceof Integer)
                v.iconst((Integer) value);
            else
            if(value instanceof Long)
                v.lconst((Long) value);
            else
            if(value instanceof Float)
                v.fconst((Float) value);
            else
            if(value instanceof Double)
                v.dconst((Double) value);
            else
                v.aconst(value);
            coerce(type, v);
        }

        @Override
        public void condJump(Label label, boolean jumpIfFalse, InstructionAdapter v) {
            if (value instanceof Boolean) {
                boolean boolValue = (Boolean) value;
                if (boolValue ^ jumpIfFalse) {
                    v.goTo(label);
                }
            }
            else {
                throw new UnsupportedOperationException("don't know how to generate this condjump");
            }
        }
    }

    private static class NumberCompare extends StackValue {
        protected final IElementType opToken;
        private final Type operandType;

        public NumberCompare(IElementType opToken, Type operandType) {
            super(Type.BOOLEAN_TYPE);
            this.opToken = opToken;
            this.operandType = operandType;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            if (type == Type.VOID_TYPE) {
                return;
            }
            if (type != Type.BOOLEAN_TYPE) {
                throw new UnsupportedOperationException("don't know how to put a compare as a non-boolean type " + type);
            }
            putAsBoolean(v);
        }

        @Override
        public void condJump(Label label, boolean jumpIfFalse, InstructionAdapter v) {
            int opcode;
            if (opToken == JetTokens.EQEQ) {
                opcode = jumpIfFalse ? Opcodes.IFNE : Opcodes.IFEQ;
            }
            else if (opToken == JetTokens.EXCLEQ) {
                opcode = jumpIfFalse ? Opcodes.IFEQ : Opcodes.IFNE;
            }
            else if (opToken == JetTokens.GT) {
                opcode = jumpIfFalse ? Opcodes.IFLE : Opcodes.IFGT;
            }
            else if (opToken == JetTokens.GTEQ) {
                opcode = jumpIfFalse ? Opcodes.IFLT : Opcodes.IFGE;
            }
            else if (opToken == JetTokens.LT) {
                opcode = jumpIfFalse ? Opcodes.IFGE : Opcodes.IFLT;
            }
            else if (opToken == JetTokens.LTEQ) {
                opcode = jumpIfFalse ? Opcodes.IFGT : Opcodes.IFLE;
            }
            else {
                throw new UnsupportedOperationException("don't know how to generate this condjump");
            }
            if (operandType == Type.FLOAT_TYPE || operandType == Type.DOUBLE_TYPE) {
                if (opToken == JetTokens.GT || opToken == JetTokens.GTEQ) {
                    v.cmpg(operandType);
                }
                else {
                    v.cmpl(operandType);
                }
            }
            else if (operandType == Type.LONG_TYPE) {
                v.lcmp();
            }
            else {
                opcode += (Opcodes.IF_ICMPEQ - Opcodes.IFEQ);
            }
            v.visitJumpInsn(opcode, label);
        }
    }

    private static class ObjectCompare extends NumberCompare {
        public ObjectCompare(IElementType opToken, Type operandType) {
            super(opToken, operandType);
        }

        @Override
        public void condJump(Label label, boolean jumpIfFalse, InstructionAdapter v) {
            int opcode;
            if (opToken == JetTokens.EQEQEQ) {
                opcode = jumpIfFalse ? Opcodes.IF_ACMPNE : Opcodes.IF_ACMPEQ;
            }
            else if (opToken == JetTokens.EXCLEQEQEQ) {
                opcode = jumpIfFalse ? Opcodes.IF_ACMPEQ : Opcodes.IF_ACMPNE;
            }
            else {
                throw new UnsupportedOperationException("don't know how to generate this condjump");
            }
            v.visitJumpInsn(opcode, label);
        }
    }

    private static class Invert extends StackValue {
        private StackValue myOperand;

        private Invert(StackValue operand) {
            super(operand.type);
            myOperand = operand;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            if (type != Type.BOOLEAN_TYPE) {
                throw new UnsupportedOperationException("don't know how to put a compare as a non-boolean type");
            }
            putAsBoolean(v);
        }

        @Override
        public void condJump(Label label, boolean jumpIfFalse, InstructionAdapter v) {
            myOperand.condJump(label, !jumpIfFalse, v);
        }
    }

    private static class ArrayElement extends StackValue {
        public ArrayElement(Type type) {
            super(type);
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            v.aload(type);    // assumes array and index are on the stack
        }

        @Override
        public void store(InstructionAdapter v) {
            v.astore(type);   // assumes array and index are on the stack
        }

        @Override
        public void dupReceiver(InstructionAdapter v, int below) {
            if (below == 1) {
                v.dup2X1();
            }
            else {
                v.dup2();   // array and index
            }
        }
    }

    private static class CollectionElement extends StackValue {
        private final CallableMethod getter;
        private final CallableMethod setter;

        public CollectionElement(Type type, CallableMethod getter, CallableMethod setter) {
            super(type);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            if (getter == null) {
                throw new UnsupportedOperationException("no getter specified");
            }
            getter.invoke(v);
            coerce(type, v);
        }

        @Override
        public void store(InstructionAdapter v) {
            if (setter == null) {
                throw new UnsupportedOperationException("no setter specified");
            }
            setter.invoke(v);
        }

        @Override
        public void dupReceiver(InstructionAdapter v, int below) {
            if (below == 1) {
                v.dup2X1();
            }
            else {
                v.dup2();   // collection and index
            }
        }
    }


    private static class Field extends StackValue {
        private final String owner;
        private final String name;
        private final boolean isStatic;

        public Field(Type type, String owner, String name, boolean isStatic) {
            super(type);
            this.owner = owner;
            this.name = name;
            this.isStatic = isStatic;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            v.visitFieldInsn(isStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD, owner, name, this.type.getDescriptor());
        }

        @Override
        public void dupReceiver(InstructionAdapter v, int below) {
            if (!isStatic) {
                if (below == 1) {
                    v.dupX1();
                }
                else {
                    v.dup();
                }
            }
        }

        @Override
        public void store(InstructionAdapter v) {
            v.visitFieldInsn(isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, owner, name, this.type.getDescriptor());
        }
    }

    private static class InstanceField extends StackValue {
        private final String owner;
        private final String name;

        public InstanceField(Type type, String owner, String name) {
            super(type);
            this.owner = owner;
            this.name = name;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            v.load(0, JetTypeMapper.TYPE_OBJECT);
            v.getfield(owner, name, this.type.getDescriptor());
        }

        @Override
        public void dupReceiver(InstructionAdapter v, int below) {
        }

        @Override
        public void store(InstructionAdapter v) {
            v.load(0, JetTypeMapper.TYPE_OBJECT);
            v.putfield(owner, name, this.type.getDescriptor());
        }
    }

    private static class Property extends StackValue {
        private final String name;
        private final Method getter;
        private final Method setter;
        private final String owner;
        private final boolean isStatic;
        private final boolean isInterface;

        public Property(String name, String owner, Method getter, Method setter, boolean aStatic, boolean isInterface, Type type) {
            super(type);
            this.name = name;
            this.owner = owner;
            this.getter = getter;
            this.setter = setter;
            isStatic = aStatic;
            this.isInterface = isInterface;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            if (getter == null) {
                v.visitFieldInsn(isStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD, owner, name, this.type.getDescriptor());
            }
            else {
                v.visitMethodInsn(isStatic ? Opcodes.INVOKESTATIC : isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, owner, getter.getName(), getter.getDescriptor());
            }
            coerce(type, v);
        }

        @Override
        public void store(InstructionAdapter v) {
            if (setter == null) {
                v.visitFieldInsn(isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, owner, name, this.type.getDescriptor());
            }
            else {
                v.visitMethodInsn(isStatic ? Opcodes.INVOKESTATIC : isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, owner, setter.getName(), setter.getDescriptor());
            }
        }

        @Override
        public void dupReceiver(InstructionAdapter v, int below) {
            if (!isStatic) {
                if (below == 1) {
                    v.dupX1();
                }
                else {
                    v.dup();
                }
            }
        }
    }

    private static class Expression extends StackValue {
        private final JetExpression expression;
        private final ExpressionCodegen generator;

        public Expression(Type type, JetExpression expression, ExpressionCodegen generator) {
            super(type);
            this.expression = expression;
            this.generator = generator;
        }

        @Override
        public void put(Type type, InstructionAdapter v) {
            generator.gen(expression, type);
        }
    }
}
