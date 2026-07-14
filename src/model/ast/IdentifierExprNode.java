package model.ast;

import model.entities.Token;

public class IdentifierExprNode implements ExprNode {
    private final Token token;

    public IdentifierExprNode(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}
