package edu.cmu.ml.rtw.generic.task.classify.multi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.cmu.ml.rtw.generic.data.Context;
import edu.cmu.ml.rtw.generic.data.annotation.DataSet;
import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.Datum.Tools.Structurizer;
import edu.cmu.ml.rtw.generic.data.feature.fn.Fn;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.structure.FnStructure;
import edu.cmu.ml.rtw.generic.structure.WeightedStructure;
import edu.cmu.ml.rtw.generic.task.classify.MethodClassification;
import edu.cmu.ml.rtw.generic.task.classify.Trainable;
import edu.cmu.ml.rtw.generic.task.classify.meta.PredictionClassification;
import edu.cmu.ml.rtw.generic.task.classify.meta.PredictionClassificationDatum;
import edu.cmu.ml.rtw.generic.util.CounterTable;
import edu.cmu.ml.rtw.generic.util.Pair;
import edu.cmu.ml.rtw.generic.util.Singleton;
import edu.cmu.ml.rtw.generic.util.ThreadMapper;
import edu.cmu.ml.rtw.generic.util.Triple;

public class MethodMultiClassificationPrecedenceScoreByModel extends MethodMultiClassification implements MultiTrainable {
	private List<MethodClassification<?, ?>> methods;
	private List<Structurizer<?, ?, ?>> structurizers;
	private Fn<?, ?> structureTransformFn;
	private boolean trainOnInit = false;
	private int trainIters = 10;
	private boolean trainStructured = false;
	private boolean threadStructure = true;
	private MethodClassification<PredictionClassificationDatum<Boolean>, Boolean> precedenceModel;
	private MethodClassification<?, ?> baselineMethod;
	private Structurizer<?, ?, ?> baselineStructurizer;
	private String[] parameterNames = { "methods", "structurizers", "structureTransformFn", "trainOnInit", "trainIters", "trainStructured", "threadStructure", "precedenceModel", "baselineMethod", "baselineStructurizer" };
	
	private boolean initialized = false;
	
	public MethodMultiClassificationPrecedenceScoreByModel() {
		
	}
	
