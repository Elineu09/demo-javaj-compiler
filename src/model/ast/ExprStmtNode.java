package model.ast;

import model.entities.Token;

public class ExprStmtNode implements StmtNode {
    private final Token leadingKeyword;
    private final ExprNode expr;

    public ExprStmtNode(Token leadingKeyword, ExprNode expr) {
        this.leadingKeyword = leadingKeyword;
        this.expr = expr;
    }

    public Token getLeadingKeyword() {
        return leadingKeyword;
    }

    public ExprNode getExpr() {
        return expr;
    }
}
