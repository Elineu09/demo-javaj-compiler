package model.ast;

public class ChanTypeNode implements TypeNode {
    private final TypeNode element;

    public ChanTypeNode(TypeNode element) {
        this.element = element;
    }

    public TypeNode getElement() {
        return element;
    }
}
