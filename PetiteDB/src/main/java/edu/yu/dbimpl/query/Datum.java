package edu.yu.dbimpl.query;

import java.sql.Types;
import java.util.Arrays;

public class Datum extends DatumBase {

    private final Object value;
    private final int sqlType;

    public Datum(final Integer ival) {
        super(ival);
        this.value = ival;
        this.sqlType = Types.INTEGER;
    }

    public Datum(final String sval) {
        super(sval);
        this.value = sval;
        this.sqlType = Types.VARCHAR;
    }

    public Datum(final Boolean bval) {
        super(bval);
        this.value = bval;
        this.sqlType = Types.BOOLEAN;
    }

    public Datum(final Double dval) {
        super(dval);
        this.value = dval;
        this.sqlType = Types.DOUBLE;
    }

    public Datum(final byte[] array) {
        super(array);
        this.value = array;
        this.sqlType = Types.VARBINARY;
    }

    @Override
    public int asInt() {
        if (value instanceof Number) { // Integer.intValue() is exact, Double.intValue() truncates
            return ((Number) value).intValue();
        }
        throw new ClassCastException("Cannot convert " + value.getClass() + " to int");
    }

    @Override
    public boolean asBoolean() {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        throw new ClassCastException("Cannot convert " + value.getClass() + " to boolean");
    }

    @Override
    public double asDouble() {
        if (value instanceof Number) { // every Number supports doubleValue()
            return ((Number) value).doubleValue();
        }
        throw new ClassCastException("Cannot convert " + value.getClass() + " to double");
    }

    @Override
    public String asString() {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value);
        }
        throw new ClassCastException("Cannot convert " + value.getClass() + " to String");
    }

    @Override
    public byte[] asBinaryArray() {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof String) {
            return ((String) value).getBytes();
        }
        throw new ClassCastException("Cannot convert " + value.getClass() + " to byte[]");
    }

    @Override
    public int getSQLType() {
        return sqlType;
    }

    @Override
    public int compareTo(DatumBase o) {
        if (!(o instanceof Datum)) {
            throw new ClassCastException("Cannot compare with non-Datum");
        }
        Datum that = (Datum) o;
        if (value.getClass() != that.value.getClass()) {
            throw new ClassCastException("Type mismatch: " + value.getClass() + " vs " + that.value.getClass());
        }
        if (value instanceof Integer) {
            return ((Integer) value).compareTo((Integer) that.value);
        }
        if (value instanceof Double) {
            return ((Double) value).compareTo((Double) that.value);
        }
        if (value instanceof String) {
            return ((String) value).compareTo((String) that.value);
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).compareTo((Boolean) that.value);
        }
        if (value instanceof byte[]) {
            return Arrays.compare((byte[]) value, (byte[]) that.value);
        }
        throw new ClassCastException("Unsupported type: " + value.getClass());
    }

    @Override
    public boolean equals(Object obj) { // Make sure String.equals and Arrays.equals are used as appropriate
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Datum)) { // this is an implicit null check as well
            return false;
        }
        Datum that = (Datum) obj;
        if (value instanceof byte[] && that.value instanceof byte[]) {
            return Arrays.equals((byte[]) value, (byte[]) that.value);
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        if (value instanceof byte[]) {
            return Arrays.hashCode((byte[]) value);
        }
        return value.hashCode();
    }
}
