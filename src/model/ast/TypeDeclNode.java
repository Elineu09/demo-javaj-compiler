package model.ast;

import java.util.List;

import model.entities.Token;

public class TypeDeclNode implements DeclNode {

    public enum TypeDeclKind {
        STRUCT, INTERFACE, ALIAS
    }

    private final Token nameToken;
    private final TypeDeclKind kind;
    private final List<TypeNode> structFieldTypes;
    private final List<MethodSigNode> interfaceMethods;
    private final TypeNode aliasedType;

    public TypeDeclNode(Token nameToken, TypeDeclKind kind, List<TypeNode> structFieldTypes,
            List<MethodSigNode> interfaceMethods, TypeNode aliasedType) {
        this.nameToken = nameToken;
        this.kind = kind;
        this.structFieldTypes = structFieldTypes;
        this.interfaceMethods = interfaceMethods;
        this.aliasedType = aliasedType;
    }

    public Token getNameToken() {
        return nameToken;
    }

    public TypeDeclKind getKind() {
        return kind;
    }

    public List<TypeNode> getStructFieldTypes() {
        return structFieldTypes;
    }

    public List<MethodSigNode> getInterfaceMethods() {
        return interfaceMethods;
    }

    public TypeNode getAliasedType() {
        return aliasedType;
    }
}
