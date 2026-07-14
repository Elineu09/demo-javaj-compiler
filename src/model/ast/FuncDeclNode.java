package model.ast;

import java.util.List;

import model.entities.Token;

public class FuncDeclNode implements DeclNode {
    private final Token receiverNameToken;
    private final TypeNode receiverType;
    private final Token nameToken;
    private final List<ParamNode> params;
    private final List<TypeNode> returnTypes;
    private final BlockNode body;

    public FuncDeclNode(Token receiverNameToken, TypeNode receiverType, Token nameToken, List<ParamNode> params,
            List<TypeNode> returnTypes, BlockNode body) {
        this.receiverNameToken = receiverNameToken;
        this.receiverType = receiverType;
        this.nameToken = nameToken;
        this.params = params;
        this.returnTypes = returnTypes;
        this.body = body;
    }

    public Token getReceiverNameToken() {
        return receiverNameToken;
    }

    public TypeNode getReceiverType() {
        return receiverType;
    }

    public Token getNameToken() {
        return nameToken;
    }

    public List<ParamNode> getParams() {
        return params;
    }

    public List<TypeNode> getReturnTypes() {
        return returnTypes;
    }

    public BlockNode getBody() {
        return body;
    }
}
