import org.apache.fulcrum.InitializationException;
import org.apache.fulcrum.ServiceException;
import org.apache.log4j.Category;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.*;

import java.io.IOException;

/**
 * Implementation of {@link SearchService}.
 */
public class LuceneSearchService
        extends org.apache.fulcrum.BaseService implements SearchService
{
    /**
     * Log4j category.
     */
    private static Category cat = Category.getInstance(LuceneSearchService.class);

    /**
     * The analyzer used for searching and indexing. Analyzers have no
     * state so its ok to return the same analyzer to clients.
     */
    private Analyzer analyzer;

    /**
     * Is the index locked?
     */
    private boolean indexLocked = false;

    /**
     * Filesystem location of the index.
     */
    private String searchIndexLocation;

    public void init() throws InitializationException
    {
        searchIndexLocation = getConfiguration().getString(SearchService.INDEX_LOCATION_KEY);
        setInit(true);
    }

    public SearchResults search(Query query) throws ServiceException
    {
        return search(query, null);
    }

    public SearchResults search(Query query, Filter filter) throws ServiceException
    {
        Searcher searcher = null;
        SearchResults results = null;
        try
        {
            searcher = new IndexSearcher(searchIndexLocation);
            Hits hits = searcher.search(query, filter);
            results = new SearchResults(hits);
        }
        catch (IOException ioe)
        {
            throw new ServiceException("Error encountered searching!", ioe);
        }
        finally
        {
            try
            {
                if (searcher != null)
                    searcher.close();
            }
            catch (IOException ioe)
            {
                throw new ServiceException("Error encountered searching!", ioe);
            }
            return results;
        }
    }

    public SearchResults search(Query query, Filter filter,
                                int from, int to) throws ServiceException
    {
        Searcher searcher = null;
        SearchResults results = null;
        try
        {
            searcher = new IndexSearcher(searchIndexLocation);
            Hits hits = searcher.search(query, filter);
            results = new SearchResults(hits, from, to);
        }
        catch (IOException ioe)
        {
            throw new ServiceException("Error encountered searching!", ioe);
        }
        finally
        {
            try
            {
                if (searcher != null)
                    searcher.close();
            }
            catch (IOException ioe)
            {
                throw new ServiceException("Error encountered searching!", ioe);
            }
            return results;
        }
    }

    public void batchIndex() throws ServiceException
    {
        throw new UnsupportedOperationException();
    }

    public boolean isIndexing()
    {
        return indexLocked;
    }

    public Analyzer getAnalyzer()
    {
        if (analyzer == null)
        {
            analyzer = new StandardAnalyzer();
        }
        return analyzer;
    }

    protected synchronized void acquireIndexLock() throws InterruptedException
    {
        while (isIndexing())
        {
            wait(500);
        }
        indexLocked = true;
    }

    protected synchronized void releaseIndexLock()
    {
        indexLocked = false;
    }
}