package edu.yu.dbimpl.parse;

import edu.yu.dbimpl.query.Predicate;

/**
 * AST node for a parsed DELETE statement.
 *
 * Holds the pieces the parser extracted:
 *   DELETE FROM tableName [WHERE predicate]
 *
 * If there is no WHERE clause, the parser passes an empty Predicate
 * (which means "match all rows").
 */
public class ParseDelete extends ParseDeleteBase {

    private final String tableName;
    private final Predicate predicate;

    /**
     * @param tableName table whose rows may be deleted
     * @param predicate filter for which rows to delete (empty Predicate = all)
     * @throws IllegalArgumentException if tableName is null/blank or predicate is null
     */
    public ParseDelete(String tableName, Predicate predicate) {
        super(tableName, predicate);

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName cannot be null or blank");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate cannot be null");
        }

        this.tableName = tableName;
        this.predicate = predicate;
    }

    @Override
    public String tableName() {
        return tableName;
    }

    @Override
    public Predicate predicate() {
        return predicate;
    }
}
