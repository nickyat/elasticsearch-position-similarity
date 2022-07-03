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

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PositionMatchScorer extends Scorer {
    private final int NOT_FOUND_POSITION = -1;
    private final int HALF_SCORE_POSITION = 5; // position where score should decrease by 50%

    private final LeafReaderContext context;
    private final Scorer scorer;

    PositionMatchScorer(PositionMatchWeight weight, LeafReaderContext context) throws IOException {
        super(weight);
        this.context = context;
        this.scorer = weight.weight.scorer(context);
    }

    @Override
    public DocIdSetIterator iterator() {
        // NPE: Если search не вернул ни одного документа, то scorer == null
        if (scorer == null) {
            return new DocIdSetIterator() {
                @Override
                public int docID() {
                    return -1;
                }

                @Override
                public int nextDoc() throws IOException {
                    return -1;
                }

                @Override
                public int advance(int target) throws IOException {
                    return NO_MORE_DOCS;
                }

                @Override
                public long cost() {
                    return 0;
                }
            };
        }
        return scorer.iterator();
    }

    @Override
    public float getMaxScore(int upTo) throws IOException {
        return scorer.getMaxScore(upTo);
    }

    /**
     * Ранжирование: Чем точнее порядок и ближе положение слов в запросе к началу в искомом поле
     * На каждое слово в запросе после применения analyze мы возможно получим несколько словоформ
     * исходное + russian morphology + синонимы
     * Например: запрос "шнек мотобур" приходит как ((шнек шнека) мотобур)
     *
     * @return [1..0] для одиночного term (1 в начале, 0.5 в середине тд.)
     * Для multi term дополнительный boost:
     * +1 если фраза начинается с первого искомого term
     * +0.5 если и второе
     * +0.25 если и третье
     */
    @Override
    public float score() {

        float totalScore = 0.0f;
        float boost = 0.0f;

        // Так мы можем получить набор query term, но без сохранения порядка
        // "шнек мотобур" становится [мотобур] [шнек шнека]
        //weight.getQuery().visit(QueryVisitor.termCollector(terms));

        // Нам важен порядок term в запросе: пройдемся по коллекции из BooleanQuery в порядке ее формирования
        // "шнек мотобур" сохранит положение term [шнек шнека] [мотобур]

        var positions = new ArrayList<Integer>();
        AtomicInteger sumPositionsAtomic = new AtomicInteger();

        // weight.getQuery() возвращает для нескольких слов в запросе BooleanQuery
        if (weight.getQuery() instanceof BooleanQuery query) {

            for (BooleanClause clause : query.clauses()) {
                Set<Term> subTerm = new HashSet<>();
                clause.getQuery().visit(QueryVisitor.termCollector(subTerm));
                subTerm.forEach(term -> {
                    int pos = position(docID(), term);
                    if (pos >= 0 && !positions.contains(pos)) {
                        positions.add(pos);
                        sumPositionsAtomic.addAndGet(pos);
                    }
                });
            }
            float sumPositions = sumPositionsAtomic.get();
            int termsCount = positions.size();
            // Расчет среднего отклонения позиций
            float averageSubject = sumPositions / termsCount;
            float averageTerms = (termsCount - 1) / 2f; // average(0..n-1)
            float rank = Math.abs(averageSubject - averageTerms);
            for (var i = 0; i < termsCount; i++) {
                var relativePos1 = positions.get(i) - averageSubject;
                var relativePos2 = i - averageTerms;
                rank += Math.abs(relativePos2 - relativePos1);
            }
            var base = 50;
            totalScore = (base - rank) / base;


            // +1 если первый терм в начале слова
            if (positions.get(0) == 0) {
                boost += 1f;
                // +0.5 если и второй терм в начале слова
                if (positions.size() > 1 && positions.get(1) == 1) {
                    boost += 0.5f;
                    // +0.25 если и третий терм в начале слова
                    if (positions.size() > 2 && positions.get(2) == 2) {
                        boost += 0.25f;
                    }
                }
            }

            return totalScore + boost;
        } else {
            // weight.getQuery() возвращает TermQuery если одно слово в запросе
            // Тогда просто возвращаем насколько далеко от начала фразы искомый term
            if (weight.getQuery() instanceof TermQuery query) {
                totalScore = scoreTerm(docID(), query.getTerm());
            }
        }
        return totalScore;
    }

    Explanation explain(int docID) {
        List<Explanation> explanations = new ArrayList<>();

        float totalScore = 0.0f;

        Set<Term> terms = new HashSet<>();
        weight.getQuery().visit(QueryVisitor.termCollector(terms));

        for (Term term : terms) {
            Explanation termExplanation = explainTerm(docID, term);
            explanations.add(termExplanation);
            totalScore += termExplanation.getValue().floatValue();
        }

        return Explanation.match(
                totalScore,
                String.format("score(doc=%d), sum of:", docID),
                explanations
        );
    }

    @Override
    public int docID() {
        return scorer.docID();
    }

    private float scoreTerm(int docID, Term term) {
        float termScore = 0.0f; // default score
        int termPosition = position(docID, term);
        if (NOT_FOUND_POSITION != termPosition)
            termScore = ((float) HALF_SCORE_POSITION) / (HALF_SCORE_POSITION + termPosition);
        return termScore;
    }

    private Explanation explainTerm(int docID, Term term) {
        int termPosition = position(docID, term);
        if (NOT_FOUND_POSITION == termPosition) {
            return Explanation.noMatch(
                    String.format("PositionMatchScorer: no matching terms for field=%s, term=%s", term.field(), term.text())
            );
        } else {
            float termScore = ((float) HALF_SCORE_POSITION) / (HALF_SCORE_POSITION + termPosition);
            String func = HALF_SCORE_POSITION + "/(" + HALF_SCORE_POSITION + "+" + termPosition + ")";
            return Explanation.match(
                    termScore,
                    String.format("PositionMatchScorer: score(field=%s, term=%s, pos=%d, func=%s)", term.field(), term.text(), termPosition, func)
            );
        }
    }

    /**
     * Позиция с 0 искомого term в field
     */
    private int position(int docID, Term term) {
        try {
            Terms terms = context.reader().getTermVector(docID, term.field());
            if (terms == null) {
                return NOT_FOUND_POSITION;
            }
            TermsEnum termsEnum = terms.iterator();
            if (!termsEnum.seekExact(term.bytes())) {
                return NOT_FOUND_POSITION;
            }
            PostingsEnum dpEnum = termsEnum.postings(null, PostingsEnum.ALL);
            dpEnum.nextDoc();
            var position = dpEnum.nextPosition();
            return position;
//            BytesRef payload = dpEnum.getPayload();
//            if (payload == null) {
//                return NOT_FOUND_POSITION;
//            }
//            return PayloadHelper.decodeInt(payload.bytes, payload.offset);
        } catch (UnsupportedOperationException ex) {
            LogManager.getLogger(this.getClass()).error("Unsupported operation, returning position = " +
                    NOT_FOUND_POSITION + " for field = " + term.field());
            return NOT_FOUND_POSITION;
        } catch (Exception ex) {
            LogManager.getLogger(this.getClass()).error("Unexpected exception, returning position = " +
                    NOT_FOUND_POSITION + " for field = " + term.field());
            return NOT_FOUND_POSITION;
        }
    }

    float rank(List<String> words, List<String> terms) {

        // Calculate the positions
        var positions = new ArrayList<Integer>();
        var sumPositions = 0;
        for (var term : terms) {
            int pos = words.indexOf(term);
            positions.add(pos);
            sumPositions += pos;
        }

        // Calculate the difference in average positions
        float averageSubject = (float) sumPositions / terms.size();
        float averageTerms = (terms.size() - 1) / 2f; // среднее(0..n-1)
        float rank = Math.abs(averageSubject - averageTerms);

        for (int i = 0; i < terms.size(); i++) {
            float relativePos1 = positions.get(i) - averageSubject;
            float relativePos2 = i - averageTerms;
            rank += Math.abs(relativePos2 - relativePos1);
        }

        var base = 25 * (terms.size() + 1);
        rank = (base - rank) / base;
        return rank;
    }
}
