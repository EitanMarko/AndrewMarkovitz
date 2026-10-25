package edu.yu.dbimpl.parse;

import java.util.ArrayList;
import java.util.List;

import edu.yu.dbimpl.parse.LexerBase.Token;
import edu.yu.dbimpl.parse.LexerBase.TokenType;
import edu.yu.dbimpl.query.Datum;
import edu.yu.dbimpl.query.DatumBase;
import edu.yu.dbimpl.query.Expression;
import edu.yu.dbimpl.query.Predicate;
import edu.yu.dbimpl.query.Term;

/**
 * Syntax-only recursive-descent parser for PetiteDB SQL.
 *
 * Owns a {@link Lexer} and walks tokens from the current position, building
 * AST nodes ({@link Expression}, {@link Term}, {@link Predicate}, and the
 * {@code Parse*} statement classes). Does not execute SQL or check semantics.
 *
 * Each public method attempts to parse one grammar construct from the current
 * token. Success leaves the cursor just past that construct; failure throws
 * {@link BadSyntaxException} with no recovery.
 */
public class Parser extends ParserBase {

    private final Lexer lexer;
    private Token current;

    /**
     * @param s SQL text to parse
     * @throws BadSyntaxException if the lexer cannot extract the first token
     */
    public Parser(String s) {
        super(s);
        this.lexer = new Lexer(s);
        this.current = lexer.firstTokenRetrieved();
    }

    @Override
    public boolean atEOF() {
        return current.type == TokenType.EOF;
    }

    @Override
    public String field() {
        if (current.type != TokenType.ID) {
            throw new BadSyntaxException("Expected field (identifier), got " + current);
        }
        String name = current.value;
        advance();
        return name;
    }

    @Override
    public String keyword() {
        if (current.type != TokenType.KEYWORD) {
            throw new BadSyntaxException("Expected keyword, got " + current);
        }
        String word = current.value;
        advance();
        return word;
    }

    @Override
    public DatumBase constant() {
        TokenType type = current.type;
        String value = current.value;

        switch (type) {
            case INT_CONSTANT:
                advance();
                return new Datum(Integer.valueOf(value));
            case DOUBLE_CONSTANT:
                advance();
                return new Datum(Double.valueOf(value));
            case BOOLEAN_CONSTANT:
                advance();
                return new Datum(Boolean.valueOf(value));
            case STRING_CONSTANT:
                advance();
                return new Datum(value);
            default:
                throw new BadSyntaxException("Expected constant, got " + current);
        }
    }

    @Override
    public Expression expression() {
        // Expression ::= Field | DatumBase
        if (current.type == TokenType.ID) {
            return new Expression(field());
        }
        return new Expression(constant());
    }

    @Override
    public Term term() {
        // Term ::= Expression ComparisonOp Expression
        Expression lhs = expression();
        char op = comparisonOp();
        Expression rhs = expression();
        return new Term(lhs, op, rhs);
    }

    @Override
    public Predicate predicate() {
        // Predicate ::= Term ("AND" Predicate)?
        Predicate predicate = new Predicate(term());
        while (isKeyword("and")) {
            eatKeyword("and");
            predicate.add(term());
        }
        return predicate;
    }

    @Override
    public ParseQueryBase query() {
        // Query ::= "SELECT" SelectList "FROM" TableList ("WHERE" Predicate)?
        eatKeyword("select");
        List<String> fields = commaSeparatedFields();
        eatKeyword("from");
        List<String> tables = commaSeparatedIds();

        Predicate where;
        if (isKeyword("where")) {
            eatKeyword("where");
            where = predicate();
        } else {
            where = new Predicate();
        }

        return new ParseQuery(fields, tables, where);
    }

    @Override
    public ParseInsertBase insert() {
        // Insert ::= "INSERT" "INTO" IdTok "(" FieldList ")" "VALUES" "(" DatumBaseList ")"
        eatKeyword("insert");
        eatKeyword("into");
        String tableName = id();
        eatDelimiter("(");
        List<String> fields = commaSeparatedFields();
        eatDelimiter(")");
        eatKeyword("values");
        eatDelimiter("(");
        List<DatumBase> values = commaSeparatedConstants();
        eatDelimiter(")");

        return new ParseInsert(tableName, fields, values);
    }

    @Override
    public ParseDeleteBase delete() {
        // Delete ::= "DELETE" "FROM" IdTok ("WHERE" Predicate)?
        eatKeyword("delete");
        eatKeyword("from");
        String tableName = id();

        Predicate where;
        if (isKeyword("where")) {
            eatKeyword("where");
            where = predicate();
        } else {
            where = new Predicate();
        }

        return new ParseDelete(tableName, where);
    }

    @Override
    public ParseUpdateBase update() {
        // Update ::= "UPDATE" IdTok "SET" Field "=" Expression ("WHERE" Predicate)?
        eatKeyword("update");
        String tableName = id();
        eatKeyword("set");
        String fieldName = field();
        eatComparison("=");
        Expression newValue = expression();

        Predicate where;
        if (isKeyword("where")) {
            eatKeyword("where");
            where = predicate();
        } else {
            where = new Predicate();
        }

        return new ParseUpdate(tableName, fieldName, newValue, where);
    }

    @Override
    public ParseCreateTableBase createTable() {
        // Deferred until Schema is introduced; still required by ParserBase.
        throw new BadSyntaxException("create table is not supported in this milestone");
    }

    // -------------------------------------------------------------------------
    // Lists (EBNF: X ("," X)* with at least one element)
    // -------------------------------------------------------------------------

    private List<String> commaSeparatedFields() {
        List<String> fields = new ArrayList<>();
        fields.add(field());
        while (isDelimiter(",")) {
            eatDelimiter(",");
            fields.add(field());
        }
        return fields;
    }

    private List<String> commaSeparatedIds() {
        List<String> ids = new ArrayList<>();
        ids.add(id());
        while (isDelimiter(",")) {
            eatDelimiter(",");
            ids.add(id());
        }
        return ids;
    }

    private List<DatumBase> commaSeparatedConstants() {
        List<DatumBase> values = new ArrayList<>();
        values.add(constant());
        while (isDelimiter(",")) {
            eatDelimiter(",");
            values.add(constant());
        }
        return values;
    }

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    private void advance() {
        current = lexer.nextToken();
    }

    private String id() {
        if (current.type != TokenType.ID) {
            throw new BadSyntaxException("Expected identifier, got " + current);
        }
        String name = current.value;
        advance();
        return name;
    }

    private char comparisonOp() {
        if (current.type != TokenType.COMPARISON_OP) {
            throw new BadSyntaxException("Expected comparison operator, got " + current);
        }
        char op = current.value.charAt(0);
        advance();
        return op;
    }

    private boolean isKeyword(String expected) {
        return current.type == TokenType.KEYWORD && current.value.equals(expected);
    }

    private boolean isDelimiter(String expected) {
        return current.type == TokenType.DELIMITER && current.value.equals(expected);
    }

    private void eatKeyword(String expected) {
        if (!isKeyword(expected)) {
            throw new BadSyntaxException("Expected keyword '" + expected + "', got " + current);
        }
        advance();
    }

    private void eatDelimiter(String expected) {
        if (!isDelimiter(expected)) {
            throw new BadSyntaxException("Expected '" + expected + "', got " + current);
        }
        advance();
    }

    private void eatComparison(String expected) {
        if (current.type != TokenType.COMPARISON_OP || !current.value.equals(expected)) {
            throw new BadSyntaxException("Expected '" + expected + "', got " + current);
        }
        advance();
    }
}
