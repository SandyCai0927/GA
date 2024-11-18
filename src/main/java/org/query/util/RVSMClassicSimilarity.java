package org.query.util;

import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;

/**
 * @author CAI
 * @date 2023/9/13
 **/
public class RVSMClassicSimilarity extends RVSMSimilarity{

    public float lengthNorm(int numTerms) {
        return (float)(1.0 / Math.sqrt((double)numTerms));
    }

    @Override
    public float tf(float freq) {
        return (float) Math.log(freq) + 1.0F;
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
        long df = termStats.docFreq();
        long docCount = collectionStats.docCount();
        float idf = this.idf(df, docCount);
        return Explanation.match(idf, "idf, computed as log((docCount+1)/(docFreq+1)) + 1 from:", new Explanation[]{Explanation.match(df, "docFreq, number of documents containing term", new Explanation[0]), Explanation.match(docCount, "docCount, total number of documents with field", new Explanation[0])});
    }

    public float idf(long docFreq, long docCount) {
        return (float)(Math.log((double)(docCount + 1L) / (double)(docFreq + 1L)) + 1.0);
    }

    public String toString() {
        return "RVSMClassicSimilarity";
    }
}
