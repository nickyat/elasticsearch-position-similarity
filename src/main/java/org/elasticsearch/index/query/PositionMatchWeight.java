/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.elasticsearch.index.query;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;


import java.io.IOException;
public class PositionMatchWeight extends Weight {
    final Weight weight;

    PositionMatchWeight(Query query, Weight weight) {
        super(query);
        this.weight = weight;
    }

    @Override
    public Explanation explain(LeafReaderContext context, int docID) throws IOException {
        Scorer scorer = scorer(context);
        if (scorer == null) {
            return Explanation.noMatch("PositionMatchScorer: no matching scorer");
        }
        return ((PositionMatchScorer) scorer).explain(docID);
    }

    @Override
    public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        ScorerSupplier scorerSupplier = weight.scorerSupplier(context);
        if (scorerSupplier == null) {
            return null;
        }
        return new ScorerSupplier() {
            @Override
            public Scorer get(long leadCost) throws IOException {
                return new PositionMatchScorer(PositionMatchWeight.this, context, scorerSupplier.get(leadCost));
            }

            @Override
            public long cost() {
                return scorerSupplier.cost();
            }
        };
    }

    @Override
    public boolean isCacheable(LeafReaderContext context) {
        return false; //weight.isCacheable(context);
    }
}
