package model.ast;

import model.entities.Token;

public class IndexExprNode implements ExprNode {
    private final ExprNode collection;
    private final ExprNode index;
    private final Token bracketToken;

    public IndexExprNode(ExprNode collection, ExprNode index, Token bracketToken) {
        this.collection = collection;
        this.index = index;
        this.bracketToken = bracketToken;
    }

    public ExprNode getCollection() {
        return collection;
    }

    public ExprNode getIndex() {
        return index;
    }

    public Token getBracketToken() {
        return bracketToken;
    }
}
