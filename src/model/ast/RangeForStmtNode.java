package model.ast;

import model.entities.Token;

public class RangeForStmtNode implements StmtNode {
    private final Token rangeToken;
    private final Token keyToken;
    private final Token valueToken;
    private final ExprNode rangeExpr;
    private final BlockNode body;

    public RangeForStmtNode(Token rangeToken, Token keyToken, Token valueToken, ExprNode rangeExpr, BlockNode body) {
        this.rangeToken = rangeToken;
        this.keyToken = keyToken;
        this.valueToken = valueToken;
        this.rangeExpr = rangeExpr;
        this.body = body;
    }

    public Token getRangeToken() {
        return rangeToken;
    }

    public Token getKeyToken() {
        return keyToken;
    }

    public Token getValueToken() {
        return valueToken;
    }

    public ExprNode getRangeExpr() {
        return rangeExpr;
    }

    public BlockNode getBody() {
        return body;
    }
}
