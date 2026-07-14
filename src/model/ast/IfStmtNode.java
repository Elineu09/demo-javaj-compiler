package model.ast;

import model.entities.Token;

public class IfStmtNode implements StmtNode {
    private final Token ifToken;
    private final ExprNode condition;
    private final BlockNode thenBlock;
    private final StmtNode elseBranch;

    public IfStmtNode(Token ifToken, ExprNode condition, BlockNode thenBlock, StmtNode elseBranch) {
        this.ifToken = ifToken;
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseBranch = elseBranch;
    }

    public Token getIfToken() {
        return ifToken;
    }

    public ExprNode getCondition() {
        return condition;
    }

    public BlockNode getThenBlock() {
        return thenBlock;
    }

    public StmtNode getElseBranch() {
        return elseBranch;
    }
}
