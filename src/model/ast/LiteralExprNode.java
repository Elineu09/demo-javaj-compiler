package model.ast;

import model.entities.Token;

public class LiteralExprNode implements ExprNode {
    private final Token token;

    public LiteralExprNode(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}
