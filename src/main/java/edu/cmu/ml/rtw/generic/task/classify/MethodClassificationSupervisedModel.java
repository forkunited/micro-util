package edu.cmu.ml.rtw.generic.task.classify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.cmu.ml.rtw.generic.data.annotation.DataSet;
import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.DatumContext;
import edu.cmu.ml.rtw.generic.data.feature.DataFeatureMatrix;
import edu.cmu.ml.rtw.generic.data.feature.FeatureSet;
import edu.cmu.ml.rtw.generic.model.SupervisedModel;
import edu.cmu.ml.rtw.generic.model.evaluation.metric.SupervisedModelEvaluation;
import edu.cmu.ml.rtw.generic.parse.Assignment;
import edu.cmu.ml.rtw.generic.parse.Assignment.AssignmentTyped;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.util.Pair;

public class MethodClassificationSupervisedModel<D extends Datum<L>, L> extends MethodClassification<D, L> implements Trainable<D, L> {
	private DataFeatureMatrix<D, L> data;
	private SupervisedModel<D, L> model;
	private FeatureSet<D, L> dataFeatures;
	private boolean trainOnInit = true;
	private SupervisedModelEvaluation<D, L> trainEvaluation; // FIXME Switch this to classification evaluation 
	private String[] parameterNames = { "data", "model", "trainEvaluation", "dataFeatures", "trainOnInit" };
	
	private DataSet<D, L> devData;
	
	private boolean initialized = false;
	
	public MethodClassificationSupervisedModel() {
		this(null);
	}
	
	public MethodClassificationSupervisedModel(DatumContext<D, L> context) {
		super(context);
	}
	
	public SupervisedModel<D, L> getModel() {
		return this.model;
	}

