package model.ast;

public class MapTypeNode implements TypeNode {
    private final TypeNode keyType;
    private final TypeNode valueType;

    public MapTypeNode(TypeNode keyType, TypeNode valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public TypeNode getKeyType() {
        return keyType;
    }

    public TypeNode getValueType() {
        return valueType;
    }
}
