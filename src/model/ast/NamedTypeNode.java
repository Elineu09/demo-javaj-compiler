package model.ast;

import model.entities.Token;

public class NamedTypeNode implements TypeNode {
    private final Token nameToken;

    public NamedTypeNode(Token nameToken) {
        this.nameToken = nameToken;
    }

    public Token getNameToken() {
        return nameToken;
    }
}
