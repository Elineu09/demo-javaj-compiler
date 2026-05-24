package model.entities;

public class Symbol {
	private String name;
	private String type; // Represents data type (e.g., "int", "string", "struct Persona")
	private String category; // Represents variable type (e.g., "VAR", "CONST", "FUNC", "TYPE")
	private int scopeLevel;
	
	private boolean isInitialized;
	private String assignedValue; // Stores the literal value or a representation of the assigned expression
	private int memoryAddress; // Placeholder for semantic/code generation phase
	private int sizeInBytes;   // Placeholder for semantic/code generation phase

	public Symbol(String name, String type, String category, int scopeLevel, boolean isInitialized, String assignedValue) {
		super();
		this.name = name;
		this.type = type;
		this.category = category;
		this.scopeLevel = scopeLevel;
		this.isInitialized = isInitialized;
		this.assignedValue = assignedValue;
		this.memoryAddress = 0; // Default placeholder
		this.sizeInBytes = 0;   // Default placeholder
	}
	
	// Existing constructor for backward compatibility, will be updated in Parser.java
	public Symbol(String name, String type, String category, int scopeLevel) {
		this(name, type, category, scopeLevel, false, null); // Default to not initialized, no assigned value
	}

	public boolean isInitialized() {
		return isInitialized;
	}

	public void setInitialized(boolean isInitialized) {
		this.isInitialized = isInitialized;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getCategory() {
		return category;
	}

	public int getScopeLevel() {
		return scopeLevel;
	}
	
	public String getAssignedValue() {
		return assignedValue;
	}

	public void setAssignedValue(String assignedValue) {
		this.assignedValue = assignedValue;
	}

	public int getMemoryAddress() {
		return memoryAddress;
	}

	public void setMemoryAddress(int memoryAddress) {
		this.memoryAddress = memoryAddress;
	}

	public int getSizeInBytes() {
		return sizeInBytes;
	}

	public void setSizeInBytes(int sizeInBytes) {
		this.sizeInBytes = sizeInBytes;
	}

	@Override 
	public String toString() {
		return String.format("Symbol[Name: %s, Type: %s, Category: %s, Scope: %d, Initialized: %b, Value: %s, Memory: %d, Size: %d]", 
		                     name, type, category, scopeLevel, isInitialized, 
							 assignedValue != null ? assignedValue : "N/A", memoryAddress, sizeInBytes);
	}
}
