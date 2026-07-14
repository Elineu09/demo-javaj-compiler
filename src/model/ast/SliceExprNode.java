package model.ast;

import model.entities.Token;

public class SliceExprNode implements ExprNode {
    private final ExprNode collection;
    private final ExprNode low;
    private final ExprNode high;
    private final Token bracketToken;

    public SliceExprNode(ExprNode collection, ExprNode low, ExprNode high, Token bracketToken) {
        this.collection = collection;
        this.low = low;
        this.high = high;
        this.bracketToken = bracketToken;
    }

    public ExprNode getCollection() {
        return collection;
    }

    public ExprNode getLow() {
        return low;
    }

    public ExprNode getHigh() {
        return high;
    }

    public Token getBracketToken() {
        return bracketToken;
    }
}
