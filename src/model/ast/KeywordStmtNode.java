package model.ast;

import model.entities.Token;

public class KeywordStmtNode implements StmtNode {
    private final Token token;

    public KeywordStmtNode(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}
