package org.apache.lucene.analysis.fr;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import java.io.File;
import java.io.Reader;
import java.util.Hashtable;
import org.apache.lucene.analysis.de.WordlistLoader;

/**
 * Analyzer for french language. Supports an external list of stopwords (words that
 * will not be indexed at all) and an external list of exclusions (word that will
 * not be stemmed, but indexed).
 * A default set of stopwords is used unless an other list is specified, the
 * exclusionlist is empty by default.
 *
 * @author    Patrick Talbot (based on Gerhard Schwarz work for German)
 * @version   $Id$
 */
public final class FrenchAnalyzer extends Analyzer {

	/**
	 * Extended list of typical french stopwords.
	 */
	private String[] FRENCH_STOP_WORDS = {
		"a", "afin", "ai", "ainsi", "apr�s", "attendu", "au", "aujourd", "auquel", "aussi",
		"autre", "autres", "aux", "auxquelles", "auxquels", "avait", "avant", "avec", "avoir",
		"c", "car", "ce", "ceci", "cela", "celle", "celles", "celui", "cependant", "certain",
		"certaine", "certaines", "certains", "ces", "cet", "cette", "ceux", "chez", "ci",
		"combien", "comme", "comment", "concernant", "contre", "d", "dans", "de", "debout",
		"dedans", "dehors", "del�", "depuis", "derri�re", "des", "d�sormais", "desquelles",
		"desquels", "dessous", "dessus", "devant", "devers", "devra", "divers", "diverse",
		"diverses", "doit", "donc", "dont", "du", "duquel", "durant", "d�s", "elle", "elles",
		"en", "entre", "environ", "est", "et", "etc", "etre", "eu", "eux", "except�", "hormis",
		"hors", "h�las", "hui", "il", "ils", "j", "je", "jusqu", "jusque", "l", "la", "laquelle",
		"le", "lequel", "les", "lesquelles", "lesquels", "leur", "leurs", "lorsque", "lui", "l�",
		"ma", "mais", "malgr�", "me", "merci", "mes", "mien", "mienne", "miennes", "miens", "moi",
		"moins", "mon", "moyennant", "m�me", "m�mes", "n", "ne", "ni", "non", "nos", "notre",
		"nous", "n�anmoins", "n�tre", "n�tres", "on", "ont", "ou", "outre", "o�", "par", "parmi",
		"partant", "pas", "pass�", "pendant", "plein", "plus", "plusieurs", "pour", "pourquoi",
		"proche", "pr�s", "puisque", "qu", "quand", "que", "quel", "quelle", "quelles", "quels",
		"qui", "quoi", "quoique", "revoici", "revoil�", "s", "sa", "sans", "sauf", "se", "selon",
		"seront", "ses", "si", "sien", "sienne", "siennes", "siens", "sinon", "soi", "soit",
		"son", "sont", "sous", "suivant", "sur", "ta", "te", "tes", "tien", "tienne", "tiennes",
		"tiens", "toi", "ton", "tous", "tout", "toute", "toutes", "tu", "un", "une", "va", "vers",
		"voici", "voil�", "vos", "votre", "vous", "vu", "v�tre", "v�tres", "y", "�", "�a", "�s",
		"�t�", "�tre", "�"
	};

	/**
	 * Contains the stopwords used with the StopFilter.
	 */
	private Hashtable stoptable = new Hashtable();
	/**
	 * Contains words that should be indexed but not stemmed.
	 */
	private Hashtable excltable = new Hashtable();

	/**
	 * Builds an analyzer.
	 */
	public FrenchAnalyzer() {
		stoptable = StopFilter.makeStopTable( FRENCH_STOP_WORDS );
	}

	/**
	 * Builds an analyzer with the given stop words.
	 */
	public FrenchAnalyzer( String[] stopwords ) {
		stoptable = StopFilter.makeStopTable( stopwords );
	}

	/**
	 * Builds an analyzer with the given stop words.
	 */
	public FrenchAnalyzer( Hashtable stopwords ) {
		stoptable = stopwords;
	}

	/**
	 * Builds an analyzer with the given stop words.
	 */
	public FrenchAnalyzer( File stopwords ) {
		stoptable = WordlistLoader.getWordtable( stopwords );
	}

	/**
	 * Builds an exclusionlist from an array of Strings.
	 */
	public void setStemExclusionTable( String[] exclusionlist ) {
		excltable = StopFilter.makeStopTable( exclusionlist );
	}
	/**
	 * Builds an exclusionlist from a Hashtable.
	 */
	public void setStemExclusionTable( Hashtable exclusionlist ) {
		excltable = exclusionlist;
	}
	/**
	 * Builds an exclusionlist from the words contained in the given file.
	 */
	public void setStemExclusionTable( File exclusionlist ) {
		excltable = WordlistLoader.getWordtable( exclusionlist );
	}

	/**
	 * Creates a TokenStream which tokenizes all the text in the provided Reader.
	 *
	 * @return  A TokenStream build from a StandardTokenizer filtered with
	 * 			StandardFilter, StopFilter, FrenchStemFilter and LowerCaseFilter
	 */
	public final TokenStream tokenStream( String fieldName, Reader reader ) {
		TokenStream result = new StandardTokenizer( reader );
		result = new StandardFilter( result );
		result = new StopFilter( result, stoptable );
		result = new FrenchStemFilter( result, excltable );
		// Convert to lowercase after stemming!
		result = new LowerCaseFilter( result );
		return result;
	}
}

