package edu.yu.dbimpl.parse;

import edu.yu.dbimpl.query.Expression;
import edu.yu.dbimpl.query.Predicate;

/**
 * AST node for a parsed UPDATE statement.
 *
 * Holds the pieces the parser extracted:
 *   UPDATE tableName SET fieldName = newValue [WHERE predicate]
 *
 * newValue is an Expression: either a constant (Datum) or another field name.
 * If there is no WHERE clause, the parser passes an empty Predicate.
 */
public class ParseUpdate extends ParseUpdateBase {

    private final String tableName;
    private final String fieldName;
    private final Expression newValue;
    private final Predicate predicate;

    /**
     * @param tableName table to update
     * @param fieldName column being assigned (returned by targetField())
     * @param newValue  RHS (right-hand side) expression of the SET assignment
     * @param predicate filter for which rows to update (empty Predicate = all)
     * @throws IllegalArgumentException if any required parameter is null/blank
     */
    public ParseUpdate(String tableName, String fieldName, Expression newValue, Predicate predicate) {
        super(tableName, fieldName, newValue, predicate);

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName cannot be null or blank");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be null or blank");
        }
        if (newValue == null) {
            throw new IllegalArgumentException("newValue cannot be null");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate cannot be null");
        }

        this.tableName = tableName;
        this.fieldName = fieldName;
        this.newValue = newValue; 
        this.predicate = predicate;
    }

    @Override
    public String tableName() {
        return tableName;
    }

    @Override
    public String targetField() {
        return fieldName;
    }

    @Override
    public Expression newValue() {
        return newValue;
    }

    @Override
    public Predicate predicate() {
        return predicate;
    }
}
