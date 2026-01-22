package io.queryanalyzer.core.analyzer;

import java.util.Objects;


public final class InferredRelationship {
    

    public enum RelationshipType {
        MANY_TO_ONE,
        
        ONE_TO_MANY,
        
        /** Could not determine direction */
        UNKNOWN
    }
    
    private final String childTable;
    private final String parentTable;
    private final String foreignKeyColumn;
    private final RelationshipType relationshipType;
    private final double confidence;
    
    private InferredRelationship(Builder builder) {
        this.childTable = builder.childTable;
        this.parentTable = builder.parentTable;
        this.foreignKeyColumn = builder.foreignKeyColumn;
        this.relationshipType = builder.relationshipType;
        this.confidence = builder.confidence;
    }
    
    public String getChildTable() {
        return childTable;
    }
    
    public String getParentTable() {
        return parentTable;
    }
    
    public String getForeignKeyColumn() {
        return foreignKeyColumn;
    }
    
    public RelationshipType getRelationshipType() {
        return relationshipType;
    }
    
    public double getConfidence() {
        return confidence;
    }
    

    public String getDescription() {
        if (parentTable == null || childTable == null) {
            return "Unknown relationship";
        }
        
        return String.format("%s -> %s (via %s)", 
            parentTable, childTable, foreignKeyColumn);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InferredRelationship that = (InferredRelationship) o;
        return Objects.equals(childTable, that.childTable) &&
               Objects.equals(parentTable, that.parentTable) &&
               Objects.equals(foreignKeyColumn, that.foreignKeyColumn);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(childTable, parentTable, foreignKeyColumn);
    }
    
    @Override
    public String toString() {
        return "InferredRelationship{" +
               "childTable='" + childTable + '\'' +
               ", parentTable='" + parentTable + '\'' +
               ", foreignKeyColumn='" + foreignKeyColumn + '\'' +
               ", type=" + relationshipType +
               ", confidence=" + confidence +
               '}';
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String childTable;
        private String parentTable;
        private String foreignKeyColumn;
        private RelationshipType relationshipType = RelationshipType.UNKNOWN;
        private double confidence = 0.5;
        
        private Builder() {}
        
        public Builder childTable(String childTable) {
            this.childTable = childTable;
            return this;
        }
        
        public Builder parentTable(String parentTable) {
            this.parentTable = parentTable;
            return this;
        }
        
        public Builder foreignKeyColumn(String foreignKeyColumn) {
            this.foreignKeyColumn = foreignKeyColumn;
            return this;
        }
        
        public Builder relationshipType(RelationshipType relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }
        
        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }
        
        public InferredRelationship build() {
            return new InferredRelationship(this);
        }
    }
}
