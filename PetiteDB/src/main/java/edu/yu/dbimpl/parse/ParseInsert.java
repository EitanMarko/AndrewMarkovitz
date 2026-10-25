package edu.yu.dbimpl.parse;

import edu.yu.dbimpl.query.DatumBase;
import java.util.List;

/**
 * AST node for a parsed INSERT statement.
 *
 * Holds the pieces the parser extracted:
 *   INSERT INTO tableName (fields) VALUES (values)
 *
 * The ith entry in values corresponds to the ith entry in fields.
 */
public class ParseInsert extends ParseInsertBase {

    private final String tableName;
    private final List<String> fields;
    private final List<DatumBase> values;

    /**
     * @param tableName table that will receive the new row
     * @param fields    column names for the insert (order preserved)
     * @param values    values for those columns (same length/order as fields)
     * @throws IllegalArgumentException if any parameter is null/blank/empty,
     *         or if fields and values have different sizes
     */
    public ParseInsert(String tableName, List<String> fields, List<DatumBase> values) {
        super(tableName, fields, values);

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName cannot be null or blank");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields cannot be null or empty");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values cannot be null or empty");
        }
        if (fields.size() != values.size()) {
            throw new IllegalArgumentException(
                    "fields and values must have the same size");
        }

        this.tableName = tableName;
        this.fields = fields;
        this.values = values;
    }

    @Override
    public List<String> fields() {
        return fields;
    }

    @Override
    public List<DatumBase> values() {
        return values;
    }

    @Override
    public String tableName() {
        return tableName;
    }
}
