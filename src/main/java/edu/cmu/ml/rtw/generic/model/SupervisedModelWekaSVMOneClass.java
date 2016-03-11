package edu.cmu.ml.rtw.generic.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.platanios.learn.math.matrix.Vector;
import org.platanios.learn.math.matrix.Vector.VectorElement;

import weka.classifiers.functions.LibSVM;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;
import edu.cmu.ml.rtw.generic.data.Context;
import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.Datum.Tools.LabelIndicator;
import edu.cmu.ml.rtw.generic.data.annotation.DatumContext;
import edu.cmu.ml.rtw.generic.data.feature.DataFeatureMatrix;
import edu.cmu.ml.rtw.generic.model.evaluation.metric.SupervisedModelEvaluation;
import edu.cmu.ml.rtw.generic.parse.Assignment;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.util.StringUtil;

public class SupervisedModelWekaSVMOneClass<D extends Datum<L>, L> extends SupervisedModel<D, L> {
	private L targetLabel;
	private L defaultOutlierLabel;
	private String[] parameterNames = { "targetLabel", "defaultOutlierLabel" };
	
	private LibSVM classifier;
	
	public SupervisedModelWekaSVMOneClass() {
		
	}
	
	public SupervisedModelWekaSVMOneClass(DatumContext<D, L> context) {
		this.context = context;
	}
	
	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("targetLabel"))
			return Obj.stringValue(String.valueOf(this.targetLabel.toString()));
		else if (parameter.equals("defaultOutlierLabel"))
			return Obj.stringValue(String.valueOf(this.defaultOutlierLabel.toString()));
		else
			return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("targetLabel"))
			this.targetLabel = this.context.getDatumTools().labelFromString(this.context.getMatchValue(parameterValue));
		else if (parameter.equals("defaultOutlierLabel"))
			this.defaultOutlierLabel = this.context.getDatumTools().labelFromString(this.context.getMatchValue(parameterValue));
		else
			return false;
		return true;
	}

	@Override
	public SupervisedModel<D, L> makeInstance(DatumContext<D, L> context) {
		return new SupervisedModelWekaSVMOneClass<D, L>(context);
	}

	@Override
	protected boolean fromParseInternalHelper(AssignmentList internalAssignments) {
		if (internalAssignments == null || !internalAssignments.contains("classifier"))
			return true;
		
		try {
			this.classifier = (LibSVM)StringUtil.deserializeFromBase64String(((Obj.Value)internalAssignments.get("classifier").getValue()).getStr());
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	@Override
	protected AssignmentList toParseInternalHelper(AssignmentList internalAssignments) {
		if (this.classifier == null) 
			return internalAssignments;
		
		try {
			String classifier = StringUtil.serializeToBase64String(this.classifier);
			internalAssignments.add(
					Assignment.assignmentTyped(null, 
					Context.ObjectType.VALUE.toString(), "classifier", Obj.stringValue(classifier)));
		} catch (IOException e) {
			return null;
		}
		
		return internalAssignments;
	}

	@Override
	protected <T extends Datum<Boolean>> SupervisedModel<T, Boolean> makeBinaryHelper(
			DatumContext<T, Boolean> context, LabelIndicator<L> labelIndicator,
			SupervisedModel<T, Boolean> binaryModel) {
		return binaryModel;
	}

	@Override
	public String getGenericName() {
		return "WekaSVMOneClass";
	}

	@Override
	public boolean train(DataFeatureMatrix<D, L> data,
			DataFeatureMatrix<D, L> testData,
			List<SupervisedModelEvaluation<D, L>> evaluations) {		
		this.context.getDataTools().getOutputWriter().debugWriteln("WekaSVMOneClass constructing data set... ");

		Instances instances = constructInstances(data, true);
		
		this.classifier = new LibSVM();
		
		try {
			this.context.getDataTools().getOutputWriter().debugWriteln("WekaSVMOneClass training model... ");

			this.classifier.buildClassifier(instances);
		} catch (Exception e) {
			this.context.getDataTools().getOutputWriter().debugWriteln("ERROR: " + e.getMessage());
			return false;
		}
		
		return true;
	}

	@Override
	public Map<D, Map<L, Double>> posterior(DataFeatureMatrix<D, L> data) {
		Instances instances = constructInstances(data, false);
		
		Map<D, Map<L, Double>> out = new HashMap<D, Map<L, Double>>();
		try {
			double[][] p = this.classifier.distributionsForInstances(instances);
			int i = 0;
			for (D datum : data.getData()) {
				Map<L, Double> outP = new HashMap<L, Double>();
				outP.put(this.targetLabel, p[i][0]);
				outP.put(this.defaultOutlierLabel, 1.0 - p[i][0]);
				out.put(datum, outP);
				i++;
			}
		
		} catch (Exception e) {
			this.context.getDataTools().getOutputWriter().debugWriteln(e.getMessage());
			return null;
		}
		
		return out;
	}
	
	@Override
	public Map<D, L> classify(DataFeatureMatrix<D, L> data) {
		Instances instances = constructInstances(data, false);

		Map<D, L> out = new HashMap<D, L>();
		try {
			int i = 0;
			for (D datum : data.getData()) {
				int classIndex = Double.compare(this.classifier.classifyInstance(instances.get(i)), 0);
				if (classIndex == 0)
					out.put(datum, this.targetLabel);
				else
					out.put(datum, this.defaultOutlierLabel);
				i++;
			}
		} catch (Exception e) {
			this.context.getDataTools().getOutputWriter().debugWriteln(e.getMessage());
			return null;
		}
		
		return out;
	}

	private Instances constructInstances(DataFeatureMatrix<D, L> data, boolean includeLabels) {
		ArrayList<Attribute> attrs = new ArrayList<Attribute>();
		List<String> featureNames = data.getFeatures().getFeatureVocabularyNames();
		for (String featureName : featureNames) {
			attrs.add(new Attribute(featureName));
		}

		ArrayList<String> outputClassValues = new ArrayList<String>();
		outputClassValues.add("target");
		Attribute outputClass = new Attribute("output", outputClassValues);
		attrs.add(outputClass);
		
		Instances dataSet = new Instances("data", attrs, 10);
		dataSet.setClassIndex(featureNames.size());
		
		for (D datum : data.getData()) {
			Vector v = data.getFeatureVocabularyValues(datum, false);
			Instance instance = new SparseInstance(featureNames.size() + 1);
			for (VectorElement e : v) {
				instance.setValue(attrs.get(e.index()), e.value());
			}
			
			if (includeLabels) {
				if (datum.getLabel().equals(this.targetLabel)) 
					instance.setValue(attrs.get(featureNames.size()), "target");
			}
			dataSet.add(instance);
			instance.setDataset(dataSet);
		}
	
		return dataSet;
	}
}
