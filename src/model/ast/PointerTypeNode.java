package model.ast;

public class PointerTypeNode implements TypeNode {
    private final TypeNode base;

    public PointerTypeNode(TypeNode base) {
        this.base = base;
    }

    public TypeNode getBase() {
        return base;
    }
}
