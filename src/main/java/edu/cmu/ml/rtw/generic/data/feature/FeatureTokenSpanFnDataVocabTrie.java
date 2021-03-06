package edu.cmu.ml.rtw.generic.data.feature;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import org.ardverk.collection.PatriciaTrie;
import org.ardverk.collection.StringKeyAnalyzer;
import org.ardverk.collection.Trie;

import edu.cmu.ml.rtw.generic.data.annotation.DataSet;
import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.Datum.Tools.LabelIndicator;
import edu.cmu.ml.rtw.generic.data.annotation.DatumContext;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.util.BidirectionalLookupTable;
import edu.cmu.ml.rtw.generic.util.CounterTable;
import edu.cmu.ml.rtw.generic.util.ThreadMapper;

/**
 * FeatureTokenSpanFnDataVocabTrie performs the same function as 
 * FeatureTokenSpanFnDataVocab, except that it maintains internal 
 * trie data structures for retrieving elements
 * of the feature vocabulary that are suffixed or prefixed by a
 * given string. This is useful in feature grammar models (like 
 * edu.cmu.ml.rtw.generic.model.SupervisedModelLogistmarGrammression)
 * to construct new features that have existing features as
 * prefixes/suffixes (this is done through FeatureTokenSpanFnFilteredVocab).
 * 
 * @author Bill McDowell
 *
 * @param <D>
 * @param <L>
 */
public class FeatureTokenSpanFnDataVocabTrie<D extends Datum<L>, L> extends FeatureTokenSpanFnDataVocab<D, L> {
	private Trie<String, Double> forwardTrie;
	private Trie<String, Double> backwardTrie;
	
	public FeatureTokenSpanFnDataVocabTrie() {
		super();
	}
	
	public FeatureTokenSpanFnDataVocabTrie(DatumContext<D, L> context) {
		super(context);
		
		this.forwardTrie = new PatriciaTrie<String, Double>(StringKeyAnalyzer.CHAR);
		this.backwardTrie = new PatriciaTrie<String, Double>(StringKeyAnalyzer.CHAR);
	}
	
	@Override
	public boolean init(DataSet<D, L> dataSet) {
		final CounterTable<String> counter = new CounterTable<String>();
		if (FeatureTokenSpanFnDataVocabTrie.this.initMode == InitMode.BY_DATUM) { 
			dataSet.map(new ThreadMapper.Fn<D, Boolean>() {
				@Override
				public Boolean apply(D datum) {
					Map<String, Integer> gramsForDatum = applyFnToDatum(datum);
					for (String gram : gramsForDatum.keySet()) {
						counter.incrementCount(gram);
					}
					return true;
				}
			}, this.context.getMaxThreads());
		} else {
			final Map<String, Set<String>> gramsToDocuments = new ConcurrentHashMap<String, Set<String>>();
			dataSet.map(new ThreadMapper.Fn<D, Boolean>() {
				@Override
				public Boolean apply(D datum) {
					Map<String, Integer> gramsForDatum = applyFnToDatum(datum);
					String documentName = FeatureTokenSpanFnDataVocabTrie.this.tokenExtractor.extract(datum)[0].getDocument().getName();
					for (String gram : gramsForDatum.keySet()) {
						synchronized (this) {
							if (!gramsToDocuments.containsKey(gram))
								gramsToDocuments.put(gram, new HashSet<String>());
							gramsToDocuments.get(gram).add(documentName);
						}
					}
					return true;
				}
			}, this.context.getMaxThreads());
			
			for (Entry<String, Set<String>> entry : gramsToDocuments.entrySet()) {
				counter.incrementCount(entry.getKey(), entry.getValue().size());
			}
		}
		
		counter.removeCountsLessThan(this.minFeatureOccurrence);
		
		this.vocabulary = new BidirectionalLookupTable<String, Integer>(counter.buildIndex());
		
		Map<String, Integer> counts = counter.getCounts();
		double N = dataSet.size();
		for (Entry<String, Integer> entry : counts.entrySet()) {
			int id = this.vocabulary.get(entry.getKey());
			double idf = Math.log(N/(1.0 + entry.getValue()));
			this.idfs.put(id, idf);
			this.forwardTrie.put(entry.getKey(), idf);
			this.backwardTrie.put(new StringBuilder(entry.getKey()).reverse().toString(), idf);
		}
		
		return true;
	}
	
	@Override
	protected <T extends Datum<Boolean>> Feature<T, Boolean> makeBinaryHelper(
			DatumContext<T, Boolean> context, LabelIndicator<L> labelIndicator,
			Feature<T, Boolean> binaryFeature) {
		FeatureTokenSpanFnDataVocabTrie<T, Boolean> binaryFeatureTokenSpanFnDataVocabTrie = (FeatureTokenSpanFnDataVocabTrie<T, Boolean>)super.makeBinaryHelper(context, labelIndicator, binaryFeature);
		binaryFeatureTokenSpanFnDataVocabTrie.forwardTrie = this.forwardTrie;
		binaryFeatureTokenSpanFnDataVocabTrie.backwardTrie = this.backwardTrie;
		
		
		return binaryFeatureTokenSpanFnDataVocabTrie;
	}

	@Override
	protected boolean cloneHelper(Feature<D, L> clone) {
		if (!super.cloneHelper(clone))
			return false;
		
		FeatureTokenSpanFnDataVocabTrie<D, L> cloneTrie = (FeatureTokenSpanFnDataVocabTrie<D, L>)clone;
		cloneTrie.forwardTrie = this.forwardTrie;
		cloneTrie.backwardTrie = this.backwardTrie;
		
		return true;
	}
	
	@Override
	protected boolean fromParseInternalHelper(AssignmentList internalAssignments) {
		if (!super.fromParseInternalHelper(internalAssignments))
			return false;
		if (internalAssignments == null)
			return true;
	
		this.forwardTrie = new PatriciaTrie<String, Double>(StringKeyAnalyzer.CHAR);
		this.backwardTrie = new PatriciaTrie<String, Double>(StringKeyAnalyzer.CHAR);
		
		for (int i = 0; i < this.vocabulary.size(); i++) {
			String term = this.vocabulary.reverseGet(i);
			this.forwardTrie.put(term, this.idfs.get(i));
			this.backwardTrie.put(new StringBuilder(term).reverse().toString(), this.idfs.get(i));
		}
		
		return true;
	}

	@Override
	public Feature<D, L> makeInstance(DatumContext<D, L> context) {
		return new FeatureTokenSpanFnDataVocabTrie<D, L>(context);	
	}

	@Override
	public String getGenericName() {
		return "TokenSpanFnDataVocabTrie";
	}
	
	public Set<String> getVocabularyTermsPrefixedBy(String prefix) {
		synchronized (this.forwardTrie) {
			Map<String, Double> prefixed = this.forwardTrie.prefixMap(prefix);
			Set<String> prefixedSet = new HashSet<String>();
			prefixedSet.addAll(prefixed.keySet()); // Avoid race conditions
			return prefixedSet;
		}
	}
	
	public Set<String> getVocabularyTermsSuffixedBy(String suffix) {
		synchronized (this.backwardTrie) {
			Map<String, Double> suffixed = this.backwardTrie.prefixMap(suffix);
			Set<String> suffixedSet = new HashSet<String>();
			suffixedSet.addAll(suffixed.keySet()); // Avoid race conditions
			return suffixedSet;
		}
	}	
}
