package model.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Symbol {
	private String name;
	private String type; 
	private String category; 
	private int scopeLevel;
	
	private boolean isInitialized;
	private String assignedValue; 
	private int memoryAddress;
	private int sizeInBytes;   
	private List<String> parameterTypes;
	private List<String> returnTypes;

	public Symbol(String name, String type, String category, int scopeLevel, boolean isInitialized, String assignedValue) {
		super();
		this.name = name;
		this.type = type;
		this.category = category;
		this.scopeLevel = scopeLevel;
		this.isInitialized = isInitialized;
		this.assignedValue = assignedValue;
		this.memoryAddress = 0; 
		this.sizeInBytes = 0;   
		this.parameterTypes = new ArrayList<>();
		this.returnTypes = new ArrayList<>();
	}
	
	public Symbol(String name, String type, String category, int scopeLevel) {
		this(name, type, category, scopeLevel, false, null); 
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

	public void setType(String type) {
		this.type = type;
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

	public List<String> getParameterTypes() {
		return Collections.unmodifiableList(parameterTypes);
	}

	public void setParameterTypes(List<String> parameterTypes) {
		this.parameterTypes = new ArrayList<>(parameterTypes);
	}

	public List<String> getReturnTypes() {
		return Collections.unmodifiableList(returnTypes);
	}

	public void setReturnTypes(List<String> returnTypes) {
		this.returnTypes = new ArrayList<>(returnTypes);
	}

	@Override 
	public String toString() {
		return String.format("Symbol[Name: %s, Type: %s, Category: %s, Scope: %d, Initialized: %b, Value: %s, Memory: %d, Size: %d, Params: %s, Returns: %s]", 
		                     name, type, category, scopeLevel, isInitialized, 
							 assignedValue != null ? assignedValue : "N/A", memoryAddress, sizeInBytes,
							 parameterTypes, returnTypes);
	}
}