	@Override
	public synchronized String[] getParameterNames() {
		if (this.model != null) {
			String[] parameterNames = Arrays.copyOf(this.parameterNames, this.parameterNames.length + this.model.getParameterNames().length);
			for (int i = 0; i < this.model.getParameterNames().length; i++)
				parameterNames[this.parameterNames.length + i] = this.model.getParameterNames()[i];
			return parameterNames;
		} else 
			return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("data"))
			return (this.data == null) ? null : Obj.curlyBracedValue(this.data.getReferenceName());
		else if (parameter.equals("model"))
			return (this.model == null) ? null :  Obj.curlyBracedValue(this.model.getReferenceName());
		else if (parameter.equals("trainEvaluation"))
			return (this.trainEvaluation == null) ? null : Obj.curlyBracedValue(this.trainEvaluation.getReferenceName());
		else if (parameter.equals("dataFeatures"))
			return (this.dataFeatures == null) ? null : Obj.curlyBracedValue(this.dataFeatures.getReferenceName());
		else if (parameter.equals("trainOnInit")) 
			return Obj.stringValue(String.valueOf(this.trainOnInit));
		else if (parameter.equals("modelInternal")) // FIXME This is a hack to allow for outputting trained model.  It's intentionally left out of parameterNames
			return (this.model == null) ? null : this.model.toParse();
		else if (this.model != null)
			return this.model.getParameterValue(parameter);
		else 
			return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("data"))
			this.data = (parameterValue == null) ? null : this.context.getMatchDataFeatures(parameterValue);
		else if (parameter.equals("model")) {
			if (parameterValue == null)
				this.model = null;
			SupervisedModel<D, L> model = this.context.getMatchModel(parameterValue);
			if (this.model == null || (model != null && !model.getReferenceName().equals(this.model.getReferenceName()))) {
				this.model = model; // FIXME This is a hack to allow deserialization to work without interference from evaluation parameter settings
			}
		} else if (parameter.equals("trainEvaluation"))
			this.trainEvaluation = (parameterValue == null) ? null : this.context.getMatchEvaluation(parameterValue);
		else if (parameter.equals("trainOnInit"))
			this.trainOnInit = (parameterValue == null) ? true : Boolean.valueOf(this.context.getMatchValue(parameterValue));
		else if (parameter.equals("dataFeatures"))
			this.dataFeatures = (parameterValue == null) ? null : this.context.getMatchFeatureSet(parameterValue);
		else if (this.model != null)
			return this.model.setParameterValue(parameter, parameterValue);
		else
			return false;
		
		return true;
	}

	@Override
	public Map<D, L> classify(DataSet<D, L> data) {
		DataFeatureMatrix<D, L> mat = new DataFeatureMatrix<D, L>(this.context, 
																  data.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
																  data,
																  this.data.getFeatures());
		return this.model.classify(mat);
	}
	
	@Override
	public Map<D, Pair<L, Double>> classifyWithScore(DataSet<D, L> data) {
		DataFeatureMatrix<D, L> mat = new DataFeatureMatrix<D, L>(this.context, 
				  data.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
				  data,
				  this.data.getFeatures());
		
		Map<D, Pair<L, Double>> scores = new HashMap<D, Pair<L, Double>>();
		Map<D, L> classifications = this.model.classify(mat);
		Map<D, Map<L, Double>> p = this.model.posterior(mat);
		for (Entry<D, L> entry : classifications.entrySet()) {
			L label = entry.getValue();
			if (label == null)
				continue;
			double score = p.get(entry.getKey()).get(label);
			scores.put(entry.getKey(), new Pair<L, Double>(label, score));
		}
		
		return scores;
	}
	
	public FeatureSet<D, L> getDataFeatures() {
		return (this.dataFeatures != null) ? this.dataFeatures : this.data.getFeatures();
	}

	@Override
	public boolean init(DataSet<D, L> devData) {
		if (this.initialized)
			return true;
		
		if (!this.data.isInitialized() && !this.data.init())
			return false;
		
		if (devData.isBuildable() && !devData.isBuilt() && !devData.build())
			return false;
		
		this.devData = devData;
		
		if (this.trainOnInit)
			this.initialized = train();
		else
			this.initialized = true;
		
		return this.initialized;
	}

	@Override
	protected boolean fromParseInternal(AssignmentList internalAssignments) {
		if (internalAssignments == null)
			return true;
		if (internalAssignments.contains("m")) {
			AssignmentTyped assignment = (AssignmentTyped)internalAssignments.get("m");
			Obj.Function fnObj = (Obj.Function)assignment.getValue();
			SupervisedModel<D, L> model = this.context.getDatumTools().makeModelInstance(fnObj.getName(), this.context);
			if (!model.fromParse(assignment.getModifiers(), this.model.getReferenceName(), fnObj))
				return false;
			this.model = model;
			this.initialized = true;
		}
		
		return true;
	}

	@Override
	protected AssignmentList toParseInternal() {
		AssignmentList assignments = new AssignmentList();
		if (this.initialized && this.model != null)
			assignments.add(Assignment.assignmentTyped(modifiers, "model", "m", this.model.toParse(true)));
		return assignments;
	}

	@Override
	public String getGenericName() {
		return "SupervisedModel";
	}

	@Override
	public synchronized MethodClassification<D, L> clone(String referenceName) {
		SupervisedModel<D, L> temp = this.model;
		this.model = null;
		MethodClassificationSupervisedModel<D, L> clone = new MethodClassificationSupervisedModel<D, L>(this.context);
		if (!clone.fromParse(this.getModifiers(), this.getReferenceName(), toParse()))
			return null;
		this.model = temp;
		clone.model = this.model.clone();
		clone.initialized = this.initialized;
		clone.referenceName = referenceName;
		return clone;
	}

	@Override
	public MethodClassification<D, L> makeInstance(DatumContext<D, L> context) {
		return new MethodClassificationSupervisedModel<D, L>(context);
	}

	@Override
	public boolean train() {
		if (this.data == null)
			return false;
		
		DataFeatureMatrix<D, L> devMat = null;
		
		if (this.devData != null) {
			devMat = new DataFeatureMatrix<D, L>(this.context, 
					  this.devData.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
					  this.devData,
					  this.data.getFeatures());
		}
		
		List<SupervisedModelEvaluation<D, L>> evals = new ArrayList<SupervisedModelEvaluation<D, L>>();
		evals.add(this.trainEvaluation);
		return this.model.train(this.data, devMat, evals);
	}

	
	
	@Override
	public boolean hasTrainable() {
		return true;
	}

	@Override
	public Trainable<D, L> getTrainable() {
		return this;
	}

	@Override
	public boolean setTrainData(DataSet<D, L> data) {
		if (this.data != null) {
			this.dataFeatures = this.data.getFeatures();
		}
		
		this.data = new DataFeatureMatrix<D, L>(this.context, 
				  data.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
				  data,
				  this.dataFeatures);
		
		return true;
	}

	@Override
	public boolean setDevData(DataSet<D, L> data) {
		this.devData = data;
		return true;
	}

	@Override
	public boolean iterateTraining(Map<D, L> constrainedData) {
		if (this.data == null)
			return false;
		
		DataFeatureMatrix<D, L> devMat = null;
		
		if (this.devData != null) {
			devMat = new DataFeatureMatrix<D, L>(this.context, 
					  this.devData.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
					  this.devData,
					  this.data.getFeatures());
		}
		
		List<SupervisedModelEvaluation<D, L>> evals = new ArrayList<SupervisedModelEvaluation<D, L>>();
		evals.add(this.trainEvaluation);
		
		return this.model.iterateTraining(this.data, devMat, evals, constrainedData);
	}

	@Override
	public DataSet<D, L> getTrainData() {
		return this.data.getData();
	}

	@Override
	public L classify(D datum) {
		// FIXME This is slow and stupid
		DataSet<D, L> data = new DataSet<D, L>(null);
		data.add(datum);
		Map<D, L> c = classify(data);
		if (c.size() == 0)
			return null;
		else
			return c.get(datum);
	}

	@Override
	public Pair<L, Double> classifyWithScore(D datum) {
		// FIXME This is slow and stupid
		DataSet<D, L> data = new DataSet<D, L>(null);
		data.add(datum);
		Map<D, Pair<L, Double>> c = classifyWithScore(data);
		if (c.size() == 0)
			return null;
		else
			return c.get(datum);
	}
	
	@Override
	public double score(D datum, L label) {
		// FIXME This is slow and stupid
		DataSet<D, L> data = new DataSet<D, L>(null);
		data.add(datum);
		DataFeatureMatrix<D, L> mat = new DataFeatureMatrix<D, L>(this.context, 
				  data.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
				  data,
				  this.data.getFeatures());
		
		return this.model.posterior(mat).get(datum).get(label);
		
	}
	
	public SupervisedModel<D, L> getSupervisedModel() {
		return this.model;
	}

	@Override
	public Map<D, Double> score(DataSet<D, L> data, L label) {
		DataFeatureMatrix<D, L> mat = new DataFeatureMatrix<D, L>(this.context, 
				  data.getReferenceName() + "_" + this.data.getFeatures().getReferenceName(), 
				  data,
				  this.data.getFeatures());
		
		Map<D, Map<L, Double>> p = this.model.posterior(mat);
		Map<D, Double> scores = new HashMap<>();
		for (Entry<D, Map<L, Double>> entry : p.entrySet())
			scores.put(entry.getKey(), entry.getValue().get(label));
		return scores;
	}


}