	public MethodMultiClassificationPrecedenceScoreByModel(Context context) {
		this.context = context;
	}
	
	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("methods")) {
			if (this.methods == null)
				return null;
			Obj.Array array = Obj.array();
			for (MethodClassification<?, ?> method : this.methods)
				array.add(Obj.curlyBracedValue(method.getReferenceName()));
			return array;
		} else if (parameter.equals("structurizers")) {
			if (this.structurizers == null)
				return null;
			Obj.Array array = Obj.array();
			for (Structurizer<?, ?, ?> structurizer : this.structurizers)
				array.add(Obj.curlyBracedValue(structurizer.getReferenceName()));
			return array;
		} else if (parameter.equals("structureTransformFn")) {
			return (this.structureTransformFn == null) ? null : this.structureTransformFn.toParse();
		} else if (parameter.equals("trainOnInit")) {
			return Obj.stringValue(String.valueOf(this.trainOnInit));
		} else if (parameter.equals("trainIters")) {
			return Obj.stringValue(String.valueOf(this.trainIters));
		} else if (parameter.equals("trainStructured")) {
			return Obj.stringValue(String.valueOf(this.trainStructured));
		} else if (parameter.equals("threadStructure")) {
			return Obj.stringValue(String.valueOf(this.threadStructure));
		} else if (parameter.equals("precedenceModel")) {
			return (this.precedenceModel == null) ? null : Obj.curlyBracedValue(this.precedenceModel.getReferenceName());
		} else if (parameter.equals("baselineMethod")) {
			return (this.baselineMethod == null) ? null : Obj.curlyBracedValue(this.baselineMethod.getReferenceName());
		} else if (parameter.equals("baselineStructurizer")) {
			return (this.baselineStructurizer == null) ? null : Obj.curlyBracedValue(this.baselineStructurizer.getReferenceName());
		}
		
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("methods")) {
			if (parameterValue != null) {
				this.methods = new ArrayList<MethodClassification<?, ?>>();
				Obj.Array array = (Obj.Array)parameterValue;
				for (int i = 0; i < array.size(); i++)
					this.methods.add((MethodClassification<?, ?>)this.context.getAssignedMatches(array.get(i)).get(0));
			}
		} else if (parameter.equals("structurizers")) {
			if (parameterValue != null) {
				this.structurizers = new ArrayList<Structurizer<?, ?, ?>>();
				Obj.Array array = (Obj.Array)parameterValue;
				for (int i = 0; i < array.size(); i++)
					this.structurizers.add((Structurizer<?, ?, ?>)this.context.getAssignedMatches(array.get(i)).get(0));
			}
		} else if (parameter.equals("structureTransformFn")) {
			this.structureTransformFn = (parameterValue == null) ? null : this.context.getMatchOrConstructStructureFn(parameterValue);
		} else if (parameter.equals("trainOnInit")) {
			this.trainOnInit = (parameterValue == null) ? null : Boolean.valueOf(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("trainIters")) {
			this.trainIters = (parameterValue == null) ? this.trainIters : Integer.valueOf(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("trainStructured")) {
			this.trainStructured = (parameterValue == null) ? false : Boolean.valueOf(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("threadStructure")) {
			this.threadStructure = Boolean.valueOf(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("precedenceModel")) {
			this.precedenceModel = (parameterValue == null) ? null : (MethodClassification<PredictionClassificationDatum<Boolean>, Boolean>)this.context.getAssignedMatches(parameterValue).get(0);
		} else if (parameter.equals("baselineMethod")) {
			this.baselineMethod = (parameterValue == null) ? null : (MethodClassification<?, ?>)this.context.getAssignedMatches(parameterValue).get(0);
		} else if (parameter.equals("baselineStructurizer")) {
			this.baselineStructurizer = (parameterValue == null) ? null : (Structurizer<?, ?, ?>)this.context.getAssignedMatches(parameterValue).get(0);
		} else {
			return false;
		}
		return true;
	}


	// FIXME Extra pass over the data is unnecessary
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public List<Map<Datum<?>, ?>> classify(List<DataSet<?, ?>> data) {
		List<Map<Datum<?>, Pair<?, Double>>> labelsWithScores = classifyWithScore(data);
		List<Map<Datum<?>, ?>> labels = new ArrayList<>();
		for (Map<Datum<?>, Pair<?, Double>> map : labelsWithScores) {
			Map labelMap = new HashMap();
			for (Entry<Datum<?>, Pair<?, Double>> entry : map.entrySet()) {
				labelMap.put(entry.getKey(), entry.getValue().getFirst());
			}
			labels.add(labelMap);
		}
		
		return labels;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<Map<Datum<?>, Pair<?, Double>>> classifyWithScore(List<DataSet<?, ?>> data) {
		Map<String, WeightedStructure> structures = (Map<String, WeightedStructure>)this.structurizers.get(0).makeStructures();
		
		Map<String, List<Pair<Integer, Triple<Datum, Object, Double>>>> predictions = new HashMap<>();
		for (int i = 0; i < this.methods.size(); i++) {
			MethodClassification<?, ?> method = this.methods.get(i);
			Structurizer structurizer = this.structurizers.get(i);
			
			this.context.getDataTools().getOutputWriter().debugWriteln("Multi-method (precedence score) classifying with method " + method.getReferenceName());
			
			for (int j = 0; j < data.size(); j++) {
				if (!method.matchesData(data.get(j)))
					continue;
				
				Map<?, PredictionClassification> datumPredictions = (Map)method.predict((DataSet)data.get(j));
				for (Entry<?, PredictionClassification> entry : datumPredictions.entrySet()) {
					Datum datum = (Datum)entry.getKey();
					PredictionClassification prediction = entry.getValue();
					Object label = prediction.getLabel();
					if (label == null)
						continue;
					
					Double weight = this.precedenceModel.score(new PredictionClassificationDatum<Boolean>(1, prediction, null), true);
					String structureId = structurizer.getStructureId(datum, label, structures);
					
					if (!predictions.containsKey(structureId))
						predictions.put(structureId, new ArrayList<>());
					
					predictions.get(structureId).add(
							new Pair<>(i,
							new Triple<Datum, Object, Double>(
									datum, 
									label,
									weight)));
				}
			}
		}
		
		this.context.getDataTools().getOutputWriter().debugWriteln("Multi-method (precedence score) structuring data");	
		
		if (this.threadStructure) {
			Singleton<Integer> sCount = new Singleton<>(0);
			ThreadMapper<Entry<String, List<Pair<Integer, Triple<Datum, Object, Double>>>>, Boolean> threads = 
					new ThreadMapper<Entry<String, List<Pair<Integer, Triple<Datum, Object, Double>>>>, Boolean>(
						new ThreadMapper.Fn<Entry<String, List<Pair<Integer, Triple<Datum, Object, Double>>>>, Boolean>() {
				@Override
				public Boolean apply(Entry<String, List<Pair<Integer, Triple<Datum, Object, Double>>>> entry) {
					context.getDataTools().getOutputWriter().debugWriteln("Structuring data for part " + entry.getKey());	
					boolean result = structurePredictions(entry.getValue(), structures);
					synchronized (sCount) {
						sCount.set(sCount.get() + 1);
					}
					context.getDataTools().getOutputWriter().debugWriteln("Finished structuring data for part " + entry.getKey() + " (" + sCount.get() + ")");	
					return result;
				}	
			});
			
			threads.run(predictions.entrySet(), this.context.getMaxThreads());
		} else {
			for (Entry<String, List<Pair<Integer, Triple<Datum, Object, Double>>>> entry : predictions.entrySet()) {
				structurePredictions(entry.getValue(), structures);
			}
		}
		
		this.context.getDataTools().getOutputWriter().debugWriteln("Multi-method (precedence score) finished structuring data");
		
		Map<String, Collection<WeightedStructure>> changes = new HashMap<String, Collection<WeightedStructure>>();
		
		if (this.baselineMethod != null) {
			for (int j = 0; j < data.size(); j++) {
				if (!this.baselineMethod.matchesData(data.get(j)))
					continue;
				
				Map<?, PredictionClassification> datumPredictions = (Map)this.baselineMethod.predict((DataSet)data.get(j));
				
				for (Entry<?, PredictionClassification> entry : datumPredictions.entrySet()) {
					((Structurizer)this.baselineStructurizer).addToStructures(entry.getValue().getDatum(), 
															  entry.getValue().getLabel(),
															  entry.getValue().getScore(),
															  this.baselineMethod.getReferenceName(),
															  structures, 
															  changes);
				}
			}
		}
		
		
		this.context.getDataTools().getOutputWriter().debugWriteln("Multi-method (precedence score) pulling labeled data out of structures...");

		List<Map<Datum<?>, Pair<?, Double>>> classifications = new ArrayList<Map<Datum<?>, Pair<?, Double>>>();
		for (int i = 0; i < data.size(); i++) {
			Structurizer structurizer = null;
			for (int j = 0; j < this.structurizers.size(); j++) {
				if (this.structurizers.get(j).matchesData(data.get(i))) {
					structurizer = this.structurizers.get(j);
					break;
				}
			}
			
			DataSet dataSet = data.get(i);
			Map labeledData = new HashMap();
			CounterTable sourceCounter = new CounterTable();
			CounterTable sourceLengthCounter = new CounterTable();
			
			for (Object o : dataSet) {
				Datum d = (Datum)o;
				
				Map<Object, Pair<Object, Double>> labelsWeighted = structurizer.getLabels(d, structures);
				if (labelsWeighted == null)
					continue;
				
				Object maxLabel = null;
				double maxWeight = Double.NEGATIVE_INFINITY;
				Object maxSource = null;
				for (Entry<Object, Pair<Object, Double>> entry : labelsWeighted.entrySet()) {
					if (Double.compare(entry.getValue().getSecond(), maxWeight) >= 0) {
						maxLabel = entry.getKey();
						maxWeight = entry.getValue().getSecond();
						maxSource = entry.getValue().getFirst();
					}
				}
				
				if (maxLabel != null) {
					labeledData.put(d, new Pair(maxLabel, maxWeight));
					String[] sources = maxSource.toString().split(";");
					for (String source : sources) 
						sourceCounter.incrementCount(source);
					sourceLengthCounter.incrementCount(sources.length);
				}
			}
			
			classifications.add(labeledData);
			
			this.context.getDataTools().getOutputWriter().debugWriteln(data.get(i).getReferenceName() + " Prediction Source Counts");
			this.context.getDataTools().getOutputWriter().debugWriteln(sourceCounter.toString());
			this.context.getDataTools().getOutputWriter().debugWriteln(data.get(i).getReferenceName() + " Prediction Source Length Counts");
			this.context.getDataTools().getOutputWriter().debugWriteln(sourceLengthCounter.toString());
		}
		
		this.context.getDataTools().getOutputWriter().debugWriteln("Multi-method (precedence score) finished classifying data");
		
		return classifications;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private boolean structurePredictions(List<Pair<Integer, Triple<Datum, Object, Double>>> predictions, Map<String, WeightedStructure> structures) {
		Collections.sort(predictions, new Comparator<Pair<Integer, Triple<Datum, Object, Double>>>() {
			@Override
			public int compare(Pair<Integer, Triple<Datum, Object, Double>> o1, Pair<Integer, Triple<Datum, Object, Double>> o2) {
				return Double.compare(o2.getSecond().getThird(), o1.getSecond().getThird());
			}
		});
		
		boolean skippedStructure = false;
		double prevScore = Double.POSITIVE_INFINITY;
		Map<String, Collection<WeightedStructure>> changes = new HashMap<String, Collection<WeightedStructure>>();
		for (Pair<Integer, Triple<Datum, Object, Double>> pair : predictions) {
			int methodIndex = pair.getFirst();
			Triple<Datum, Object, Double> prediction = pair.getSecond();
			Datum datum = prediction.getFirst();
			Object label = prediction.getSecond();
			Double score = prediction.getThird();
			Structurizer structurizer = this.structurizers.get(methodIndex);
			
			// FIXME This is stupid... but an artifact of the way the other sieve method is designed
			
			synchronized (structures) {
				structurizer.addToStructures(datum, label, score, this.methods.get(methodIndex).getReferenceName(), structures, changes);
			}
			
			/*System.out.println("CHANGES");
			for (WeightedStructure w : changes.get(structureId))
				System.out.println(w.toParse(false).toString());
			System.out.println("STRUCTURE");
			System.out.println(structures.get(structureId));
			*/
			
			if (Double.compare(prevScore, score) != 0) {
				List<String> structureIds = new ArrayList<String>(changes.keySet());
				for (String structureId : structureIds) {
					List transformedStructures = ((FnStructure)structureTransformFn).listCompute(structures.get(structureId), changes.get(structureId));
					WeightedStructure firstTransformed = (WeightedStructure)transformedStructures.get(0);
					for (int j = 1; j < transformedStructures.size(); j++)
						firstTransformed = firstTransformed.merge((WeightedStructure)transformedStructures.get(j));		
					
					synchronized (structures) {
						structures.put(structureId, firstTransformed);
					}
				}
				
				changes = new HashMap<String, Collection<WeightedStructure>>();
				skippedStructure = false;
			} else {
				skippedStructure = true;
			}
			
			prevScore = score;
		}
		
		if (skippedStructure) {
			List<String> structureIds = new ArrayList<String>(changes.keySet());
			for (String structureId : structureIds) {
				List transformedStructures = ((FnStructure)structureTransformFn).listCompute(structures.get(structureId), changes.get(structureId));
				WeightedStructure firstTransformed = (WeightedStructure)transformedStructures.get(0);
				for (int j = 1; j < transformedStructures.size(); j++)
					firstTransformed = firstTransformed.merge((WeightedStructure)transformedStructures.get(j));		
				
				synchronized (structures) {
					structures.put(structureId, firstTransformed);
				}
			}
		}
		
		return true;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean init(List<DataSet<?, ?>> testData) {
		if (this.initialized)
			return true;
		
		for (MethodClassification<?, ?> method : this.methods) {
			boolean init = false;
			for (int j = 0; j < testData.size(); j++) {
				if (!method.matchesData(testData.get(j)))
					continue;
				if (method.init((DataSet)testData.get(j))) {
					init = true;
					break;
				}
			}
			
			if (!init)
				method.init();
		}
	
		if (this.baselineMethod != null) {
			boolean init = false;
			for (int j = 0; j < testData.size(); j++) {
				if (!this.baselineMethod.matchesData(testData.get(j)))
					continue;
				if (this.baselineMethod.init((DataSet)testData.get(j))) {
					init = true;
					break;
				}
			}
			
			if (!init)
				this.baselineMethod.init();
		}
			
		this.initialized = true;
		
		if (this.trainOnInit)
			return train();
		else
			return true;
	}

	@Override
	public MethodMultiClassification clone() {
		MethodMultiClassificationPrecedenceScoreByModel clone = new MethodMultiClassificationPrecedenceScoreByModel(this.context);
		if (!clone.fromParse(this.getModifiers(), this.getReferenceName(), toParse()))
			return null;
		
		clone.methods = new ArrayList<MethodClassification<?, ?>>();
		clone.precedenceModel = this.precedenceModel.clone();
		clone.baselineMethod = this.baselineMethod.clone();
		
		for (MethodClassification<?, ?> method : this.methods) {
			clone.methods.add(method.clone());
		}
		
		return clone;
	}

	@Override
	public MethodMultiClassification makeInstance(Context context) {
		return new MethodMultiClassificationPrecedenceScoreByModel(context);
	}

	@Override
	protected boolean fromParseInternal(AssignmentList internalAssignments) {
		return true;
	}

	@Override
	protected AssignmentList toParseInternal() {
		return null;
	}

	@Override
	public String getGenericName() {
		return "PrecedenceScoreByModel";
	}

	// FIXME Need to add possible retraining of precedence model
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean train() {
		if (!this.trainStructured) {
			List<Trainable<?, ?>> trainables = new ArrayList<>(getTrainableMethods().keySet()); 
			for (Trainable<?, ?> trainable : trainables)
				if (!trainable.train())
					return false;
		} else {
			List<Trainable<?, ?>> trainables = new ArrayList<>(getTrainableMethods().keySet()); 
			List<DataSet<?, ?>> data = new ArrayList<>();
			for (Trainable<?, ?> trainable : trainables)
				data.add(trainable.getTrainData());
			
			
			for (int i = 0; i < this.trainIters; i++) {
				List<Map<Datum<?>, ?>> classifiedData = classify(data);
				
				for (int j = 0; j < trainables.size(); j++) {
					Map<Datum<?>, ?> c = classifiedData.get(j);
					if (!trainables.get(j).iterateTraining((Map)c))
						return false;
				}
			}
		}
		
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean setTrainData(List<DataSet<?, ?>> data) {
		Map<Trainable<?, ?>, Integer> trainables = getTrainableToDataIndices(data);
		for (Entry<Trainable<? ,?>, Integer> entry : trainables.entrySet())
			if (!entry.getKey().setTrainData((DataSet)data.get(entry.getValue())))
				return false;
		return true;
	}

	@Override
	public List<DataSet<?, ?>> getTrainData() {
		List<DataSet<?, ?>> data = new ArrayList<>();
		Map<Trainable<?, ?>, List<MethodClassification<?, ?>>> trainables = getTrainableMethods();
		for (Entry<Trainable<?, ?>, List<MethodClassification<?, ?>>> entry : trainables.entrySet()) {
			data.add(entry.getKey().getTrainData());
		}
		
		return data;
	}
	
	private Map<Trainable<?, ?>, Integer> getTrainableToDataIndices(List<DataSet<?, ?>> data) {
		Map<Trainable<?, ?>, List<MethodClassification<?, ?>>> trainables = getTrainableMethods();
		Map<Trainable<?, ?>, Integer> indices = new HashMap<>();
		
		for (Entry<Trainable<?, ?>, List<MethodClassification<?, ?>>> entry : trainables.entrySet()) {
			for (MethodClassification<?, ?> method : entry.getValue()) {
				boolean foundIndex = false;
				for (int i = 0; i < data.size(); i++) {
					if (method.matchesData(data.get(i))) {
						indices.put(entry.getKey(), i);
						foundIndex = true;
						break;
					}
				}
				
				if (foundIndex)
					break;
			}
		}
		
		return indices;
	}
	
	// Map from trainable to list of methods that use that trainable
	private Map<Trainable<?, ?>, List<MethodClassification<?, ?>>> getTrainableMethods() {
		Map<Trainable<?, ?>, List<MethodClassification<?, ?>>> trainableMethods = new HashMap<>();
		
		for (int i = 0; i < this.methods.size(); i++) {
			if (this.methods.get(i).hasTrainable()) {
				if (!trainableMethods.containsKey(this.methods.get(i).getTrainable()))
					trainableMethods.put(this.methods.get(i).getTrainable(), new ArrayList<>());
				trainableMethods.get(this.methods.get(i).getTrainable()).add(this.methods.get(i));
			}
		}
		
		return trainableMethods;
	}

	@Override
	public boolean hasTrainable() {
		return true;
	}

	@Override
	public MultiTrainable getTrainable() {
		return this;
	}
}
