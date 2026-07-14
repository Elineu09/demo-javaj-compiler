package model.ast;

public class SliceTypeNode implements TypeNode {
    private final TypeNode element;

    public SliceTypeNode(TypeNode element) {
        this.element = element;
    }

    public TypeNode getElement() {
        return element;
    }
}
