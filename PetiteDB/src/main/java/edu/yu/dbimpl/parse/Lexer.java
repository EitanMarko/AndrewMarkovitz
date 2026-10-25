package edu.yu.dbimpl.parse;

import java.util.HashSet;
import java.util.Set;

/**
 * Lexer: turns a SQL string into a stream of Tokens, following the PetiteDB EBNF.
 *
 * Example:
 *   "SELECT Name FROM Students WHERE Age = 18"
 * becomes tokens like:
 *   KEYWORD("select"), ID("name"), KEYWORD("from"), ID("students"), ...
 *
 * Rules from EBNF / LexerBase:
 * - Keywords and identifiers are lowercased (case-insensitive matches).
 * - String constants keep their original case.
 * - Whitespace and "--" comments are consumed and discarded.
 * - Constructor must already read the first token.
 * - After input ends, keep returning EOF_Token.
 */
public class Lexer extends LexerBase {

    /**
     * The words this lexer classifies as KEYWORD: every entry in
     * LexerBase.keywords, plus the "double" and "boolean" type names.
     *
     * The grammar needs all four type names for "create table" (SchemaBase
     * supports int, varchar, double and boolean fields), but LexerBase.keywords
     * lists only int and varchar.  LexerBase may not be modified and its list is
     * immutable, so the two missing names are added to a derived set here.
     */
    private static final Set<String> ALL_KEYWORDS = buildAllKeywords();

    private static Set<String> buildAllKeywords() {
        final Set<String> all = new HashSet<>(keywords);
        all.add("double");
        all.add("boolean");
        return Set.copyOf(all);
    }

    /** The full SQL text we are scanning. */
    private final String input;

    /** Current index into {@link #input}. Advances as we consume characters. */
    private int pos;

    /**
     * The very first token scanned by the constructor.
     * Never overwritten — firstTokenRetrieved() always returns this.
     */
    private final Token firstToken;

    /**
     * Create a lexer for the given statement.
     * Per LexerBase: by the time the constructor finishes, one token
     * has already been consumed and is available via firstTokenRetrieved().
     */
    public Lexer(String s) {
        super(s);
        // Null is not a valid statement string — fail early like other bad input.
        if (s == null) {
            throw new BadSyntaxException("Lexer input cannot be null");
        }
        this.input = s;
        this.pos = 0;
        // Eagerly scan the first token (required by the API) and freeze it.
        this.firstToken = readNextToken();
    }

    /**
     * Always return the token the constructor extracted.
     * Calling nextToken() does NOT change what this returns.
     * Calling this again also does NOT reset the scan position.
     */
    @Override
    public Token firstTokenRetrieved() {
        return firstToken;
    }

    /**
     * Scan and return the next token.
     * If we're past the end of input, return EOF_Token (and keep doing so).
     */
    @Override
    public Token nextToken() throws BadSyntaxException {
        return readNextToken();
    }

    // -------------------------------------------------------------------------
    // Core scanning
    // -------------------------------------------------------------------------

    /**
     * Look at the next significant characters and build one Token.
     * This is the "brain" of the lexer.
     */
    private Token readNextToken() {
        // EBNF: Whitespace and Comment are consumed/discarded by the lexer.
        skipWhitespaceAndComments();

        // Nothing left → end of file.
        if (pos >= input.length()) {
            return EOF_Token;
        }

        char ch = input.charAt(pos);

        // StrToken ::= "'" [a-zA-Z0-9_ ]+ "'"
        if (ch == '\'') {
            return readStringConstant();
        }

        // ComparisonOp ::= "=" | ">" | "<"
        if (comparisonOperators.contains(ch)) {
            pos++;
            return new Token(TokenType.COMPARISON_OP, String.valueOf(ch));
        }

        // Delimiters used in the EBNF: "(" ")" ","
        // (semicolon is NOT in the EBNF)
        if (ch == '(' || ch == ')' || ch == ',') {
            pos++;
            return new Token(TokenType.DELIMITER, String.valueOf(ch));
        }

        // IntToken / DoubleToken — digits, or a leading "." that starts a double like ".5"
        if (Character.isDigit(ch) || startsDoubleWithDot()) {
            return readNumber();
        }

        // Keyword | BooleanToken | IdTok — must start with [a-zA-Z]
        if (isLetter(ch)) {
            return readWord();
        }

        // Character we don't recognize → fail immediately (no recovery).
        throw new BadSyntaxException(
                "Unexpected character '" + ch + "' at position " + pos);
    }

    /**
     * Discard Whitespace ::= [ \t\n\r]+
     * and Comment ::= "--" [^\n]* "\n"
     * (possibly interleaved), until the next real token.
     */
    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char ch = input.charAt(pos);

