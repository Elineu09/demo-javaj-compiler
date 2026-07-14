package model.ast;

import model.entities.Token;

public class ForStmtNode implements StmtNode {
    private final Token forToken;
    private final StmtNode init;
    private final ExprNode condition;
    private final StmtNode post;
    private final BlockNode body;

    public ForStmtNode(Token forToken, StmtNode init, ExprNode condition, StmtNode post, BlockNode body) {
        this.forToken = forToken;
        this.init = init;
        this.condition = condition;
        this.post = post;
        this.body = body;
    }

    public Token getForToken() {
        return forToken;
    }

    public StmtNode getInit() {
        return init;
    }

    public ExprNode getCondition() {
        return condition;
    }

    public StmtNode getPost() {
        return post;
    }

    public BlockNode getBody() {
        return body;
    }
}
