package edu.yu.dbimpl.query;

/** A Predicate is a boolean combination of terms (see the PetiteDB BNF
 * definition).  This class is NOT immutable as clients can add arbitrary
 * number of terms to the Predicate.
 *
 * IMPORTANT: a Predicate currently only provides a "conjunction of terms"
 * semantics.  This decision simplifies implementation considerably but is very
 * limiting.
 *
 * FIXME: allow (at least) OR clauses in predicates.
 *
 * Design note/fixme?: terms() is a List because order (may) matter in the
 * context of e.g., parsing and error reporting; but duplicates are not
 * allowed, and semantically the order of Terms in a predicate doesn't affect
 * logical corretness so a Predicate resembles a Set.  Adding/experimenting
 * with set semantics in order to decide: definitely in flux.
 *
 * Students MAY NOT modify this class in any way!.
 *
 * @author Avraham Leff
 */

import java.util.*;

public class Predicate {

  /** Constructor which creates an "empty" predicate (no terms), thus
   * evaluating to "true".
   */
  public Predicate() {
    // the predicate contains no terms
  }

  /** Constructor which creates a predicate containing a single term.
   *
   * @param t the term
   */
  public Predicate(Term t) {
    if (terms.contains(t)) {
      throw new IllegalArgumentException
        ("Term parameter already in predicate: "+t);
    }
    
    terms.add(t);
  }

  /** Adds a term to those currently encapsulated by the Predicate.  Throws an
   * exception if the term is already present in the predicate.
   *
   * @param t the term
   * @throws IllegalArgumentException if term is already present.
   */
  public void add(final Term t) {
    terms.add(t);
  }

  /** Adds a list of terms to those currently encapsulated by the Predicate.
   *
   * @param thoseTerms the list of terms to be added.
   */
  public void add(final List<Term> thoseTerms) {
    // Validate the entire batch against the current state
    for (Term term : thoseTerms) {
      if (terms.contains(term)) {
        throw new IllegalArgumentException
          ("Batch rejected: duplicate term detected:" + term);
      }
    }
    
    // Validate the input parameter against itself
    long distinctCount = thoseTerms.stream().distinct().count();
    if (distinctCount < thoseTerms.size()) {
      throw new IllegalArgumentException
        ("Batch rejected: incoming list contains duplicates within itself.");
    }
    
    terms.addAll(thoseTerms);
  }
  
  @Override
  public String toString() {
    Iterator<Term> iter = terms.iterator();
    if (!iter.hasNext()) 
      return "";
    String result = iter.next().toString();
    while (iter.hasNext())
      result += " and " + iter.next().toString();
    return result;
  }

  @Override
  // given current definition only allows for AND, we can use set-equality
  // semantics, and not worry about term order or worry about comparing logical
  // operators.  NOT a hack: but very tightly coupled to current BNF.
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
 
    // "null instanceof [type]" also returns false 
    if (!(obj instanceof Predicate)) {
      return false;
    }
         
    final Predicate that = (Predicate) obj;
    // see Set Javadoc for "equals"
    return termsAsSet().equals(that.termsAsSet());
  }
  
  @Override
  public int hashCode() {
    return termsAsSet().hashCode();
  }

  /** Returns an unmodifiable version of the Predicate's terms
   *
   * @return an unmodifiable version of the Predicate's terms
   */
  public List<Term> terms() {
    return Collections.unmodifiableList(terms);
  }

  /** Return an unmodifiable set version of the Predicate's terms
   */
  public Set<Term> termsAsSet() {
    return Collections.unmodifiableSet(new HashSet<>(terms));
  }

  // deliberately package protected: claim that ok to do so because "friends" +
  // List is imutable (but note that the contents of the List is mutable
  final List<Term> terms = new ArrayList<Term>();
} // class