            // Whitespace: blank, tab, newline, carriage return
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                pos++;
                continue;
            }

            // Comment starts with "--" and runs through the end of the line
            if (ch == '-' && pos + 1 < input.length() && input.charAt(pos + 1) == '-') {
                pos += 2; // skip the "--"
                while (pos < input.length() && input.charAt(pos) != '\n') {
                    pos++;
                }
                if (pos < input.length()) {
                    pos++; // consume the terminating newline
                }
                continue;
            }

            // Next character belongs to a real token
            break;
        }
    }

    /**
     * True if the next characters match a DoubleToken that starts with '.':
     *   [0-9]* "." [0-9]+   with zero leading digits → "." [0-9]+
     */
    private boolean startsDoubleWithDot() {
        return input.charAt(pos) == '.'
                && pos + 1 < input.length()
                && Character.isDigit(input.charAt(pos + 1));
    }

    /**
     * StrToken ::= "'" [a-zA-Z0-9_ ]+ "'"
     * - Non-empty
     * - Only letters, digits, underscore, and spaces inside
     * - Case preserved
     * - No '' escape (not in the EBNF)
     */
    private Token readStringConstant() {
        pos++; // skip the opening '

        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char ch = input.charAt(pos);

            if (ch == '\'') {
                pos++; // closing quote
                // "+" in the EBNF means at least one character inside
                if (sb.length() == 0) {
                    throw new BadSyntaxException("Empty string constant is not allowed");
                }
                return new Token(TokenType.STRING_CONSTANT, sb.toString());
            }

            // Only [a-zA-Z0-9_ ] are legal inside a StrToken
            if (!isIdChar(ch) && ch != ' ') {
                throw new BadSyntaxException(
                        "Illegal character '" + ch + "' inside string constant");
            }

            sb.append(ch);
            pos++;
        }

        throw new BadSyntaxException("Unterminated string constant");
    }

    /**
     * IntToken    ::= [0-9]+
     * DoubleToken ::= [0-9]+ "." [0-9]*
     *              | [0-9]* "." [0-9]+
     *
     * No leading minus (not in the EBNF).
     * Token.value is the number as a String (per LexerBase).
     */
    private Token readNumber() {
        int start = pos;
        boolean sawDot = false;
        boolean sawDigitBeforeDot = false;
        boolean sawDigitAfterDot = false;

        // Leading digits (optional if we start with '.')
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            sawDigitBeforeDot = true;
            pos++;
        }

        // Optional decimal point + fractional digits
        if (pos < input.length() && input.charAt(pos) == '.') {
            sawDot = true;
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                sawDigitAfterDot = true;
                pos++;
            }
        }

        // Validate against the two DoubleToken alternatives / IntToken
        if (sawDot) {
            // Need digits before the dot, OR digits after the dot (or both)
            if (!sawDigitBeforeDot && !sawDigitAfterDot) {
                throw new BadSyntaxException("Invalid double constant");
            }
            return new Token(TokenType.DOUBLE_CONSTANT, input.substring(start, pos));
        }

        // IntToken requires at least one digit 
        if (!sawDigitBeforeDot) {
            throw new BadSyntaxException("Invalid integer constant");
        }
        return new Token(TokenType.INT_CONSTANT, input.substring(start, pos));
    }

    /**
     * Read a word that starts with a letter.
     *
     * IdTok ::= [a-zA-Z][a-zA-Z0-9_]*   (and not a Keyword / BooleanToken)
     * BooleanToken ::= "true" | "false"
     * Keyword ::= entries in ALL_KEYWORDS (lowercased)
     *
     * Always lowercased before classifying / storing.
     */
    private Token readWord() {
        int start = pos;
        pos++; // first character is already known to be a letter

        // Rest: [a-zA-Z0-9_]*
        while (pos < input.length() && isIdChar(input.charAt(pos))) {
            pos++;
        }

        String word = input.substring(start, pos).toLowerCase();

        if (word.equals("true") || word.equals("false")) {
            return new Token(TokenType.BOOLEAN_CONSTANT, word);
        }

        // Reserved words: LexerBase.keywords plus the type names it omits
        if (ALL_KEYWORDS.contains(word)) {
            return new Token(TokenType.KEYWORD, word);
        }

        return new Token(TokenType.ID, word);
    }

    /** [a-zA-Z] */
    private static boolean isLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    /** [a-zA-Z0-9_] — characters allowed after the first letter of an IdTok,
     *  and (plus space) inside a StrToken. */
    private static boolean isIdChar(char ch) {
        return isLetter(ch) || (ch >= '0' && ch <= '9') || ch == '_';
    }
}
