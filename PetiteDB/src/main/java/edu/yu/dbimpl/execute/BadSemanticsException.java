package edu.yu.dbimpl.execute;

/** An unchecked exception that indicates that a purported SQL CRUD command
 * failed a semantic check.  In contrast, BadSyntaxException should be thrown
 * when a purported SQL CRUD command is invalid with respect to PetiteDB's
 * "subset SQL" grammar.
 *
 * Here are some examples of how a syntactically valid command can be
 * semantically invalid.  A non-create command references tables and fields
 * that don't currently exist in the catalog.  A command performs actions on
 * fields that are invalid with respect to the field's type.  A command's
 * predicates (and terms and expressions) must be valid with respect to the
 * column types and supplied values.
 *
 * These (and similar examples) have valid syntax, but given meta-data
 * information that is available to PetiteDB at the time that the command is
 * being executed, is invalid.  PetiteDB must use this exception to communicate
 * this fact to the client.
 *
 * @author Avraham Leff
 */

public class BadSemanticsException extends RuntimeException {
  BadSemanticsException() { super(); }

  BadSemanticsException(final String msg) { super(msg); }

  BadSemanticsException(final String msg, final Throwable t) { super(msg, t); }  
}
