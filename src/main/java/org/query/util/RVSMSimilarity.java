package org.query.util;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.SmallFloat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author CAI
 * @date 2023/9/13
 **/
public abstract class RVSMSimilarity extends Similarity {

    protected boolean discountOverlaps = true;

    public void setDiscountOverlaps(boolean v) {
        this.discountOverlaps = v;
    }

    public boolean getDiscountOverlaps() {
        return this.discountOverlaps;
    }

    public abstract float tf(float var1);

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
        long df = termStats.docFreq();
        long docCount = collectionStats.docCount();
        float idf = this.idf(df, docCount);
        return Explanation.match(idf, "idf(docFreq, docCount)", new Explanation[]{Explanation.match(df, "docFreq, number of documents containing term", new Explanation[0]), Explanation.match(docCount, "docCount, total number of documents with field", new Explanation[0])});
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics[] termStats) {
        double idf = 0.0;
        List<Explanation> subs = new ArrayList();
        TermStatistics[] var6 = termStats;
        int var7 = termStats.length;

        for(int var8 = 0; var8 < var7; ++var8) {
            TermStatistics stat = var6[var8];
            Explanation idfExplain = this.idfExplain(collectionStats, stat);
            subs.add(idfExplain);
            idf += (double)idfExplain.getValue().floatValue();
        }

        return Explanation.match((float)idf, "idf(), sum of:", subs);
    }

    public abstract float idf(long var1, long var3);

    public abstract float lengthNorm(int var1);

    @Override
    public final long computeNorm(FieldInvertState state) {
        int numTerms;
        if (state.getIndexOptions() == IndexOptions.DOCS && state.getIndexCreatedVersionMajor() >= 8) {
            numTerms = state.getUniqueTermCount();
        } else if (this.discountOverlaps) {
            numTerms = state.getLength() - state.getNumOverlap();
        } else {
            numTerms = state.getLength();
        }

        return (long) SmallFloat.intToByte4(numTerms);
    }

    @Override
    public final Similarity.SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        Explanation idf = termStats.length == 1 ? this.idfExplain(collectionStats, termStats[0]) : this.idfExplain(collectionStats, termStats);
        float[] normTable = new float[256];

        for(int i = 1; i < 256; ++i) {
            int length = SmallFloat.byte4ToInt((byte)i);
            float norm = this.lengthNorm(length);
            normTable[i] = norm;
        }

        normTable[0] = 1.0F / normTable[255];
        return new RVSMSimilarity.RVSMScorer(boost, idf, normTable);
    }

    class RVSMScorer extends Similarity.SimScorer {

        private final Explanation idf;
        private final float boost;
        private final float queryWeight;
        final float[] normTable;

        public RVSMScorer(float boost, Explanation idf, float[] normTable) {
            this.idf = idf;
            this.boost = boost;
            this.queryWeight = boost * idf.getValue().floatValue();
            this.normTable = normTable;
        }

        @Override
        public float score(float freq, long norm) {
            float raw = RVSMSimilarity.this.tf(freq) * this.queryWeight;
            float normValue = this.normTable[(int) (norm & 255L)];
            return raw * normValue;
        }

        public Explanation explain(Explanation freq, long norm) {
            return this.explainScore(freq, norm, this.normTable);
        }

        private Explanation explainScore(Explanation freq, long encodedNorm, float[] normTable) {
            List<Explanation> subs = new ArrayList();
            if (this.boost != 1.0F) {
                subs.add(Explanation.match(this.boost, "boost", new Explanation[0]));
            }

            subs.add(this.idf);
            Explanation tf = Explanation.match(RVSMSimilarity.this.tf(freq.getValue().floatValue()), "tf(freq=" + freq.getValue() + "), with freq of:", new Explanation[]{freq});
            subs.add(tf);
            float norm = normTable[(int)(encodedNorm & 255L)];
            Explanation fieldNorm = Explanation.match(norm, "fieldNorm", new Explanation[0]);
            subs.add(fieldNorm);
            return Explanation.match(this.queryWeight * tf.getValue().floatValue() * norm, "score(freq=" + freq.getValue() + "), product of:", subs);
        }
    }
}
