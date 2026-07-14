package model.ast;

import model.entities.Token;

public class FieldAccessExprNode implements ExprNode {
    private final ExprNode target;
    private final Token fieldToken;

    public FieldAccessExprNode(ExprNode target, Token fieldToken) {
        this.target = target;
        this.fieldToken = fieldToken;
    }

    public ExprNode getTarget() {
        return target;
    }

    public Token getFieldToken() {
        return fieldToken;
    }
}
