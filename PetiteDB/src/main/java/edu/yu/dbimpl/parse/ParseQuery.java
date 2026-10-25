package edu.yu.dbimpl.parse;

import edu.yu.dbimpl.query.Predicate;
import java.util.List;

/**
 * AST node for a parsed SELECT (Query) statement.
 *
 * Holds the pieces the parser extracted:
 *   SELECT fields FROM tables [WHERE predicate]
 *
 * This class only stores and returns that data — no parsing happens here.
 */
public class ParseQuery extends ParseQueryBase {

    private final List<String> fields;
    private final List<String> tables;
    private final Predicate predicate;

    /**
     * @param fields    column names from the SELECT list (order preserved)
     * @param tables    table names from the FROM list (order preserved)
     * @param predicate WHERE clause predicate; use an empty Predicate if none
     * @throws IllegalArgumentException if any parameter is null or a list is empty
     */
    public ParseQuery(List<String> fields, List<String> tables, Predicate predicate) {
        super(fields, tables, predicate);

        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields cannot be null or empty");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("tables cannot be null or empty");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate cannot be null");
        }

        this.fields = fields;
        this.tables = tables;
        this.predicate = predicate;
    }

    @Override
    public List<String> fields() {
        return fields;
    }

    @Override
    public List<String> tables() {
        return tables;
    }

    @Override
    public Predicate predicate() {
        return predicate;
    }
}
