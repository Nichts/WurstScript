package de.peeeq.wurstscript.types;

import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.JassIm;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.utils.Utils;
import org.eclipse.jdt.annotation.Nullable;


public class WurstTypeNull extends WurstTypePrimitive {

    private static final WurstTypeNull instance = new WurstTypeNull();

    // make constructor private as we only need one instance
    protected WurstTypeNull() {
        super("null");
    }

    @Override
    VariableBinding matchAgainstSupertypeIntern(WurstType other, @Nullable Element location, VariableBinding mapping, VariablePosition variablePosition) {
        if (other.isNullable()) {
            return mapping;
        }
        if (Utils.isJassCode(location) && (other instanceof WurstTypeInt || other instanceof WurstTypeIntLiteral)) {
            return mapping;
        }

//            other instanceof WurstTypeNull
//                    || other instanceof WurstTypeHandle
//                    || other instanceof WurstNativeType
//                    || other instanceof WurstTypeString
//                    || other instanceof WurstTypeCode
//                    || other instanceof WurstTypeClass
//                    || other instanceof WurstTypeInterface
//                    || other instanceof WurstTypeModule
//                    || other instanceof WurstTypeModuleInstanciation
//                    ||
//        other instanceof WurstTypeTypeParam
//        other instanceof WurstTypeBoundTypeParam

        return null;
    }


    public static WurstTypeNull instance() {
        return instance;
    }

    @Override
    public ImExprOpt getDefaultValue(ImTranslator tr) {
        return JassIm.ImIntVal(0);
    }

    @Override
    protected boolean isNullable() {
        return true;
    }
}
