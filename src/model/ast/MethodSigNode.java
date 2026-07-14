package model.ast;

import java.util.List;

public class MethodSigNode {
    private final List<TypeNode> paramTypes;
    private final List<TypeNode> returnTypes;

    public MethodSigNode(List<TypeNode> paramTypes, List<TypeNode> returnTypes) {
        this.paramTypes = paramTypes;
        this.returnTypes = returnTypes;
    }

    public List<TypeNode> getParamTypes() {
        return paramTypes;
    }

    public List<TypeNode> getReturnTypes() {
        return returnTypes;
    }
}
