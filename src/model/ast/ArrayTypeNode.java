package model.ast;

public class ArrayTypeNode implements TypeNode {
    private final ExprNode dimension;
    private final TypeNode element;

    public ArrayTypeNode(ExprNode dimension, TypeNode element) {
        this.dimension = dimension;
        this.element = element;
    }

    public ExprNode getDimension() {
        return dimension;
    }

    public TypeNode getElement() {
        return element;
    }
}
