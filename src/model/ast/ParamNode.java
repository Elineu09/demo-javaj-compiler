package model.ast;

import model.entities.Token;

public class ParamNode {
    private final Token nameToken;
    private final TypeNode type;

    public ParamNode(Token nameToken, TypeNode type) {
        this.nameToken = nameToken;
        this.type = type;
    }

    public Token getNameToken() {
        return nameToken;
    }

    public TypeNode getType() {
        return type;
    }
}
